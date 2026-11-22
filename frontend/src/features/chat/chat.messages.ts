/**
 * 聊天消息状态机（纯函数，可单测）
 *
 * 设计原则：
 * - 不改入参，返回新数组
 * - 不引 Pinia、不引组件、不出现 WebSocket 细节
 * - 唯一入口 applyServerMessage；delta 按 sequence 单调去重
 */

import { isChatEvent } from './types'
import type {
  ChatErrorData,
  ChatMessageRecord,
  ReferenceItem,
  ReferenceWire,
  ServerMessage,
  ChatViewMessage,
} from './types'

let idCounter = 0

function nextId(prefix: string): string {
  idCounter += 1
  return `${prefix}-${Date.now().toString(36)}-${idCounter}`
}

export function createLocalId(prefix = 'msg'): string {
  return nextId(prefix)
}

/**
 * 生成 clientMessageId（业务幂等键）。
 * 重试必须复用同一个值，后端靠它去重。
 */
export function createClientMessageId(): string {
  const cryptoApi = globalThis.crypto
  if (cryptoApi && typeof cryptoApi.randomUUID === 'function') {
    return cryptoApi.randomUUID()
  }
  return nextId('cmid')
}

/** 新建用户消息：排队态，ack 后转 sending */
export function createUserMessage(
  content: string,
  conversationId: number | null = null,
): ChatViewMessage {
  return {
    localId: createLocalId('user'),
    clientMessageId: createClientMessageId(),
    conversationId: conversationId ?? undefined,
    role: 'user',
    content,
    status: 'queued',
    lastSequence: 0,
    createTime: new Date().toISOString(),
  }
}

/** 新建助手消息：一开始 streaming，由 delta 逐段填充 */
export function createAssistantMessage(streamId?: string): ChatViewMessage {
  return {
    localId: createLocalId('assistant'),
    clientMessageId: createClientMessageId(),
    role: 'assistant',
    content: '',
    status: 'streaming',
    streamId,
    lastSequence: 0,
    createTime: new Date().toISOString(),
  }
}

/**
 * 抹平学生 / 管理员两种引用形状 → 统一展示形状。
 * 学生端：{index, fileName, summary, score}
 * 管理员端：{index, docName, docId, chunkId, score, snippet}
 */
export function normalizeReference(wire: ReferenceWire): ReferenceItem {
  const admin = wire as { docName?: string; snippet?: string }
  const student = wire as { fileName?: string; summary?: string }
  return {
    index: typeof wire.index === 'number' ? wire.index : 0,
    fileName: admin.docName ?? student.fileName ?? '未知文档',
    summary: admin.snippet ?? student.summary ?? '',
    score: typeof wire.score === 'number' ? wire.score : 0,
  }
}

/**
 * 解析后端 referencesJson。
 * A-1 落库的是引用数组 JSON（学生/管理员字段不同），
 * 也兼容 `{items: [...]}` 形态；坏数据降级为空数组，不抛异常。
 */
export function parseReferences(referencesJson: string | null | undefined): ReferenceItem[] {
  if (!referencesJson) return []
  try {
    const parsed: unknown = JSON.parse(referencesJson)
    const list = Array.isArray(parsed)
      ? parsed
      : typeof parsed === 'object' && parsed !== null && Array.isArray((parsed as { items?: unknown }).items)
        ? (parsed as { items: unknown[] }).items
        : []
    return list
      .filter((item): item is ReferenceWire => typeof item === 'object' && item !== null)
      .map(normalizeReference)
  } catch {
    return []
  }
}

