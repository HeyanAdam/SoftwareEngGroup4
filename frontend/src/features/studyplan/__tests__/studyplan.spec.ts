// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useInsightStore } from '../../../app/stores/insight'
import {
  MOCK_WEEKLY_REPORT_EMPTY,
  getStudyPlanMockScenario,
  mockGenerateTodayPlan,
  mockTodayPlan,
  mockUpdateTaskStatus,
  resetStudyPlanMock,
} from '../mock/studyplan.mock'
import type { StudyTask, TodayStudyPlan } from '../types'
import {
  applyTaskStatus,
  applyTaskUpdate,
  formatPercent,
  formatTaskTime,
  insertTask,
  isPlanEmpty,
  nextToggleStatus,
  recomputeSummary,
  restoreTask,
  sortTasks,
} from '../utils'

function makeTask(overrides: Partial<StudyTask> & Pick<StudyTask, 'id'>): StudyTask {
  const { id, ...rest } = overrides
  return {
    id,
    title: `任务 ${id}`,
    subject: '数学',
    knowledgePoint: null,
    estimatedMinutes: 20,
    dueAt: null,
    status: 'TODO',
    overdue: false,
    priority: 50,
    version: 0,
    ...rest,
  }
}

function makePlan(tasks: StudyTask[]): TodayStudyPlan {
  return {
    planId: 1001,
    planDate: '2026-11-26',
    title: '2027 考研基础阶段',
    examDate: '2026-12-26',
    daysUntilExam: 30,
    summary: { completedTasks: 0, totalTasks: tasks.length, studyMinutes: 0, streakDays: 6 },
    tasks,
  }
}

describe('sortTasks（契约 1.6）', () => {
  it('priority 降序 → dueAt 升序 → 无截止时间靠后 → id 升序', () => {
    const tasks = [
      makeTask({ id: 4, priority: 70, dueAt: null }),
      makeTask({ id: 3, priority: 80, dueAt: '2026-11-26T22:00:00+08:00' }),
      makeTask({ id: 2, priority: 80, dueAt: '2026-11-26T21:30:00+08:00' }),
      makeTask({ id: 1, priority: 90, dueAt: '2026-11-26T20:00:00+08:00' }),
      makeTask({ id: 5, priority: 80, dueAt: '2026-11-26T22:00:00+08:00' }),
    ]
    expect(sortTasks(tasks).map((task) => task.id)).toEqual([1, 2, 3, 5, 4])
  })
})

describe('recomputeSummary（契约 4.2）', () => {
  it('completedTasks 只算 COMPLETED，totalTasks 不含 CANCELLED，studyMinutes 只算已完成', () => {
    const plan = makePlan([
      makeTask({ id: 1, status: 'COMPLETED', estimatedMinutes: 20 }),
      makeTask({ id: 2, status: 'COMPLETED', estimatedMinutes: 30 }),
      makeTask({ id: 3, status: 'IN_PROGRESS', estimatedMinutes: 25 }),
      makeTask({ id: 4, status: 'CANCELLED', estimatedMinutes: 40 }),
    ])
    const summary = recomputeSummary(plan)
    expect(summary.completedTasks).toBe(2)
    expect(summary.totalTasks).toBe(3)
    expect(summary.studyMinutes).toBe(50)
    // streakDays 由服务端计算，前端不重算
    expect(summary.streakDays).toBe(6)
  })
})

describe('任务状态修改与回滚（契约 9.2）', () => {
  it('乐观更新立即重算统计', () => {
    const plan = makePlan([makeTask({ id: 1, status: 'TODO', estimatedMinutes: 20 })])
    const optimistic = applyTaskStatus(plan, 1, 'COMPLETED')
    expect(optimistic.tasks[0].status).toBe('COMPLETED')
    expect(optimistic.summary.completedTasks).toBe(1)
    expect(optimistic.summary.studyMinutes).toBe(20)
    // 乐观更新不自行判断 overdue / version
    expect(optimistic.tasks[0].overdue).toBe(false)
    expect(optimistic.tasks[0].version).toBe(0)
  })

  it('200 后用服务端任务覆盖本地版本，不留旧 version', () => {
    const plan = makePlan([makeTask({ id: 1, status: 'TODO', version: 1 })])
    const optimistic = applyTaskStatus(plan, 1, 'COMPLETED')
    const saved = makeTask({ id: 1, status: 'COMPLETED', version: 2, overdue: false })
    const applied = applyTaskUpdate(optimistic, saved)
    expect(applied.tasks[0].version).toBe(2)
    expect(applied.tasks[0].status).toBe('COMPLETED')
  })

  it('失败回滚恢复提交前的状态与版本', () => {
    const plan = makePlan([makeTask({ id: 1, status: 'TODO', version: 1, estimatedMinutes: 20 })])
    const previous = { ...plan.tasks[0] }
    const optimistic = applyTaskStatus(plan, 1, 'COMPLETED')
    const rolledBack = restoreTask(optimistic, previous)
    expect(rolledBack.tasks[0].status).toBe('TODO')
    expect(rolledBack.tasks[0].version).toBe(1)
    expect(rolledBack.summary.completedTasks).toBe(0)
    expect(rolledBack.summary.studyMinutes).toBe(0)
  })

  it('接受推荐后插入任务并重排', () => {
    const plan = makePlan([makeTask({ id: 1, priority: 50 })])
    const inserted = insertTask(plan, makeTask({ id: 9, priority: 90 }))
    expect(inserted.tasks.map((task) => task.id)).toEqual([9, 1])
    expect(inserted.summary.totalTasks).toBe(2)
  })

  it('已完成任务恢复成 TODO（契约 5.3）', () => {
    expect(nextToggleStatus('COMPLETED')).toBe('TODO')
    expect(nextToggleStatus('IN_PROGRESS')).toBe('COMPLETED')
    expect(nextToggleStatus('TODO')).toBe('COMPLETED')
  })
})

