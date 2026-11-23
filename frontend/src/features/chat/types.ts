/**
 * 聊天模块前端类型（C-1）
 *
 * 契约来源（逐项对照远程 main 后端代码，不是文档描述）：
 * - 客户端上行：`platform/realtime/protocol/RealtimeEnvelope.java`（B-1）
 * - 服务端连接层：`RealtimeMessageWriter.java` + `RealtimeCommandContext.java`（pong / error / ack）
 * - 聊天业务事件：`modules/chat/stream/ChatStreamEvent.java` + `ChatStreamService.java`（A-1）
 * - WebSocket 接入：`modules/chat/realtime/ChatRealtimeCommandHandler.java`（B-2）
 * - 会话与历史：`ChatController.java` + entity `ChatConversation` / `ChatMessage`
 *
 * 三类服务端消息结构不同，**不能合并成一个信封**：
 * 1. 连接层（pong / error / ack）：只有 version/type/requestId/data，没有 streamId/sequence/serverTime
 * 2. 聊天业务事件（meta/references/delta/done/error）：七个字段齐全，sequence 从 1 连续递增
 * 3. 同名 `error` 有两种：连接层 `{code:number, reason}`、聊天级 `{code:string, message}`
 *    —— 用有没有 streamId 区分（isChatEvent）
 */

/* ══════════════════════════════════════════════════════════════
 * 一、客户端上行（B-1 RealtimeEnvelope）
 * ══════════════════════════════════════════════════════════════ */

export interface ClientEnvelope<TData> {
  version: 1
  type: string
  requestId: string
  /** 仅 chat.send 使用，业务幂等键；重试必须复用同一个值 */
  clientMessageId?: string
  data: TData
}

export interface PingCommand extends ClientEnvelope<Record<string, never>> {
  type: 'ping'
}

/** conversationId 为 null 表示由服务端新建会话；kbId 为 null 表示检索本人全部知识库 */
export interface ChatSendCommand
  extends ClientEnvelope<{
    conversationId: number | null
    kbId: number | null
    content: string
  }> {
  type: 'chat.send'
  clientMessageId: string
}

/* ══════════════════════════════════════════════════════════════
 * 二、服务端连接层（无 streamId / sequence / serverTime）
 * ══════════════════════════════════════════════════════════════ */

export interface PongMessage {
  version: 1
  type: 'pong'
  requestId?: string
  data: Record<string, never>
}

/** 连接层错误：{code:number, reason, retryable}（RealtimeMessageWriter） */
export interface RealtimeErrorMessage {
  version: 1
  type: 'error'
  requestId?: string
  data: {
    code: number
    reason: string
    retryable: boolean
  }
}

/**
 * 回执：B-2 收到 chat.send 后点对点回发起连接。
 * 注意 data 里带 streamId，但**外层没有** streamId/sequence —— 它是连接层消息。
 */
export interface AckMessage {
  version: 1
  type: 'ack'
  requestId: string
  data: AckData
}

export interface AckData {
  conversationId: number
  streamId: string
  clientMessageId: string
}

/* ══════════════════════════════════════════════════════════════
 * 三、聊天业务事件（A-1 冻结：带 streamId / sequence / serverTime）
 * ══════════════════════════════════════════════════════════════ */

export type ChatEventType = 'meta' | 'references' | 'delta' | 'done' | 'error'

export interface MetaData {
  conversationId: number
  /** 本次提问对应的用户消息 ID（A-1 在 meta 里给，不在 ack 里） */
  userMessageId?: number | null
  model: string
}

/**
 * 引用条目有两种形状（同一接口，由后端按 isAdmin() 决定，前端不按角色拼字段）：
 * - 普通学生（脱敏，绝不返回 docId / chunkId）：index / fileName / summary / score
 * - 管理员（可追溯）：index / docName / docId / chunkId / score / snippet
 * 各字段都可能缺省，展示前交给 normalizeReference 兜底。
 */
export interface ReferenceWireStudent {
  index?: number
  fileName?: string
  summary?: string
  score?: number
}

export interface ReferenceWireAdmin {
  index?: number
  docName?: string
  docId?: number
  chunkId?: number
  score?: number
  snippet?: string
}

export type ReferenceWire = ReferenceWireStudent | ReferenceWireAdmin

/** references 事件的 data 是 `{ items: [...] }`，不是数组本身 */
export interface ReferencesData {
  items: ReferenceWire[]
}

export interface DeltaData {
  content: string
}

/**
 * done：`done` 本身就代表正常完成，`finishReason: "stop"` 是正常结束值（不要再造 "completed"）。
 * `"length"` 表示被截断，前端只当参考；`"cancelled"` / `"failed"` 等 B 补取消能力后再用。
 * 注意 done 里的 token 字段当前是 0 占位，不能拿来判断截断。
 */
export interface DoneData {
  messageId: number | null
  model?: string
  promptTokens?: number
  completionTokens?: number
  finishReason: 'stop' | 'length' | 'cancelled' | 'failed'
}

/** 聊天级错误：{code:string, message, retryable, messageId, partialChars}（A-1） */
export interface ChatErrorData {
  /** 业务码是字符串枚举，如 INVALID_REQUEST / CONVERSATION_NOT_FOUND / INTERNAL_ERROR */
  code: string
  message: string
  retryable: boolean
  messageId?: number | null
  /** 失败前已生成的字符数 */
  partialChars?: number
}

