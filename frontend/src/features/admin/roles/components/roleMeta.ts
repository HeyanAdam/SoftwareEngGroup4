/** 内置角色与展示口径（纯函数，便于单测） */

export const SYSTEM_ADMIN_ROLE_CODE = 'SYSTEM_ADMIN'

/** 官方内置角色（与后端 RoleConstants.BUILTIN_ROLES 一致，顺序为后端文档顺序） */
export const BUILTIN_ROLE_CODES = [
  'SYSTEM_ADMIN',
  'STUDENT',
  'CONTENT_OPERATOR',
  'DATA_AUDITOR',
] as const

const BUILTIN_ROLE_SET = new Set<string>(BUILTIN_ROLE_CODES)

/** 内置角色不允许删除 */
export function isBuiltinRole(roleCode: string): boolean {
  return BUILTIN_ROLE_SET.has(roleCode)
}

/** 系统管理员：全权角色，权限页只读、隐藏保存 */
export function isSystemAdminRole(roleCode: string): boolean {
  return roleCode === SYSTEM_ADMIN_ROLE_CODE
}

/** 绑定人数展示：null 表示后端尚无汇总接口，不伪造数字 */
export function formatUserCount(userCount: number | null | undefined): string {
  return typeof userCount === 'number' ? `${userCount} 人` : '待接入'
}