describe('展示工具', () => {
  it('空计划判定：planId = 0 且 tasks = []', () => {
    expect(isPlanEmpty(null)).toBe(true)
    // 有计划 id 但没有任务：不算空计划，页面走“今日暂无任务”
    expect(isPlanEmpty(makePlan([]))).toBe(false)
    expect(isPlanEmpty({ ...makePlan([]), planId: 0 })).toBe(true)
    expect(isPlanEmpty({ ...makePlan([makeTask({ id: 1 })]), planId: 0 })).toBe(false)
  })

  it('截止时间只取服务端时区里的时分', () => {
    expect(formatTaskTime('2026-11-26T21:30:00+08:00')).toBe('21:30')
    expect(formatTaskTime(null)).toBe('')
  })

  it('完成率 0~1 转百分比', () => {
    expect(formatPercent(0.72)).toBe('72%')
    expect(formatPercent(0)).toBe('0%')
    expect(formatPercent(1)).toBe('100%')
  })
})

describe('学习计划 Mock（契约 5.1 / 5.11）', () => {
  beforeEach(() => {
    resetStudyPlanMock()
    localStorage.setItem('kaoyan.insight.mock', 'on')
    localStorage.setItem('kaoyan.insight.mock.scenario', 'normal')
  })

  it('返回 4 个任务、2 个已完成、1 个逾期，顺序符合契约', async () => {
    const plan = await mockTodayPlan()
    expect(plan.planId).toBe(1001)
    expect(plan.tasks).toHaveLength(4)
    expect(plan.tasks.filter((task) => task.status === 'COMPLETED')).toHaveLength(2)
    expect(plan.tasks.filter((task) => task.overdue)).toHaveLength(1)
    expect(plan.tasks.map((task) => task.id)).toEqual([502, 501, 504, 503])
    expect(plan.summary).toEqual({ completedTasks: 2, totalTasks: 4, studyMinutes: 50, streakDays: 6 })
  })

  it('未生成周报 reportId = null，不是错误', () => {
    expect(MOCK_WEEKLY_REPORT_EMPTY.reportId).toBeNull()
    expect(MOCK_WEEKLY_REPORT_EMPTY.completionRate).toBe(0)
  })

  it('expectedVersion 不一致返回 409，版本匹配后成功', async () => {
    await expect(mockUpdateTaskStatus(502, 'COMPLETED', 1)).rejects.toMatchObject({ code: 409 })
    await expect(mockUpdateTaskStatus(502, 'COMPLETED', 2)).resolves.toMatchObject({
      status: 'COMPLETED',
      version: 3,
    })
  })

  it('loading 场景可切换（骨架屏用），no-goal 场景生成计划返回 400 + 10000', async () => {
    localStorage.setItem('kaoyan.insight.mock.scenario', 'loading')
    expect(getStudyPlanMockScenario()).toBe('loading')

    localStorage.setItem('kaoyan.insight.mock.scenario', 'no-goal')
    await expect(mockGenerateTodayPlan()).rejects.toMatchObject({ code: 10000, httpStatus: 400 })
  })
})

