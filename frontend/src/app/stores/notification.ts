/**
 * 通知 Store（前端 B 唯一通知数据源）
 *
 * 契约依据：5.7 / 5.8 / 5.9 / 5.10 / 6.3 / 6.4。
 * 几条硬规则写在这里，组件不再各自判断：
 * 1. 未读数以接口/事件给的 unreadCount 为准，绝不做本地 ±1（避免和并发的两条已读打架算错）；
 * 2. 单条已读、全部已读都用接口返回值覆盖 unreadCount；
 * 3. 实时事件按 eventId + notification.id 双去重；
 * 4. 列表只在打开抽屉时按需拉，进入主布局只拉一次未读数。
 */

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { NotificationCreatedEvent, NotificationPage, UserNotification } from '../../features/notification/types'
import {
  fetchNotifications,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
} from '../../features/notification/notification.api'

/** 角标文案：0 不显示，超过 99 显示 99+（与契约 7.1 的口径一致） */
export function formatUnreadBadge(count: number): string {
  if (!Number.isFinite(count) || count <= 0) return ''
  return count > 99 ? '99+' : String(count)
}

/** 合并列表：按 id 去重，先出现的保留（REST 与 WS 撞车时以先到的为准，随后统一由 unreadCount 校正） */
export function mergeNotifications(
  current: readonly UserNotification[],
  incoming: readonly UserNotification[],
): UserNotification[] {
  const seen = new Set<number>()
  const result: UserNotification[] = []
  for (const item of [...incoming, ...current]) {
    if (seen.has(item.id)) continue
    seen.add(item.id)
    result.push(item)
  }
  return result
}

export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)
  const notifications = ref<UserNotification[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)
  const loadingUnread = ref(false)
  const loadingList = ref(false)
  const markingIds = ref<number[]>([])
  const markingAll = ref(false)
  const errorMessage = ref('')
  /** 已处理过的实时 eventId：断线重连补发同一条时不能多插一条 */
  const seenEventIds = ref<string[]>([])

  const hasUnread = computed(() => unreadCount.value > 0)
  const badgeText = computed(() => formatUnreadBadge(unreadCount.value))
  const hasMore = computed(() => notifications.value.length < total.value)

  function rememberEvent(eventId: string): boolean {
    if (seenEventIds.value.includes(eventId)) return false
    seenEventIds.value = [...seenEventIds.value, eventId].slice(-200)
    return true
  }

  /** 进入主布局调用一次；失败不清空已有数字，只是不刷新 */
  async function loadUnreadCount(): Promise<void> {
    if (loadingUnread.value) return
    loadingUnread.value = true
    try {
      const result = await fetchUnreadCount()
      unreadCount.value = result.unreadCount
    } catch {
      // 角标失败不该影响页面主流程，也不弹全局错误
    } finally {
      loadingUnread.value = false
    }
  }

  /** 打开抽屉才调用；status 默认只看未读（契约 5.8 默认 UNREAD） */
  async function loadNotifications(options: { reset?: boolean; status?: 'UNREAD' | 'ALL' } = {}): Promise<void> {
    const reset = options.reset ?? false
    const status = options.status ?? 'UNREAD'
    if (loadingList.value) return
    if (!reset && notifications.value.length >= total.value && total.value > 0) return
    loadingList.value = true
    errorMessage.value = ''
    const nextPage = reset ? 1 : pageNum.value + 1
    try {
      const page: NotificationPage = await fetchNotifications({
        status,
        pageNum: nextPage,
        pageSize: pageSize.value,
      })
      // 后端返回的 page_num 可能被服务端纠正（例如超出总页数），以返回值同步
      pageNum.value = page.page_num
      pageSize.value = page.page_size
      total.value = page.total
      notifications.value = reset ? page.list : mergeNotifications(notifications.value, page.list)
    } catch (error) {
      // 错误只落到抽屉内部的 alert，不打断主流程（契约 7.3）
      errorMessage.value = error instanceof Error && error.message ? error.message : '通知加载失败'
    } finally {
      loadingList.value = false
    }
  }

  /** 单条已读：unreadCount 以接口返回为准 */
  async function markRead(id: number): Promise<boolean> {
    if (markingIds.value.includes(id)) return false
    const target = notifications.value.find((item) => item.id === id)
    if (target?.read) return true
    markingIds.value = [...markingIds.value, id]
    try {
      const result = await markNotificationRead(id)
      unreadCount.value = result.unreadCount
      notifications.value = notifications.value.map((item) =>
        item.id === id ? { ...item, read: true, readAt: item.readAt ?? new Date().toISOString() } : item,
      )
      return true
    } catch (error) {
      errorMessage.value = error instanceof Error && error.message ? error.message : '标记已读失败'
      return false
    } finally {
      markingIds.value = markingIds.value.filter((item) => item !== id)
    }
  }

  /** 全部已读：先调接口，成功后按返回值把未读清零（不本地清零，防接口失败） */
  async function markAllRead(): Promise<boolean> {
    if (markingAll.value) return false
    markingAll.value = true
    errorMessage.value = ''
    try {
      const result = await markAllNotificationsRead()
      unreadCount.value = result.unreadCount
      notifications.value = notifications.value.map((item) => ({
        ...item,
        read: true,
        readAt: item.readAt ?? new Date().toISOString(),
      }))
      return true
    } catch (error) {
      errorMessage.value = error instanceof Error && error.message ? error.message : '全部已读失败'
      return false
    } finally {
      markingAll.value = false
    }
  }

  /** 实时事件入口：双去重后插入列表头，未读以事件值为准（契约 6.4） */
  function applyNotificationCreated(event: NotificationCreatedEvent): boolean {
    const eventId = event.data.eventId
    if (!eventId || !rememberEvent(eventId)) return false
    const incoming = event.data.notification
    if (!incoming) return false
    unreadCount.value = event.data.unreadCount
    const exists = notifications.value.some((item) => item.id === incoming.id)
    if (!exists) {
      notifications.value = [incoming, ...notifications.value]
      total.value += 1
    }
    return true
  }

  /** 退出登录/切换账号时清空，避免把上一个用户的通知带过去 */
  function reset(): void {
    unreadCount.value = 0
    notifications.value = []
    total.value = 0
    pageNum.value = 1
    loadingList.value = false
    loadingUnread.value = false
    markingIds.value = []
    markingAll.value = false
    errorMessage.value = ''
    seenEventIds.value = []
  }

  return {
    unreadCount,
    notifications,
    total,
    pageNum,
    pageSize,
    loadingUnread,
    loadingList,
    markingIds,
    markingAll,
    errorMessage,
    seenEventIds,
    hasUnread,
    badgeText,
    hasMore,
    loadUnreadCount,
    loadNotifications,
    markRead,
    markAllRead,
    applyNotificationCreated,
    rememberEvent,
    reset,
  }
})
