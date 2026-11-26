import { describe, expect, it } from 'vitest'
import { resolveLandingPath, STUDY_PLAN_PERMISSIONS } from '../access'

/**
 * 任务A：根路径落地页选择规则的单元测试。
 * 规则：学习计划权限优先 → 文件权限 → 兜底聊天，任何情况下都不再回 /dashboard。
 */
describe('resolveLandingPath', () => {
  it('有 study-plan:view → 进入学习计划', () => {
    expect(resolveLandingPath(['study-plan:view'])).toBe('/study-plans')
  })

  it('只有 study-plan:create 也进入学习计划（数组为「或」逻辑）', () => {
    expect(resolveLandingPath(['study-plan:create'])).toBe('/study-plans')
  })

  it('没有学习计划但有 file:view → 进入文件页', () => {
    expect(resolveLandingPath(['file:view'])).toBe('/files')
  })

  it('没有任何相关权限 → 兜底聊天页', () => {
    expect(resolveLandingPath([])).toBe('/chat')
    expect(resolveLandingPath(['chat:send'])).toBe('/chat')
  })

  it('权限码常量包含学习计划的两个入口权限', () => {
    expect([...STUDY_PLAN_PERMISSIONS]).toEqual(['study-plan:view', 'study-plan:create'])
  })
})
