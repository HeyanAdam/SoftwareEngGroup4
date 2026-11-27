/**
 * 学习计划 Mock
 *
 * 开关沿用 insight 层同一套，不新增开关：
 *   localStorage.setItem('kaoyan.insight.mock', 'on')
 *   localStorage.setItem('kaoyan.insight.mock.scenario', 'normal' | 'empty' | 'error')
 * 只有 import.meta.env.DEV 为真且显式打开才生效。
 *
 * 数据形状严格按契约 4.1 / 4.2 / 4.8 / 5.1，不额外造字段。
 */

import { ApiError } from '../../../shared/api/request'
import type { StudyTask, StudyTaskStatus, TodayStudyPlan, WeeklyStudyReportSummary } from '../types'
import { sortTasks } from '../utils'

/** 与推荐 Mock 共用的开关键，见 features/insight/mock/insight.mock.ts */
export const STUDY_PLAN_MOCK_FLAG_KEY = 'kaoyan.insight.mock'
export const STUDY_PLAN_MOCK_SCENARIO_KEY = 'kaoyan.insight.mock.scenario'

const MOCK_DELAY = 160

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function readStorage(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

export function isStudyPlanMockEnabled(): boolean {
  return import.meta.env.DEV && readStorage(STUDY_PLAN_MOCK_FLAG_KEY) === 'on'
}

export function getStudyPlanMockScenario(): 'normal' | 'empty' | 'error' {
  const value = readStorage(STUDY_PLAN_MOCK_SCENARIO_KEY)
  if (value === 'empty' || value === 'error') return value
  return 'normal'
}

const MOCK_PLAN_DATE = '2026-11-26'
const MOCK_EXAM_DATE = '2026-12-26'
const MOCK_GENERATED_AT = '2026-11-26T20:00:00+08:00'

/** 逾期判定只在本 Mock 里扮演服务端（前端页面永远读 overdue 字段） */
function calcOverdue(status: StudyTaskStatus, dueAt: string | null): boolean {
  if (!dueAt || status === 'COMPLETED' || status === 'CANCELLED') return false
  return Date.parse(dueAt) < Date.now()
}

function withOverdue(task: StudyTask): StudyTask {
  return { ...task, overdue: calcOverdue(task.status, task.dueAt) }
}

/** 4 个任务：2 个已完成、1 个逾期，顺序符合契约 1.6 */
const MOCK_TASKS: StudyTask[] = [
  {
    id: 502,
    title: '矩阵相似对角化：回看 2 道错题',
    subject: '数学',
    knowledgePoint: '矩阵相似对角化',
    estimatedMinutes: 30,
    dueAt: '2026-11-26T20:00:00+08:00',
    status: 'TODO',
    overdue: true,
    priority: 90,
    version: 2,
  },
  {
    id: 501,
    title: '概率论：全概率公式复习',
    subject: '数学',
    knowledgePoint: '全概率公式',
    estimatedMinutes: 20,
    dueAt: '2026-11-26T21:30:00+08:00',
    status: 'COMPLETED',
    overdue: false,
    priority: 80,
    version: 3,
  },
  {
    id: 504,
    title: '英语阅读：2019 年真题精读',
    subject: '英语',
    knowledgePoint: '阅读理解',
    estimatedMinutes: 30,
    dueAt: '2026-11-26T22:00:00+08:00',
    status: 'COMPLETED',
    overdue: false,
    priority: 80,
    version: 1,
  },
  {
    id: 503,
    title: '政治：马原第二章知识点梳理',
    subject: '政治',
    knowledgePoint: '唯物辩证法',
    estimatedMinutes: 25,
    dueAt: null,
    status: 'IN_PROGRESS',
    overdue: false,
    priority: 70,
    version: 0,
  },
]

/** 有计划的今日计划（契约 5.1 成功响应同款结构） */
const MOCK_PLAN: TodayStudyPlan = {
  planId: 1001,
  planDate: MOCK_PLAN_DATE,
  title: '2027 考研基础阶段',
  examDate: MOCK_EXAM_DATE,
  daysUntilExam: 30,
  summary: {
    completedTasks: 2,
    totalTasks: 4,
    studyMinutes: 50,
    streakDays: 6,
  },
  tasks: MOCK_TASKS,
}

/** 空计划（契约 5.1 空计划示例），不返回 404 */
const MOCK_EMPTY_PLAN: TodayStudyPlan = {
  planId: 0,
  planDate: MOCK_PLAN_DATE,
  title: '',
  examDate: null,
  daysUntilExam: null,
  summary: { completedTasks: 0, totalTasks: 0, studyMinutes: 0, streakDays: 0 },
  tasks: [],
}

/** 已生成的周报（契约 4.8） */
export const MOCK_WEEKLY_REPORT: WeeklyStudyReportSummary = {
  reportId: 3001,
  weekStart: '2026-11-23',
  weekEnd: '2026-11-29',
  completionRate: 0.72,
  studyMinutes: 315,
  chatQuestions: 18,
  weakSubjects: ['数学'],
  summary: '本周任务完成率 72%，英语阅读理解表现稳定，数学任务仍有 2 项逾期。',
  nextActions: ['优先补完矩阵相似对角化', '每天安排 20 分钟英语阅读', '周日复盘本周数学错题'],
  generatedAt: MOCK_GENERATED_AT,
}

/** 未生成周报（契约 5.11 未生成示例，reportId = null 不是错误） */
export const MOCK_WEEKLY_REPORT_EMPTY: WeeklyStudyReportSummary = {
  reportId: null,
  weekStart: '2026-11-23',
  weekEnd: '2026-11-29',
  completionRate: 0,
  studyMinutes: 0,
  chatQuestions: 0,
  weakSubjects: [],
  summary: '',
  nextActions: [],
  generatedAt: null,
}

/** Mock 的「数据库」：同一份内存状态，让任务状态修改能连续演示 */
let currentPlan: TodayStudyPlan = {
  ...MOCK_PLAN,
  tasks: MOCK_PLAN.tasks.map((task) => ({ ...task })),
}

/** 供测试与页面重置 Mock 状态 */
export function resetStudyPlanMock(): void {
  currentPlan = { ...MOCK_PLAN, tasks: MOCK_PLAN.tasks.map((task) => ({ ...task })) }
}

function clonePlan(plan: TodayStudyPlan): TodayStudyPlan {
  return {
    ...plan,
    summary: { ...plan.summary },
    tasks: plan.tasks.map((task) => ({ ...task })),
  }
}

/** GET /study-plans/today */
export async function mockTodayPlan(): Promise<TodayStudyPlan> {
  await delay(MOCK_DELAY)
  if (getStudyPlanMockScenario() === 'empty') {
    return clonePlan({ ...MOCK_EMPTY_PLAN, planDate: currentPlan.planDate })
  }
  return clonePlan(currentPlan)
}

/** POST /study-plans/today/generate：已有计划直接返回，不重复创建（契约 5.2） */
export async function mockGenerateTodayPlan(): Promise<TodayStudyPlan> {
  await delay(MOCK_DELAY)
  const emptyScenario = getStudyPlanMockScenario() === 'empty'
  if (!emptyScenario && currentPlan.planId !== 0 && currentPlan.tasks.length > 0) {
    return clonePlan(currentPlan)
  }

  const tasks: StudyTask[] = [
    {
      id: 601,
      title: '数学：基础知识点复习',
      subject: '数学',
      knowledgePoint: null,
      estimatedMinutes: 30,
      dueAt: `${currentPlan.planDate}T21:30:00+08:00`,
      status: 'TODO',
      overdue: false,
      priority: 80,
      version: 0,
    },
    {
      id: 602,
      title: '英语：单词 30 个 + 一篇精读',
      subject: '英语',
      knowledgePoint: null,
      estimatedMinutes: 25,
      dueAt: `${currentPlan.planDate}T22:30:00+08:00`,
      status: 'TODO',
      overdue: false,
      priority: 60,
      version: 0,
    },
  ]

  currentPlan = {
    planId: 2001,
    planDate: currentPlan.planDate,
    title: '2027 考研基础阶段',
    examDate: MOCK_EXAM_DATE,
    daysUntilExam: 30,
    summary: { completedTasks: 0, totalTasks: tasks.length, studyMinutes: 0, streakDays: 6 },
    tasks,
  }
  return clonePlan(currentPlan)
}

/** PATCH /study-tasks/{id}：expectedVersion 不一致抛 409（契约 5.3） */
export async function mockUpdateTaskStatus(
  taskId: number,
  status: Exclude<StudyTaskStatus, 'CANCELLED'>,
  expectedVersion: number,
): Promise<StudyTask> {
  await delay(MOCK_DELAY)
  const task = currentPlan.tasks.find((candidate) => candidate.id === taskId)
  if (!task) {
    throw new ApiError({ message: '任务不存在', code: 404, httpStatus: 404 })
  }
  if (task.version !== expectedVersion) {
    throw new ApiError({
      message: '任务已被修改，请刷新后重试',
      code: 409,
      httpStatus: 409,
    })
  }

  const updated = withOverdue({ ...task, status, version: task.version + 1 })
  currentPlan = {
    ...currentPlan,
    tasks: sortTasks(currentPlan.tasks.map((candidate) => (candidate.id === taskId ? updated : candidate))),
  }
  return { ...updated }
}

/** GET /study-reports/weekly/current */
export async function mockWeeklyReport(): Promise<WeeklyStudyReportSummary> {
  await delay(MOCK_DELAY)
  if (getStudyPlanMockScenario() === 'empty') {
    return { ...MOCK_WEEKLY_REPORT_EMPTY, weakSubjects: [], nextActions: [] }
  }
  return { ...MOCK_WEEKLY_REPORT, weakSubjects: [...MOCK_WEEKLY_REPORT.weakSubjects], nextActions: [...MOCK_WEEKLY_REPORT.nextActions] }
}
