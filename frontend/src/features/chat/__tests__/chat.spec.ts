/**
 * C-1 聊天模块单测
 *
 * fixtures 目录的 JSON 就是前后端共享契约（字段形状照 A-1/B-2 真实实现），
 * A/B 的后端序列化测试可复用同一份样例。
 */

import { describe, expect, it } from 'vitest'
import transportClient from './fixtures/transport-client.json'
import transportServer from './fixtures/transport-server.json'
import chatEvents from './fixtures/chat-events.json'
import {
  applyServerMessage,
  createAssistantMessage,
  createUserMessage,
  markMessageCancelled,
  normalizeReference,
  parseReferences,
  prepareRetry,
  toViewMessage,
} from '../chat.messages'
import type {
  AckMessage,
  ChatMessageRecord,
  ChatSendCommand,
  ChatServerEvent,
  ChatViewMessage,
  RealtimeErrorMessage,
  ServerMessage,
} from '../types'

const clientMessages = transportClient as unknown as Array<Record<string, unknown>>
const serverMessages = transportServer as unknown as Array<Record<string, unknown>>
const events = chatEvents as unknown as ChatServerEvent[]

/** 按 type（可选 sequence）取聊天事件 */
function chatEventOf(type: ChatServerEvent['type'], sequence?: number): ChatServerEvent {
  const found = events.find(
    (item) => item.type === type && (sequence === undefined || item.sequence === sequence),
  )
  if (!found) throw new Error(`fixture 缺少聊天事件：${type} seq=${sequence ?? 'any'}`)
  return found
}

/** ack 是连接层消息，单独从 transport fixture 取 */
const ackMessage = serverMessages.find((item) => item.type === 'ack') as unknown as AckMessage

/** 用户消息 + 助手占位（助手已绑定 ack 里的 streamId） */
function initialState(): ChatViewMessage[] {
  return [
    createUserMessage('线性表的链式存储为什么更适合频繁插入？', 12),
    createAssistantMessage('stream-6b21a7e0-0001'),
  ]
}

describe('fixtures：三类消息字段集合', () => {
  it('客户端上行不带 streamId / sequence / serverTime', () => {
    for (const message of clientMessages) {
      expect(message.version).toBe(1)
      expect(message).toHaveProperty('requestId')
      expect(message).not.toHaveProperty('streamId')
      expect(message).not.toHaveProperty('sequence')
      expect(message).not.toHaveProperty('serverTime')
    }
  })

  it('chat.send 必须带 clientMessageId 与 conversationId/kbId/content', () => {
    const send = clientMessages.find(
      (item) => item.type === 'chat.send',
    ) as unknown as ChatSendCommand
    expect(send.clientMessageId).toBe('cmid-8f1c2d34-0001')
    expect(send.data.conversationId).toBe(12)
    expect(send.data.kbId).toBeNull()
    expect(typeof send.data.content).toBe('string')
  })

  it('连接层 pong / error / ack 外层没有 streamId', () => {
    for (const message of serverMessages) {
      expect(message).not.toHaveProperty('streamId')
      expect(message).not.toHaveProperty('sequence')
      expect(message).not.toHaveProperty('serverTime')
    }
  })

  it('连接层 error 是 {code:number, reason, retryable}；ack 的 data 是 {conversationId, streamId, clientMessageId}', () => {
    const error = serverMessages.find(
      (item) => item.type === 'error',
    ) as unknown as RealtimeErrorMessage
    expect(Object.keys(error.data).sort()).toEqual(['code', 'reason', 'retryable'])
    expect(typeof error.data.code).toBe('number')
    expect(error.data).not.toHaveProperty('message')

    expect(Object.keys(ackMessage.data).sort()).toEqual([
      'clientMessageId',
      'conversationId',
      'streamId',
    ])
  })

  it('聊天事件七个字段齐全；references 的 data 是 {items}；done 的 finishReason 是 stop', () => {
    for (const event of events) {
      expect(event.version).toBe(1)
      expect(typeof event.streamId).toBe('string')
      expect(typeof event.requestId).toBe('string')
      expect(typeof event.sequence).toBe('number')
      expect(typeof event.serverTime).toBe('string')
    }
    const references = chatEventOf('references')
    if (references.type !== 'references') throw new Error('类型收窄失败')
    expect(Array.isArray(references.data.items)).toBe(true)

    const done = chatEventOf('done')
    if (done.type !== 'done') throw new Error('类型收窄失败')
    expect(done.data.finishReason).toBe('stop')
  })

  it('聊天级 error 的 code 是字符串，字段是 message（不是 reason）', () => {
    const error = chatEventOf('error')
    if (error.type !== 'error') throw new Error('类型收窄失败')
    expect(typeof error.data.code).toBe('string')
    expect(error.data.message).toBe('知识库检索失败')
    expect(error.data).not.toHaveProperty('reason')
    expect(error.data.retryable).toBe(true)
  })
})

