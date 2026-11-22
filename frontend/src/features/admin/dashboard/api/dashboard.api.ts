import { adminGet } from '../../../../shared/api/request'
import type { AdminDashboardSummary, DashboardDays } from '../types'

/**
 * 获取运营大屏聚合数据。
 *
 * 使用管理员后台专用实例（baseURL = /api/v1/admin，携带后台 Token），
 * 因此 url 只需写 /dashboard/summary，实际请求为
 * GET /api/v1/admin/dashboard/summary?days=7|14|30。
 *
 * 注意：本函数永远请求真实后端接口，不包含任何 mock 分支。
 * 本地调试需要的模拟数据见 ../mock/dashboard.mock.ts，由开发者自行临时引用。
 */
export function getDashboardSummary(days: DashboardDays): Promise<AdminDashboardSummary> {
  return adminGet<AdminDashboardSummary>('/dashboard/summary', { params: { days } })
}
