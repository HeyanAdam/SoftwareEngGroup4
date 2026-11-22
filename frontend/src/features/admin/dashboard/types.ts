/**
 * 管理员运营大屏 —— 接口契约类型
 *
 * 来源：后端契约 GET /api/v1/admin/dashboard/summary?days=7|14|30
 * 字段名必须与后端 DTO（甲的 AdminDashboardSummaryVO）保持一致，
 * 改动字段前先与后端负责人对齐，避免联调时字段错位。
 */

/** 趋势天数，后端只允许 7 / 14 / 30 */
export type DashboardDays = 7 | 14 | 30

/** 顶部 KPI 卡片统计 */
export interface DashboardCards {
  totalUsers: number
  enabledUsers: number
  todayLogins: number
  todayLoginFailures: number
  files: number
  /** 文件占用字节总数 */
  storageBytes: number
  documents: number
  failedDocuments: number
  conversations: number
  chatMessages: number
  todayChatMessages: number
}

/**
 * 趋势数据。
 * dates 与 logins / uploads / chatMessages 长度必须一致，
 * 缺失日期由后端补 0，前端按数组下标一一对应。
 */
export interface DashboardTrend {
  dates: string[]
  logins: number[]
  uploads: number[]
  chatMessages: number[]
}

/**
 * 系统与队列指标。
 * 前三个为探活结果，只允许 UP / DOWN；
 * 其余为非负整数。
 */
export interface DashboardSystem {
  mysql: 'UP' | 'DOWN'
  redis: 'UP' | 'DOWN'
  rabbitmq: 'UP' | 'DOWN'
  docParseReady: number
  docParseConsumers: number
  docParseDlq: number
  outboxPending: number
  outboxFailed: number
}

/** 大屏聚合响应根对象 */
export interface AdminDashboardSummary {
  cards: DashboardCards
  trend: DashboardTrend
  system: DashboardSystem
  /** 后端生成时间，ISO 8601，Asia/Shanghai */
  generatedAt: string
}

/** KPI 卡片的展示等级，用于配色 */
export type KpiTone = 'primary' | 'success' | 'warning' | 'danger'

/** 页面内部使用的 KPI 卡片视图模型 */
export interface KpiItem {
  key: string
  title: string
  value: string | number
  hint: string
  tone: KpiTone
}
