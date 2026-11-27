/**
 * 学习计划类型
 * 唯一口径：《契约_学习计划推荐通知前后端共同契约》4.1 / 4.2 / 4.8 节。
 * 本文件在基础 PR 只提供类型；页面与接口在功能 PR 接入。
 */

/** 任务状态（契约 3.1）：OVERDUE 不是状态，逾期看 overdue 字段 */
export type StudyTaskStatus = 'TODO' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'

/** 可提交的状态（契约 5.3）：不能提交 CANCELLED */
export type StudyTaskUpdateStatus = 'TODO' | 'IN_PROGRESS' | 'COMPLETED'

/** 单个学习任务（契约 4.1） */
export interface StudyTask {
  id: number
  title: string
  subject: string
  knowledgePoint: string | null
  estimatedMinutes: number
  dueAt: string | null
  status: StudyTaskStatus
  overdue: boolean
  priority: number
  version: number
}

/** 今日概览（契约 4.2） */
export interface TodayStudySummary {
  completedTasks: number
  totalTasks: number
  studyMinutes: number
  streakDays: number
}

/** 今日学习计划（契约 4.2）；无计划时 planId = 0、tasks = [] */
export interface TodayStudyPlan {
  planId: number
  planDate: string
  title: string
  examDate: string | null
  daysUntilExam: number | null
  summary: TodayStudySummary
  tasks: StudyTask[]
}

/** 本周学习诊断（契约 4.8）；未生成时 reportId = null，不是 404 */
export interface WeeklyStudyReportSummary {
  reportId: number | null
  weekStart: string
  weekEnd: string
  completionRate: number
  studyMinutes: number
  chatQuestions: number
  weakSubjects: string[]
  summary: string
  nextActions: string[]
  generatedAt: string | null
}
