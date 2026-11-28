/**
 * 聊天页导学建议：上下文 + 候选池
 *
 * 为什么单独抽一层：
 * ChatPage 只负责决定"什么时候触发"（进入会话 / 一条助手回答完成），
 * 推荐栏负责"长什么样"。但窄屏时推荐栏在抽屉里、可能没挂载，
 * 如果触发逻辑挂在组件 ref 上就会漏触发。把上下文与请求放在模块里，
 * 桌面栏和移动抽屉共用同一份状态，任何时候触发都落到同一个池子。
 *
 * 触发口径（契约 5.4 / 前端 B 指南）：
 * - 进入/切换会话：CONVERSATION + conversationId；
 * - 助手消息 status === 'completed' 且有 serverId：MESSAGE + serverId；
 * - 流式 delta 阶段绝不请求；历史消息加载后不逐条请求。
 *
 * 池子口径（本次新增）：
 * - 一次最多拉 10 条（后端 RecommendationService 的 realLimit 上限就是 10），
 *   但一屏只显示 3 条（契约 1.6）；
 * - "换一批"在池内往前轮换一屏，走到末尾回卷，不出现空栏；
 * - "暂不需要"成功后把这张从池里摘掉，后面备选的会自动顶上来；
 *   池子不足一屏时才重新拉一次补货。
 * 这是页面内的候选缓存，不是第二份推荐 Store：接受/忽略仍走前端 A 的接口与 Store。
 */

import { computed, ref } from 'vue'
import { useInsightStore } from '../../app/stores/insight'
import {
  dismissRecommendation as dismissRecommendationApi,
  fetchRecommendations,
} from '../insight/api/insight.api'
import { getInsightMockScenario, isInsightMockEnabled } from '../insight/mock/insight.mock'
import type { Recommendation } from '../insight/types'
import { mockChatCoachPool } from './chatCoach.mock'

export interface CoachQuery {
  contextType: 'CONVERSATION' | 'MESSAGE'
  contextId: number
}

/** 一屏显示几条（契约 1.6） */
export const COACH_PAGE_SIZE = 3

/** 一次预取的候选条数；后端把 limit 上限钳在 10，这里跟着取满 */
export const COACH_POOL_LIMIT = 10

/** 当前推荐栏对应的上下文，切换会话/消息时重置池子 */
export const coachQuery = ref<CoachQuery | null>(null)

/** 候选池（保序，不在组件里二次排序，避免"换一批"顺序乱跳） */
const coachPool = ref<Recommendation[]>([])

/** 当前一屏的起点 */
const coachCursor = ref(0)

export const coachLoading = ref(false)
export const coachError = ref('')
export const coachAcceptingIds = ref<number[]>([])
/** 本次上下文里已忽略的 id：补货时排除，避免"说不要了又回来" */
const coachDismissedIds = ref<number[]>([])

/** 当前一屏（3 条，池子不足 3 条时有几条显示几条） */
export const coachVisibleItems = computed<Recommendation[]>(() =>
  computeWindow(coachPool.value, coachCursor.value, COACH_PAGE_SIZE),
)

/** 池子里还有多少条没上屏，用于"备选还有 N 条"提示 */
export const coachBackupCount = computed(() => Math.max(0, coachPool.value.length - coachVisibleItems.value.length))

export const coachPoolCount = computed(() => coachPool.value.length)

// ===== 纯函数（可单测，不依赖网络） =====

/** 从池里取一屏；越界自动回卷，池空返回空数组 */
export function computeWindow<T>(items: T[], cursor: number, size: number): T[] {
  if (!items.length || size <= 0) return []
  const total = items.length
  const start = ((cursor % total) + total) % total
  const count = Math.min(size, total)
  const window: T[] = []
  for (let i = 0; i < count; i += 1) window.push(items[(start + i) % total])
  return window
}

/** 换一批的游标推进：不足一屏时不动，走到末尾回卷 */
export function advanceCursor(cursor: number, total: number, step: number): number {
  if (total <= 0 || step <= 0) return 0
  if (total <= step) return 0
  return (cursor + step) % total
}

/** 补货：只并入没见过的、且没被忽略过的 */
export function mergeFreshPool<T extends { id: number }>(
  pool: T[],
  incoming: T[],
  excludedIds: number[] = [],
): T[] {
  const known = new Set(pool.map((item) => item.id))
  const excluded = new Set(excludedIds)
  const fresh = incoming.filter((item) => !known.has(item.id) && !excluded.has(item.id))
  return fresh.length ? [...pool, ...fresh] : pool
}

/** 同 id 只保留第一条（后端分页/重试可能返回重复） */
function dedupeById(items: Recommendation[]): Recommendation[] {
  const seen = new Set<number>()
  const result: Recommendation[] = []
  for (const item of items) {
    if (seen.has(item.id)) continue
    seen.add(item.id)
    result.push(item)
  }
  return result
}

function errorText(error: unknown): string {
  if (error instanceof Error && error.message) return error.message
  return '导学建议加载失败，请稍后重试'
}

/**
 * 取候选：
 * - 开发环境打开 insight Mock 且场景为 normal 时，用 B 的 9 条演示池
 *   （原因写在 chatCoach.mock.ts：后端 CHAT 场景规则仍是空实现）；
 * - 其余情况（含 Mock 的 empty / loading / error 场景）交给前端 A 的 API 层，
 *   保持与学习计划页一致的 Mock 行为。
 */
async function fetchCandidates(query: CoachQuery): Promise<Recommendation[]> {
  if (isInsightMockEnabled() && getInsightMockScenario() === 'normal') {
    return mockChatCoachPool()
  }
  const result = await fetchRecommendations({
    scene: 'CHAT',
    contextType: query.contextType,
    contextId: query.contextId,
    limit: COACH_POOL_LIMIT,
  })
  return result.items
}

