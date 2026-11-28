/**
 * 站内通知类型
 * 唯一口径：《契约_学习计划推荐通知前后端共同契约》3.7 / 3.8 / 4.6 / 4.7 / 6.3。
 * 只允许出现契约里的字段：接口需要新字段时先改契约，不在前端临时加。
 */

/** 通知类型（契约 3.7） */
export type NotificationType =
  | 'TASK_DUE'
  | 'TASK_OVERDUE'
  | 'DAILY_PLAN'
  | 'WEEKLY_REPORT'
  | 'EXAM_MILESTONE'

/** 通知级别（契约 3.8） */
export type NotificationSeverity = 'INFO' | 'WARNING' | 'URGENT'

/** 单条通知（契约 4.6） */
export interface UserNotification {
  id: number
  type: NotificationType
  severity: NotificationSeverity
  title: string
  content: string
  actionPath: string | null
  read: boolean
  createdAt: string
  readAt: string | null
}

/** 通知分页（契约 4.7）：沿用项目既有 R.PageData 口径 list / total / page_num / page_size */
export interface NotificationPage {
  list: UserNotification[]
  total: number
  page_num: number
  page_size: number
}

/** 未读数（契约 5.7） */
export interface UnreadCountResult {
  unreadCount: number
}

/** 单条已读结果（契约 5.9） */
export interface MarkReadResult {
  id: number
  read: boolean
  unreadCount: number
}

/** 全部已读结果（契约 5.10） */
export interface MarkAllReadResult {
  updated: number
  unreadCount: number
}

/** 通知列表查询参数（契约 5.8） */
export interface NotificationQuery {
  status?: 'UNREAD' | 'ALL'
  pageNum?: number
  pageSize?: number
}

/** 实时新通知事件（契约 6.3） */
export interface NotificationCreatedEvent {
  version: 1
  type: 'notification.created'
  requestId: string
  serverTime: string
  data: {
    eventId: string
    notification: UserNotification
    unreadCount: number
  }
}

/** 实时推荐更新事件（契约 6.2），前端 B 只负责把它转交给前端 A 的 Insight Store */
export interface RecommendationUpdatedEvent {
  version: 1
  type: 'recommendation.updated'
  requestId: string
  serverTime: string
  data: {
    eventId: string
    scene: 'TODAY' | 'CHAT' | 'TASK_COMPLETE'
    contextType: 'NONE' | 'MESSAGE' | 'TASK' | 'CONVERSATION'
    contextId: number | null
    items: unknown[]
  }
}

/** 通知等级 → Element Plus tag 类型（展示层唯一映射，组件不各自写 switch） */
export function severityTagType(severity: NotificationSeverity): 'info' | 'warning' | 'danger' {
  switch (severity) {
    case 'WARNING':
      return 'warning'
    case 'URGENT':
      return 'danger'
    default:
      return 'info'
  }
}

/** 通知类型 → 中文标签，避免模板里堆 if */
export const NOTIFICATION_TYPE_LABELS: Record<NotificationType, string> = {
  TASK_DUE: '任务将截止',
  TASK_OVERDUE: '任务已逾期',
  DAILY_PLAN: '每日计划',
  WEEKLY_REPORT: '学习周报',
  EXAM_MILESTONE: '考试节点',
}
