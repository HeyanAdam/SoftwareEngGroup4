import { describe, expect, it } from 'vitest'
import { formatLoginTime, isLoginSuccess, loginResultLabel } from './logUtils'

describe('登录日志展示', () => {
  it('ISO 时间转成空格分隔，并去掉毫秒与 Z', () => {
    expect(formatLoginTime('2026-11-21T10:20:30')).toBe('2026-11-21 10:20:30')
    expect(formatLoginTime('2026-11-21T10:20:30.123')).toBe('2026-11-21 10:20:30')
    expect(formatLoginTime('2026-11-21T10:20:30Z')).toBe('2026-11-21 10:20:30')
    expect(formatLoginTime(null)).toBe('—')
    expect(formatLoginTime('')).toBe('—')
  })

  it('success 按后端 Integer 口径判定，兼容 boolean', () => {
    expect(isLoginSuccess(1)).toBe(true)
    expect(isLoginSuccess(0)).toBe(false)
    expect(isLoginSuccess(true)).toBe(true)
    expect(isLoginSuccess(null)).toBe(false)
    expect(loginResultLabel(1)).toBe('成功')
    expect(loginResultLabel(0)).toBe('失败')
  })
})
