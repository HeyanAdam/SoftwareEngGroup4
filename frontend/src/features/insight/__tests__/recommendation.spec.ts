import { describe, expect, it } from 'vitest'
import type { Recommendation } from '../types'
import {
  buildRecommendationQuery,
  dedupeRecommendations,
  isSafeActionPath,
  normalizeRecommendations,
  sortRecommendations,
} from '../utils'

/** 契约 4.3 形状的测试数据，只关心排序/去重用到的字段 */
function makeRecommendation(overrides: Partial<Recommendation> & Pick<Recommendation, 'id'>): Recommendation {
  const { id, ...rest } = overrides
  return {
    id,
    scene: 'TODAY',
    type: 'KNOWLEDGE_POINT',
    title: `推荐 ${id}`,
    reason: '测试理由',
    actionType: 'CREATE_TASK',
    actionLabel: '加入今日任务',
    actionPath: null,
    score: 0.5,
    estimatedMinutes: 15,
    subject: '数学',
    knowledgePoint: null,
    status: 'ACTIVE',
    createdAt: '2026-11-26T20:05:00+08:00',
    expiresAt: null,
    ...rest,
  }
}

describe('sortRecommendations', () => {
  it('score 降序，相同 score 按 id 降序（契约 1.6）', () => {
    const items = [
      makeRecommendation({ id: 1, score: 0.6 }),
      makeRecommendation({ id: 3, score: 0.9 }),
      makeRecommendation({ id: 2, score: 0.6 }),
    ]
    expect(sortRecommendations(items).map((item) => item.id)).toEqual([3, 2, 1])
  })

  it('不修改入参数组', () => {
    const items = [makeRecommendation({ id: 1, score: 0.2 }), makeRecommendation({ id: 2, score: 0.8 })]
    sortRecommendations(items)
    expect(items.map((item) => item.id)).toEqual([1, 2])
  })
})

describe('dedupeRecommendations', () => {
  it('相同 id 只保留先出现的一条（REST 与 WS 合并）', () => {
    const items = [
      makeRecommendation({ id: 901, score: 0.92 }),
      makeRecommendation({ id: 902, score: 0.74 }),
      makeRecommendation({ id: 901, score: 0.92, status: 'ACCEPTED' }),
    ]
    const result = dedupeRecommendations(items)
    expect(result.map((item) => item.id)).toEqual([901, 902])
    expect(result[0].status).toBe('ACTIVE')
  })
})

describe('normalizeRecommendations', () => {
  it('去重、排序后最多 3 条（契约 4.4 / 1.6）', () => {
    const items = [
      makeRecommendation({ id: 1, score: 0.2 }),
      makeRecommendation({ id: 2, score: 0.9 }),
      makeRecommendation({ id: 3, score: 0.8 }),
      makeRecommendation({ id: 4, score: 0.7 }),
      makeRecommendation({ id: 2, score: 0.9 }),
    ]
    expect(normalizeRecommendations(items).map((item) => item.id)).toEqual([2, 3, 4])
  })

  it('空列表保持为空，不当成错误', () => {
    expect(normalizeRecommendations([])).toEqual([])
  })
})

describe('isSafeActionPath', () => {
  it('只接受以单个 / 开头的站内路径', () => {
    expect(isSafeActionPath('/study-plans')).toBe(true)
    expect(isSafeActionPath('/chat?from=rec')).toBe(true)
    expect(isSafeActionPath('https://evil.example.com')).toBe(false)
    expect(isSafeActionPath('//evil.example.com')).toBe(false)
    expect(isSafeActionPath('chat')).toBe(false)
    expect(isSafeActionPath('')).toBe(false)
    expect(isSafeActionPath(null)).toBe(false)
  })
})

describe('buildRecommendationQuery', () => {
  it('NONE 场景不带 contextType / contextId，默认 limit 为 3（契约 5.4）', () => {
    expect(buildRecommendationQuery({ scene: 'TODAY' })).toEqual({ scene: 'TODAY', limit: 3 })
    expect(buildRecommendationQuery({ scene: 'TODAY', contextType: 'NONE' })).toEqual({
      scene: 'TODAY',
      limit: 3,
    })
  })

  it('MESSAGE 场景带助手消息 id', () => {
    expect(
      buildRecommendationQuery({ scene: 'CHAT', contextType: 'MESSAGE', contextId: 8803, limit: 5 }),
    ).toEqual({ scene: 'CHAT', contextType: 'MESSAGE', contextId: 8803, limit: 5 })
  })
})
