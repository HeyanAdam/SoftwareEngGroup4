import {
  adminAuthGet,
  adminAuthPost,
  adminDel,
  adminGet,
  adminPost,
  adminPut,
} from '../../../shared/api/request'
import type { CaptchaData, LoginResult } from '../../../shared/types/auth'
import type {
  AdminLoginPayload,
  AdminRole,
  AdminUserQuery,
  AdminUserVO,
  PageData,
} from './types'
import type {
  AdminLoginLog,
  AdminLoginLogQuery,
  AdminRoleSummary,
  RoleCreatePayload,
  RoleUpdatePayload,
  SysPermission,
  SysRole,
} from './admin.types'

/** GET /auth/captcha 图形验证码（走后台认证实例，读 sa_admin_token，不碰学生 token） */
export function fetchAdminCaptcha(): Promise<CaptchaData> {
  return adminAuthGet<CaptchaData>('/auth/captcha')
}

/** POST /auth/login 管理员登录 */
export function adminLogin(payload: AdminLoginPayload): Promise<LoginResult> {
  return adminAuthPost<LoginResult>('/auth/login', payload)
}

/** GET /admin/users 用户列表（分页 + 关键词 + 状态筛选） */
export function listUsers(query: AdminUserQuery): Promise<PageData<AdminUserVO>> {
  return adminGet<PageData<AdminUserVO>>('/users', {
    params: {
      keyword: query.keyword || undefined,
      status: query.status,
      page_num: query.page_num,
      page_size: query.page_size,
    },
  })
}

/** GET /admin/users/{id} 用户详情 */
export function getUser(id: number): Promise<AdminUserVO> {
  return adminGet<AdminUserVO>(`/users/${id}`)
}

/** PUT /admin/users/{id}/status 启用 / 禁用（status: 0 禁用，1 启用） */
export function updateUserStatus(id: number, status: number): Promise<null> {
  return adminPut<null>(`/users/${id}/status`, { status })
}

/** GET /admin/users/{id}/roles 当前用户已绑定角色 */
export function getUserRoles(id: number): Promise<AdminRole[]> {
  return adminGet<AdminRole[]>(`/users/${id}/roles`)
}

/** PUT /admin/users/{id}/roles 覆盖式分配角色（最后一个 SYSTEM_ADMIN 保护由后端判断） */
export function updateUserRoles(id: number, ids: number[]): Promise<null> {
  return adminPut<null>(`/users/${id}/roles`, { ids })
}

/** GET /admin/roles 全部角色（分配角色抽屉用） */
export function listRoles(): Promise<AdminRole[]> {
  return adminGet<AdminRole[]>('/roles')
}

/**
 * 后端是否已实现 `GET /admin/roles/summary`。
 *
 * 当前后端 AdminController 只有 `/roles`，访问 `/roles/summary` 会被 Spring 判成不支持的方法，
 * 再被全局异常兜底成 HTTP 500 + 90000，页面会整页报「服务端异常」，后端日志也会刷一条 ERROR。
 * 因此在前端先按「端点未就绪」处理，走降级统计；后端接口上线后把此开关改为 true 即可切换。
 */
const ROLE_SUMMARY_ENDPOINT_READY = false

/**
 * 角色汇总：角色实体 + 权限数 + 绑定人数。
 *
 * summary 接口就绪时一次拿到统计（避免 N+1）；未就绪时降级为
 * `/roles` 一次 + 每个角色并发查一次权限码（角色数量很少，只统计权限数）。
 * 绑定人数在 summary 就绪前保持 null，页面显示「待接入」而不是编造数字。
 */
export async function listRoleSummaries(): Promise<AdminRoleSummary[]> {
  if (ROLE_SUMMARY_ENDPOINT_READY) {
    const data = await adminGet<AdminRoleSummary[]>('/roles/summary')
    if (Array.isArray(data)) return data
  }
  return buildRoleSummariesFromRoleList()
}

/** 降级统计：角色列表 + 逐角色权限码条数；userCount 保持 null */
async function buildRoleSummariesFromRoleList(): Promise<AdminRoleSummary[]> {
  const roles = (await listRoles()) ?? []
  return Promise.all(
    roles.map(async (role) => {
      const codes = (await getRolePermissionCodes(role.id)) ?? []
      return { ...role, permissionCount: codes.length, userCount: null }
    }),
  )
}

/** POST /admin/roles 新建角色（roleCode 创建后不可修改） */
export function createRole(payload: RoleCreatePayload): Promise<SysRole> {
  return adminPost<SysRole>('/roles', payload)
}

/** PUT /admin/roles/{id} 编辑角色：只改 roleName / description */
export function updateRole(id: number, payload: RoleUpdatePayload): Promise<SysRole> {
  return adminPut<SysRole>(`/roles/${id}`, payload)
}

/** DELETE /admin/roles/{id} 删除角色（内置角色与有绑定用户的角色由后端拒绝） */
export function removeRole(id: number): Promise<null> {
  return adminDel<null>(`/roles/${id}`)
}

/** GET /admin/permissions 权限字典（全量 20 条） */
export function listPermissions(): Promise<SysPermission[]> {
  return adminGet<SysPermission[]>('/permissions')
}

/**
 * GET /admin/roles/{id}/permissions 角色已授权限
 * 注意不对称：GET 返回权限码字符串数组，PUT 接收权限主键数组。
 */
export function getRolePermissionCodes(id: number): Promise<string[]> {
  return adminGet<string[]>(`/roles/${id}/permissions`)
}

/** PUT /admin/roles/{id}/permissions 保存角色授权（覆盖式，传权限主键） */
export function saveRolePermissions(id: number, ids: number[]): Promise<null> {
  return adminPut<null>(`/roles/${id}/permissions`, { ids })
}

/** GET /admin/login-logs 登录日志（按账号过滤，limit 默认 20） */
export function listLoginLogs(query: AdminLoginLogQuery = {}): Promise<AdminLoginLog[]> {
  return adminGet<AdminLoginLog[]>('/login-logs', {
    params: {
      account: query.account || undefined,
      limit: query.limit,
    },
  })
}