/** 历史消息 → 页面消息（服务端 status 高的直接按终态展示） */
export function toViewMessage(item: ChatMessageRecord): ChatViewMessage {
  const role: ChatViewMessage['role'] = item.role === 'assistant' ? 'assistant' : 'user'
  const failed = (item.status ?? '').toUpperCase() === 'FAILED'
  return {
    localId: `history-${item.id}`,
    serverId: item.id,
    clientMessageId: item.clientMessageId ?? `history-${item.id}`,
    conversationId: item.conversationId,
    role,
    content: item.content,
    status: failed ? 'failed' : 'completed',
    lastSequence: item.lastSequence ?? 0,
    streamId: item.streamId ?? undefined,
    finishReason: failed ? 'failed' : 'completed',
    errorMessage: failed ? (item.errorMessage ?? '生成失败') : undefined,
    references: role === 'assistant' ? parseReferences(item.referencesJson) : undefined,
    createTime: item.createTime,
  }
}

function lastIndexOfRole(messages: ChatViewMessage[], role: ChatViewMessage['role']): number {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    if (messages[index].role === role) return index
  }
  return -1
}

/** 按 streamId 精确定位助手消息 */
function exactAssistantIndex(messages: ChatViewMessage[], streamId: string): number {
  return messages.findIndex((item) => item.role === 'assistant' && item.streamId === streamId)
}

/**
 * 定位某条流对应的助手消息：
 * 1. streamId 精确命中；
 * 2. 否则回退到「还没绑定 streamId 的本地占位」（ack 还没到或丢失的场景）；
 * 找不到返回 -1 —— 多标签页收到陌生 streamId 时由 meta 负责新建占位，不能抢别人的消息。
 */
function resolveAssistantIndex(messages: ChatViewMessage[], streamId: string): number {
  const exact = exactAssistantIndex(messages, streamId)
  if (exact >= 0) return exact
  return messages.findIndex(
    (item) => item.role === 'assistant' && !item.streamId && item.status === 'streaming',
  )
}

function patchAt(
  messages: ChatViewMessage[],
  index: number,
  patch: Partial<ChatViewMessage>,
): ChatViewMessage[] {
  if (index < 0) return messages
  const next = messages.slice()
  next[index] = { ...messages[index], ...patch }
  return next
}

/**
 * 应用一条服务端消息。
 *
 * pong            → 与消息渲染无关，忽略（C-2 连接层处理）
 * ack（连接层）    → 用户消息转 sending、回填 conversationId，并把 streamId 绑定到助手占位
 * meta            → 回填 conversationId / userMessageId，绑定 streamId
 * references      → data.items 抹平后挂到助手消息
 * delta           → 按 sequence 单调追加，重复或乱序丢弃
 * done            → finishReason=stop 视为 completed；cancelled 视为 cancelled
 * error（聊天级）  → 助手消息失败，记录 message
 * error（连接层）  → 交给 C-2 连接层，不改消息
 */
