/**
 * C-2 实时客户端单测（不依赖真实网络）
 *
 * 覆盖《03_成员C》PR C-2 的验证点：
 * 1) 心跳 ping 无 pong → 判定断线并重连
 * 2) 每次重连重新申请票据
 * 3) delta 按 sequence 去重，重复事件不产生重复文字
 * 4) 相同 clientMessageId 不重复上行、不重复入待发队列
 * 5) 重连后按 lastSequence 补发遗漏事件
 * 6) 停止生成发送 chat.cancel
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryOfflineQueue } from '../offlineQueue'
import { createRealtimeChatClient, type SocketLike } from '../realtimeClient'
import type {
  ChatSendPayload,
  ChatServerEvent,
  ChatStreamBacklog,
  ChatWsTicket,
  ServerMessage,
} from '../types'

class FakeSocket implements SocketLike {
  static instances: FakeSocket[] = []

  readonly url: string
  readyState = 0
  sent: string[] = []
  onopen: ((event?: unknown) => void) | null = null
  onmessage: ((event: { data: unknown }) => void) | null = null
  onclose: ((event?: unknown) => void) | null = null
  onerror: ((event?: unknown) => void) | null = null

  constructor(url: string) {
    this.url = url
    FakeSocket.instances.push(this)
  }

  send(data: string): void {
    this.sent.push(data)
  }

  close(): void {
    if (this.readyState === 3) return
    this.readyState = 3
    this.onclose?.()
  }

  open(): void {
    this.readyState = 1
    this.onopen?.()
  }

  /** 模拟服务端断线（浏览器触发 onclose） */
  drop(): void {
    if (this.readyState === 3) return
    this.readyState = 3
    this.onclose?.()
  }

  receive(message: ServerMessage): void {
    this.onmessage?.({ data: JSON.stringify(message) })
  }

  frames(): Array<Record<string, unknown>> {
    return this.sent.map((item) => JSON.parse(item) as Record<string, unknown>)
  }
}

function payload(overrides: Partial<ChatSendPayload> = {}): ChatSendPayload {
  return {
    content: '考研数学怎么复习？',
    conversationId: null,
    kbId: null,
    clientMessageId: 'client-1',
    requestId: 'client-1',
    ...overrides,
  }
}

function delta(streamId: string, sequence: number, content: string): ChatServerEvent {
  return {
    version: 1,
    type: 'delta',
    requestId: 'r-1',
    streamId,
    sequence,
    serverTime: new Date().toISOString(),
    data: { content },
  }
}

function done(
  streamId: string,
  sequence: number,
  finishReason: 'stop' | 'cancelled' = 'stop',
): ChatServerEvent {
  return {
    version: 1,
    type: 'done',
    requestId: 'r-1',
    streamId,
    sequence,
    serverTime: new Date().toISOString(),
    data: { messageId: 1, finishReason },
  }
}

function ack(streamId: string, clientMessageId = 'client-1'): ServerMessage {
  return {
    version: 1,
    type: 'ack',
    requestId: clientMessageId,
    data: { conversationId: 7, streamId, clientMessageId },
  } as ServerMessage
}

function createMemoryStorage(): Storage {
  const map = new Map<string, string>()
  return {
    get length() {
      return map.size
    },
    clear: () => map.clear(),
    getItem: (key: string) => map.get(key) ?? null,
    key: (index: number) => [...map.keys()][index] ?? null,
    removeItem: (key: string) => void map.delete(key),
    setItem: (key: string, value: string) => void map.set(key, value),
  }
}

interface Harness {
  client: ReturnType<typeof createRealtimeChatClient>
  ticketCalls: () => number
  backlogCalls: Array<{ streamId: string; lastSequence: number }>
  backlog: ChatStreamBacklog | null
  messages: ServerMessage[]
  statuses: string[]
  lastSocket: () => FakeSocket
}

