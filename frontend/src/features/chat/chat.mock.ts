/**
 * 聊天 Mock 数据源（C-1）
 *
 * 形状严格照 B-2 / A-1 的真实实现：
 * - ack 是**连接层**消息：{version,type,requestId,data:{conversationId,streamId,clientMessageId}}，外层没有 streamId
 * - meta 带 userMessageId；references 的 data 是 {items:[...]}（学生版脱敏字段）
 * - done 的 finishReason 后端目前只给 "stop"
 * - 聊天级 error 是 {code:string, message, retryable, messageId, partialChars}
 *
 * 交互验证点：
 * - 正常提问 → ack → meta(seq1) → references(seq2) → delta(seq3..) → done
 * - 输入含「失败/报错/error」→ delta 后 error
 * - 点停止生成 → mock 补一条 done(cancelled)（真实链路等 B 实现 chat.cancel，C-2 再接）
 */

import type {
  ChatConversationRecord,
  ChatEvent,
  ChatEventType,
  ChatHistoryPage,
  ChatHistoryQuery,
  ChatMessageRecord,
  ChatPort,
  ChatSendPayload,
  ChatServerEvent,
  ReferenceWireStudent,
  ServerMessage,
} from './types'

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

const MOCK_USER_ID = 1001

export const MOCK_CONVERSATIONS: ChatConversationRecord[] = [
  {
    id: 1,
    userId: MOCK_USER_ID,
    title: '线性表的链式存储为什么更适合频繁插入',
    kbId: null,
    deleted: 0,
    createTime: '2026-11-22T10:05:00+08:00',
    updateTime: '2026-11-22T10:12:00+08:00',
  },
  {
    id: 2,
    userId: MOCK_USER_ID,
    title: '操作系统进程调度算法对比',
    kbId: null,
    deleted: 0,
    createTime: '2026-11-21T21:30:00+08:00',
    updateTime: '2026-11-21T21:40:00+08:00',
  },
  {
    id: 3,
    userId: MOCK_USER_ID,
    title: '计算机网络三次握手为什么不是两次',
    kbId: null,
    deleted: 0,
    createTime: '2026-11-20T19:00:00+08:00',
    updateTime: '2026-11-20T19:05:00+08:00',
  },
]

/** 学生视角引用（脱敏）：与 A-1 toReferences 的普通用户分支一致 */
export const MOCK_REFERENCES: ReferenceWireStudent[] = [
  {
    index: 1,
    fileName: '数据***.pdf',
    summary: '链式存储通过修改指针完成插入删除，不需要整体移动元素。',
    score: 0.87,
  },
  {
    index: 2,
    fileName: '考研***.md',
    summary: '顺序表插入平均移动 n/2 个元素，链表插入只需改动相邻指针。',
    score: 0.79,
  },
]

/** 后端把引用存成 JSON 字符串，mock 也给同一形态，逼适配层走 parseReferences */
const MOCK_REFERENCES_JSON = JSON.stringify([MOCK_REFERENCES[1]])

