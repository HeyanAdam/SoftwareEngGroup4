import type { AdminDashboardSummary } from '../types'

/**
 * 大屏本地调试用的模拟数据（4 套场景）。
 *
 * 使用约定（重要）：
 * 1. 本文件会随代码提交到仓库，作为任何人在本地快速预览页面效果的备用资源；
 * 2. 但业务代码里【不允许】存在生效的 mock 引用，dashboard.api.ts 永远请求真实接口；
 * 3. 需要看页面效果时，在你本地的 AdminDashboardPage.vue 里临时 import 并赋值，
 *    调试完必须把那段临时代码恢复成注释，否则队友真机联调会一直拿到假数据。
 */

/** 场景 1：一切正常 */
export const mockDashboardNormal: AdminDashboardSummary = {
  cards: {
    totalUsers: 120,
    enabledUsers: 118,
    todayLogins: 45,
    todayLoginFailures: 3,
    files: 260,
    storageBytes: 837124098,
    documents: 18,
    failedDocuments: 1,
    conversations: 64,
    chatMessages: 980,
    todayChatMessages: 120,
  },
  trend: {
    dates: ['11-15', '11-16', '11-17', '11-18', '11-19', '11-20', '11-21'],
    logins: [10, 14, 13, 20, 26, 31, 45],
    uploads: [2, 5, 4, 9, 12, 14, 18],
    chatMessages: [30, 42, 38, 65, 80, 96, 120],
  },
  system: {
    mysql: 'UP',
    redis: 'UP',
    rabbitmq: 'UP',
    docParseReady: 0,
    docParseConsumers: 1,
    docParseDlq: 0,
    outboxPending: 0,
    outboxFailed: 0,
  },
  generatedAt: '2026-11-21T14:00:00+08:00',
}

/** 场景 2：空数据（新部署环境，所有统计为 0，趋势全 0） */
export const mockDashboardEmpty: AdminDashboardSummary = {
  cards: {
    totalUsers: 0,
    enabledUsers: 0,
    todayLogins: 0,
    todayLoginFailures: 0,
    files: 0,
    storageBytes: 0,
    documents: 0,
    failedDocuments: 0,
    conversations: 0,
    chatMessages: 0,
    todayChatMessages: 0,
  },
  trend: {
    dates: ['11-15', '11-16', '11-17', '11-18', '11-19', '11-20', '11-21'],
    logins: [0, 0, 0, 0, 0, 0, 0],
    uploads: [0, 0, 0, 0, 0, 0, 0],
    chatMessages: [0, 0, 0, 0, 0, 0, 0],
  },
  system: {
    mysql: 'UP',
    redis: 'UP',
    rabbitmq: 'UP',
    docParseReady: 0,
    docParseConsumers: 0,
    docParseDlq: 0,
    outboxPending: 0,
    outboxFailed: 0,
  },
  generatedAt: '2026-11-21T14:00:00+08:00',
}

/** 场景 3：Redis 与 RabbitMQ 宕机（验证降级：业务数据仍可展示，服务状态变红） */
export const mockDashboardServiceDown: AdminDashboardSummary = {
  cards: {
    totalUsers: 120,
    enabledUsers: 118,
    todayLogins: 45,
    todayLoginFailures: 3,
    files: 260,
    storageBytes: 837124098,
    documents: 18,
    failedDocuments: 4,
    conversations: 64,
    chatMessages: 980,
    todayChatMessages: 120,
  },
  trend: {
    dates: ['11-15', '11-16', '11-17', '11-18', '11-19', '11-20', '11-21'],
    logins: [10, 14, 13, 20, 26, 31, 45],
    uploads: [2, 5, 4, 9, 12, 14, 18],
    chatMessages: [30, 42, 38, 65, 80, 96, 120],
  },
  system: {
    mysql: 'UP',
    redis: 'DOWN',
    rabbitmq: 'DOWN',
    docParseReady: 0,
    docParseConsumers: 0,
    docParseDlq: 0,
    outboxPending: 0,
    outboxFailed: 0,
  },
  generatedAt: '2026-11-21T14:00:00+08:00',
}

/** 场景 4：队列积压与死信（验证告警配色：死信、失败必须红色） */
export const mockDashboardQueueBacklog: AdminDashboardSummary = {
  cards: {
    totalUsers: 120,
    enabledUsers: 118,
    todayLogins: 45,
    todayLoginFailures: 3,
    files: 260,
    storageBytes: 837124098,
    documents: 18,
    failedDocuments: 6,
    conversations: 64,
    chatMessages: 980,
    todayChatMessages: 120,
  },
  trend: {
    dates: ['11-15', '11-16', '11-17', '11-18', '11-19', '11-20', '11-21'],
    logins: [10, 14, 13, 20, 26, 31, 45],
    uploads: [2, 5, 4, 9, 12, 14, 18],
    chatMessages: [30, 42, 38, 65, 80, 96, 120],
  },
  system: {
    mysql: 'UP',
    redis: 'UP',
    rabbitmq: 'UP',
    docParseReady: 35,
    docParseConsumers: 2,
    docParseDlq: 7,
    outboxPending: 12,
    outboxFailed: 5,
  },
  generatedAt: '2026-11-21T14:00:00+08:00',
}
