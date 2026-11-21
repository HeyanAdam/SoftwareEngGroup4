import type { AdminRole, AdminUserQuery, AdminUserVO } from '../api/types'

/** 状态值：0 = 禁用，1 = 正常（与后端 user status 口径一致） */
export const USER_STATUS = {
  DISABLED: 0,
  ACTIVE: 1,
} as const

/** 内置角色码 → 中文名兜底（后端 /roles 返回 roleName 时优先用后端的） */
const ROLE_LABELS: Record<string, string> = {
  SYSTEM_ADMIN: '系统管理员',
  DATA_AUDITOR: '数据审计员',
  STUDENT: '学生',
}

/** 角色码展示名：优先用角色字典里的 roleName，未知角色码原样展示 */
export function roleLabel(code: string, roles: AdminRole[] = []): string {
  const matched = roles.find((role) => role.roleCode === code)
  return matched?.roleName || ROLE_LABELS[code] || code
}

/** 表格筛选条件 → 后端查询参数：空关键词不传，状态留空表示全部 */
export function buildUserQuery(params: {
  keyword: string
  status: string | number | ''
  pageNum: number
  pageSize: number
}): AdminUserQuery {
  const keyword = params.keyword.trim()
  const status =
    params.status === '' || params.status === null || params.status === undefined
      ? undefined
      : Number(params.status)

  return {
    keyword: keyword || undefined,
    status,
    page_num: params.pageNum,
    page_size: params.pageSize,
  }
}

/** 取反状态：启用 ↔ 禁用 */
export function nextStatus(current: number): number {
  return current === USER_STATUS.ACTIVE ? USER_STATUS.DISABLED : USER_STATUS.ACTIVE
}

/**
 * 切换用户状态。
 * 只有请求成功才修改 user.status；失败时把 user.status 写回原值（UI 回滚并保留原状态）。
 * 返回结果便于页面提示与单测断言。
 */
export async function toggleUserStatus(
  user: AdminUserVO,
  request: (id: number, status: number) => Promise<unknown>,
): Promise<{ ok: boolean; status: number; error?: unknown }> {
  const previous = user.status
  const target = nextStatus(previous)

  try {
    await request(user.user_id, target)
    user.status = target
    return { ok: true, status: target }
  } catch (error) {
    user.status = previous
    return { ok: false, status: previous, error }
  }
}

/**
 * 保存角色后再拉一次该用户的角色绑定，保证表格标签与后端一致。
 * 保存失败直接抛出，由抽屉原样展示后端错误（含最后一个 SYSTEM_ADMIN 保护提示）。
 */
export async function saveUserRolesWithRefresh(params: {
  userId: number
  ids: number[]
  update: (id: number, ids: number[]) => Promise<unknown>
  reload: (id: number) => Promise<AdminRole[]>
}): Promise<AdminRole[]> {
  await params.update(params.userId, params.ids)
  return params.reload(params.userId)
}
