import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { UNAUTHORIZED_EVENT } from '../../shared/api/request'
import { useAuthStore } from '../stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    /** 是否需要登录，缺省为 true（公开页面显式写 false） */
    requiresAuth?: boolean
    /** 进入该路由所需权限码（数组为「或」逻辑：命中任意一个即放行） */
    permission?: string | string[]
    /** 进入该路由所需角色（任一命中即可） */
    roles?: string[]
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
  {
    path: '/',
    redirect: '/dashboard',
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
      {
        path: 'admin',
        name: 'Admin',
        component: () => import('../../features/admin/AdminPage.vue'),
        meta: { permission: 'admin:user:list' },
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

// 全局路由守卫：未登录跳登录，刷新恢复登录态，权限不足跳 403
router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const requiresAuth = to.meta.requiresAuth !== false

  if (!requiresAuth) {
    return true
  }

  if (!authStore.token) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }

  try {
    if (!authStore.userId) {
      await authStore.loadCurrentUser()
    }
  } catch {
    authStore.clearAuth()
    return { name: 'Login', query: { redirect: to.fullPath } }
  }

  const needPermission = to.meta.permission
  if (needPermission) {
    const codes = Array.isArray(needPermission) ? needPermission : [needPermission]
    if (!codes.some((code) => authStore.hasPermission(code))) {
      return { name: 'Forbidden' }
    }
  }

  const needRoles = to.meta.roles
  if (needRoles && needRoles.length > 0 && !needRoles.some((role) => authStore.hasRole(role))) {
    return { name: 'Forbidden' }
  }

  return true
})

// 401（Token 失效）统一跳登录页
window.addEventListener(UNAUTHORIZED_EVENT, () => {
  const current = router.currentRoute.value
  if (current.name === 'Login') return
  void router.replace({ name: 'Login', query: { redirect: current.fullPath } })
})

export default router