describe('applyServerMessage：回执与事件顺序', () => {
  it('ack 把用户消息转 sending、回填会话与 streamId', () => {
    let messages = [createUserMessage('问题', null), createAssistantMessage()]
    messages = applyServerMessage(messages, ackMessage)
    expect(messages[0].status).toBe('sending')
    expect(messages[0].conversationId).toBe(12)
    expect(messages[1].streamId).toBe('stream-6b21a7e0-0001')
  })

  it('meta → references → delta → done 后内容与状态正确', () => {
    let messages = initialState()

    messages = applyServerMessage(messages, chatEventOf('meta'))
    expect(messages[0].serverId).toBe(8801)
    expect(messages[1].conversationId).toBe(12)

    messages = applyServerMessage(messages, chatEventOf('references'))
    expect(messages[1].references).toHaveLength(2)
    expect(messages[1].references?.[0].fileName).toBe('数据***.pdf')
    expect(messages[1].references?.[0].summary).toBe(
      '链式存储通过修改指针完成插入删除，不需要整体移动元素。',
    )

    messages = applyServerMessage(messages, chatEventOf('delta', 3))
    expect(messages[1].content).toBe('因为链式存储')
    messages = applyServerMessage(messages, chatEventOf('delta', 4))
    expect(messages[1].content).toBe('因为链式存储插入只需改动相邻指针。')
    expect(messages[1].lastSequence).toBe(4)
    expect(messages[1].status).toBe('streaming')

    messages = applyServerMessage(messages, chatEventOf('done'))
    expect(messages[1].status).toBe('completed')
    expect(messages[1].finishReason).toBe('completed')
    expect(messages[1].serverId).toBe(8802)
  })

  it('delta 重复补发（sequence 回退）不重复追加', () => {
    let messages = initialState()
    messages = applyServerMessage(messages, chatEventOf('delta', 3))
    messages = applyServerMessage(messages, chatEventOf('delta', 4))
    messages = applyServerMessage(messages, chatEventOf('delta', 3))
    expect(messages[1].content).toBe('因为链式存储插入只需改动相邻指针。')
    expect(messages[1].lastSequence).toBe(4)
  })

  it('聊天级 error 置 failed 并保留 message', () => {
    let messages = initialState()
    messages = applyServerMessage(messages, chatEventOf('error'))
    expect(messages[1].status).toBe('failed')
    expect(messages[1].finishReason).toBe('failed')
    expect(messages[1].errorMessage).toBe('知识库检索失败')
  })

  it('连接层 error / pong 不改消息状态', () => {
    const messages = initialState()
    const transportError = serverMessages.find(
      (item) => item.type === 'error',
    ) as unknown as ServerMessage
    expect(applyServerMessage(messages, transportError)).toEqual(messages)
    const pong = serverMessages.find((item) => item.type === 'pong') as unknown as ServerMessage
    expect(applyServerMessage(messages, pong)).toEqual(messages)
  })

  it('失败后再到达的 done 不会把状态翻回成功（新链路以 error 为准）', () => {
    let messages = initialState()
    messages = applyServerMessage(messages, chatEventOf('error'))
    messages = applyServerMessage(messages, chatEventOf('done'))
    expect(messages[1].status).toBe('failed')
    expect(messages[1].errorMessage).toBe('知识库检索失败')
  })

  it('另一个标签页的 meta（陌生 streamId）会新建占位，不抢现有消息', () => {
    const messages = initialState()
    const otherTabMeta: ChatServerEvent = {
      version: 1,
      type: 'meta',
      requestId: 'req-other-tab',
      streamId: 'stream-other-tab',
      sequence: 1,
      serverTime: '2026-11-22T16:31:00+08:00',
      data: { conversationId: 12, userMessageId: 8803, model: 'gpt-4o-mini' },
    }
    const next = applyServerMessage(messages, otherTabMeta)
    expect(next).toHaveLength(3)
    expect(next[2].streamId).toBe('stream-other-tab')
    expect(next[2].status).toBe('streaming')
    // 原有助手消息不受影响
    expect(next[1].content).toBe('')
  })

  it('finishReason=length 仍算正常完成（截断只当参考）', () => {
    let messages = initialState()
    const doneLength: ChatServerEvent = {
      version: 1,
      type: 'done',
      requestId: 'req-send-001',
      streamId: 'stream-6b21a7e0-0001',
      sequence: 5,
      serverTime: '2026-11-22T16:30:03+08:00',
      data: { messageId: 8802, finishReason: 'length' },
    }
    messages = applyServerMessage(messages, doneLength)
    expect(messages[1].status).toBe('completed')
  })
})