function setup(storage: Storage = createMemoryStorage()): Harness {
  FakeSocket.instances = []
  let ticketCount = 0
  const backlogCalls: Array<{ streamId: string; lastSequence: number }> = []
  const state: { backlog: ChatStreamBacklog | null } = { backlog: null }

  const client = createRealtimeChatClient({
    fetchTicket: async (): Promise<ChatWsTicket> => {
      ticketCount += 1
      return { ticket: `ticket-${ticketCount}`, expiresIn: 60, wsUrl: '/ws/chat' }
    },
    fetchBacklog: async (streamId: string, lastSequence: number) => {
      backlogCalls.push({ streamId, lastSequence })
      return (
        state.backlog ?? {
          streamId,
          status: 'STREAMING',
          lastSequence,
          assistantMessageId: null,
          events: [],
        }
      )
    },
    socketFactory: (url) => new FakeSocket(url),
    queue: createMemoryOfflineQueue(),
    heartbeatIntervalMs: 30_000,
    heartbeatTimeoutMs: 10_000,
    baseReconnectDelayMs: 1_000,
    maxReconnectDelayMs: 30_000,
    jitterRatio: 0,
    random: () => 0.5,
    buildUrl: (ticket) => `ws://test/ws/chat?ticket=${ticket.ticket}`,
    storage,
  })

  const messages: ServerMessage[] = []
  const statuses: string[] = []
  client.onStatusChange?.((status) => statuses.push(status))
  client.connect?.((message) => messages.push(message))

  return {
    client,
    ticketCalls: () => ticketCount,
    backlogCalls,
    get backlog() {
      return state.backlog
    },
    set backlog(value: ChatStreamBacklog | null) {
      state.backlog = value
    },
    messages,
    statuses,
    lastSocket: () => FakeSocket.instances[FakeSocket.instances.length - 1],
  }
}

