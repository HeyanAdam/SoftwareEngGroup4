/**
 * 导学推荐接口适配层
 * 契约 5.4 ~ 5.6；统一走 shared/api/request.ts，不新建 axios 实例。
 * Mock 判定集中在这里（Mock 只存在于 API 层，组件不写 Mock 分支）。
 */

import { ApiError, createIdempotencyKey, get, post } from '../../../shared/api/request'
import {
  getInsightMockScenario,
  isInsightMockEnabled,
  mockAcceptRecommendation,
  mockDismissRecommendation,
  mockRecommendationList,
} from '../mock/insight.mock'
import type {
  AcceptRecommendationResult,
  DismissRecommendationResult,
  RecommendationList,
  RecommendationQuery,
} from '../types'
import { buildRecommendationQuery } from '../utils'

/** 推荐接口路径（契约 5.4 ~ 5.6），前缀 /api/v1 由 request.ts 携带 */
export const INSIGHT_PATHS = {
  recommendations: '/recommendations',
  accept: (id: number) => `/recommendations/${id}/accept`,
  dismiss: (id: number) => `/recommendations/${id}/dismiss`,
} as const

/** Mock 错误场景：按契约 8.1 的服务端异常口径抛出，页面走错误卡而不是空白 */
function mockServiceError(): ApiError {
  return new ApiError({
    message: '推荐服务暂时不可用，请稍后重试',
    code: 90000,
    httpStatus: 500,
  })
}

/** GET /recommendations，失败时返回空列表的业务处理在 Store 层 */
export async function fetchRecommendations(query: RecommendationQuery): Promise<RecommendationList> {
  if (isInsightMockEnabled()) {
    if (getInsightMockScenario() === 'error') throw mockServiceError()
    return mockRecommendationList(query)
  }
  return get<RecommendationList>(INSIGHT_PATHS.recommendations, {
    params: buildRecommendationQuery(query),
  })
}

/**
 * POST /recommendations/{id}/accept，Idempotency-Key 必填（契约 5.5）。
 * 幂等键由调用方（Store）生成并保留，提交失败重试时复用同一个键。
 */
export async function acceptRecommendation(
  id: number,
  targetDate: string,
  idempotencyKey: string,
): Promise<AcceptRecommendationResult> {
  if (!idempotencyKey) {
    throw new ApiError({ message: '缺少幂等键', code: 10000, httpStatus: 400 })
  }

  if (isInsightMockEnabled()) {
    if (getInsightMockScenario() === 'error') throw mockServiceError()
    const result = await mockAcceptRecommendation(id, targetDate)
    if (!result) {
      throw new ApiError({ message: '该推荐不存在', code: 404, httpStatus: 404 })
    }
    return result
  }

  return post<AcceptRecommendationResult>(
    INSIGHT_PATHS.accept(id),
    { targetDate },
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
}

/** POST /recommendations/{id}/dismiss：重复忽略仍返回成功（契约 5.6） */
export async function dismissRecommendation(id: number): Promise<DismissRecommendationResult> {
  if (isInsightMockEnabled()) {
    if (getInsightMockScenario() === 'error') throw mockServiceError()
    return mockDismissRecommendation(id)
  }
  return post<DismissRecommendationResult>(INSIGHT_PATHS.dismiss(id), {})
}

export { createIdempotencyKey }
