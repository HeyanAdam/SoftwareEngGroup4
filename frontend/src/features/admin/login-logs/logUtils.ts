/** 登录日志展示的纯函数（便于单测，不依赖 Vue） */

/**
 * 后端 LocalDateTime 序列化为 2026-11-21T10:20:30（可能带毫秒或 Z），
 * 展示时统一成「2026-11-21 10:20:30」。
 */
export function formatLoginTime(value: string | null | undefined): string {
  if (!value) return '—'
  return value
    .replace('T', ' ')
    .replace(/\.\d+/, '')
    .replace(/Z$/, '')
    .trim()
}

/** 后端 success 是 Integer（1 成功 / 0 失败），兼容被序列化成 boolean 的情况 */
export function isLoginSuccess(value: number | boolean | null | undefined): boolean {
  return value === 1 || value === true
}

/** 结果标签文案 */
export function loginResultLabel(value: number | boolean | null | undefined): string {
  return isLoginSuccess(value) ? '成功' : '失败'
}
