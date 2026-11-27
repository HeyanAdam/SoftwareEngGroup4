/**
 * 导学推荐纯函数工具：排序、去重、数量上限、站内路径校验。
 * 契约依据：1.6（排序与数量限制）、4.3（actionPath 必须以 / 开头）、6.3（跳转只允许站内路径）。
 * 只做与契约一致的裁剪，不做字段兜底。
 */

import type {
  Recommendation,
  RecommendationContextType,
  RecommendationQuery,
  RecommendationScene,
} from './types'

/** 默认/最多返回 3 条（契约 1.6：默认 3，limit 最大 10） */
export const RECOMMENDATION_DEFAULT_LIMIT = 3

/** 推荐栏展示上限，前端不因为接口多返回就展示更多 */
export const RECOMMENDATION_MAX_VISIBLE = 3

/** 来源标签文案：只由 scene 派生，不读内部字段 */
export const RECOMMENDATION_SCENE_LABELS: Record<RecommendationScene, string> = {
  TODAY: '今日推荐',
  CHAT: '答疑延伸',
  TASK_COMPLETE: '任务完成推荐',
}

/** 契约 1.6：score 降序，相同按 id 降序 */
export function sortRecommendations(items: readonly Recommendation[]): Recommendation[] {
  return [...items].sort((a, b) => {
    if (b.score !== a.score) return b.score - a.score
    return b.id - a.id
  })
}

/** 按 id 去重，保留先出现的一条（REST 与 WebSocket 合并时使用） */
export function dedupeRecommendations(items: readonly Recommendation[]): Recommendation[] {
  const seen = new Set<number>()
  const result: Recommendation[] = []
  for (const item of items) {
    if (seen.has(item.id)) continue
    seen.add(item.id)
    result.push(item)
  }
  return result
}

/** 推荐栏标准处理顺序：去重 → 排序 → 截断到 3 条 */
export function normalizeRecommendations(
  items: readonly Recommendation[],
  limit: number = RECOMMENDATION_MAX_VISIBLE,
): Recommendation[] {
  return sortRecommendations(dedupeRecommendations(items)).slice(0, Math.max(0, limit))
}

/** 站内路径校验：必须以 / 开头，且不能是 //host 形式的站外跳转 */
export function isSafeActionPath(path: string | null | undefined): path is string {
  if (typeof path !== 'string' || path.length === 0) return false
  if (!path.startsWith('/')) return false
  return !path.startsWith('//')
}

/** 查询参数组装：contextType 非 NONE 时必须带 contextId（契约 5.4） */
export function buildRecommendationQuery(query: RecommendationQuery): Record<string, string | number> {
  const params: Record<string, string | number> = { scene: query.scene }
  const contextType: RecommendationContextType = query.contextType ?? 'NONE'
  if (contextType !== 'NONE') {
    params.contextType = contextType
    if (query.contextId !== undefined) params.contextId = query.contextId
  }
  params.limit = query.limit ?? RECOMMENDATION_DEFAULT_LIMIT
  return params
}

/**
 * 幂等键复用（契约 5.5）：同一推荐 id 的第二次尝试必须用第一次的键，
 * 只有请求成功后才从缓存里删，避免失败重试创造第二个任务。
 */
export function resolveIdempotencyKey(
  keys: Map<number, string>,
  id: number,
  create: () => string,
): string {
  const cached = keys.get(id)
  if (cached) return cached
  const created = create()
  keys.set(id, created)
  return created
}
