/**
 * 真实 WebSocket 客户端（C-2）
 *
 * 职责（对应《03_成员C》PR C-2 第 1-9 步）：
 * - 申请一次性票据，拼接 ws(s):// 地址，建立 /ws/chat 连接；
 * - 解析统一事件信封，按 streamId + sequence 去重后分发；
 * - 30s 心跳 ping，pong 超时视为断线；
 * - 指数退避 + 随机抖动重连，每次重连重新申请票据；
 * - 重连后按 lastSequence 调补发接口，补齐缺失事件；
 * - 离线发送落 IndexedDB，恢复后用同一 clientMessageId 重发；
 * - chat.cancel 停止生成。
 *
 * 该文件不依赖 Vue / Pinia，全部依赖可注入，便于 Vitest 用假 WebSocket 验证。
 */

import {
  buildChatCancelCommand,
  buildChatSendCommand,
  deleteConversation,
  fetchChatBacklog,
  fetchConversations,
  fetchHistoryMessages,
  fetchWsTicket,
} from './chat.api'
import { createIndexedDbOfflineQueue, type OfflineQueue } from './offlineQueue'
import { isChatEvent } from './types'
import type {
  ChatErrorData,
  ChatPort,
  ChatSendPayload,
  ChatWsTicket,
  RealtimeStatus,
  ServerMessage,
} from './types'

/** 只依赖 WebSocket 用到的字段，方便测试替换 */
export interface SocketLike {
  readyState: number
  send(data: string): void
  close(code?: number, reason?: string): void
  onopen: ((event?: unknown) => void) | null
  onmessage: ((event: { data: unknown }) => void) | null
  onclose: ((event?: unknown) => void) | null
  onerror: ((event?: unknown) => void) | null
}

export interface RealtimeClientOptions {
  fetchTicket?: () => Promise<ChatWsTicket>
  fetchBacklog?: (streamId: string, lastSequence: number) => Promise<Awaited<ReturnType<typeof fetchChatBacklog>>>
  socketFactory?: (url: string) => SocketLike
  queue?: OfflineQueue
  /** 心跳间隔，默认 30s（与后端 heartbeat-interval 一致） */
  heartbeatIntervalMs?: number
  /** pong 超时，默认 10s；超过即判定连接失效 */
  heartbeatTimeoutMs?: number
  /** 重连基础间隔，默认 1s，按 1→2→4→8→16→30s 退避 */
  baseReconnectDelayMs?: number
  /** 重连最大间隔，默认 30s */
  maxReconnectDelayMs?: number
  /** 抖动比例，默认 ±20% */
  jitterRatio?: number
  random?: () => number
  /** 默认按 ticket.wsUrl 拼当前站点域名；单测可覆盖 */
  buildUrl?: (ticket: ChatWsTicket) => string
  /** 持久化未完成流的存储，默认 localStorage；传 null 关闭（单测用） */
  storage?: Storage | null
}

interface PendingSend {
  payload: ChatSendPayload
  streamId?: string
  resolve: () => void
  reject: (error: Error) => void
  promise: Promise<void>
  onMessage?: (message: ServerMessage) => void
}

const OPEN = 1
const ACTIVE_STREAMS_KEY = 'kaoyan-chat-active-streams'

function createRequestId(prefix: string): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `${prefix}-${crypto.randomUUID()}`
  }
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/** 默认地址：/ws/chat + ?ticket=xxx，https 自动用 wss */
export function buildRealtimeUrl(ticket: ChatWsTicket): string {
  const path = ticket.wsUrl || '/ws/chat'
  const absolute = /^wss?:\/\//i.test(path)
  let base = path
  if (!absolute) {
    const loc = typeof location !== 'undefined' ? location : undefined
    const protocol = loc?.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = loc?.host || 'localhost:8080'
    base = `${protocol}//${host}${path.startsWith('/') ? '' : '/'}${path}`
  }
  const separator = base.includes('?') ? '&' : '?'
  return `${base}${separator}ticket=${encodeURIComponent(ticket.ticket)}`
}