export const MOCK_HISTORY: Record<number, ChatMessageRecord[]> = {
  1: [
    {
      id: 101,
      conversationId: 1,
      userId: MOCK_USER_ID,
      role: 'user',
      content: '线性表的链式存储为什么更适合频繁插入？',
      referencesJson: null,
      clientMessageId: 'mock-cmid-101',
      replyToId: null,
      streamId: null,
      status: 'COMPLETED',
      lastSequence: 0,
      errorMessage: null,
      completedAt: null,
      modelName: null,
      promptTokens: null,
      completionTokens: null,
      createTime: '2026-11-22T10:08:00+08:00',
    },
    {
      id: 102,
      conversationId: 1,
      userId: MOCK_USER_ID,
      role: 'assistant',
      content:
        '因为链式存储插入时只需要改动相邻结点的指针，已知位置时是 O(1)，不需要像顺序表那样整体后移元素。',
      referencesJson: MOCK_REFERENCES_JSON,
      clientMessageId: null,
      replyToId: 101,
      streamId: 'mock-stream-history-1',
      status: 'COMPLETED',
      lastSequence: 8,
      errorMessage: null,
      completedAt: '2026-11-22T10:09:30+08:00',
      modelName: 'mock-llm',
      promptTokens: 812,
      completionTokens: 96,
      createTime: '2026-11-22T10:09:00+08:00',
    },
  ],
  2: [
    {
      id: 201,
      conversationId: 2,
      userId: MOCK_USER_ID,
      role: 'user',
      content: '进程调度算法有哪些区别？',
      referencesJson: null,
      clientMessageId: 'mock-cmid-201',
      replyToId: null,
      streamId: null,
      status: 'COMPLETED',
      lastSequence: 0,
      errorMessage: null,
      completedAt: null,
      modelName: null,
      promptTokens: null,
      completionTokens: null,
      createTime: '2026-11-21T21:38:00+08:00',
    },
  ],
}

const MOCK_ANSWER =
  '链式存储和顺序存储的核心差别在「元素是否连续存放」。' +
  '顺序表插入或删除时，为了保持连续性必须整体搬动后续元素，平均要移动 n/2 个元素；' +
  '链表只需要改动相邻几个结点的指针，位置已知时插入是常数时间。' +
  '但链表也有代价：不能随机访问，查找第 k 个元素要从头遍历，' +
  '而且每个结点额外存指针，空间利用率低于顺序表。' +
  '所以判断是否适合频繁插入，要同时看「插入位置是否已知」和「是否需要随机访问」，' +
  '考研答题时建议把两边的复杂度都写清楚，再给出适用场景结论。'

/** 按固定长度切块，尽量在标点处断开，模拟真实 token 流 */
function splitAnswer(text: string, chunkSize: number): string[] {
  const punctuation = ['，', '。', '；', '、']
  const chunks: string[] = []
  let cursor = 0
  while (cursor < text.length) {
    let end = Math.min(cursor + chunkSize, text.length)
    if (end < text.length) {
      for (let probe = end; probe > cursor + chunkSize / 2; probe -= 1) {
        if (punctuation.includes(text[probe])) {
          end = probe + 1
          break
        }
      }
    }
    chunks.push(text.slice(cursor, end))
    cursor = end
  }
  return chunks
}

const FAIL_KEYWORDS = ['失败', '报错', 'error']

export interface MockChatPortOptions {
  failOnKeyword?: boolean
  /** delta 间隔，默认 90ms */
  chunkIntervalMs?: number
  /** ack 之前的延迟，默认 120ms */
  startDelayMs?: number
}

