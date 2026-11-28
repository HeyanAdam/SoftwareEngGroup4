import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { UNAUTHORIZED_EVENT, type UnauthorizedDetail } from '../../shared/api/request'
import { useAuthStore } from '../stores/auth'
import { useAdminAuthStore } from '../stores/adminAuth'
import { decideAccess, hasAdminScope, resolveLandingPath } from './access'

declare module 'vue-router' {
  interface RouteMeta {
    /** 是否需要登录，缺省为 true（公开页面显式写 false） */
    requiresAuth?: boolean
    /** 进入该路由所需权限码（数组为「或」逻辑：命中任意一个即放行） */
    permission?: string | string[]
    /** 进入该路由所需角色（任一命中即可） */
    roles?: string[]
    /** 是否为后台路由域：true 时使用后台 Store / 令牌 / 登录页 */
    adminScope?: boolean
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../../features/auth/LoginPage.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../../features/auth/RegisterPage.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('../../features/auth/ForgotPasswordPage.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/401',
    name: 'Unauthorized',
    component: () => import('../../features/auth/error/UnauthorizedPage.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('../../features/auth/error/ForbiddenPage.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/429',
    name: 'TooManyRequests',
    component: () => import('../../features/auth/error/TooManyRequestsPage.vue'),
    meta: { requiresAuth: false },
  },
  // ---- 后台登录页：独立于学生端壳层，不显示学生侧栏 ----
  {
    path: '/admin/login',
    name: 'AdminLogin',
    component: () => import('../../features/admin/login/AdminLoginPage.vue'),
    meta: { requiresAuth: false, adminScope: true },
  },
  // ---- 后台壳层：/admin 及其子路由 ----
  {
    path: '/admin',
    component: () => import('../../features/admin/layout/AdminLayout.vue'),
    meta: { requiresAuth: true, adminScope: true },
    children: [
      {
        path: '',
        redirect: '/admin/dashboard',
      },
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('../../features/admin/dashboard/AdminDashboardPage.vue'),
        meta: { permission: 'admin:user:list' },
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('../../features/admin/users/AdminUsersPage.vue'),
        meta: { permission: 'admin:user:list' },
      },
      {
        path: 'roles',
        name: 'AdminRoles',
        component: () => import('../../features/admin/roles/AdminRolesPage.vue'),
        meta: { permission: 'admin:role:list' },
      },
      {
        path: 'permissions',
        name: 'AdminPermissions',
        component: () => import('../../features/admin/permissions/AdminPermissionsPage.vue'),
        meta: { permission: 'admin:role:list' },
      },
      {
        path: 'logs',
        name: 'AdminLogs',
        component: () => import('../../features/admin/login-logs/AdminLogsPage.vue'),
        meta: { permission: 'admin:user:list' },
      },
    ],
  },
  {
    path: '/',
    // 兜底指到 /chat；真正按权限挑落地页在 beforeEach 里做（那时权限快照已恢复）
    redirect: '/chat',
    component: () => import('../layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../../features/dashboard/DashboardPage.vue'),
      },
      {
        path: 'study-plans',
        name: 'StudyPlan',
        component: () => import('../../features/studyplan/StudyPlanPage.vue'),
        meta: { permission: ['study-plan:view', 'study-plan:create'] },
      },
      {
        path: 'settings',
        name: 'StudySettings',
        component: () => import('../../features/settings/StudySettingsPage.vue'),
      },
      {
        path: 'files',
        name: 'File',
        component: () => import('../../features/file/FilePage.vue'),
        meta: { permission: 'file:view' },
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('../../features/chat/ChatPage.vue'),
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../NotFoundPage.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

/**
 * 全局路由守卫：按 adminScope 分流到后台 / 学生端 Store。
 * - 未登录跳对应登录页；刷新时对应 loadCurrentUser 恢复权限快照。
 * - 后台只校验具体权限码，准入要求任意 admin:* 权限，不硬编码 SYSTEM_ADMIN。
 */
router.beforeEach(async (to) => {
  const isAdminScope = to.meta.adminScope === true
  const requiresAuth = to.meta.requiresAuth !== false

  if (!requiresAuth) {
    return true
  }

  const adminStore = isAdminScope ? useAdminAuthStore() : null
  const userStore = useAuthStore()

  if (isAdminScope) {
    if (!adminStore?.token) {
      // 学生误闯后台 → 403；若学生账号本身带 admin:* 权限，引导去后台登录页换后台令牌
      if (userStore.token) {
        if (!userStore.userId) {
          await userStore.loadCurrentUser().catch(() => undefined)
        }
        if (hasAdminScope(userStore.permissions)) {
          return { name: 'AdminLogin', query: { redirect: to.fullPath } }
        }
        return { name: 'Forbidden' }
      }
      return { name: 'AdminLogin', query: { redirect: to.fullPath } }
    }

    try {
      if (!adminStore.userId) {
        await adminStore.loadCurrentUser()
      }
    } catch {
      adminStore.clearAuth()
      return { name: 'AdminLogin', query: { redirect: to.fullPath } }
    }

    const decision = decideAccess({
      scope: 'admin',
      token: adminStore.token,
      loaded: adminStore.userId !== null,
      permissions: adminStore.permissions,
      requiredPermission: to.meta.permission,
    })

    if (decision.type === 'forbidden') {
      return { name: 'Forbidden' }
    }
    return true
  }

  if (!userStore.token) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }

  try {
    if (!userStore.userId) {
      await userStore.loadCurrentUser()
    }
  } catch {
    userStore.clearAuth()
    return { name: 'Login', query: { redirect: to.fullPath } }
  }

  // 根路径：按当前权限挑默认落地页（学习计划 → 文件 → 聊天），不再统一送控制台。
  // '/' 的静态 redirect 会先把目标改写成 /chat，所以这里同时看 redirectedFrom。
  if (to.path === '/' || to.redirectedFrom?.path === '/') {
    const landing = resolveLandingPath(userStore.permissions)
    if (landing !== to.path) {
      return landing
    }
  }

  const decision = decideAccess({
    scope: 'user',
    token: userStore.token,
    loaded: userStore.userId !== null,
    permissions: userStore.permissions,
    roles: userStore.roles,
    requiredPermission: to.meta.permission,
    requiredRoles: to.meta.roles,
  })

  if (decision.type === 'forbidden') {
    return { name: 'Forbidden' }
  }

  return true
})

/** 401（Token 失效）统一跳登录页：后台 401 只回后台登录，学生 401 回学生登录 */
window.addEventListener(UNAUTHORIZED_EVENT, (event) => {
  const detail = (event as CustomEvent<UnauthorizedDetail>).detail
  const scope = detail?.scope === 'admin' ? 'admin' : 'user'

  if (scope === 'admin') {
    useAdminAuthStore().clearAuth()
    const current = router.currentRoute.value
    if (current.name === 'AdminLogin') return
    void router.replace({
      name: 'AdminLogin',
      query: current.fullPath.startsWith('/admin') ? { redirect: current.fullPath } : {},
    })
    return
  }

  const current = router.currentRoute.value
  if (current.name === 'Login') return
  void router.replace({ name: 'Login', query: { redirect: current.fullPath } })
})

export default router
export { routes }
