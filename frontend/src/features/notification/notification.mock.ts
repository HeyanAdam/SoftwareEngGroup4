/**
 * 通知 Mock 数据与 Mock 响应
 *
 * 为什么 Mock 放在这里而不是 shared/api/request.ts 的 dispatchMock：
 * 那一层的 dispatchMock 只覆盖学生端认证接口（见 request.ts 注释）；
 * 前端 A 的导学推荐也是在 API 层自带 Mock 分支（kaoyan.insight.mock）。
 * 通知沿用同一做法，开关 key 单独用 kaoyan.notification.mock，互不干扰。
 *
 * Mock 只模拟"后端按契约返回什么"，不改变前端任何字段口径（分页仍是 list/total/page_num/page_size）。
 */

import type {
  MarkAllReadResult,
  MarkReadResult,
  NotificationPage,
  NotificationQuery,
  NotificationType,
  NotificationSeverity,
  UnreadCountResult,
  UserNotification,
} from './types'

/** Mock 开关（localStorage），与 A 的 kaoyan.insight.mock 区分开 */
export const NOTIFICATION_MOCK_STORAGE_KEY = 'kaoyan.notification.mock'

/** 只在开发环境允许开 Mock，避免演练/线上误开 */
export function isNotificationMockEnabled(): boolean {
  if (!import.meta.env.DEV) return false
  try {
    return localStorage.getItem(NOTIFICATION_MOCK_STORAGE_KEY) === 'on'
  } catch {
    return false
  }
}

interface MockSeed {
  id: number
  type: NotificationType
  severity: NotificationSeverity
  title: string
  content: string
  actionPath: string | null
  read: boolean
  createdAt: string
}

/** 覆盖 5 种类型 + 3 种级别 + 有/无 actionPath，方便验收时一眼看全 */
const MOCK_SEEDS: MockSeed[] = [
  {
    id: 101,
    type: 'TASK_OVERDUE',
    severity: 'URGENT',
    title: '「线代第3章 特征值」已逾期',
    content: '原计划 11-27 完成，现已逾期 1 天，建议今天先补 30 分钟。',
    actionPath: '/study-plans',
    read: false,
    createdAt: '2026-11-28T08:40:00+08:00',
  },
  {
    id: 100,
    type: 'TASK_DUE',
    severity: 'WARNING',
    title: '今天 21:00 前需完成「高数第5章 定积分」',
    content: '预计还需 40 分钟，现在开始可以按时完成。',
    actionPath: '/study-plans',
    read: false,
    createdAt: '2026-11-28T08:10:00+08:00',
  },
  {
    id: 99,
    type: 'DAILY_PLAN',
    severity: 'INFO',
    title: '今日学习计划已生成',
    content: '共 4 个任务、预计 150 分钟，先从「英语阅读精练」开始。',
    actionPath: '/study-plans',
    read: false,
    createdAt: '2026-11-28T07:05:00+08:00',
  },
  {
    id: 98,
    type: 'WEEKLY_REPORT',
    severity: 'INFO',
    title: '上周学习周报已生成',
    content: '完成率 78%，数学正确率提升 6%，查看完整周报了解薄弱点。',
    actionPath: '/study-plans',
    read: false,
    createdAt: '2026-11-24T20:00:00+08:00',
  },
  {
    id: 97,
    type: 'EXAM_MILESTONE',
    severity: 'WARNING',
    title: '距考研初试还有 25 天',
    content: '进入冲刺阶段，建议把每日刷题量提升到 3 套。',
    actionPath: '/dashboard',
    read: true,
    createdAt: '2026-11-23T09:00:00+08:00',
  },
  {
    id: 96,
    type: 'TASK_DUE',
    severity: 'WARNING',
    title: '「政治 马原 第一章」今天到期',
    content: '还剩 20 道选择题未完成。',
    actionPath: '/study-plans',
    read: true,
    createdAt: '2026-11-23T08:20:00+08:00',
  },
  {
    id: 95,
    type: 'DAILY_PLAN',
    severity: 'INFO',
    title: '昨日复盘提醒',
    content: '昨天完成 2/4 个任务，建议今天先补未完成的「英语阅读精练」。',
    actionPath: '/study-plans',
    read: true,
    createdAt: '2026-11-23T07:30:00+08:00',
  },
  {
    id: 94,
    type: 'WEEKLY_REPORT',
    severity: 'INFO',
    title: '两周学习报告已生成',
    content: '累计学习 21 小时，连续打卡 9 天。',
    actionPath: null,
    read: true,
    createdAt: '2026-11-17T20:00:00+08:00',
  },
  {
    id: 93,
    type: 'TASK_OVERDUE',
    severity: 'URGENT',
    title: '「英语阅读精练」已逾期',
    content: '原计划 11-16 完成，逾期 1 天自动提醒。',
    actionPath: '/study-plans',
    read: true,
    createdAt: '2026-11-17T08:40:00+08:00',
  },
  {
    id: 92,
    type: 'EXAM_MILESTONE',
    severity: 'INFO',
    title: '距考研初试还有 30 天',
    content: '最后一个月，重点回归真题与错题。',
    actionPath: '/dashboard',
    read: true,
    createdAt: '2026-11-16T09:00:00+08:00',
  },
  {
    id: 91,
    type: 'DAILY_PLAN',
    severity: 'INFO',
    title: '今日学习计划已生成',
    content: '共 3 个任务、预计 120 分钟。',
    actionPath: '/study-plans',
    read: true,
    createdAt: '2026-11-16T07:05:00+08:00',
  },
  {
    id: 90,
    type: 'TASK_DUE',
    severity: 'INFO',
    title: '「线代第2章 矩阵」今天到期',
    content: '还剩 1 组练习。',
    actionPath: '/study-plans',
    read: true,
    createdAt: '2026-11-15T08:20:00+08:00',
  },
]

