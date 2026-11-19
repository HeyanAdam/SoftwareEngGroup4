import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/dashboard',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      {
        path: 'dashboard',
        component: () => import('../../features/dashboard/DashboardPage.vue'),
      },
      {
        path: 'study-plans',
        component: () => import('../../features/studyplan/StudyPlanPage.vue'),
      },
      {
        path: 'files',
        component: () => import('../../features/file/FilePage.vue'),
      },
      {
        path: 'chat',
        component: () => import('../../features/chat/ChatPage.vue'),
      },
      {
        path: 'admin',
        component: () => import('../../features/admin/AdminPage.vue'),
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('../NotFoundPage.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
