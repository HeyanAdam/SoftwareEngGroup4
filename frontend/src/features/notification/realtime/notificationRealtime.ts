/**
 * 通知实时事件的解析与接线（纯函数 + 可注入的事件源）
 *
 * 为什么这里不自己 new WebSocket：
 * 现有实时通道是 Chat 域内的 `setChatPort(createRealtimeChatClient())`，只在聊天页建立。
 * 通知要"任何页面都能实时收到"，正确做法是把这条连接提升为学生端全局连接（单连接、多事件类型分发），
 * 这属于架构变更，需要组长和后端确认，不能由前端 B 私自在 MainLayout 再开第二条 WebSocket。
 * 所以本模块只做两件事：
 *   1. 把一条原始事件解析成契约 6.3 的 NotificationCreatedEvent（解析失败就丢弃，不猜字段）；
 *   2. 提供一个"事件源"接口，谁持有连接谁转发进来。全局连接落地后只需把连接层的事件往这灌，不用改通知业务。
 */

import type { NotificationCreatedEvent, UserNotification } from '../types'

/** 全局事件源投递通知事件用的浏览器事件名（单连接 owner 转发即可，不开第二条 WS） */
export const NOTIFICATION_REALTIME_EVENT = 'kaoyan:realtime:event'

/** 站内跳转白名单：必须以 / 开头且不能是 //host（契约 6.3，禁止 javascript:/外站） */
export function isSafeActionPath(path: string | null | undefined): path is string {
  if (typeof path !== 'string' || path.length === 0) return false
  if (!path.startsWith('/')) return false
  if (path.startsWith('//')) return false
  return true
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isNotificationPayload(value: unknown): value is UserNotification {
  if (!isPlainObject(value)) return false
  return (
    typeof value.id === 'number' &&
    typeof value.type === 'string' &&
    typeof value.severity === 'string' &&
    typeof value.title === 'string' &&
    typeof value.content === 'string' &&
    (value.actionPath === null || typeof value.actionPath === 'string') &&
    typeof value.read === 'boolean' &&
    typeof value.createdAt === 'string'
  )
}

/** 只认契约 6.3 的形状；缺字段/类型不对一律判为不合法，交给上层忽略 */
export function parseNotificationCreatedEvent(raw: unknown): NotificationCreatedEvent | null {
  if (!isPlainObject(raw)) return null
  if (raw.version !== 1 || raw.type !== 'notification.created') return null
  const data = raw.data
  if (!isPlainObject(data)) return null
  if (typeof data.eventId !== 'string' || data.eventId.length === 0) return null
  if (typeof data.unreadCount !== 'number' || !Number.isFinite(data.unreadCount)) return null
  if (!isNotificationPayload(data.notification)) return null
  return raw as unknown as NotificationCreatedEvent
}

export interface NotificationRealtimeHandlers {
  onCreated: (event: NotificationCreatedEvent) => void
}

/**
 * 把任意原始事件交给通知层：
 * - notification.created 交给通知 Store；
 * - recommendation.updated 不在这里处理，由前端 A 的 Insight Store 负责；
 * - 其它类型（聊天流式事件）直接忽略，保证同一条连接上多事件类型互不干扰。
 */
export function dispatchRealtimeEvent(raw: unknown, handlers: NotificationRealtimeHandlers): boolean {
  const event = parseNotificationCreatedEvent(raw)
  if (!event) return false
  handlers.onCreated(event)
  return true
}

export interface RealtimeEventSource {
  /** 订阅原始事件，返回取消订阅函数 */
  subscribe: (listener: (raw: unknown) => void) => () => void
}

/**
 * 基于 window 事件的事件源：供当前单连接 owner（聊天连接层/后续全局连接）转发。
 * 没有 owner 转发时它什么也不会收到，不会产生连接或轮询，属于安全空转。
 */
export function createWindowRealtimeSource(): RealtimeEventSource {
  return {
    subscribe(listener) {
      const handler = (event: Event): void => {
        const detail = (event as CustomEvent<unknown>).detail
        listener(detail)
      }
      window.addEventListener(NOTIFICATION_REALTIME_EVENT, handler)
      return () => window.removeEventListener(NOTIFICATION_REALTIME_EVENT, handler)
    },
  }
}

/** 绑定事件源 → 通知层；返回解绑函数，组件卸载时调用 */
export function bindNotificationRealtime(
  source: RealtimeEventSource,
  handlers: NotificationRealtimeHandlers,
): () => void {
  return source.subscribe((raw) => {
    dispatchRealtimeEvent(raw, handlers)
  })
}
