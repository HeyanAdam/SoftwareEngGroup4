/**
 * 学习计划接口路径（契约 5.1 / 5.2 / 5.3 / 5.11）
 * 基础 PR 只放常量，请求函数在功能 PR 的 studyplan.api.ts 里实现。
 * 前缀 /api/v1 由 shared/api/request.ts 统一携带，这里不重复写。
 */
export const STUDY_PLAN_PATHS = {
  today: '/study-plans/today',
  generateToday: '/study-plans/today/generate',
  task: (id: number) => `/study-tasks/${id}`,
  weeklyReport: '/study-reports/weekly/current',
} as const