export function createRealtimeChatClient(options: RealtimeClientOptions = {}): ChatPort {
  const fetchTicket = options.fetchTicket ?? fetchWsTicket
  const fetchBacklog =
    options.fetchBacklog ?? ((streamId: string, lastSequence: number) => fetchChatBacklog(streamId, lastSequence))
  const queue = options.queue ?? createIndexedDbOfflineQueue()
  const socketFactory =
    options.socketFactory ??
    ((url: string) => new WebSocket(url) as unknown as SocketLike)
  const buildUrl = options.buildUrl ?? buildRealtimeUrl

  const heartbeatIntervalMs = options.heartbeatIntervalMs ?? 30_000
  const heartbeatTimeoutMs = options.heartbeatTimeoutMs ?? 10_000
  const baseReconnectDelayMs = options.baseReconnectDelayMs ?? 1_000
  const maxReconnectDelayMs = options.maxReconnectDelayMs ?? 30_000
  const jitterRatio = options.jitterRatio ?? 0.2
  const random = options.random ?? Math.random

  const listeners = new Set<(message: ServerMessage) => void>()
  const statusListeners = new Set<(status: RealtimeStatus) => void>()
  const pending = new Map<string, PendingSend>()
  /** streamId → 已应用的最大 sequence，用于去重与补发游标 */
  const streamSequences = new Map<string, number>()
  const storage =
    options.storage !== undefined
      ? options.storage
      : typeof localStorage !== 'undefined'
        ? localStorage
        : null

  /** 刷新页面后仍需补发：把未完成流落到本地存储 */
  function persistStreams(): void {
    if (!storage) return
    try {
      storage.setItem(ACTIVE_STREAMS_KEY, JSON.stringify(Object.fromEntries(streamSequences)))
    } catch {
      // 隐私模式写入失败不影响主流程
    }
  }

  function restoreStreams(): void {
    if (!storage) return
    try {
      const raw = storage.getItem(ACTIVE_STREAMS_KEY)
      if (!raw) return
      const saved = JSON.parse(raw) as Record<string, number>
      Object.entries(saved).forEach(([streamId, sequence]) => {
        streamSequences.set(streamId, Number(sequence) || 0)
      })
    } catch {
      // 脏数据直接忽略
    }
  }

  restoreStreams()

  let socket: SocketLike | null = null
  let status: RealtimeStatus = 'disconnected'
  let started = false
  let attempt = 0
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  let pongTimer: ReturnType<typeof setTimeout> | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let awaitingPong = false

  function setStatus(next: RealtimeStatus): void {
    if (status === next) return
    status = next
    statusListeners.forEach((listener) => listener(next))
  }

  function emit(message: ServerMessage): void {
    listeners.forEach((listener) => listener(message))
    // 当前发送方单独注册的回调只收自己的消息，避免重复处理别人的流
    pending.forEach((item) => {
      if (!item.onMessage) return
      if (message.type === 'ack') {
        if (message.data.clientMessageId === item.payload.clientMessageId) item.onMessage(message)
        return
      }
      if (isChatEvent(message) && item.streamId && message.streamId === item.streamId) {
        item.onMessage(message)
      }
    })
  }

  function clearHeartbeat(): void {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
    if (pongTimer) {
      clearTimeout(pongTimer)
      pongTimer = null
    }
    awaitingPong = false
  }

  function clearReconnect(): void {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  function isOpen(): boolean {
    return socket !== null && socket.readyState === OPEN
  }

  function write(payload: unknown): void {
    if (!isOpen()) return
    socket?.send(JSON.stringify(payload))
  }

  function startHeartbeat(): void {
    clearHeartbeat()
    heartbeatTimer = setInterval(() => {
      if (!isOpen()) return
      write({ version: 1, type: 'ping', requestId: createRequestId('ping'), data: {} })
      awaitingPong = true
      if (pongTimer) clearTimeout(pongTimer)
      pongTimer = setTimeout(() => {
        if (awaitingPong) handleDrop()
      }, heartbeatTimeoutMs)
    }, heartbeatIntervalMs)
  }

  function settle(streamId: string, error?: Error): void {
    streamSequences.delete(streamId)
    persistStreams()
    pending.forEach((item, clientMessageId) => {
      if (item.streamId !== streamId) return
      pending.delete(clientMessageId)
      void queue.remove(clientMessageId)
      if (error) item.reject(error)
      else item.resolve()
    })
  }

  function handleIncoming(raw: unknown): void {
    const text = typeof raw === 'string' ? raw : ''
    if (!text) return
    let message: ServerMessage
    try {
      message = JSON.parse(text) as ServerMessage
    } catch {
      return
    }
    if (!message || typeof message.type !== 'string') return

    if (message.type === 'pong') {
      awaitingPong = false
      if (pongTimer) {
        clearTimeout(pongTimer)
        pongTimer = null
      }
      emit(message)
      return
    }

    if (message.type === 'ack') {
      const item = pending.get(message.data.clientMessageId)
      if (item) {
        item.streamId = message.data.streamId
        streamSequences.set(message.data.streamId, 0)
        persistStreams()
        void queue.remove(message.data.clientMessageId)
      }
      emit(message)
      return
    }

    // 聊天业务事件：带 streamId + sequence，按序号单调去重
    if (isChatEvent(message)) {
      const applied = streamSequences.get(message.streamId) ?? 0
      if (message.sequence > 0 && message.sequence <= applied) return
      streamSequences.set(message.streamId, Math.max(applied, message.sequence))
      persistStreams()
      emit(message)
      if (message.type === 'done') {
        settle(message.streamId)
      } else if (message.type === 'error') {
        settle(message.streamId, new Error((message.data as ChatErrorData).message || '生成失败'))
      }
      return
    }

    // 连接层 error：点对点回发起连接，requestId 与 chat.send 的 clientMessageId 对齐
    emit(message)
    const requestId = (message as { requestId?: string }).requestId
    if (requestId) {
      const item = pending.get(requestId)
      if (item) {
        pending.delete(requestId)
        void queue.remove(requestId)
        const reason =
          (message as { data?: { reason?: string } }).data?.reason || '聊天请求被服务端拒绝'
        item.reject(new Error(reason))
      }
    }
  }

  async function flushQueue(): Promise<void> {
    if (!isOpen()) return
    const items = await queue.list()
    items.forEach((item) => {
      // 重连后使用同一 clientMessageId 重发，服务端幂等去重
      write(buildChatSendCommand(item))
    })
  }

  async function recoverStreams(): Promise<void> {
    const streams = [...streamSequences.entries()]
    for (const [streamId, lastSequence] of streams) {
      try {
        const backlog = await fetchBacklog(streamId, lastSequence)
        const events = [...backlog.events].sort((a, b) => a.sequence - b.sequence)
        for (const event of events) {
          const applied = streamSequences.get(streamId) ?? 0
          if (event.sequence > 0 && event.sequence <= applied) continue
          streamSequences.set(streamId, event.sequence)
          emit(event)
          if (event.type === 'done') settle(streamId)
          if (event.type === 'error') {
            settle(streamId, new Error((event.data as ChatErrorData).message || '生成失败'))
          }
        }
        if (backlog.status !== 'STREAMING') {
          // 补发已到终态但没拿到 done（例如 Redis 过期）时清理游标，避免下次重连重复请求
          if (!streamSequences.has(streamId)) continue
          if (backlog.lastSequence <= (streamSequences.get(streamId) ?? 0)) {
            streamSequences.delete(streamId)
          }
        }
      } catch {
        // 404/403：流已过期或无权读取，放弃恢复，等用户手动重试
        streamSequences.delete(streamId)
        persistStreams()
      }
    }
    persistStreams()
  }

  function scheduleReconnect(): void {
    if (!started || reconnectTimer) return
    attempt += 1
    setStatus('reconnecting')
    const exponential = Math.min(
      maxReconnectDelayMs,
      baseReconnectDelayMs * 2 ** Math.max(0, attempt - 1),
    )
    const jitter = exponential * jitterRatio * (random() * 2 - 1)
    const delay = Math.max(0, Math.round(exponential + jitter))
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      void open()
    }, delay)
  }

  function handleDrop(dropped?: SocketLike): void {
    if (dropped && socket && dropped !== socket) return
    clearHeartbeat()
    const current = socket
    socket = null
    if (current) {
      current.onopen = null
      current.onmessage = null
      current.onclose = null
      current.onerror = null
      try {
        current.close()
      } catch {
        // 忽略关闭异常
      }
    }
    if (!started) {
      setStatus('closed')
      return
    }
    scheduleReconnect()
  }

  async function open(): Promise<void> {
    if (!started || socket) return
    setStatus(attempt > 0 ? 'reconnecting' : 'connecting')
    let ticket: ChatWsTicket
    try {
      // 每次（重）连接都重新申请票据：票据一次性且 60s 过期
      ticket = await fetchTicket()
    } catch {
      scheduleReconnect()
      return
    }
    if (!started) return

    const next = socketFactory(buildUrl(ticket))
    socket = next
    next.onopen = () => {
      if (socket !== next) return
      attempt = 0
      setStatus('connected')
      startHeartbeat()
      void flushQueue()
      void recoverStreams()
    }
    next.onmessage = (event) => {
      if (socket !== next) return
      handleIncoming(event.data)
    }
    next.onclose = () => handleDrop(next)
    next.onerror = () => {
      try {
        next.close()
      } catch {
        // 忽略
      }
      handleDrop(next)
    }
  }

  function enqueueOffline(payload: ChatSendPayload): void {
    void queue.enqueue({ ...payload, createdAt: Date.now() })
  }

  return {
    kind: 'realtime',

    connect(onMessage: (message: ServerMessage) => void): void {
      listeners.add(onMessage)
      started = true
      if (status === 'disconnected' || status === 'closed') void open()
    },

    onStatusChange(listener: (next: RealtimeStatus) => void): () => void {
      statusListeners.add(listener)
      listener(status)
      return () => statusListeners.delete(listener)
    },

    listConversations() {
      return fetchConversations()
    },

    listMessages(conversationId: number) {
      return fetchHistoryMessages(conversationId)
    },

    deleteConversation(conversationId: number) {
      return deleteConversation(conversationId)
    },

    sendQuestion(
      payload: ChatSendPayload,
      onMessage?: (message: ServerMessage) => void,
    ): Promise<void> {
      const existing = pending.get(payload.clientMessageId)
      if (existing) {
        // 同一 clientMessageId 重复调用：复用原 promise，不重复上行
        if (onMessage && !existing.onMessage) existing.onMessage = onMessage
        return existing.promise
      }

      let resolve: () => void = () => {}
      let reject: (error: Error) => void = () => {}
      const promise = new Promise<void>((res, rej) => {
        resolve = res
        reject = rej
      })
      pending.set(payload.clientMessageId, {
        payload,
        resolve,
        reject,
        promise,
        onMessage,
      })

      started = true
      if (isOpen()) {
        write(buildChatSendCommand(payload))
      } else {
        // 离线：落 IndexedDB，连接恢复后由 flushQueue 用同一 clientMessageId 重发
        enqueueOffline(payload)
        if (status === 'disconnected' || status === 'closed') void open()
        else if (status === 'connected') scheduleReconnect()
      }
      return promise
    },

    abort(): void {
      // 未 ack 的消息直接从队列撤销
      pending.forEach((item, clientMessageId) => {
        if (item.streamId) return
        pending.delete(clientMessageId)
        void queue.remove(clientMessageId)
        item.resolve()
      })
      // 已经 ack 的流发 chat.cancel，服务端会回 done(cancelled)
      streamSequences.forEach((_sequence, streamId) => {
        write(buildChatCancelCommand(streamId))
      })
    },

    dispose(): void {
      started = false
      clearHeartbeat()
      clearReconnect()
      handleDrop()
      listeners.clear()
      statusListeners.clear()
      setStatus('closed')
    },
  }
}
