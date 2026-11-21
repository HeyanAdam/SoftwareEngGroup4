/**
 * 后台前端测试：令牌隔离、401 分流、路由准入、用户启停回滚、角色保存刷新
 */

import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AxiosResponse } from 'axios'
import {
  ADMIN_TOKEN_STORAGE_KEY,
  TOKEN_STORAGE_KEY,
  UNAUTHORIZED_EVENT,
  createService,
  handleTokenInvalid,
  type UnauthorizedDetail,
} from '../../../shared/api/request'
import {
  decideAccess,
  hasAdminScope,
  resolveAdminRedirect,
} from '../../../app/router/access'
import {
  buildUserQuery,
  roleLabel,
  saveUserRolesWithRefresh,
  toggleUserStatus,
} from '../users/userActions'
import type { AdminRole, AdminUserVO } from '../api/types'

/** 构造一个不联网的 axios 适配器：记录请求头，返回指定状态与响应体 */
function adapterOf(status: number, body: unknown, headers: Record<string, string> = {}) {
  const calls: Array<Record<string, unknown>> = []
  const adapter = async (config: { headers: { toJSON(): Record<string, unknown> } }) => {
    calls.push(config.headers.toJSON())
    return {
      data: body,
      status,
      statusText: '',
      headers,
      config,
    } as unknown as AxiosResponse
  }
  return { adapter, calls }
}

beforeEach(() => {
  localStorage.clear()
})

describe('后台令牌与学生令牌隔离', () => {
  it('后台实例只带 sa_admin_token，学生实例只带 sa_token', async () => {
    localStorage.setItem(TOKEN_STORAGE_KEY, 'student-token')
    localStorage.setItem(ADMIN_TOKEN_STORAGE_KEY, 'admin-token')

    const userService = createService('/api/v1', TOKEN_STORAGE_KEY)
    const adminService = createService('/api/v1/admin', ADMIN_TOKEN_STORAGE_KEY)

    const userPlan = adapterOf(200, { code: 0, message: 'ok', data: null })
    const adminPlan = adapterOf(200, { code: 0, message: 'ok', data: null })

    userService.defaults.adapter = userPlan.adapter
    adminService.defaults.adapter = adminPlan.adapter

    await userService.get('/auth/me')
    await adminService.get('/users')

    const lower = (headers: Record<string, unknown>) =>
      headers.Authorization ?? headers.authorization
    expect(lower(userPlan.calls[0])).toBe('Bearer student-token')
    expect(lower(adminPlan.calls[0])).toBe('Bearer admin-token')
  })

  it('学生请求不读取 sa_admin_token（只有后台令牌时不发 Authorization）', async () => {
    localStorage.setItem(ADMIN_TOKEN_STORAGE_KEY, 'admin-token')

    const userService = createService('/api/v1', TOKEN_STORAGE_KEY)
    const plan = adapterOf(200, { code: 0, message: 'ok', data: null })
    userService.defaults.adapter = plan.adapter

    await userService.get('/auth/me')
    const headers = plan.calls[0] as Record<string, unknown>
    expect(headers.Authorization).toBeUndefined()
    expect(headers.authorization).toBeUndefined()
  })
})

describe('401 分流', () => {
  it('后台 401 只清 sa_admin_token，学生会话保留', () => {
    localStorage.setItem(TOKEN_STORAGE_KEY, 'student-token')
    localStorage.setItem(ADMIN_TOKEN_STORAGE_KEY, 'admin-token')

    const events: UnauthorizedDetail[] = []
    const listener = (event: Event) => {
      events.push((event as CustomEvent<UnauthorizedDetail>).detail)
    }
    window.addEventListener(UNAUTHORIZED_EVENT, listener)

    handleTokenInvalid('admin', { code: 10001, message: '未登录', requestId: 'rid-1' })

    expect(localStorage.getItem(ADMIN_TOKEN_STORAGE_KEY)).toBeNull()
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBe('student-token')
    expect(events[0].scope).toBe('admin')

    window.removeEventListener(UNAUTHORIZED_EVENT, listener)
  })

  it('学生 401 只清 sa_token，后台会话保留', () => {
    localStorage.setItem(TOKEN_STORAGE_KEY, 'student-token')
    localStorage.setItem(ADMIN_TOKEN_STORAGE_KEY, 'admin-token')

    const events: UnauthorizedDetail[] = []
    const listener = (event: Event) => {
      events.push((event as CustomEvent<UnauthorizedDetail>).detail)
    }
    window.addEventListener(UNAUTHORIZED_EVENT, listener)

    handleTokenInvalid('user', { code: 10001, message: '未登录', requestId: 'rid-2' })

    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull()
    expect(localStorage.getItem(ADMIN_TOKEN_STORAGE_KEY)).toBe('admin-token')
    expect(events[0].scope).toBe('user')

    window.removeEventListener(UNAUTHORIZED_EVENT, listener)
  })

  it('后台实例收到 401 时自动清后台令牌并派发 admin 事件', async () => {
    localStorage.setItem(TOKEN_STORAGE_KEY, 'student-token')
    localStorage.setItem(ADMIN_TOKEN_STORAGE_KEY, 'admin-token')

    const adminService = createService('/api/v1/admin', ADMIN_TOKEN_STORAGE_KEY)
    const plan = adapterOf(401, { code: 10001, message: '登录已失效' })
    adminService.defaults.adapter = plan.adapter

    await expect(adminService.get('/users')).rejects.toMatchObject({ code: 10001 })
    expect(localStorage.getItem(ADMIN_TOKEN_STORAGE_KEY)).toBeNull()
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBe('student-token')
  })
})