export function createMockChatPort(options: MockChatPortOptions = {}): ChatPort {
  const failOnKeyword = options.failOnKeyword ?? true
  const chunkIntervalMs = options.chunkIntervalMs ?? 90
  const startDelayMs = options.startDelayMs ?? 120

  let timers: Array<ReturnType<typeof setTimeout>> = []
  let requestCounter = 0
  let finishCurrent: (() => void) | null = null
  let emitCurrent: ((message: ServerMessage) => void) | null = null
  let currentStreamId = ''

  function clearTimers(): void {
    timers.forEach((timer) => clearTimeout(timer))
    timers = []
  }

  return {
    kind: 'mock',

    async listConversations(): Promise<ChatConversationRecord[]> {
      await delay(120)
      return MOCK_CONVERSATIONS.map((item) => ({ ...item }))
    },

    async listMessages(
      conversationId: number,
      query: ChatHistoryQuery = {},
    ): Promise<ChatHistoryPage> {
      await delay(120)
      const all = (MOCK_HISTORY[conversationId] ?? []).slice().sort((a, b) => a.id - b.id)
      const limit = Math.max(1, Math.min(query.limit ?? 20, 100))
      const beforeId = query.beforeId ?? null
      const eligible = beforeId === null ? all : all.filter((item) => item.id < beforeId)
      const items = eligible.slice(Math.max(0, eligible.length - limit)).map((item) => ({ ...item }))
      return {
        items,
        hasMore: eligible.length > items.length,
        nextBeforeId: items.length ? items[0].id : null,
      }
    },

    async deleteConversation(_conversationId: number): Promise<void> {
      await delay(60)
    },

    sendQuestion(
      payload: ChatSendPayload,
      onMessage: (message: ServerMessage) => void,
    ): Promise<void> {
      clearTimers()
      requestCounter += 1

      const streamId = `mock-stream-${requestCounter}-${Date.now().toString(36)}`
      const requestId = payload.requestId
      const conversationId = payload.conversationId ?? 1
      const userMessageId = 8000 + requestCounter
      const shouldFail =
        failOnKeyword && FAIL_KEYWORDS.some((word) => payload.content.includes(word))
      currentStreamId = streamId
      emitCurrent = onMessage

      /** 聊天业务事件：七个字段齐全，sequence 从 1 连续递增 */
      let sequence = 0
      const emitEvent = <TType extends ChatEventType>(
        type: TType,
        data: ChatEvent<TType>['data'],
      ): void => {
        sequence += 1
        onMessage({
          version: 1,
          type,
          requestId,
          streamId,
          sequence,
          serverTime: new Date().toISOString(),
          data,
        } as ChatServerEvent)
      }

      return new Promise<void>((resolve) => {
        finishCurrent = () => {
          finishCurrent = null
          emitCurrent = null
          resolve()
        }

        const schedule = (ms: number, task: () => void): void => {
          timers.push(
            setTimeout(() => {
              if (finishCurrent === null) return
              task()
            }, ms),
          )
        }

        // ack：连接层消息，只回发起连接
        schedule(startDelayMs, () => {
          onMessage({
            version: 1,
            type: 'ack',
            requestId,
            data: {
              conversationId,
              streamId,
              clientMessageId: payload.clientMessageId,
            },
          })
        })
        // meta：带 userMessageId
        schedule(startDelayMs + 120, () =>
          emitEvent('meta', { conversationId, userMessageId, model: 'mock-llm' }),
        )
        // references：data 是 {items:[...]}
        schedule(startDelayMs + 260, () => emitEvent('references', { items: MOCK_REFERENCES }))

        if (shouldFail) {
          schedule(startDelayMs + 400, () =>
            emitEvent('delta', { content: '检索知识库时出' }),
          )
          schedule(startDelayMs + 540, () => {
            emitEvent('error', {
              code: 'INTERNAL_ERROR',
              message: '知识库检索失败（Mock 场景）',
              retryable: true,
              messageId: null,
              partialChars: 8,
            })
            finishCurrent?.()
          })
          return
        }

        const chunks = splitAnswer(MOCK_ANSWER, 14)
        chunks.forEach((chunk, index) => {
          schedule(startDelayMs + 400 + index * chunkIntervalMs, () =>
            emitEvent('delta', { content: chunk }),
          )
        })
        schedule(startDelayMs + 400 + chunks.length * chunkIntervalMs + 120, () => {
          emitEvent('done', {
            messageId: userMessageId + 1,
            model: 'mock-llm',
            promptTokens: 812,
            completionTokens: chunks.length,
            finishReason: 'stop',
          })
          finishCurrent?.()
        })
      })
    },

    /** 停止生成：mock 立即以 done(cancelled) 收尾 */
    abort(): void {
      const emitter = emitCurrent
      const streamId = currentStreamId
      clearTimers()
      const finish = finishCurrent
      finishCurrent = null
      emitCurrent = null
      if (emitter && streamId) {
        emitter({
          version: 1,
          type: 'done',
          requestId: `cancel-${streamId}`,
          streamId,
          sequence: 0,
          serverTime: new Date().toISOString(),
          data: { messageId: null, finishReason: 'cancelled' },
        })
      }
      finish?.()
    },
  }
}
