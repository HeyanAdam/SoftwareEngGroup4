/**
 * 聊天历史增量加载单测（成员 1 任务 6.3 / 6.4）
 *
 * 覆盖：首次只拉一页、更早消息顺序与去重、没有更多后停止、
 * 并发去抖、失败重试、切换会话不串写、滚动位置恢复。
 */

import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { getChatPort, setChatPort, useChatStore } from '../../../app/stores/chat'
import { HISTORY_PAGE_SIZE, computeRestoredScrollTop, mergeEarlierMessages } from '../chat.history'
import type {
  ChatConversationRecord,
  ChatHistoryPage,
  ChatHistoryQuery,
  ChatMessageRecord,
  ChatPort,
} from '../types'

function record(id: number, conversationId = 1): ChatMessageRecord {
  return {
    id,
    conversationId,
    userId: 1,
    role: id % 2 === 0 ? 'assistant' : 'user',
    content: `消息 ${id}`,
    referencesJson: null,
    status: 'COMPLETED',
    createTime: '2026-11-22T10:00:00+08:00',
  }
}

/** 用 id 列表构造一页（升序） */
function page(ids: number[], hasMore = false, conversationId = 1): ChatHistoryPage {
  return {
    items: ids.map((id) => record(id, conversationId)),
    hasMore,
    nextBeforeId: ids.length ? ids[0] : null,
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

class FakePort implements ChatPort {
  kind = 'mock' as const
  calls: Array<{ conversationId: number; query: ChatHistoryQuery }> = []
  handler: (conversationId: number, query: ChatHistoryQuery) => Promise<ChatHistoryPage> = async () =>
    page([])

  async listConversations(): Promise<ChatConversationRecord[]> {
    return []
  }

  async listMessages(conversationId: number, query: ChatHistoryQuery = {}): Promise<ChatHistoryPage> {
    this.calls.push({ conversationId, query })
    return this.handler(conversationId, query)
  }

  async deleteConversation(): Promise<void> {}

  async sendQuestion(): Promise<void> {}

  abort(): void {}
}

describe('历史合并纯函数', () => {
  const view = (id: number) => ({
    localId: `history-${id}`,
    serverId: id,
    clientMessageId: `history-${id}`,
    role: 'user' as const,
    content: String(id),
    status: 'completed' as const,
    lastSequence: 0,
  })

  it('更早一页前置，重复 serverId 只保留一份', () => {
    const current = [view(101), view(102)]
    const merged = mergeEarlierMessages(current, [view(99), view(100), view(101)])
    expect(merged.map((item) => item.serverId)).toEqual([99, 100, 101, 102])
  })

  it('没有新增消息时返回原数组引用', () => {
    const current = [view(101)]
    expect(mergeEarlierMessages(current, [view(101)])).toBe(current)
    expect(mergeEarlierMessages(current, [])).toBe(current)
  })

  it('插入更早消息后滚动位置按高度差同步下移', () => {
    expect(computeRestoredScrollTop(1000, 200, 1400)).toBe(600)
    expect(computeRestoredScrollTop(1000, 0, 1000)).toBe(0)
    // 高度变小时不出现负值
    expect(computeRestoredScrollTop(1000, 20, 800)).toBe(20)
  })
})

describe('聊天 Store 历史分页', () => {
  const originalPort = getChatPort()
  let port: FakePort

  beforeEach(() => {
    setActivePinia(createPinia())
    port = new FakePort()
    setChatPort(port)
  })

  afterEach(() => {
    setChatPort(originalPort)
  })

  it('首次进入会话只请求最近一页', async () => {
    port.handler = async () => page([101, 102], true)
    const store = useChatStore()

    await store.selectConversation(1)

    expect(port.calls).toHaveLength(1)
    expect(port.calls[0].conversationId).toBe(1)
    expect(port.calls[0].query.limit).toBe(HISTORY_PAGE_SIZE)
    expect(port.calls[0].query.beforeId ?? null).toBeNull()
    expect(store.messages.map((item) => item.serverId)).toEqual([101, 102])
    expect(store.hasMoreMessages).toBe(true)
    expect(store.nextBeforeId).toBe(101)
  })

  it('加载更早消息后顺序正确且不重复', async () => {
    port.handler = async (_conversationId, query) =>
      query.beforeId ? page([97, 98, 101], false) : page([101, 102], true)
    const store = useChatStore()
    await store.selectConversation(1)

    const added = await store.loadEarlierMessages()

    expect(added).toBe(true)
    expect(port.calls[1].query.beforeId).toBe(101)
    expect(store.messages.map((item) => item.serverId)).toEqual([97, 98, 101, 102])
    expect(store.hasMoreMessages).toBe(false)
    expect(store.nextBeforeId).toBe(97)
  })

  it('没有更多数据后不再发起请求', async () => {
    port.handler = async () => page([101], false)
    const store = useChatStore()
    await store.selectConversation(1)

    expect(await store.loadEarlierMessages()).toBe(false)
    expect(port.calls).toHaveLength(1)
  })

  it('加载过程中重复触发不会重复请求', async () => {
    const slow = deferred<ChatHistoryPage>()
    port.handler = async (_conversationId, query) =>
      query.beforeId ? slow.promise : page([101], true)
    const store = useChatStore()
    await store.selectConversation(1)

    const first = store.loadEarlierMessages()
    const second = store.loadEarlierMessages()
    expect(await second).toBe(false)
    expect(port.calls.filter((call) => call.query.beforeId)).toHaveLength(1)

    slow.resolve(page([99], false))
    expect(await first).toBe(true)
    expect(store.messages.map((item) => item.serverId)).toEqual([99, 101])
  })

  it('请求失败后保留状态并可重试', async () => {
    let attempts = 0
    port.handler = async (_conversationId, query) => {
      if (!query.beforeId) return page([101], true)
      attempts += 1
      if (attempts === 1) throw new Error('网络异常')
      return page([99], false)
    }
    const store = useChatStore()
    await store.selectConversation(1)

    expect(await store.loadEarlierMessages()).toBe(false)
    expect(store.earlierError).toBe('网络异常')
    expect(store.hasMoreMessages).toBe(true)

    expect(await store.loadEarlierMessages()).toBe(true)
    expect(store.earlierError).toBe('')
    expect(store.messages.map((item) => item.serverId)).toEqual([99, 101])
  })

  it('切换会话后旧会话的慢响应不会污染当前会话', async () => {
    const slow = deferred<ChatHistoryPage>()
    port.handler = async (conversationId) =>
      conversationId === 1 ? slow.promise : page([201, 202], false, 2)
    const store = useChatStore()

    const stale = store.selectConversation(1)
    await store.selectConversation(2)
    expect(store.messages.map((item) => item.serverId)).toEqual([201, 202])

    slow.resolve(page([101, 102], false, 1))
    await stale

    expect(store.activeConversationId).toBe(2)
    expect(store.messages.map((item) => item.serverId)).toEqual([201, 202])
    expect(store.loadingMessages).toBe(false)
  })

  it('切换会话会重置分页游标与状态', async () => {
    port.handler = async (conversationId) =>
      conversationId === 1 ? page([101], true) : page([201], false, 2)
    const store = useChatStore()
    await store.selectConversation(1)

    await store.selectConversation(2)

    expect(store.hasMoreMessages).toBe(false)
    expect(store.nextBeforeId).toBe(201)
    expect(store.earlierError).toBe('')
  })
})
