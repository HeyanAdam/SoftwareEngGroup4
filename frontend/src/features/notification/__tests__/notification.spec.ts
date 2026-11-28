import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import {
  buildMockRealtimeNotification,
  NOTIFICATION_MOCK_STORAGE_KEY,
  resetNotificationMockState,
} from '../notification.mock'
import {
  dispatchRealtimeEvent,
  isSafeActionPath,
  parseNotificationCreatedEvent,
} from '../realtime/notificationRealtime'
import { createNotificationMockRealtime } from '../realtime/notificationRealtime.mock'
import {
  formatUnreadBadge,
  mergeNotifications,
  useNotificationStore,
} from '../../../app/stores/notification'
import type { NotificationCreatedEvent, UserNotification } from '../types'

function makeNotification(overrides: Partial<UserNotification> & Pick<UserNotification, 'id'>): UserNotification {
  const { id, ...rest } = overrides
  return {
    id,
    type: 'TASK_DUE',
    severity: 'WARNING',
    title: `通知 ${id}`,
    content: '测试内容',
    actionPath: '/study-plans',
    read: false,
    createdAt: '2026-11-28T09:00:00+08:00',
    readAt: null,
    ...rest,
  }
}

function makeEvent(overrides: {
  eventId: string
  notification: UserNotification
  unreadCount: number
}): NotificationCreatedEvent {
  return {
    version: 1,
    type: 'notification.created',
    requestId: `req-${overrides.eventId}`,
    serverTime: '2026-11-28T09:00:00+08:00',
    data: overrides,
  }
}

describe('formatUnreadBadge（铃铛角标文案）', () => {
  it('0 未读不显示角标', () => {
    expect(formatUnreadBadge(0)).toBe('')
  })

  it('负数/非数字按 0 处理，不出现 -1', () => {
    expect(formatUnreadBadge(-3)).toBe('')
    expect(formatUnreadBadge(Number.NaN)).toBe('')
  })

  it('1~99 原样显示，超过 99 显示 99+', () => {
    expect(formatUnreadBadge(1)).toBe('1')
    expect(formatUnreadBadge(99)).toBe('99')
    expect(formatUnreadBadge(100)).toBe('99+')
  })
})

describe('mergeNotifications（REST 与 WS 合并去重）', () => {
  it('相同 id 只保留先出现的一条，不重复插入', () => {
    const current = [makeNotification({ id: 1 }), makeNotification({ id: 2 })]
    const incoming = [makeNotification({ id: 2, title: '推送来的 2' }), makeNotification({ id: 3 })]
    const merged = mergeNotifications(current, incoming)
    expect(merged.map((item) => item.id)).toEqual([2, 3, 1])
    expect(merged.find((item) => item.id === 2)?.title).toBe('推送来的 2')
  })
})

describe('parseNotificationCreatedEvent（只认契约 6.3 形状）', () => {
  it('合法事件解析成功', () => {
    const event = makeEvent({ eventId: 'e1', notification: makeNotification({ id: 10 }), unreadCount: 3 })
    expect(parseNotificationCreatedEvent(event)?.data.eventId).toBe('e1')
  })

  it('type 不是 notification.created 一律丢弃（聊天流式事件不算通知）', () => {
    const raw = { ...makeEvent({ eventId: 'e2', notification: makeNotification({ id: 11 }), unreadCount: 1 }), type: 'delta' }
    expect(parseNotificationCreatedEvent(raw)).toBeNull()
  })

  it('缺 eventId 或 unreadCount 丢弃，不用本地推算兜底', () => {
    const event = makeEvent({ eventId: '', notification: makeNotification({ id: 12 }), unreadCount: 1 })
    expect(parseNotificationCreatedEvent(event)).toBeNull()

    const noCount = makeEvent({ eventId: 'e3', notification: makeNotification({ id: 13 }), unreadCount: Number.NaN })
    expect(parseNotificationCreatedEvent(noCount)).toBeNull()
  })

  it('notification 缺 id / 类型不对丢弃', () => {
    const bad = makeEvent({
      eventId: 'e4',
      notification: { ...makeNotification({ id: 14 }), id: '14' as unknown as number },
      unreadCount: 1,
    })
    expect(parseNotificationCreatedEvent(bad)).toBeNull()
  })
})

describe('isSafeActionPath（站内跳转白名单）', () => {
  it('站内绝对路径放行', () => {
    expect(isSafeActionPath('/study-plans')).toBe(true)
    expect(isSafeActionPath('/chat?conversationId=3')).toBe(true)
  })

  it('javascript: / 外站 / //host / 空值全部拒绝', () => {
    expect(isSafeActionPath('javascript:alert(1)')).toBe(false)
    expect(isSafeActionPath('https://evil.example.com')).toBe(false)
    expect(isSafeActionPath('//evil.example.com/x')).toBe(false)
    expect(isSafeActionPath('study-plans')).toBe(false)
    expect(isSafeActionPath(null)).toBe(false)
    expect(isSafeActionPath('')).toBe(false)
  })
})

describe('dispatchRealtimeEvent（同一条连接多事件类型互不干扰）', () => {
  it('只把 notification.created 交给通知层', () => {
    const onCreated = vi.fn()
    const event = makeEvent({ eventId: 'e5', notification: makeNotification({ id: 15 }), unreadCount: 2 })
    expect(dispatchRealtimeEvent(event, { onCreated })).toBe(true)
    expect(onCreated).toHaveBeenCalledTimes(1)

    onCreated.mockClear()
    expect(dispatchRealtimeEvent({ version: 1, type: 'recommendation.updated', data: {} }, { onCreated })).toBe(false)
    expect(onCreated).not.toHaveBeenCalled()
  })
})

