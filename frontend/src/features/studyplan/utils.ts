/**
 * 学习计划纯函数工具
 * 契约依据：1.6（任务排序）、3.1（逾期判定由服务端给值）、4.1（字段范围）、4.2（今日概览统计规则）。
 * 这里只做「按契约规则重算展示值」，不替代服务端鉴权与逾期判断。
 */

import type {
  StudyTask,
  StudyTaskStatus,
  TodayStudyPlan,
  TodayStudySummary,
} from './types'

/** 任务状态文案 */
export const TASK_STATUS_LABELS: Record<StudyTaskStatus, string> = {
  TODO: '未开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

/** 契约 1.6：priority 降序 → dueAt 升序 → 无截止时间靠后 → id 升序 */
export function compareStudyTask(a: StudyTask, b: StudyTask): number {
  if (b.priority !== a.priority) return b.priority - a.priority
  if (a.dueAt !== b.dueAt) {
    if (a.dueAt === null) return 1
    if (b.dueAt === null) return -1
    return a.dueAt < b.dueAt ? -1 : 1
  }
  return a.id - b.id
}

export function sortTasks(tasks: readonly StudyTask[]): StudyTask[] {
  return [...tasks].sort(compareStudyTask)
}

/**
 * 契约 4.2 统计规则：
 * - completedTasks = COMPLETED 数量
 * - totalTasks 不含 CANCELLED
 * - studyMinutes = 已完成任务的 estimatedMinutes 之和
 * - streakDays 服务端计算，前端不重算，沿用原值
 */
export function recomputeSummary(plan: TodayStudyPlan): TodayStudySummary {
  const completed = plan.tasks.filter((task) => task.status === 'COMPLETED')
  const counted = plan.tasks.filter((task) => task.status !== 'CANCELLED')
  return {
    completedTasks: completed.length,
    totalTasks: counted.length,
    studyMinutes: completed.reduce((total, task) => total + task.estimatedMinutes, 0),
    streakDays: plan.summary.streakDays,
  }
}

/** 用服务端返回的任务覆盖本地任务（按 id 匹配），并重排、重算概览（契约 9.2 的 200 分支） */
export function applyTaskUpdate(plan: TodayStudyPlan, updated: StudyTask): TodayStudyPlan {
  const tasks = plan.tasks.map((task) => (task.id === updated.id ? updated : task))
  const next: TodayStudyPlan = { ...plan, tasks: sortTasks(tasks) }
  return { ...next, summary: recomputeSummary(next) }
}

/**
 * 乐观更新：只改本地 status，overdue / version 保留服务端值，
 * 等 PATCH 返回后再用 applyTaskUpdate 覆盖。
 */
export function applyTaskStatus(
  plan: TodayStudyPlan,
  taskId: number,
  status: StudyTaskStatus,
): TodayStudyPlan {
  const tasks = plan.tasks.map((task) => (task.id === taskId ? { ...task, status } : task))
  const next: TodayStudyPlan = { ...plan, tasks }
  return { ...next, summary: recomputeSummary(next) }
}

/** 失败回滚：把提交前的任务原样放回去（恢复 status / version / overdue） */
export function restoreTask(plan: TodayStudyPlan, previous: StudyTask): TodayStudyPlan {
  const tasks = plan.tasks.map((task) => (task.id === previous.id ? previous : task))
  const next: TodayStudyPlan = { ...plan, tasks: sortTasks(tasks) }
  return { ...next, summary: recomputeSummary(next) }
}

/** 接受推荐后把新任务插入今日列表并按契约 1.6 排序（契约 5.5 返回 task） */
export function insertTask(plan: TodayStudyPlan, task: StudyTask): TodayStudyPlan {
  const tasks = sortTasks([...plan.tasks.filter((item) => item.id !== task.id), task])
  const next: TodayStudyPlan = { ...plan, tasks }
  return { ...next, summary: recomputeSummary(next) }
}

/** 点击复选框的目标状态：已完成 → 恢复未开始；其余 → 标记完成（契约 5.3 允许的状态） */
export function nextToggleStatus(status: StudyTaskStatus): 'TODO' | 'COMPLETED' {
  return status === 'COMPLETED' ? 'TODO' : 'COMPLETED'
}

/**
 * 空计划判定：只要今天没有任务，就展示可操作空态。
 * 数据库可能已有计划容器但任务为空，此时继续显示 0/0 概览会像没有实现的空格子。
 */
export function isPlanEmpty(plan: TodayStudyPlan | null): boolean {
  return !plan || plan.tasks.length === 0
}

/** 截止时间只展示服务端时区里的时分，不做本地时区换算 */
export function formatTaskTime(dueAt: string | null): string {
  if (!dueAt) return ''
  const matched = /T(\d{2}:\d{2})/.exec(dueAt)
  return matched ? matched[1] : ''
}

/** 完成率 0~1 → 百分比文案（契约 4.8：不是 0~100） */
export function formatPercent(rate: number): string {
  if (!Number.isFinite(rate)) return '0%'
  const clamped = Math.min(1, Math.max(0, rate))
  return `${Math.round(clamped * 100)}%`
}

/** 完成进度用于进度条（0~100），空计划返回 0 */
export function progressPercent(summary: TodayStudySummary): number {
  if (summary.totalTasks <= 0) return 0
  return Math.round((summary.completedTasks / summary.totalTasks) * 100)
}
