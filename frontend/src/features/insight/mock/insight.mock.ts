/**
 * 导学推荐 Mock
 *
 * 开关只在 API 层读取（契约与《前端 A 指南》6 / 10 节）：
 *   localStorage.setItem('kaoyan.insight.mock', 'on')
 *   localStorage.setItem('kaoyan.insight.mock.scenario', 'normal' | 'empty' | 'error')
 * Mock 只在开发环境生效，生产构建里 import.meta.env.DEV 为 false，永远走真实接口。
 *
 * 数据形状严格按契约 4.3 / 4.4 / 4.5 / 5.6，不额外造字段。
 */

import type { StudyTask } from '../../studyplan/types'
import type {
  AcceptRecommendationResult,
  DismissRecommendationResult,
  Recommendation,
  RecommendationList,
  RecommendationQuery,
  RecommendationScene,
} from '../types'
import { RECOMMENDATION_DEFAULT_LIMIT } from '../utils'

export const INSIGHT_MOCK_FLAG_KEY = 'kaoyan.insight.mock'
export const INSIGHT_MOCK_SCENARIO_KEY = 'kaoyan.insight.mock.scenario'

export type InsightMockScenario = 'normal' | 'empty' | 'error'

/** 模拟网络延迟，让骨架屏和错误态能被看到 */
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

/** Mock 总开关：开发环境 + 显式打开，两个条件都满足才生效 */
export function isInsightMockEnabled(): boolean {
  return import.meta.env.DEV && readStorage(INSIGHT_MOCK_FLAG_KEY) === 'on'
}

export function getInsightMockScenario(): InsightMockScenario {
  const value = readStorage(INSIGHT_MOCK_SCENARIO_KEY)
  if (value === 'empty' || value === 'error') return value
  return 'normal'
}

/** 契约示例同款时间戳，统一 Asia/Shanghai 偏移 */
const MOCK_GENERATED_AT = '2026-11-26T20:05:00+08:00'

/** 3 条推荐（契约 10 节：至少 3 条；不生成 OPEN_RESOURCE，因为后端尚未实现该类型） */
const MOCK_RECOMMENDATIONS: Recommendation[] = [
  {
    id: 901,
    scene: 'TODAY',
    type: 'KNOWLEDGE_POINT',
    title: '贝叶斯公式：从结论反推原因',
    reason: '本周数学任务里全概率公式的错题集中在“完备事件组”判断，继续学贝叶斯公式可以形成知识串联。',
    actionType: 'CREATE_TASK',
    actionLabel: '加入今日任务',
    actionPath: null,
    score: 0.92,
    estimatedMinutes: 15,
    subject: '数学',
    knowledgePoint: '贝叶斯公式',
    status: 'ACTIVE',
    createdAt: MOCK_GENERATED_AT,
    expiresAt: '2026-11-27T20:05:00+08:00',
  },
  {
    id: 902,
    scene: 'TODAY',
    type: 'REVIEW',
    title: '矩阵相似对角化：回看 2 道逾期题',
    reason: '这个知识点有 2 项任务已逾期，先补完再进入特征值应用，避免后面推导断档。',
    actionType: 'CREATE_TASK',
    actionLabel: '加入今日任务',
    actionPath: null,
    score: 0.86,
    estimatedMinutes: 25,
    subject: '数学',
    knowledgePoint: '矩阵相似对角化',
    status: 'ACTIVE',
    createdAt: MOCK_GENERATED_AT,
    expiresAt: '2026-11-27T20:05:00+08:00',
  },
  {
    id: 903,
    scene: 'TODAY',
    type: 'STUDY_TASK',
    title: '英语阅读：限时 20 分钟一篇',
    reason: '近三天英语任务都按计划完成，阅读速度稳定，可以再加一篇保持手感。',
    actionType: 'OPEN_CHAT',
    actionLabel: '去问问导学助手',
    actionPath: '/chat',
    score: 0.74,
    estimatedMinutes: 20,
    subject: '英语',
    knowledgePoint: '阅读理解',
    status: 'ACTIVE',
    createdAt: MOCK_GENERATED_AT,
    expiresAt: '2026-11-27T20:05:00+08:00',
  },
]

/** 场景随查询变化，保证返回的 scene 与请求一致 */
function withScene(scene: RecommendationScene): Recommendation[] {
  return MOCK_RECOMMENDATIONS.map((item) => ({ ...item, scene }))
}

/** GET /recommendations */
export async function mockRecommendationList(
  query: RecommendationQuery,
): Promise<RecommendationList> {
  await delay(MOCK_DELAY)
  if (getInsightMockScenario() === 'empty' || getInsightMockScenario() === 'error') {
    return { items: [], generatedAt: MOCK_GENERATED_AT }
  }
  const limit = Math.min(query.limit ?? RECOMMENDATION_DEFAULT_LIMIT, RECOMMENDATION_DEFAULT_LIMIT)
  return {
    items: withScene(query.scene).slice(0, Math.max(0, limit)),
    generatedAt: MOCK_GENERATED_AT,
  }
}

/** POST /recommendations/{id}/accept：未命中 id 返回 null，由 API 层抛 404 */
export async function mockAcceptRecommendation(
  id: number,
  targetDate: string,
): Promise<AcceptRecommendationResult | null> {
  await delay(MOCK_DELAY)
  const item = MOCK_RECOMMENDATIONS.find((candidate) => candidate.id === id)
  if (!item) return null

  const accepted: Recommendation = { ...item, status: 'ACCEPTED' }
  const task: StudyTask = {
    id: 5000 + id,
    title: item.title,
    subject: item.subject ?? '综合',
    knowledgePoint: item.knowledgePoint,
    estimatedMinutes: item.estimatedMinutes ?? 15,
    dueAt: `${targetDate}T23:59:59+08:00`,
    status: 'TODO',
    overdue: false,
    priority: 90,
    version: 0,
  }
  return { recommendation: accepted, task }
}

/** POST /recommendations/{id}/dismiss：契约 5.6，重复忽略仍返回成功 */
export async function mockDismissRecommendation(id: number): Promise<DismissRecommendationResult> {
  await delay(MOCK_DELAY)
  return { recommendationId: id, status: 'DISMISSED' }
}
