import {
  adminAuthGet,
  adminAuthPost,
  adminGet,
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
