/**
 * 导学推荐 / 学习计划 Store（前端 A 基础版）
 *
 * 基础 PR 范围：状态字段按契约指南 11 节建齐，推荐相关 action 可运行；
 * 学习计划的 loadTodayStudy / generateTodayStudy / updateTaskStatus / loadWeeklyReport
 * 依赖 studyplan.api.ts，在功能 PR `feat/frontend-study-plan-coach` 补齐，
 * 这里只保留状态位，不写假实现，避免调用方拿到“看起来成功其实没请求”的结果。
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
import type { Recommendation, RecommendationContextType, RecommendationScene } from '../../features/insight/types'
import { normalizeRecommendations, RECOMMENDATION_DEFAULT_LIMIT } from '../../features/insight/utils'
import type { StudyTask, TodayStudyPlan, WeeklyStudyReportSummary } from '../../features/studyplan/types'

/**
 * 任务排序（契约 1.6）：priority 降序 → dueAt 升序 → 无截止时间靠后 → id 升序。
 * 只用于接受推荐后把新任务插到正确位置，页面渲染在功能 PR 复用。
 */
function compareStudyTask(a: StudyTask, b: StudyTask): number {
  if (b.priority !== a.priority) return b.priority - a.priority
  if (a.dueAt !== b.dueAt) {
    if (a.dueAt === null) return 1
    if (b.dueAt === null) return -1
    return a.dueAt < b.dueAt ? -1 : 1
  }
  return a.id - b.id
}

function errorText(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error) return error.message
  return '操作失败，请稍后重试'
}

export const useInsightStore = defineStore('insight', () => {
  const todayPlan = ref<TodayStudyPlan | null>(null)
  const weeklyReport = ref<WeeklyStudyReportSummary | null>(null)
  const recommendations = ref<Recommendation[]>([])
  const loadingPlan = ref(false)
  const loadingRecommendations = ref(false)
  const loadingWeeklyReport = ref(false)
  const submittingTaskIds = ref<number[]>([])
  const acceptingRecommendationIds = ref<number[]>([])
  const errorMessage = ref('')
  const dataMode = ref<'mock' | 'api'>(isInsightMockEnabled() ? 'mock' : 'api')

  /** 幂等键按推荐 id 保留：失败重试复用同一个键，成功后随推荐状态一起作废 */
  const idempotencyKeys = new Map<number, string>()

  const hasRecommendations = computed(() => recommendations.value.length > 0)

  /** 查询推荐（契约 5.4）；失败只置错误信息，不影响其它区块 */
  async function loadRecommendations(
    scene: RecommendationScene,
    contextType: RecommendationContextType = 'NONE',
    contextId?: number,
    limit: number = RECOMMENDATION_DEFAULT_LIMIT,
  ): Promise<void> {
    loadingRecommendations.value = true
    errorMessage.value = ''
    dataMode.value = isInsightMockEnabled() ? 'mock' : 'api'
    try {
      const result = await fetchRecommendations({ scene, contextType, contextId, limit })
      recommendations.value = normalizeRecommendations(result.items)
    } catch (error) {
      recommendations.value = []
      errorMessage.value = errorText(error)
    } finally {
      loadingRecommendations.value = false
    }
  }

  /** 接受推荐（契约 5.5）：幂等键只生成一次，重复点击直接忽略 */
  async function acceptRecommendation(id: number, targetDate?: string): Promise<void> {
    if (acceptingRecommendationIds.value.includes(id)) return

    const date = targetDate ?? todayPlan.value?.planDate ?? ''
    if (!date) {
      // targetDate 是契约 5.5 的请求体字段，前端不自行推算“今天”，
      // 未加载今日计划时不伪造日期，直接给出可展示提示。
      errorMessage.value = '请先加载今日学习计划后再接受推荐'
      return
    }

    const key = idempotencyKeys.get(id) ?? createIdempotencyKey()
    idempotencyKeys.set(id, key)
    acceptingRecommendationIds.value = [...acceptingRecommendationIds.value, id]
    errorMessage.value = ''

    try {
      const result = await acceptRecommendationApi(id, date, key)
      idempotencyKeys.delete(id)
      recommendations.value = recommendations.value.map((item) =>
        item.id === result.recommendation.id ? result.recommendation : item,
      )
      if (todayPlan.value) {
        const tasks = todayPlan.value.tasks.filter((task) => task.id !== result.task.id)
        tasks.push(result.task)
        todayPlan.value = { ...todayPlan.value, tasks: tasks.sort(compareStudyTask) }
      }
    } catch (error) {
      errorMessage.value = errorText(error)
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
    errorMessage.value = ''
    try {
      await dismissRecommendationApi(id)
      recommendations.value = recommendations.value.filter((item) => item.id !== id)
      idempotencyKeys.delete(id)
    } catch (error) {
      errorMessage.value = errorText(error)
    } finally {
      acceptingRecommendationIds.value = acceptingRecommendationIds.value.filter(
        (value) => value !== id,
      )
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
    acceptingRecommendationIds.value = []
    errorMessage.value = ''
    idempotencyKeys.clear()
    dataMode.value = isInsightMockEnabled() ? 'mock' : 'api'
  }

  return {
    todayPlan,
    weeklyReport,
    recommendations,
    loadingPlan,
    loadingRecommendations,
    loadingWeeklyReport,
    submittingTaskIds,
    acceptingRecommendationIds,
    errorMessage,
    dataMode,
    hasRecommendations,
    loadRecommendations,
    acceptRecommendation,
    dismissRecommendation,
    reset,
  }
})
