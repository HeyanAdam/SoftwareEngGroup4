/**
 * 通知展示层纯函数：时间格式化、站内跳转校验、分组文案。
 * 时间只做"显示成什么样"，不做任何"是否逾期/是否今天"的判断（契约 6.6：逾期一律由后端算）。
 */

export const NOTIFICATION_TIMEZONE = 'Asia/Shanghai'

/** 通知时间显示：今天只显示时分，其它显示月日；跨年补年份 */
export function formatNotificationTime(
  iso: string,
  now: Date = new Date(),
): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const parts = (value: Date): string =>
    new Intl.DateTimeFormat('zh-CN', {
      timeZone: NOTIFICATION_TIMEZONE,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(value)

  const sameDay = parts(date) === parts(now)
  if (sameDay) {
    return new Intl.DateTimeFormat('zh-CN', {
      timeZone: NOTIFICATION_TIMEZONE,
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(date)
  }
  const sameYear =
    new Intl.DateTimeFormat('en-CA', { timeZone: NOTIFICATION_TIMEZONE, year: 'numeric' }).format(date) ===
    new Intl.DateTimeFormat('en-CA', { timeZone: NOTIFICATION_TIMEZONE, year: 'numeric' }).format(now)
  const formatter = new Intl.DateTimeFormat('zh-CN', {
    timeZone: NOTIFICATION_TIMEZONE,
    ...(sameYear ? {} : { year: 'numeric' as const }),
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
  return formatter.format(date)
}

/** 未读条目在列表里的样式类 */
export function notificationItemClass(read: boolean): string {
  return read ? 'notif-item notif-item--read' : 'notif-item'
}
