import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 接受推荐的幂等键行为（契约 5.5）：同一个推荐失败重试复用同一个 Idempotency-Key，
 * 成功后作废；连点两次只发一次请求。
 * 这里把 request 层整体替换，才能看到真正传给接口的 header。
 */
const mocks = vi.hoisted(() => {
  let counter = 0
  return {
    post: vi.fn(),
    createIdempotencyKey: vi.fn(() => {
      counter += 1
      return `key-${counter}`
    }),
  }
})

vi.mock('../../../shared/api/request', () => {
  class ApiError extends Error {
    code: number
    httpStatus: number

    constructor(options: { message: string; code: number; httpStatus: number }) {
      super(options.message)
      this.name = 'ApiError'
      this.code = options.code
      this.httpStatus = options.httpStatus
    }
  }

  return {
    ApiError,
    createIdempotencyKey: mocks.createIdempotencyKey,
    get: vi.fn(),
    post: mocks.post,
    patch: vi.fn(),
    put: vi.fn(),
    del: vi.fn(),
    default: {},
  }
})

import { createPinia, setActivePinia } from 'pinia'
import { useInsightStore } from '../../../app/stores/insight'

/** 契约 5.5 成功响应里的最小结构 */
function acceptedResult(recommendationId: number) {
  return {
    recommendation: {
      id: recommendationId,
      scene: 'TODAY',
      type: 'KNOWLEDGE_POINT',
      title: '贝叶斯公式',
      reason: '测试理由',
      actionType: 'CREATE_TASK',
      actionLabel: '加入今日任务',
      actionPath: null,
      score: 0.9,
      estimatedMinutes: 15,
      subject: '数学',
      knowledgePoint: '贝叶斯公式',
      status: 'ACCEPTED',
      createdAt: '2026-11-26T20:05:00+08:00',
      expiresAt: null,
    },
    task: {
      id: 5901,
      title: '贝叶斯公式',
      subject: '数学',
      knowledgePoint: null,
      estimatedMinutes: 15,
      dueAt: '2026-11-26T23:59:59+08:00',
      status: 'TODO',
      overdue: false,
      priority: 90,
      version: 0,
    },
  }
}

function sentIdempotencyKey(callIndex: number): string | undefined {
  const config = mocks.post.mock.calls[callIndex]?.[2] as
    | { headers?: Record<string, string> }
    | undefined
  return config?.headers?.['Idempotency-Key']
}

describe('接受推荐幂等键（契约 5.5）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mocks.post.mockReset()
    mocks.createIdempotencyKey.mockClear()
  })

  it('失败重试复用同一个幂等键，成功后作废并生成新键', async () => {
    const store = useInsightStore()

    mocks.post.mockRejectedValueOnce(new Error('网络异常'))
    await expect(store.acceptRecommendation(901, '2026-11-26')).resolves.toBe(false)

    mocks.post.mockResolvedValueOnce(acceptedResult(901))
    await expect(store.acceptRecommendation(901, '2026-11-26')).resolves.toBe(true)
    expect(store.recommendationErrorMessage).toBe('')

    // 失败与重试是同一次业务提交，必须共用 key-1
    expect(sentIdempotencyKey(0)).toBe('key-1')
    expect(sentIdempotencyKey(1)).toBe('key-1')

    // 成功后再接受同一推荐，属于新一次提交
    mocks.post.mockResolvedValueOnce(acceptedResult(901))
    await store.acceptRecommendation(901, '2026-11-26')
    expect(sentIdempotencyKey(2)).toBe('key-2')
  })

  it('连续点击只发一次请求（防止重复提交）', async () => {
    const store = useInsightStore()
    mocks.post.mockReturnValueOnce(new Promise(() => {}) as never)

    const first = store.acceptRecommendation(902, '2026-11-26')
    await expect(store.acceptRecommendation(902, '2026-11-26')).resolves.toBe(false)

    expect(mocks.post).toHaveBeenCalledTimes(1)
    expect(store.acceptingRecommendationIds).toEqual([902])
    void first
  })

  it('未加载今日计划且没传 targetDate 时不发请求，直接给出提示', async () => {
    const store = useInsightStore()
    await expect(store.acceptRecommendation(903)).resolves.toBe(false)
    expect(mocks.post).not.toHaveBeenCalled()
    expect(store.recommendationErrorMessage).toBe('请先加载今日学习计划后再接受推荐')
  })
})