export function applyServerMessage(
  messages: ChatViewMessage[],
  message: ServerMessage,
): ChatViewMessage[] {
  if (!message || typeof message.type !== 'string') return messages

  if (message.type === 'pong') return messages

  if (message.type === 'ack') {
    const userIndex = lastIndexOfRole(messages, 'user')
    let next = patchAt(messages, userIndex, {
      status: 'sending',
      conversationId: message.data.conversationId,
    })
    // ack 里的 streamId 要绑定到刚创建的助手占位，后续事件才能按 streamId 匹配
    const assistantIndex = resolveAssistantIndex(next, message.data.streamId)
    if (assistantIndex >= 0 && !next[assistantIndex].streamId) {
      next = patchAt(next, assistantIndex, { streamId: message.data.streamId })
    }
    return next
  }

  // 连接层 error（没有 streamId 或 code 不是字符串）：属于连接状态，不在这里改消息
  if (!isChatEvent(message)) return messages

  const event = message

  switch (event.type) {
    case 'meta': {
      let index = exactAssistantIndex(messages, event.streamId)
      if (index < 0) {
        // 本地没有这条流：可能是另一个标签页发起的回答，按 A 的建议以 streamId 为去重键新建占位
        const created: ChatViewMessage = {
          ...createAssistantMessage(event.streamId),
          conversationId: event.data.conversationId,
        }
        messages = [...messages, created]
        index = messages.length - 1
      }
      let next = patchAt(messages, index, {
        conversationId: event.data.conversationId,
        streamId: event.streamId,
      })
      if (event.data.userMessageId) {
        const userIndex = lastIndexOfRole(next, 'user')
        next = patchAt(next, userIndex, { serverId: event.data.userMessageId })
      }
      return next
    }

    case 'references': {
      const index = resolveAssistantIndex(messages, event.streamId)
      if (index < 0) return messages
      return patchAt(messages, index, {
        references: event.data.items.map(normalizeReference),
        streamId: event.streamId,
      })
    }

    case 'delta': {
      const index = resolveAssistantIndex(messages, event.streamId)
      if (index < 0) return messages
      const current = messages[index]
      // 只接受比已应用序号更大的片段
      if (event.sequence > 0 && event.sequence <= current.lastSequence) return messages
      return patchAt(messages, index, {
        content: current.content + event.data.content,
        status: 'streaming',
        lastSequence: Math.max(current.lastSequence, event.sequence),
        streamId: event.streamId,
      })
    }

    case 'done': {
      const index = resolveAssistantIndex(messages, event.streamId)
      if (index < 0) return messages
      const current = messages[index]
      // A 的口径：新链路以 error 为准，失败后再补的 done 不能把状态翻回成功
      if (current.status === 'failed' && event.data.finishReason !== 'cancelled') return messages
      const finishReason = event.data.finishReason
      const status: ChatViewMessage['status'] =
        finishReason === 'cancelled'
          ? 'cancelled'
          : finishReason === 'failed'
            ? 'failed'
            : 'completed'
      return patchAt(messages, index, {
        status,
        finishReason: status,
        serverId: event.data.messageId ?? current.serverId,
        errorMessage: status === 'failed' ? (current.errorMessage ?? '生成失败') : undefined,
      })
    }

    case 'error': {
      const index = resolveAssistantIndex(messages, event.streamId)
      if (index < 0) return messages
      const data: ChatErrorData = event.data
      return patchAt(messages, index, {
        status: 'failed',
        finishReason: 'failed',
        errorMessage: data.message || '生成失败',
        serverId: data.messageId ?? messages[index].serverId,
      })
    }

    default:
      return messages
  }
}

/** 标记失败（前端侧原因：网络中断等） */
export function markMessageFailed(
  messages: ChatViewMessage[],
  localId: string,
  errorMessage: string,
): ChatViewMessage[] {
  const index = messages.findIndex((item) => item.localId === localId)
  if (index < 0) return messages
  return patchAt(messages, index, { status: 'failed', finishReason: 'failed', errorMessage })
}

/**
 * 标记为用户主动停止：保留已生成文字、中性态、不写 errorMessage。
 * 目前后端还没有 chat.cancel，真实链路由 C-2 做前端中断 + 这条兜底。
 */
export function markMessageCancelled(
  messages: ChatViewMessage[],
  localId: string,
): ChatViewMessage[] {
  const index = messages.findIndex((item) => item.localId === localId)
  if (index < 0) return messages
  return patchAt(messages, index, {
    status: 'cancelled',
    finishReason: 'cancelled',
    errorMessage: undefined,
  })
}

/**
 * 准备重试：只保留用户问题并回退状态，不新建用户消息。
 * - 复用原 clientMessageId（后端靠 (conversation_id, user_id, client_message_id) 唯一约束去重）
 */
export function prepareRetry(messages: ChatViewMessage[], userLocalId: string): ChatViewMessage[] {
  const userIndex = messages.findIndex(
    (item) => item.localId === userLocalId && item.role === 'user',
  )
  if (userIndex < 0) return messages

  const nextUserIndex = messages.findIndex(
    (item, index) => index > userIndex && item.role === 'user',
  )
  const end = nextUserIndex < 0 ? messages.length : nextUserIndex

  const kept = messages.slice(0, userIndex + 1)
  kept[userIndex] = {
    ...messages[userIndex],
    status: 'queued',
    errorMessage: undefined,
    finishReason: undefined,
  }
  return [...kept, ...messages.slice(end)]
}
