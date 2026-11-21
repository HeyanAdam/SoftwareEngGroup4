import { defineStore } from 'pinia'
import { adminAuthGet, adminAuthPost, ADMIN_TOKEN_STORAGE_KEY } from '../../shared/api/request'
import type { CurrentUser, LoginResult } from '../../shared/types/auth'

interface AdminAuthState {
  token: string | null
  userId: number | null
  username: string | null
  nickname: string | null
  roles: string[]
  permissions: string[]
}

/**
 * 后台登录态：与学生端 auth Store 完全隔离。
 * token 存 sa_admin_token，请求走 adminAuthGet / adminAuthPost（/api/v1/auth 前缀 + 后台 token）。
 */
export const useAdminAuthStore = defineStore('adminAuth', {
  state: (): AdminAuthState => ({
    token: localStorage.getItem(ADMIN_TOKEN_STORAGE_KEY),
    userId: null,
    username: null,
    nickname: null,
    roles: [],
    permissions: [],
  }),

  actions: {
    /** 登录成功后写入后台登录态与权限快照 */
    setLoginResult(result: LoginResult): void {
      this.token = result.token
      localStorage.setItem(ADMIN_TOKEN_STORAGE_KEY, result.token)
      this.userId = result.user_id
      this.username = result.username
      this.nickname = result.nickname
      this.roles = result.roles ?? []
      this.permissions = result.permissions ?? []
    },

    /** 仅有 token 时先写入（后续 loadCurrentUser 补齐用户信息） */
    setToken(token: string): void {
      this.token = token
      localStorage.setItem(ADMIN_TOKEN_STORAGE_KEY, token)
    },

    /** 刷新恢复登录态：走后台认证实例拉 /auth/me */
    async loadCurrentUser(): Promise<void> {
      if (!this.token) return
      const user = await adminAuthGet<CurrentUser>('/auth/me')
      this.setCurrentUser(user)
    },

    /** 写入当前用户（来自 /auth/me） */
    setCurrentUser(user: CurrentUser): void {
      this.userId = user.user_id
      this.username = user.username
      this.nickname = user.nickname
      this.roles = user.roles ?? []
      this.permissions = user.permissions ?? []
    },

    /** 退出后台：尽力调 /auth/logout，无论成败都清后台状态 */
    async logout(): Promise<void> {
      try {
        if (this.token) {
          await adminAuthPost<null>('/auth/logout')
        }
      } catch {
        // 退出失败也要清理本地状态
      } finally {
        this.clearAuth()
      }
    },

    /** 只清后台登录态与 sa_admin_token，不动学生会话 */
    clearAuth(): void {
      this.token = null
      this.userId = null
      this.username = null
      this.nickname = null
      this.roles = []
      this.permissions = []
      localStorage.removeItem(ADMIN_TOKEN_STORAGE_KEY)
    },

    /** 是否拥有某后台权限码 */
    hasPermission(permissionCode: string): boolean {
      return this.permissions.includes(permissionCode)
    },

    /** 是否拥有任意 admin:* 权限：后台准入唯一口径，不按角色名判断 */
    hasAnyAdminPermission(): boolean {
      return this.permissions.some((code) => code.startsWith('admin:'))
    },
  },
})
