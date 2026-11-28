/**
 * 导学推荐 + 学习计划 Store（前端 A）
 *
 * 契约口径：REST 5.1 / 5.2 / 5.3 / 5.4 / 5.5 / 5.6 / 5.11，状态处理 9.1 / 9.2。
 * 分层：接口在 features 各模块的 api 目录，Mock 只在 API 层；本文件不含任何 Mock 分支。
 * 错误分区：计划/任务错误走 errorMessage，推荐错误走 recommendationErrorMessage，
 * 周报错误走 weeklyReportErrorMessage —— 推荐或周报失败不能污染任务区块（契约 9.1 / 5.11）。
 */

import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ApiError } from '../../shared/api/request'
import {
  acceptRecommendation as acceptRecommendationApi,
  createIdempotencyKey,
  dismissRecommendation as dismissRecommendationApi,
  fetchRecommendations,
} from '../../features/insight/api/insight.api'
import { isInsightMockEnabled } from '../../features/insight/mock/insight.mock'
import type {
  Recommendation,
  RecommendationContextType,
  RecommendationScene,
} from '../../features/insight/types'
import { normalizeRecommendations, RECOMMENDATION_DEFAULT_LIMIT } from '../../features/insight/utils'
import {
  createTask as createTaskApi,
  deleteTask as deleteTaskApi,
  fetchTodayPlan,
  fetchWeeklyReport,
  generateTodayPlan as generateTodayPlanApi,
  updateTask as updateTaskApi,
  updateTaskStatus as updateTaskStatusApi,
} from '../../features/studyplan/api/studyplan.api'
import type {
  StudyTaskUpdateStatus,
  StudyTaskWritePayload,
  TodayStudyPlan,
  WeeklyStudyReportSummary,
} from '../../features/studyplan/types'
import {
  applyTaskStatus,
  applyTaskUpdate,
  insertTask,
  restoreTask,
} from '../../features/studyplan/utils'

function errorText(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error) return error.message
  return '操作失败，请稍后重试'
}

/** 契约 5.2：没有有效备考目标时后端返回 400 + 10000，页面要给出可执行提示 */
const NO_EXAM_GOAL_CODE = 10000