/** 拉一池新候选（进入会话 / 切消息 / 重试时用） */
async function loadCoach(query: CoachQuery): Promise<void> {
  coachQuery.value = query
  coachLoading.value = true
  coachError.value = ''
  coachDismissedIds.value = []
  try {
    coachPool.value = dedupeById(await fetchCandidates(query))
    coachCursor.value = 0
  } catch (error) {
    coachPool.value = []
    coachCursor.value = 0
    coachError.value = errorText(error)
  } finally {
    coachLoading.value = false
  }
}

export async function loadCoachForConversation(conversationId: number | null | undefined): Promise<void> {
  if (!conversationId) {
    coachQuery.value = null
    coachPool.value = []
    coachCursor.value = 0
    coachError.value = ''
    return
  }
  await loadCoach({ contextType: 'CONVERSATION', contextId: conversationId })
}

export async function loadCoachForMessage(messageId: number | null | undefined): Promise<void> {
  if (!messageId || messageId <= 0) return
  await loadCoach({ contextType: 'MESSAGE', contextId: messageId })
}

/** 重试 / 重新加载当前上下文 */
export async function reloadCoach(): Promise<void> {
  if (!coachQuery.value) return
  await loadCoach(coachQuery.value)
}

/** 补货：不足一屏时把后端/演示池里没上过屏的补进来；失败静默，保留现有卡片 */
async function refillCoachPool(): Promise<void> {
  if (!coachQuery.value) return
  try {
    const items = await fetchCandidates(coachQuery.value)
    coachPool.value = mergeFreshPool(coachPool.value, dedupeById(items), coachDismissedIds.value)
  } catch {
    // 补货失败不弹错：现有卡片继续可用，下次"换一批"会再试
  }
}

/**
 * 换一批：池里还有备选就轮换一屏；池子不足一屏时先尝试补货，
 * 补不到新内容就把当前一屏原样保留（绝不把整栏清空）。
 */
export async function refreshCoach(conversationId?: number | null): Promise<void> {
  if (!coachQuery.value) {
    if (!conversationId) return
    await loadCoach({ contextType: 'CONVERSATION', contextId: conversationId })
    return
  }
  if (coachLoading.value) return

  if (coachPool.value.length > COACH_PAGE_SIZE) {
    coachCursor.value = advanceCursor(coachCursor.value, coachPool.value.length, COACH_PAGE_SIZE)
    coachError.value = ''
    return
  }

  coachLoading.value = true
  coachError.value = ''
  try {
    const items = await fetchCandidates(coachQuery.value)
    coachPool.value = mergeFreshPool(
      dedupeById(coachPool.value),
      dedupeById(items),
      coachDismissedIds.value,
    )
    coachCursor.value = 0
  } catch (error) {
    // 已经有卡片时不把用户眼前的建议换成错误页
    if (!coachPool.value.length) coachError.value = errorText(error)
  } finally {
    coachLoading.value = false
  }
}

/**
 * 接受推荐：只走前端 A 的 Insight Store，不另建第二份推荐状态。
 * 今日计划未加载时先拉一次（A 的功能 PR 已合并，5.1 接口可用），
 * 拿不到 planDate 才退化为 Asia/Shanghai 当天；返回值转为可展示错误文案。
 */
export async function acceptCoachRecommendation(item: Recommendation): Promise<string> {
  const insight = useInsightStore()
  if (!insight.todayPlan) await insight.loadTodayStudy()
  const ok = await insight.acceptRecommendation(item.id, resolveCoachTargetDate())
  if (!ok) return insight.recommendationErrorMessage || '接受推荐失败，请稍后重试'
  coachPool.value = coachPool.value.map((candidate) =>
    candidate.id === item.id ? { ...candidate, status: 'ACCEPTED' } : candidate,
  )
  return ''
}

/**
 * 暂不需要：先调契约 5.6 的忽略接口，成功后从池里摘掉；
 * 后面备选的会自动顶上来，池子不足一屏时再补货。
 */
export async function dismissCoachRecommendation(item: Recommendation): Promise<string> {
  if (coachAcceptingIds.value.includes(item.id)) return ''
  coachAcceptingIds.value = [...coachAcceptingIds.value, item.id]
  try {
    await dismissRecommendationApi(item.id)
    coachDismissedIds.value = [...coachDismissedIds.value, item.id]

    const index = coachPool.value.findIndex((candidate) => candidate.id === item.id)
    coachPool.value = coachPool.value.filter((candidate) => candidate.id !== item.id)
    // 被摘掉的在游标之前，游标左移一位，保证下一张正好顶到原来的位置
    if (index > -1 && index < coachCursor.value) coachCursor.value -= 1
    if (coachCursor.value >= coachPool.value.length) coachCursor.value = 0
    if (coachCursor.value < 0) coachCursor.value = 0

    if (coachPool.value.length < COACH_PAGE_SIZE) await refillCoachPool()
    return ''
  } catch (error) {
    return errorText(error)
  } finally {
    coachAcceptingIds.value = coachAcceptingIds.value.filter((id) => id !== item.id)
  }
}

/**
 * 接受推荐时的 targetDate：优先用今日计划的 planDate（和列表同一份"今日"），
 * 计划还没加载时退化为 Asia/Shanghai 的当天。
 * 只用于填契约 5.5 的请求体，不用它判断逾期/今天（逾期一律后端算，契约 6.6）。
 */
export function resolveCoachTargetDate(): string {
  const insight = useInsightStore()
  const planDate = insight.todayPlan?.planDate
  if (planDate) return planDate
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}
