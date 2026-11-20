import { defineStore } from 'pinia'
import { get, post, TOKEN_STORAGE_KEY } from '../../shared/api/request'
import type { CurrentUser, LoginResult } from '../../shared/types/auth'

interface AuthState {
  token: string | null
  userId: number | null
  username: string | null
  nickname: string | null
  emailMasked: string | null
  roles: string[]
  permissions: string[]
  hasProfile: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: localStorage.getItem(TOKEN_STORAGE_KEY),
    userId: null,
    username: null,
    nickname: null,
    emailMasked: null,
    roles: [],
    permissions: [],
    hasProfile: false,
  }),

  actions: {
    /** 登录成功后写入登录态与权限快照 */
    setLoginResult(result: LoginResult): void {
      this.token = result.token
      localStorage.setItem(TOKEN_STORAGE_KEY, result.token)
      this.userId = result.user_id
      this.username = result.username
      this.nickname = result.nickname
      this.roles = result.roles ?? []
      this.permissions = result.permissions ?? []
      this.hasProfile = result.has_profile
    },

    /** 注册等场景：后端只返回 token，先写 token 再拉取用户信息 */
    setToken(token: string): void {
      this.token = token
      localStorage.setItem(TOKEN_STORAGE_KEY, token)
    },

    /** 写入当前用户（来自 /auth/me 或 Mock） */
    setCurrentUser(user: CurrentUser): void {
      this.userId = user.user_id
      this.username = user.username
      this.nickname = user.nickname
      this.emailMasked = user.email_masked
      this.roles = user.roles ?? []
      this.permissions = user.permissions ?? []
      this.hasProfile = user.has_profile
    },

    /** 恢复登录态：刷新页面后调用 /auth/me 重新拉取权限快照（Mock 在请求层自动接管） */
    async loadCurrentUser(): Promise<void> {
      if (!this.token) return
      const user = await get<CurrentUser>('/auth/me')
      this.setCurrentUser(user)
    },

    /** 退出登录 */
    async logout(): Promise<void> {
      try {
        if (this.token) {
          await post<null>('/auth/logout')
        }
      } catch {
        // 退出失败也要清理本地状态
      } finally {
        this.clearAuth()
      }
    },

    /** 清空登录态与本地存储 */
    clearAuth(): void {
      this.token = null
      this.userId = null
      this.username = null
      this.nickname = null
      this.emailMasked = null
      this.roles = []
      this.permissions = []
      this.hasProfile = false
      localStorage.removeItem(TOKEN_STORAGE_KEY)
    },

    /** 是否拥有某角色（业务判断用，界面显隐禁止使用） */
    hasRole(roleCode: string): boolean {
      return this.roles.includes(roleCode)
    },

    /** 是否拥有某权限码（菜单、按钮、路由统一用这个判断） */
    hasPermission(permissionCode: string): boolean {
      return this.permissions.includes(permissionCode)
    },
  },
})
