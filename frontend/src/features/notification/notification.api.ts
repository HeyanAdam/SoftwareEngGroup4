/**
 * 通知接口（契约 5.7 ~ 5.10）
 *
 * 约定：
 * - 前缀 /api/v1 由 shared/api/request.ts 统一携带；
 * - 前端不往请求里塞 userId（契约 6.6），身份只由 Token 决定；
 * - 分页严格用 page_num / page_size，返回按 list / total / page_num / page_size 解析；
 * - Mock 只在开发环境 + localStorage 开关打开时生效，真实路径保持不变。
 */

import { get, put } from '../../shared/api/request'
import type {
  MarkAllReadResult,
  MarkReadResult,
  NotificationPage,
  NotificationQuery,
  UnreadCountResult,
} from './types'
import {
  isNotificationMockEnabled,
  mockFetchNotifications,
  mockFetchUnreadCount,
  mockMarkAllNotificationsRead,
  mockMarkNotificationRead,
} from './notification.mock'

export const NOTIFICATION_PATHS = {
  unreadCount: '/notifications/unread-count',
  list: '/notifications',
  read: (id: number) => `/notifications/${id}/read`,
  readAll: '/notifications/read-all',
} as const

const DEFAULT_PAGE_SIZE = 10

export async function fetchUnreadCount(): Promise<UnreadCountResult> {
  if (isNotificationMockEnabled()) return mockFetchUnreadCount()
  return get<UnreadCountResult>(NOTIFICATION_PATHS.unreadCount)
}

export async function fetchNotifications(query: NotificationQuery = {}): Promise<NotificationPage> {
  const params = {
    status: query.status ?? 'UNREAD',
    page_num: query.pageNum ?? 1,
    page_size: query.pageSize ?? DEFAULT_PAGE_SIZE,
  }
  if (isNotificationMockEnabled()) {
    return mockFetchNotifications({
      status: params.status,
      pageNum: params.page_num,
      pageSize: params.page_size,
    })
  }
  return get<NotificationPage>(NOTIFICATION_PATHS.list, { params })
}

export async function markNotificationRead(id: number): Promise<MarkReadResult> {
  if (isNotificationMockEnabled()) return mockMarkNotificationRead(id)
  return put<MarkReadResult>(NOTIFICATION_PATHS.read(id))
}

export async function markAllNotificationsRead(): Promise<MarkAllReadResult> {
  if (isNotificationMockEnabled()) return mockMarkAllNotificationsRead()
  return put<MarkAllReadResult>(NOTIFICATION_PATHS.readAll)
}