export const useInsightStore = defineStore('insight', () => {
  const todayPlan = ref<TodayStudyPlan | null>(null)
  const weeklyReport = ref<WeeklyStudyReportSummary | null>(null)
  const recommendations = ref<Recommendation[]>([])
  const loadingPlan = ref(false)
  const loadingRecommendations = ref(false)
  const loadingWeeklyReport = ref(false)
  const submittingTaskIds = ref<number[]>([])
  const savingTask = ref(false)
  const acceptingRecommendationIds = ref<number[]>([])
  const errorMessage = ref('')
  const recommendationErrorMessage = ref('')
  const weeklyReportErrorMessage = ref('')
  /** 最近一次计划级失败的接口码：页面据此决定是否走既有 403 处理（契约 9.1） */
  const lastErrorCode = ref(0)
  const dataMode = ref<'mock' | 'api'>(isInsightMockEnabled() ? 'mock' : 'api')

  /** 幂等键按推荐 id 保留：失败重试复用同一个键，成功后作废 */
  const idempotencyKeys = new Map<number, string>()

  const hasRecommendations = computed(() => recommendations.value.length > 0)

  function refreshDataMode(): void {
    dataMode.value = isInsightMockEnabled() ? 'mock' : 'api'
  }

  /** 获取今日计划（契约 5.1）；空计划是正常结果，不当成失败 */
  async function loadTodayStudy(): Promise<void> {
    loadingPlan.value = true
    errorMessage.value = ''
    lastErrorCode.value = 0
    refreshDataMode()
    try {
      todayPlan.value = await fetchTodayPlan()
    } catch (error) {
      todayPlan.value = null
      errorMessage.value = errorText(error)
      lastErrorCode.value = error instanceof ApiError ? error.code : 0
    } finally {
      loadingPlan.value = false
    }
  }

  /** 生成今日计划（契约 5.2）；已有计划时后端直接返回，不重复创建 */
  async function generateTodayStudy(): Promise<void> {
    if (loadingPlan.value) return
    loadingPlan.value = true
    errorMessage.value = ''
    lastErrorCode.value = 0
    try {
      todayPlan.value = await generateTodayPlanApi()
    } catch (error) {
      lastErrorCode.value = error instanceof ApiError ? error.code : 0
      if (error instanceof ApiError && error.code === NO_EXAM_GOAL_CODE) {
        errorMessage.value = '请先填写备考目标后再生成学习计划'
      } else {
        errorMessage.value = errorText(error)
      }
    } finally {
      loadingPlan.value = false
    }
  }

  /**
   * 修改任务状态（契约 5.3 / 9.2）：
   * 防重复 → 乐观更新 → 200 用响应任务覆盖 → 409 提示并重新拉今日计划 → 其它失败回滚。
   */
  async function updateTaskStatus(taskId: number, status: StudyTaskUpdateStatus): Promise<void> {
    const plan = todayPlan.value
    const task = plan?.tasks.find((item) => item.id === taskId)
    if (!plan || !task) return
    if (submittingTaskIds.value.includes(taskId)) return
    if (task.status === status) return

    const previous = { ...task }
    submittingTaskIds.value = [...submittingTaskIds.value, taskId]
    errorMessage.value = ''

    const optimistic = applyTaskStatus(plan, taskId, status)
    todayPlan.value = optimistic

    try {
      const updated = await updateTaskStatusApi(taskId, status, task.version)
      todayPlan.value = applyTaskUpdate(optimistic, updated)
    } catch (error) {
      const conflict = error instanceof ApiError && (error.httpStatus === 409 || error.code === 409)
      if (conflict) {
        // 版本冲突：不保留本地错误版本，直接以服务端为准
        await loadTodayStudy()
        errorMessage.value = '任务已被修改，请刷新后重试'
      } else {
        todayPlan.value = restoreTask(optimistic, previous)
        errorMessage.value = errorText(error)
      }
    } finally {
      submittingTaskIds.value = submittingTaskIds.value.filter((id) => id !== taskId)
    }
  }

  async function createTask(payload: StudyTaskWritePayload): Promise<boolean> {
    if (savingTask.value) return false
    savingTask.value = true
    errorMessage.value = ''
    try {
      const created = await createTaskApi(payload)
      if (todayPlan.value) {
        todayPlan.value = insertTask(todayPlan.value, created)
      } else {
        await loadTodayStudy()
      }
      return true
    } catch (error) {
      errorMessage.value = errorText(error)
      return false
    } finally {
      savingTask.value = false
    }
  }

  async function editTask(taskId: number, payload: StudyTaskWritePayload): Promise<boolean> {
    const plan = todayPlan.value
    const task = plan?.tasks.find((item) => item.id === taskId)
    if (!plan || !task || savingTask.value) return false
    savingTask.value = true
    errorMessage.value = ''
    try {
      const updated = await updateTaskApi(taskId, {
        ...payload,
        expectedVersion: task.version,
      })
      todayPlan.value = applyTaskUpdate(plan, updated)
      return true
    } catch (error) {
      const conflict = error instanceof ApiError && (error.httpStatus === 409 || error.code === 409)
      if (conflict) {
        await loadTodayStudy()
        errorMessage.value = '任务已被修改，请刷新后重试'
      } else {
        errorMessage.value = errorText(error)
      }
      return false
    } finally {
      savingTask.value = false
    }
  }

  async function removeTask(taskId: number): Promise<boolean> {
    const plan = todayPlan.value
    const task = plan?.tasks.find((item) => item.id === taskId)
    if (!task || savingTask.value) return false
    savingTask.value = true
    errorMessage.value = ''
    try {
      await deleteTaskApi(taskId, task.version)
      await loadTodayStudy()
      return true
    } catch (error) {
      const conflict = error instanceof ApiError && (error.httpStatus === 409 || error.code === 409)
      if (conflict) await loadTodayStudy()
      errorMessage.value = errorText(error)
      return false
    } finally {
      savingTask.value = false
    }
  }

  /** 查询推荐（契约 5.4）；失败只置推荐区错误，不影响任务列表 */
  async function loadRecommendations(
    scene: RecommendationScene,
    contextType: RecommendationContextType = 'NONE',
    contextId?: number,
    limit: number = RECOMMENDATION_DEFAULT_LIMIT,
  ): Promise<void> {
    loadingRecommendations.value = true
    recommendationErrorMessage.value = ''
    refreshDataMode()
    try {
      const result = await fetchRecommendations({ scene, contextType, contextId, limit })
      recommendations.value = normalizeRecommendations(result.items)
    } catch (error) {
      recommendations.value = []
      recommendationErrorMessage.value = errorText(error)
    } finally {
      loadingRecommendations.value = false
    }
  }

  /** 接受推荐（契约 5.5）：幂等键只生成一次，重复点击直接忽略；返回是否成功，供页面提示 */
  async function acceptRecommendation(id: number, targetDate?: string): Promise<boolean> {
    if (acceptingRecommendationIds.value.includes(id)) return false

    const date = targetDate ?? todayPlan.value?.planDate ?? ''
    if (!date) {
      // targetDate 是契约 5.5 的请求体字段；未加载今日计划时不伪造日期
      recommendationErrorMessage.value = '请先加载今日学习计划后再接受推荐'
      return false
    }

    const key = idempotencyKeys.get(id) ?? createIdempotencyKey()
    idempotencyKeys.set(id, key)
    acceptingRecommendationIds.value = [...acceptingRecommendationIds.value, id]
    recommendationErrorMessage.value = ''

    try {
      const result = await acceptRecommendationApi(id, date, key)
      idempotencyKeys.delete(id)
      recommendations.value = recommendations.value.map((item) =>
        item.id === result.recommendation.id ? result.recommendation : item,
      )
      if (todayPlan.value) {
        todayPlan.value = insertTask(todayPlan.value, result.task)
      }
      return true
    } catch (error) {
      recommendationErrorMessage.value = errorText(error)
      // 失败保留同一个幂等键，重试不会重复建任务
      return false
    } finally {
      acceptingRecommendationIds.value = acceptingRecommendationIds.value.filter(
        (value) => value !== id,
      )
    }
  }

  /** 忽略推荐（契约 5.6）：成功后从当前列表移除 */
  async function dismissRecommendation(id: number): Promise<void> {
    if (acceptingRecommendationIds.value.includes(id)) return
    acceptingRecommendationIds.value = [...acceptingRecommendationIds.value, id]
    recommendationErrorMessage.value = ''
    try {
      await dismissRecommendationApi(id)
      recommendations.value = recommendations.value.filter((item) => item.id !== id)
      idempotencyKeys.delete(id)
    } catch (error) {
      recommendationErrorMessage.value = errorText(error)
    } finally {
      acceptingRecommendationIds.value = acceptingRecommendationIds.value.filter(
        (value) => value !== id,
      )
    }
  }

  /** 本周诊断（契约 5.11）：reportId = null 是未生成，不是错误 */
  async function loadWeeklyReport(): Promise<void> {
    loadingWeeklyReport.value = true
    weeklyReportErrorMessage.value = ''
    try {
      weeklyReport.value = await fetchWeeklyReport()
    } catch (error) {
      weeklyReport.value = null
      weeklyReportErrorMessage.value = errorText(error)
    } finally {
      loadingWeeklyReport.value = false
    }
  }

  function reset(): void {
    todayPlan.value = null
    weeklyReport.value = null
    recommendations.value = []
    loadingPlan.value = false
    loadingRecommendations.value = false
    loadingWeeklyReport.value = false
    submittingTaskIds.value = []
    savingTask.value = false
    acceptingRecommendationIds.value = []
    errorMessage.value = ''
    recommendationErrorMessage.value = ''
    weeklyReportErrorMessage.value = ''
    lastErrorCode.value = 0
    idempotencyKeys.clear()
    refreshDataMode()
  }

  return {
    todayPlan,
    weeklyReport,
    recommendations,
    loadingPlan,
    loadingRecommendations,
    loadingWeeklyReport,
    submittingTaskIds,
    savingTask,
    acceptingRecommendationIds,
    errorMessage,
    recommendationErrorMessage,
    weeklyReportErrorMessage,
    lastErrorCode,
    dataMode,
    hasRecommendations,
    loadTodayStudy,
    generateTodayStudy,
    updateTaskStatus,
    createTask,
    editTask,
    removeTask,
    loadRecommendations,
    acceptRecommendation,
    dismissRecommendation,
    loadWeeklyReport,
    reset,
  }
})
