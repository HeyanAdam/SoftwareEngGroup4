/**
 * 通知实时 Mock（仅开发环境演示用）
 *
 * 用途：后端 B 的通知推送还没合并时，先把"铃铛角标 + 抽屉插入 + 去重"跑通给老师看。
 * 约束：
 * - 只在 kaoyan.notification.mock=on 且 DEV 时由 MainLayout 启动；
 * - 定时器必须能被 stop 清理，组件卸载/退出登录时不残留（否则会重复推送、也污染单测）；
 * - 事件形状完全按契约 6.3，和真实推送走同一个解析入口，之后换成真连接不用改业务代码。
 */

import type { NotificationCreatedEvent } from '../types'
import { buildMockRealtimeNotification } from '../notification.mock'

export interface NotificationMockRealtimeOptions {
  /** 收到一条模拟推送 */
  onCreated: (event: NotificationCreatedEvent) => void
  /** 事件里携带的未读数：以当前 Store 未读数为基准 +1（真实后端也是由服务端算好再下发） */
  nextUnreadCount: () => number
  /** 推送间隔，默认 20 秒（演示时不用等太久，又不至于刷屏） */
  intervalMs?: number
  /** 首条延迟，默认 8 秒，避免一进页面就弹 */
  firstDelayMs?: number
}

export interface NotificationMockRealtime {
  start: () => void
  stop: () => void
  /** 手动触发一条，便于演示/验收时立刻看到效果 */
  pushNow: () => void
  /** 当前是否在跑（测试断言定时器是否清理用） */
  isRunning: () => boolean
}

export function createNotificationMockRealtime(
  options: NotificationMockRealtimeOptions,
): NotificationMockRealtime {
  const intervalMs = options.intervalMs ?? 20_000
  const firstDelayMs = options.firstDelayMs ?? 8_000
  let timeoutId: ReturnType<typeof setTimeout> | null = null
  let intervalId: ReturnType<typeof setInterval> | null = null
  let seq = 0

  function emit(): void {
    seq += 1
    const notification = buildMockRealtimeNotification(new Date())
    const event: NotificationCreatedEvent = {
      version: 1,
      type: 'notification.created',
      requestId: `mock-${Date.now()}-${seq}`,
      serverTime: new Date().toISOString(),
      data: {
        // eventId 故意带上时间戳与序号：同一条只处理一次，重复投递会被 Store 挡住
        eventId: `mock-evt-${Date.now()}-${seq}`,
        notification,
        unreadCount: options.nextUnreadCount(),
      },
    }
    options.onCreated(event)
  }

  function start(): void {
    if (timeoutId !== null || intervalId !== null) return
    timeoutId = setTimeout(() => {
      timeoutId = null
      emit()
      intervalId = setInterval(emit, intervalMs)
    }, firstDelayMs)
  }

  function stop(): void {
    if (timeoutId !== null) {
      clearTimeout(timeoutId)
      timeoutId = null
    }
    if (intervalId !== null) {
      clearInterval(intervalId)
      intervalId = null
    }
  }

  return {
    start,
    stop,
    pushNow: emit,
    isRunning: () => timeoutId !== null || intervalId !== null,
  }
}