describe('前端兜底：停止生成', () => {
  it('markMessageCancelled 保留内容、不写 errorMessage', () => {
    const messages = initialState()
    messages[1] = { ...messages[1], content: '半截回答' }
    const next = markMessageCancelled(messages, messages[1].localId)
    expect(next[1].status).toBe('cancelled')
    expect(next[1].content).toBe('半截回答')
    expect(next[1].errorMessage).toBeUndefined()
  })
})

describe('引用适配', () => {
  it('normalizeReference 抹平学生 / 管理员两种字段', () => {
    const student = normalizeReference({
      index: 1,
      fileName: '数据***.pdf',
      summary: '脱敏摘要',
      score: 0.87,
    })
    expect(student.fileName).toBe('数据***.pdf')
    expect(student.summary).toBe('脱敏摘要')

    const admin = normalizeReference({
      index: 2,
      docName: '数据结构_第2章_线性表.pdf',
      docId: 11,
      chunkId: 302,
      score: 0.79,
      snippet: '切片内容',
    })
    expect(admin.fileName).toBe('数据结构_第2章_线性表.pdf')
    expect(admin.summary).toBe('切片内容')
  })

  it('parseReferences 兼容数组与 {items} 两种落库形态', () => {
    const asArray =
      '[{"index":1,"fileName":"数据***.pdf","summary":"摘要","score":0.87}]'
    expect(parseReferences(asArray)).toHaveLength(1)

    const asObject = '{"items":[{"index":1,"docName":"真实文档.pdf","snippet":"切片","score":0.9}]}'
    const parsed = parseReferences(asObject)
    expect(parsed[0].fileName).toBe('真实文档.pdf')
    expect(parsed[0].summary).toBe('切片')
  })

  it('字段缺省时不崩：空 items、空对象都给出兜底值', () => {
    expect(parseReferences('{"items":[]}')).toEqual([])
    const fallback = normalizeReference({})
    expect(fallback.fileName).toBe('未知文档')
    expect(fallback.summary).toBe('')
    expect(fallback.score).toBe(0)
  })

  it('null / 坏 JSON / 非数组一律降级为空数组，不抛异常', () => {
    expect(parseReferences(null)).toEqual([])
    expect(parseReferences('')).toEqual([])
    expect(parseReferences('{bad json')).toEqual([])
    expect(parseReferences('123')).toEqual([])
  })
})

describe('历史消息适配', () => {
  it('COMPLETED → completed，FAILED → failed 并带错误原因', () => {
    const base: ChatMessageRecord = {
      id: 102,
      conversationId: 1,
      userId: 1001,
      role: 'assistant',
      content: '回答',
      referencesJson: null,
      status: 'COMPLETED',
      createTime: '2026-11-22T10:09:00+08:00',
    }
    expect(toViewMessage(base).status).toBe('completed')

    const failed = toViewMessage({ ...base, status: 'FAILED', errorMessage: '模型超时' })
    expect(failed.status).toBe('failed')
    expect(failed.errorMessage).toBe('模型超时')
  })
})

describe('失败重试', () => {
  it('重试只保留用户问题，clientMessageId 不变', () => {
    let messages = initialState()
    const originalClientMessageId = messages[0].clientMessageId
    messages = applyServerMessage(messages, chatEventOf('error'))

    const prepared = prepareRetry(messages, messages[0].localId)
    expect(prepared).toHaveLength(1)
    expect(prepared[0].status).toBe('queued')
    expect(prepared[0].clientMessageId).toBe(originalClientMessageId)

    const twice = prepareRetry(prepared, prepared[0].localId)
    expect(twice).toHaveLength(1)
    expect(twice[0].clientMessageId).toBe(originalClientMessageId)
  })

  it('只清理本次问题之后的助手消息，历史消息不动', () => {
    const messages: ChatViewMessage[] = [
      createUserMessage('第一个问题', 12),
      createAssistantMessage(),
      createUserMessage('第二个问题', 12),
      createAssistantMessage(),
    ]
    messages[1] = { ...messages[1], content: '第一段回答', status: 'completed' }
    messages[3] = { ...messages[3], status: 'cancelled', finishReason: 'cancelled' }

    const prepared = prepareRetry(messages, messages[2].localId)
    expect(prepared).toHaveLength(3)
    expect(prepared[1].content).toBe('第一段回答')
    expect(prepared[2].status).toBe('queued')
  })
})