describe('Insight Store 学习计划流程', () => {
  beforeEach(() => {
    resetStudyPlanMock()
    localStorage.setItem('kaoyan.insight.mock', 'on')
    localStorage.setItem('kaoyan.insight.mock.scenario', 'normal')
    localStorage.removeItem('kaoyan.insight.mock.weekly')
    setActivePinia(createPinia())
  })

  it('loadTodayStudy 拉到今日计划且不报错', async () => {
    const store = useInsightStore()
    await store.loadTodayStudy()
    expect(store.errorMessage).toBe('')
    expect(store.dataMode).toBe('mock')
    expect(store.todayPlan?.tasks).toHaveLength(4)
  })

  it('任务完成成功：使用响应版本覆盖，统计随之更新', async () => {
    const store = useInsightStore()
    await store.loadTodayStudy()
    const before = store.todayPlan?.summary.completedTasks
    await store.updateTaskStatus(502, 'COMPLETED')
    const task = store.todayPlan?.tasks.find((item) => item.id === 502)
    expect(task?.status).toBe('COMPLETED')
    expect(task?.version).toBe(3)
    expect(store.todayPlan?.summary.completedTasks).toBe((before ?? 0) + 1)
    expect(store.submittingTaskIds).toEqual([])
  })

  it('同一任务连点只提交一次 PATCH（契约 9.2 / 指南 13.4）', async () => {
    const store = useInsightStore()
    await store.loadTodayStudy()
    // 连点：两次调用之间不等待，第二次必须被 submittingTaskIds 挡住
    await Promise.all([
      store.updateTaskStatus(502, 'COMPLETED'),
      store.updateTaskStatus(502, 'COMPLETED'),
    ])

    const task = store.todayPlan?.tasks.find((item) => item.id === 502)
    // 只发一次请求的直接证据：version 只前进到 3。
    // 若第二个 PATCH 也发出，它会拿 version 3 再提交，版本会变成 4 或触发 409。
    expect(task?.version).toBe(3)
    expect(task?.status).toBe('COMPLETED')
    expect(store.errorMessage).toBe('')
    expect(store.submittingTaskIds).toEqual([])
  })

  it('版本冲突 409：提示刷新并重新拉取，不保留旧版本', async () => {
    const store = useInsightStore()
    await store.loadTodayStudy()
    // 模拟别人先改了任务：服务端版本变成 3
    await mockUpdateTaskStatus(502, 'COMPLETED', 2)
    await store.updateTaskStatus(502, 'COMPLETED')
    expect(store.errorMessage).toBe('任务已被修改，请刷新后重试')
    const task = store.todayPlan?.tasks.find((item) => item.id === 502)
    expect(task?.version).toBe(3)
    expect(store.submittingTaskIds).toEqual([])
  })

  it('空计划走空态而不是错误，生成后出现任务', async () => {
    localStorage.setItem('kaoyan.insight.mock.scenario', 'empty')
    const store = useInsightStore()
    await store.loadTodayStudy()
    expect(store.errorMessage).toBe('')
    expect(isPlanEmpty(store.todayPlan)).toBe(true)

    await store.generateTodayStudy()
    expect(store.errorMessage).toBe('')
    expect(store.todayPlan?.tasks.length).toBeGreaterThanOrEqual(1)
    expect(store.todayPlan?.tasks.length).toBeLessThanOrEqual(3)
  })

  it('没有备考目标：生成计划 400 + 10000 显示可执行提示（契约 5.2 / 13.3）', async () => {
    localStorage.setItem('kaoyan.insight.mock.scenario', 'no-goal')
    const store = useInsightStore()
    await store.loadTodayStudy()
    expect(store.errorMessage).toBe('')

    await store.generateTodayStudy()
    expect(store.errorMessage).toBe('请先填写备考目标后再生成学习计划')
    expect(store.todayPlan?.tasks).toEqual([])
  })

  it('服务端 500：任务回滚到提交前状态，不留下乐观更新（契约 9.2）', async () => {
    const store = useInsightStore()
    await store.loadTodayStudy()
    localStorage.setItem('kaoyan.insight.mock.scenario', 'error')

    await store.updateTaskStatus(502, 'COMPLETED')

    const task = store.todayPlan?.tasks.find((item) => item.id === 502)
    expect(task?.status).toBe('TODO')
    expect(task?.version).toBe(2)
    expect(task?.estimatedMinutes).toBe(30)
    expect(store.submittingTaskIds).toEqual([])
    expect(store.errorMessage).toBe('学习计划服务暂时不可用，请稍后重试')
  })

  it('周报未生成可以和今日任务共存（独立开关，reportId = null 不是错误）', async () => {
    localStorage.setItem('kaoyan.insight.mock.weekly', 'empty')
    const store = useInsightStore()
    await store.loadTodayStudy()
    await store.loadWeeklyReport()

    expect(store.todayPlan?.tasks).toHaveLength(4)
    expect(store.weeklyReport?.reportId).toBeNull()
    expect(store.weeklyReportErrorMessage).toBe('')
  })

  it('403 + 10008：记下接口码，交给页面走既有无权限页（契约 9.1 / 11.4）', async () => {
    localStorage.setItem('kaoyan.insight.mock.scenario', 'forbidden')
    const store = useInsightStore()
    await store.loadTodayStudy()

    expect(store.lastErrorCode).toBe(10008)
    expect(store.errorMessage).toBe('无接口权限')
    expect(store.todayPlan).toBeNull()
  })

  it('推荐服务异常：只置推荐区错误，任务列表保持可用（契约 5.4 / 9.1）', async () => {
    const store = useInsightStore()
    await store.loadTodayStudy()
    localStorage.setItem('kaoyan.insight.mock.scenario', 'error')

    await store.loadRecommendations('TODAY', 'NONE')

    expect(store.recommendationErrorMessage).toBe('推荐服务暂时不可用，请稍后重试')
    expect(store.recommendations).toEqual([])
    expect(store.todayPlan?.tasks).toHaveLength(4)
    expect(store.errorMessage).toBe('')
  })
})