export interface ChatEventDataMap {
  meta: MetaData
  references: ReferencesData
  delta: DeltaData
  done: DoneData
  error: ChatErrorData
}

/** 单个业务事件信封（A-1 冻结字段顺序） */
export type ChatEvent<TType extends ChatEventType = ChatEventType> = {
  version: 1
  type: TType
  requestId: string
  streamId: string
  /** 从 1 连续递增，客户端据此判丢包 / 补发 */
  sequence: number
  serverTime: string
  data: ChatEventDataMap[TType]
}

/** 分发用：按 type 可判别 data，避免到处 as */
export type ChatServerEvent = { [K in ChatEventType]: ChatEvent<K> }[ChatEventType]

/** 端口回调收到的所有服务端消息 */
export type ServerMessage = PongMessage | RealtimeErrorMessage | AckMessage | ChatServerEvent

/**
 * 区分聊天级 error 与连接层 error（A 确认的判据）：
 * 有 streamId 且 `data.code` 是字符串 → 聊天流错误；否则连接层错误。
 * 聊天级 code 是 LLM/业务错误码（INTERNAL_ERROR、LLM_TIMEOUT…），连接层 code 是数字协议码。
 */
export function isChatEvent(message: ServerMessage): message is ChatServerEvent {
  if (typeof (message as { streamId?: unknown }).streamId !== 'string') return false
  if (message.type === 'error') {
    return typeof (message.data as { code?: unknown }).code === 'string'
  }
  return true
}

/* ══════════════════════════════════════════════════════════════
 * 四、后端 REST 实体（ChatController / entity 原样字段）
 * ══════════════════════════════════════════════════════════════ */

/** GET /api/v1/chat/conversations（仅需登录） */
export interface ChatConversationRecord {
  id: number
  userId: number
  title: string
  kbId: number | null
  deleted: number
  createTime: string
  updateTime: string
}

/** GET /api/v1/chat/conversations/{id}/messages（权限 chat:send） */
export interface ChatMessageRecord {
  id: number
  conversationId: number
  userId: number
  role: 'user' | 'assistant' | 'system'
  content: string
  /** JSON 字符串（A-1 存的是引用数组），必须经 parseReferences 解析 */
  referencesJson: string | null
  /** 以下为 A-1/A-2 新增字段，后端会返回 */
  clientMessageId?: string | null
  replyToId?: number | null
  streamId?: string | null
  /** PENDING / STREAMING / COMPLETED / FAILED */
  status?: string | null
  lastSequence?: number | null
  errorMessage?: string | null
  completedAt?: string | null
  modelName?: string | null
  promptTokens?: number | null
  completionTokens?: number | null
  createTime: string
}

/* ══════════════════════════════════════════════════════════════
 * 五、页面视图模型
 * ══════════════════════════════════════════════════════════════ */

/**
 * 助手消息状态
 * completed = 模型正常生成完毕（后端 finishReason=stop）
 * cancelled = 用户主动停止（等 B 实现 chat.cancel 后才会从服务端到达）
 * failed    = 异常
 */
export type AssistantStatus = 'queued' | 'sending' | 'streaming' | 'completed' | 'cancelled' | 'failed'

/** 引用条目（展示层统一形状，已抹平学生/管理员两种后端字段） */
export interface ReferenceItem {
  index: number
  /** 学生端是脱敏文件名，管理员端是真实文档名 */
  fileName: string
  /** 学生端是摘要，管理员端是切片内容 */
  summary: string
  score: number
}

export interface ChatViewMessage {
  localId: string
  serverId?: number
  clientMessageId: string
  conversationId?: number
  role: 'user' | 'assistant'
  content: string
  status: AssistantStatus
  streamId?: string
  /** 已应用的最大 delta 序号，用于补发去重 */
  lastSequence: number
  /** 仅 failed 时有值 */
  errorMessage?: string
  finishReason?: 'completed' | 'cancelled' | 'failed'
  references?: ReferenceItem[]
  createTime?: string
}

export type RealtimeStatus = 'disconnected' | 'connecting' | 'connected' | 'reconnecting' | 'closed'

/** POST /api/v1/chat/ws-ticket 响应（B 冻结：chat:send，60s 一次性） */
export interface ChatWsTicket {
  ticket: string
  expiresIn: number
  wsUrl: string
}

export interface ChatSendPayload {
  conversationId: number | null
  kbId: number | null
  content: string
  clientMessageId: string
  requestId: string
}

/**
 * 数据来源端口（Port-Adapter）。
 * C-1 注入 mock；C-2 注入真实 WebSocket 客户端，store 与页面无需改动。
 */
export interface ChatPort {
  listConversations(): Promise<ChatConversationRecord[]>
  listMessages(conversationId: number): Promise<ChatMessageRecord[]>
  deleteConversation(conversationId: number): Promise<void>
  sendQuestion(payload: ChatSendPayload, onMessage: (message: ServerMessage) => void): Promise<void>
  /** 停止当前生成（真实链路需 B 提供 chat.cancel，目前只做前端中断） */
  abort(): void
}