describe('路由准入', () => {
  it('没有后台令牌时去后台登录页', () => {
    expect(
      decideAccess({ scope: 'admin', token: null, loaded: false, permissions: [] }),
    ).toEqual({ type: 'login' })
  })

  it('学生账号（无 admin:* 权限）进入后台被拒', () => {
    const decision = decideAccess({
      scope: 'admin',
      token: 'student-token',
      loaded: true,
      permissions: ['study-plan:view', 'file:view'],
      requiredPermission: 'admin:user:list',
    })
    expect(decision).toEqual({ type: 'forbidden', reason: 'scope' })
  })

  it('DATA_AUDITOR 可进用户页，但没有修改权限', () => {
    const permissions = ['admin:user:list', 'admin:role:list']
    expect(hasAdminScope(permissions)).toBe(true)
    expect(
      decideAccess({
        scope: 'admin',
        token: 'admin-token',
        loaded: true,
        permissions,
        requiredPermission: 'admin:user:list',
      }),
    ).toEqual({ type: 'allow' })
    expect(permissions.includes('admin:user:update')).toBe(false)
  })

  it('只有 role:list 的账号访问用户页被 403', () => {
    expect(
      decideAccess({
        scope: 'admin',
        token: 'admin-token',
        loaded: true,
        permissions: ['admin:role:list'],
        requiredPermission: 'admin:user:list',
      }),
    ).toEqual({ type: 'forbidden', reason: 'permission' })
  })

  it('redirect 只接受 /admin 站内路径', () => {
    expect(resolveAdminRedirect('/admin/users?page_num=2')).toBe('/admin/users?page_num=2')
    expect(resolveAdminRedirect('/admin/login')).toBe('/admin/dashboard')
    expect(resolveAdminRedirect('/dashboard')).toBe('/admin/dashboard')
    expect(resolveAdminRedirect('https://evil.example.com')).toBe('/admin/dashboard')
    expect(resolveAdminRedirect(undefined)).toBe('/admin/dashboard')
  })
})

describe('用户列表与状态开关', () => {
  const user: AdminUserVO = {
    user_id: 7,
    username: 'student01',
    nickname: '阿叶',
    email: 's***@example.com',
    status: 1,
    roles: ['STUDENT'],
  }

  it('筛选参数：空关键词不传，状态留空表示全部', () => {
    expect(
      buildUserQuery({ keyword: '  ', status: '', pageNum: 2, pageSize: 10 }),
    ).toEqual({ keyword: undefined, status: undefined, page_num: 2, page_size: 10 })
    expect(
      buildUserQuery({ keyword: ' 阿叶 ', status: 0, pageNum: 1, pageSize: 20 }),
    ).toEqual({ keyword: '阿叶', status: 0, page_num: 1, page_size: 20 })
  })

  it('切换成功才更新状态', async () => {
    const request = vi.fn().mockResolvedValue(null)
    const result = await toggleUserStatus({ ...user }, request)

    expect(request).toHaveBeenCalledWith(7, 0)
    expect(result.ok).toBe(true)
    expect(result.status).toBe(0)
  })

  it('切换失败回滚到原状态', async () => {
    const target = { ...user }
    const request = vi.fn().mockRejectedValue(new Error('forbidden'))
    const result = await toggleUserStatus(target, request)

    expect(result.ok).toBe(false)
    expect(target.status).toBe(1)
    expect(result.status).toBe(1)
  })

  it('角色保存后按后端最新绑定刷新', async () => {
    const refreshed: AdminRole[] = [
      {
        id: 1,
        roleCode: 'STUDENT',
        roleName: '学生',
        description: null,
        status: 1,
      },
    ]
    const update = vi.fn().mockResolvedValue(null)
    const reload = vi.fn().mockResolvedValue(refreshed)

    const result = await saveUserRolesWithRefresh({
      userId: 7,
      ids: [1],
      update,
      reload,
    })

    expect(update).toHaveBeenCalledWith(7, [1])
    expect(reload).toHaveBeenCalledWith(7)
    expect(result).toBe(refreshed)
  })

  it('角色码展示名优先用字典 roleName', () => {
    const roles: AdminRole[] = [
      { id: 2, roleCode: 'DATA_AUDITOR', roleName: '数据审计员', description: null, status: 1 },
    ]
    expect(roleLabel('DATA_AUDITOR', roles)).toBe('数据审计员')
    expect(roleLabel('SYSTEM_ADMIN')).toBe('系统管理员')
    expect(roleLabel('CUSTOM_ROLE')).toBe('CUSTOM_ROLE')
  })
})