describe('realtimeClient', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('心跳 ping 无 pong 时判定断线，并用新票据重连', async () => {
    const harness = setup()
    await vi.advanceTimersByTimeAsync(0)

    const first = harness.lastSocket()
    expect(first.url).toBe('ws://test/ws/chat?ticket=ticket-1')
    first.open()
    expect(harness.statuses.at(-1)).toBe('connected')

    // 30s 后发 ping，等待 pong
    await vi.advanceTimersByTimeAsync(30_000)
    expect(first.frames().some((frame) => frame.type === 'ping')).toBe(true)

    // 10s 内没有 pong → 断开并退避重连
    await vi.advanceTimersByTimeAsync(10_000)
    expect(harness.statuses.at(-1)).toBe('reconnecting')

    await vi.advanceTimersByTimeAsync(1_000)
    expect(harness.ticketCalls()).toBe(2)
    expect(FakeSocket.instances).toHaveLength(2)
    expect(harness.lastSocket().url).toBe('ws://test/ws/chat?ticket=ticket-2')

    harness.client.dispose?.()
  })

  it('收到 pong 不会误判断线', async () => {
    const harness = setup()
    await vi.advanceTimersByTimeAsync(0)
    const socket = harness.lastSocket()
    socket.open()

    await vi.advanceTimersByTimeAsync(30_000)
    socket.receive({ version: 1, type: 'pong', requestId: 'p', data: {} } as ServerMessage)
    await vi.advanceTimersByTimeAsync(10_000)

    expect(harness.statuses.at(-1)).toBe('connected')
    expect(FakeSocket.instances).toHaveLength(1)

    harness.client.dispose?.()
  })

  it('delta 按 sequence 去重，重复事件不产生重复文字', async () => {
    const harness = setup()
    await vi.advanceTimersByTimeAsync(0)
    const socket = harness.lastSocket()
    socket.open()
    socket.receive(ack('stream-1'))

    socket.receive(delta('stream-1', 1, '第一段'))
    socket.receive(delta('stream-1', 2, '第二段'))
    socket.receive(delta('stream-1', 2, '第二段'))
    socket.receive(delta('stream-1', 3, '第三段'))

    const contents = harness.messages
      .filter((message) => message.type === 'delta')
      .map((message) => (message as { data: { content: string } }).data.content)
    expect(contents).toEqual(['第一段', '第二段', '第三段'])

    harness.client.dispose?.()
  })

  it('相同 clientMessageId 只上行一次，重复调用复用同一个 Promise', async () => {
    const harness = setup()
    await vi.advanceTimersByTimeAsync(0)
    const socket = harness.lastSocket()
    socket.open()

    const first = harness.client.sendQuestion(payload())
    const second = harness.client.sendQuestion(payload())

    const sends = socket.frames().filter((frame) => frame.type === 'chat.send')
    expect(sends).toHaveLength(1)
    expect((sends[0].clientMessageId as string)).toBe('client-1')

    socket.receive(ack('stream-1'))
    socket.receive(done('stream-1', 1))
    await expect(Promise.all([first, second])).resolves.toBeDefined()

    harness.client.dispose?.()
  })

  it('离线发送进入待发队列，连接恢复后用同一 clientMessageId 重发', async () => {
    const harness = setup()
    // 不推进到 connected，直接发送
    const pending = harness.client.sendQuestion(payload({ clientMessageId: 'offline-1' }))

    await vi.advanceTimersByTimeAsync(0)
    const socket = harness.lastSocket()
    socket.open()
    await vi.advanceTimersByTimeAsync(0)

    const sends = socket.frames().filter((frame) => frame.type === 'chat.send')
    expect(sends).toHaveLength(1)
    expect(sends[0].clientMessageId).toBe('offline-1')

    socket.receive(ack('stream-1', 'offline-1'))
    socket.receive(done('stream-1', 1))
    await pending

    harness.client.dispose?.()
  })

  it('重连后按 lastSequence 补发遗漏事件，且不重复已应用事件', async () => {
    const harness = setup()
    await vi.advanceTimersByTimeAsync(0)
    const first = harness.lastSocket()
    first.open()
    first.receive(ack('stream-1'))
    first.receive(delta('stream-1', 1, '已收到'))
    first.drop()

    harness.backlog = {
      streamId: 'stream-1',
      status: 'STREAMING',
      lastSequence: 3,
      assistantMessageId: 1,
      events: [delta('stream-1', 3, '补发三'), delta('stream-1', 2, '补发二')],
    }

    await vi.advanceTimersByTimeAsync(1_000)
    const second = harness.lastSocket()
    expect(second).not.toBe(first)
    second.open()
    await vi.advanceTimersByTimeAsync(0)

    expect(harness.backlogCalls).toEqual([{ streamId: 'stream-1', lastSequence: 1 }])
    const contents = harness.messages
      .filter((message) => message.type === 'delta')
      .map((message) => (message as { data: { content: string } }).data.content)
    expect(contents).toEqual(['已收到', '补发二', '补发三'])

    harness.client.dispose?.()
  })

  it('刷新页面后从本地存储恢复未完成流，并按 lastSequence 补发', async () => {
    const storage = createMemoryStorage()
    const first = setup(storage)
    await vi.advanceTimersByTimeAsync(0)
    const firstSocket = first.lastSocket()
    firstSocket.open()
    firstSocket.receive(ack('stream-1'))
    firstSocket.receive(delta('stream-1', 1, '刷新前'))
    first.client.dispose?.()

    const second = setup(storage)
    second.backlog = {
      streamId: 'stream-1',
      status: 'STREAMING',
      lastSequence: 2,
      assistantMessageId: 1,
      events: [delta('stream-1', 2, '刷新后')],
    }
    await vi.advanceTimersByTimeAsync(0)
    second.lastSocket().open()
    await vi.advanceTimersByTimeAsync(0)

    expect(second.backlogCalls).toEqual([{ streamId: 'stream-1', lastSequence: 1 }])
    expect(
      second.messages
        .filter((message) => message.type === 'delta')
        .map((message) => (message as { data: { content: string } }).data.content),
    ).toEqual(['刷新后'])

    second.client.dispose?.()
  })

  it('停止生成对已 ack 的流发送 chat.cancel', async () => {
    const harness = setup()
    await vi.advanceTimersByTimeAsync(0)
    const socket = harness.lastSocket()
    socket.open()

    const pending = harness.client.sendQuestion(payload())
    socket.receive(ack('stream-1'))
    socket.receive(delta('stream-1', 1, '生成中'))
    harness.client.abort()

    const cancels = socket.frames().filter((frame) => frame.type === 'chat.cancel')
    expect(cancels).toHaveLength(1)
    expect((cancels[0].data as { streamId: string }).streamId).toBe('stream-1')

    socket.receive(done('stream-1', 2, 'cancelled'))
    await pending

    harness.client.dispose?.()
  })
})
