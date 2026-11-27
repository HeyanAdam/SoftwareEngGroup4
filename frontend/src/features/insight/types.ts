/**
 * 导学推荐类型
 * 唯一口径：《契约_学习计划推荐通知前后端共同契约》4.3 ~ 4.5 节。
 * 只能依赖契约里出现过的字段：接口需要新字段时先改契约，不在前端临时加。
 */

import type { StudyTask } from '../studyplan/types'

/** 推荐场景（契约 3.2） */
export type RecommendationScene = 'TODAY' | 'CHAT' | 'TASK_COMPLETE'

/** 推荐上下文类型（契约 3.3） */
export type RecommendationContextType = 'NONE' | 'MESSAGE' | 'TASK' | 'CONVERSATION'

/** 推荐类型（契约 3.4） */
export type RecommendationType = 'STUDY_TASK' | 'KNOWLEDGE_POINT' | 'RESOURCE' | 'REVIEW'

/** 推荐操作（契约 3.5） */
export type RecommendationActionType = 'CREATE_TASK' | 'OPEN_CHAT' | 'OPEN_RESOURCE'

/** 推荐状态（契约 3.6） */
export type RecommendationStatus = 'ACTIVE' | 'ACCEPTED' | 'DISMISSED' | 'EXPIRED'

/** 单条推荐（契约 4.3） */
export interface Recommendation {
  id: number
  scene: RecommendationScene
  type: RecommendationType
  title: string
  reason: string
  actionType: RecommendationActionType
  actionLabel: string
  actionPath: string | null
  score: number
  estimatedMinutes: number | null
  subject: string | null
  knowledgePoint: string | null
  status: RecommendationStatus
  createdAt: string
  expiresAt: string | null
}

/** 推荐列表（契约 4.4） */
export interface RecommendationList {
  items: Recommendation[]
  generatedAt: string
}

/** 接受推荐结果（契约 4.5） */
export interface AcceptRecommendationResult {
  recommendation: Recommendation
  task: StudyTask
}

/** 忽略推荐结果（契约 5.6） */
export interface DismissRecommendationResult {
  recommendationId: number
  status: 'DISMISSED'
}

/** 查询参数（契约 5.4） */
export interface RecommendationQuery {
  scene: RecommendationScene
  contextType?: RecommendationContextType
  contextId?: number
  limit?: number
}