/** Mock 内存态：标记已读后列表要跟着变，否则点完再打开抽屉又变回未读 */
const mockState: UserNotification[] = MOCK_SEEDS.map((seed) => ({
  ...seed,
  readAt: seed.read ? seed.createdAt : null,
}))

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function delay(ms = 260): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function countUnread(): number {
  return mockState.filter((item) => !item.read).length
}

/** Mock：GET /notifications/unread-count */
export async function mockFetchUnreadCount(): Promise<UnreadCountResult> {
  await delay(160)
  return { unreadCount: countUnread() }
}

/** Mock：GET /notifications（分页字段严格用契约 4.7 的 list/total/page_num/page_size） */
export async function mockFetchNotifications(query: NotificationQuery = {}): Promise<NotificationPage> {
  await delay()
  const status = query.status ?? 'UNREAD'
  const pageNum = query.pageNum ?? 1
  const pageSize = query.pageSize ?? 10
  const source = status === 'UNREAD' ? mockState.filter((item) => !item.read) : mockState
  const start = (pageNum - 1) * pageSize
  return {
    list: clone(source.slice(start, start + pageSize)),
    total: source.length,
    page_num: pageNum,
    page_size: pageSize,
  }
}

/** Mock：PUT /notifications/{id}/read */
export async function mockMarkNotificationRead(id: number): Promise<MarkReadResult> {
  await delay(200)
  const target = mockState.find((item) => item.id === id)
  if (!target) {
    throw Object.assign(new Error('通知不存在'), { status: 404, code: 404 })
  }
  if (!target.read) {
    target.read = true
    target.readAt = new Date().toISOString()
  }
  return { id, read: true, unreadCount: countUnread() }
}

/** Mock：PUT /notifications/read-all */
export async function mockMarkAllNotificationsRead(): Promise<MarkAllReadResult> {
  await delay(240)
  let updated = 0
  const now = new Date().toISOString()
  for (const item of mockState) {
    if (!item.read) {
      item.read = true
      item.readAt = now
      updated += 1
    }
  }
  return { updated, unreadCount: 0 }
}

/** Mock 实时推送的一条新通知：id 从 200 起递增，不和种子数据撞号 */
let mockRealtimeSeq = 0

export function buildMockRealtimeNotification(now: Date = new Date()): UserNotification {
  mockRealtimeSeq += 1
  const templates: Array<Pick<UserNotification, 'type' | 'severity' | 'title' | 'content' | 'actionPath'>> = [
    {
      type: 'TASK_DUE',
      severity: 'WARNING',
      title: '「高数第5章 定积分」将在 1 小时后到期',
      content: '还剩 12 道题，建议现在开始。',
      actionPath: '/study-plans',
    },
    {
      type: 'TASK_OVERDUE',
      severity: 'URGENT',
      title: '「政治 马原 第一章」已逾期',
      content: '逾期未完成会自动提醒，今天可安排 20 分钟补完。',
      actionPath: '/study-plans',
    },
    {
      type: 'DAILY_PLAN',
      severity: 'INFO',
      title: '为你生成了今日补充计划',
      content: '基于昨天的完成情况，新增 2 个巩固任务。',
      actionPath: '/study-plans',
    },
  ]
  const template = templates[(mockRealtimeSeq - 1) % templates.length]
  const item: UserNotification = {
    id: 200 + mockRealtimeSeq,
    type: template.type,
    severity: template.severity,
    title: template.title,
    content: template.content,
    actionPath: template.actionPath,
    read: false,
    createdAt: now.toISOString(),
    readAt: null,
  }
  // 推进 Mock 内存态，保证"打开抽屉看到的列表"和"推送进来的那条"一致
  mockState.unshift(item)
  return clone(item)
}

/** 测试用：把 Mock 数据恢复到初始种子 */
export function resetNotificationMockState(): void {
  mockState.length = 0
  for (const seed of MOCK_SEEDS) {
    mockState.push({ ...seed, readAt: seed.read ? seed.createdAt : null })
  }
  mockRealtimeSeq = 0
}
