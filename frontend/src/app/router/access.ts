/**
 * 路由准入判定的纯函数：与 Pinia Store 解耦，便于单测覆盖
 * 「学生不能进后台」「审计员只读可进」「未登录跳对应登录页」等口径。
 */

export type AccessScope = 'user' | 'admin'

export interface AccessInput {
  scope: AccessScope
  /** 当前 scope 的 token（sa_token / sa_admin_token） */
  token: string | null
  /** 是否已拉取到用户信息（userId 非空视为已恢复） */
  loaded: boolean
  permissions: string[]
  /** 当前用户角色码（仅学生端 meta.roles 使用） */
  roles?: string[]
  /** 路由 meta.permission，数组为「或」 */
  requiredPermission?: string | string[]
  /** 路由 meta.roles，数组为「或」 */
  requiredRoles?: string[]
}

export type AccessDecision =
  | { type: 'allow' }
  | { type: 'login' }
  | { type: 'forbidden'; reason: 'scope' | 'permission' | 'role' }

/** 学习计划菜单与路由共用的权限码（数组为「或」逻辑），只在这里定义一次，避免两处漂移 */
export const STUDY_PLAN_PERMISSIONS = ['study-plan:view', 'study-plan:create'] as const

/**
 * 学生端登录后的默认落地页（任务A：不再把所有人统一送到 /dashboard）：
 * 1. 有 study-plan:view 或 study-plan:create → /study-plans；
 * 2. 否则有 file:view → /files；
 * 3. 都没有 → /chat（聊天是所有学生的基础能力）。
 */
export function resolveLandingPath(permissions: string[]): string {
  if (matchPermission(permissions, [...STUDY_PLAN_PERMISSIONS])) {
    return '/study-plans'
  }
  if (permissions.includes('file:view')) {
    return '/files'
  }
  return '/chat'
}

/** 任意 admin:* 权限：后台唯一准入口径，不看角色名 */
export function hasAdminScope(permissions: string[]): boolean {
  return permissions.some((code) => code.startsWith('admin:'))
}

/** 权限码匹配：数组为「或」逻辑，命中任意一个即通过 */
export function matchPermission(
  permissions: string[],
  required?: string | string[],
): boolean {
  if (!required) return true
  const codes = Array.isArray(required) ? required : [required]
  if (codes.length === 0) return true
  return codes.some((code) => permissions.includes(code))
}

/** 角色匹配：数组为「或」逻辑（仅保留给学生端既有逻辑使用） */
export function matchRole(roles: string[], required?: string[]): boolean {
  if (!required || required.length === 0) return true
  return required.some((role) => roles.includes(role))
}

/**
 * 判定结果：
 * 1. 没 token → 去对应登录页
 * 2. 后台要求任意 admin:* 权限，否则 403（学生账号因此被挡在后台外）
 * 3. 再校验具体权限码 / 角色
 */
export function decideAccess(input: AccessInput): AccessDecision {
  if (!input.token) {
    return { type: 'login' }
  }

  if (input.scope === 'admin' && !hasAdminScope(input.permissions)) {
    return { type: 'forbidden', reason: 'scope' }
  }

  if (!matchPermission(input.permissions, input.requiredPermission)) {
    return { type: 'forbidden', reason: 'permission' }
  }

  if (!matchRole(input.roles ?? [], input.requiredRoles)) {
    return { type: 'forbidden', reason: 'role' }
  }

  return { type: 'allow' }
}

/** 登录后的 redirect 目标：只接受站内绝对路径，且后台只接受 /admin 开头 */
export function resolveAdminRedirect(
  query: unknown,
  fallback = '/admin/dashboard',
): string {
  const raw = Array.isArray(query) ? query[0] : query
  if (typeof raw === 'string' && raw.startsWith('/admin') && !raw.startsWith('/admin/login')) {
    return raw
  }
  return fallback
}
