import { useAuthStore } from '../../app/stores/auth'

/** 权限判断组合式函数：菜单、按钮、路由共用同一套权限码 */
export function usePermission() {
  const authStore = useAuthStore()

  /** 是否拥有某权限码 */
  function hasPermission(code: string): boolean {
    return authStore.hasPermission(code)
  }

  /** 是否拥有多个权限码中的任意一个 */
  function hasAnyPermission(codes: string[]): boolean {
    return codes.some((code) => authStore.hasPermission(code))
  }

  /** 是否拥有某角色（仅业务逻辑使用，界面显隐请用权限码） */
  function hasRole(role: string): boolean {
    return authStore.hasRole(role)
  }

  return { hasPermission, hasAnyPermission, hasRole }
}
