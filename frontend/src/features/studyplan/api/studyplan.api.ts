/**
 * 学习计划接口适配层
 * 契约 5.1 / 5.2 / 5.3 / 5.11；统一走 shared/api/request.ts，不新建 axios 实例。
 * Mock 判定集中在 API 层，页面与 Store 不出现 Mock 分支。
 */

import { ApiError, del, get, patch, post, put } from '../../../shared/api/request'
import {
  getStudyPlanMockScenario,
  isStudyPlanMockEnabled,
  mockGenerateTodayPlan,
  mockCreateTask,
  mockDeleteTask,
  mockTodayPlan,
  mockUpdateTask,
  mockUpdateTaskStatus,
  mockWeeklyReport,
} from '../mock/studyplan.mock'
import type {
  StudyTask,
  StudyTaskUpdateStatus,
  StudyTaskWritePayload,
  TodayStudyPlan,
  WeeklyStudyReportSummary,
} from '../types'
import { STUDY_PLAN_PATHS } from './studyplan.paths'

/** Mock 错误场景：按契约 8.1 的服务端异常口径抛出，页面走错误卡而不是空白 */
function mockServiceError(): ApiError {
  return new ApiError({
    message: '学习计划服务暂时不可用，请稍后重试',
    code: 90000,
    httpStatus: 500,
  })
}

/** GET /study-plans/today：没有计划时返回空计划，不是错误（契约 5.1） */
export async function fetchTodayPlan(): Promise<TodayStudyPlan> {
  if (isStudyPlanMockEnabled()) {
    if (getStudyPlanMockScenario() === 'error') throw mockServiceError()
    return mockTodayPlan()
  }
  return get<TodayStudyPlan>(STUDY_PLAN_PATHS.today)
}

/** POST /study-plans/today/generate：无备考目标时后端返回 400 + 10000（契约 5.2） */
export async function generateTodayPlan(): Promise<TodayStudyPlan> {
  if (isStudyPlanMockEnabled()) {
    if (getStudyPlanMockScenario() === 'error') throw mockServiceError()
    return mockGenerateTodayPlan()
  }
  return post<TodayStudyPlan>(STUDY_PLAN_PATHS.generateToday, {})
}

/** PATCH /study-tasks/{id}：提交 status + expectedVersion，版本冲突由后端返回 409（契约 5.3） */
export async function updateTaskStatus(
  taskId: number,
  status: StudyTaskUpdateStatus,
  expectedVersion: number,
): Promise<StudyTask> {
  if (isStudyPlanMockEnabled()) {
    if (getStudyPlanMockScenario() === 'error') throw mockServiceError()
    return mockUpdateTaskStatus(taskId, status, expectedVersion)
  }
  return patch<StudyTask>(STUDY_PLAN_PATHS.task(taskId), { status, expectedVersion })
}

/** POST /study-tasks：手工加入今日任务 */
export async function createTask(payload: StudyTaskWritePayload): Promise<StudyTask> {
  if (isStudyPlanMockEnabled()) {
    if (getStudyPlanMockScenario() === 'error') throw mockServiceError()
    return mockCreateTask(payload)
  }
  return post<StudyTask>(STUDY_PLAN_PATHS.tasks, payload)
}

/** PUT /study-tasks/{id}：编辑标题、科目、知识点、时长和截止时间 */
export async function updateTask(
  taskId: number,
  payload: StudyTaskWritePayload,
): Promise<StudyTask> {
  if (isStudyPlanMockEnabled()) {
    if (getStudyPlanMockScenario() === 'error') throw mockServiceError()
    return mockUpdateTask(taskId, payload)
  }
  return put<StudyTask>(STUDY_PLAN_PATHS.task(taskId), payload)
}

/** DELETE /study-tasks/{id}：带版本号，避免删除刚被修改的任务 */
export async function deleteTask(taskId: number, expectedVersion: number): Promise<void> {
  if (isStudyPlanMockEnabled()) {
    if (getStudyPlanMockScenario() === 'error') throw mockServiceError()
    return mockDeleteTask(taskId, expectedVersion)
  }
  return del<void>(STUDY_PLAN_PATHS.task(taskId), { params: { expectedVersion } })
}

/** GET /study-reports/weekly/current：未生成时 reportId = null，不是 404（契约 5.11） */
export async function fetchWeeklyReport(): Promise<WeeklyStudyReportSummary> {
  if (isStudyPlanMockEnabled()) {
    if (getStudyPlanMockScenario() === 'error') throw mockServiceError()
    return mockWeeklyReport()
  }
  return get<WeeklyStudyReportSummary>(STUDY_PLAN_PATHS.weeklyReport)
}