describe('NotificationStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.setItem(NOTIFICATION_MOCK_STORAGE_KEY, 'on')
    localStorage.removeItem('kaoyan.insight.mock')
    resetNotificationMockState()
  })

  it('同一个 eventId 重复投递只处理一次（断线重连补发不会多一条）', () => {
    const store = useNotificationStore()
    const event = makeEvent({ eventId: 'evt-same', notification: makeNotification({ id: 501 }), unreadCount: 5 })
    expect(store.applyNotificationCreated(event)).toBe(true)
    expect(store.applyNotificationCreated(event)).toBe(false)
    expect(store.notifications).toHaveLength(1)
    expect(store.unreadCount).toBe(5)
  })

  it('同一个 notification.id 用不同 eventId 再来一次，不重复插入但未读数以事件为准', () => {
    const store = useNotificationStore()
    store.applyNotificationCreated(
      makeEvent({ eventId: 'evt-a', notification: makeNotification({ id: 502 }), unreadCount: 2 }),
    )
    store.applyNotificationCreated(
      makeEvent({ eventId: 'evt-b', notification: makeNotification({ id: 502, read: true }), unreadCount: 0 }),
    )
    expect(store.notifications.filter((item) => item.id === 502)).toHaveLength(1)
    // 未读数认事件值，不本地减一
    expect(store.unreadCount).toBe(0)
  })

  it('未读数以接口返回为准：单条已读不做本地减一', async () => {
    const store = useNotificationStore()
    await store.loadNotifications({ reset: true, status: 'UNREAD' })
    expect(store.total).toBeGreaterThan(0)
    // Mock 初始 4 条未读，读掉第一条后服务端返回 3
    expect(store.unreadCount).toBe(0)

    await store.loadUnreadCount()
    expect(store.unreadCount).toBe(4)

    const firstUnread = store.notifications[0]
    await store.markRead(firstUnread.id)
    expect(store.unreadCount).toBe(3)
    expect(store.notifications.find((item) => item.id === firstUnread.id)?.read).toBe(true)
  })

  it('全部已读后未读数归零，且列表条目都变已读', async () => {
    const store = useNotificationStore()
    await store.loadUnreadCount()
    await store.loadNotifications({ reset: true, status: 'UNREAD' })
    const ok = await store.markAllRead()
    expect(ok).toBe(true)
    expect(store.unreadCount).toBe(0)
    expect(store.notifications.every((item) => item.read)).toBe(true)
  })

  it('分页字段按契约 4.7 解析（list/total/page_num/page_size），不是 items/pageNum', async () => {
    const store = useNotificationStore()
    await store.loadNotifications({ reset: true, status: 'ALL' })
    expect(store.pageNum).toBe(1)
    expect(store.pageSize).toBe(10)
    expect(store.total).toBeGreaterThan(10)
    expect(store.hasMore).toBe(true)
  })

  it('reset 清空未读与列表，退出登录不会带着上一个人的数字', async () => {
    const store = useNotificationStore()
    await store.loadUnreadCount()
    await store.loadNotifications({ reset: true, status: 'UNREAD' })
    store.reset()
    expect(store.unreadCount).toBe(0)
    expect(store.notifications).toHaveLength(0)
    expect(store.badgeText).toBe('')
  })
})

describe('createNotificationMockRealtime（定时器必须能停）', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    resetNotificationMockState()
  })

  it('start 后按间隔推送，stop 之后不再推送且不再占用定时器', () => {
    const onCreated = vi.fn()
    const mock = createNotificationMockRealtime({
      onCreated,
      nextUnreadCount: () => 1,
      firstDelayMs: 1000,
      intervalMs: 1000,
    })

    mock.start()
    expect(mock.isRunning()).toBe(true)
    vi.advanceTimersByTime(1000)
    expect(onCreated).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(2000)
    expect(onCreated).toHaveBeenCalledTimes(3)

    mock.stop()
    expect(mock.isRunning()).toBe(false)
    vi.advanceTimersByTime(10_000)
    expect(onCreated).toHaveBeenCalledTimes(3)
    vi.useRealTimers()
  })

  it('重复 start 不会叠加出两条定时链', () => {
    const onCreated = vi.fn()
    const mock = createNotificationMockRealtime({
      onCreated,
      nextUnreadCount: () => 1,
      firstDelayMs: 500,
      intervalMs: 1000,
    })
    mock.start()
    mock.start()
    vi.advanceTimersByTime(500)
    expect(onCreated).toHaveBeenCalledTimes(1)
    mock.stop()
    vi.useRealTimers()
  })

  it('pushNow 立刻产生一条契约 6.3 形状的事件，id 从 200 起不撞种子数据', () => {
    const events: NotificationCreatedEvent[] = []
    const mock = createNotificationMockRealtime({
      onCreated: (event) => events.push(event),
      nextUnreadCount: () => 2,
    })
    mock.pushNow()
    expect(events).toHaveLength(1)
    expect(events[0].type).toBe('notification.created')
    expect(events[0].data.notification.id).toBeGreaterThan(200)
    expect(events[0].data.unreadCount).toBe(2)
    expect(parseNotificationCreatedEvent(events[0])).not.toBeNull()
  })
})

describe('buildMockRealtimeNotification（Mock 数据也走契约字段）', () => {
  it('生成的条目字段完整，actionPath 是站内路径', () => {
    const item = buildMockRealtimeNotification(new Date('2026-11-28T10:00:00+08:00'))
    expect(item.read).toBe(false)
    expect(isSafeActionPath(item.actionPath)).toBe(true)
    expect(item.createdAt).toBe('2026-11-28T02:00:00.000Z')
  })
})
