<template>
  <el-container class="layout">
    <el-aside width="220px">
      <el-menu router>
        <!-- 学习计划：没有 study-plan:* 权限时不渲染（v-if 响应式，权限变化后自动恢复） -->
        <el-menu-item
          v-if="hasAnyPermission(STUDY_PLAN_PERMISSIONS)"
          index="/study-plans"
        >
          学习计划
        </el-menu-item>
        <el-menu-item index="/settings">学习设置</el-menu-item>
        <!-- 文件：需要 file:view；用 v-if 而不是 v-permission，避免权限恢复后菜单不回来 -->
        <el-menu-item v-if="hasPermission('file:view')" index="/files">文件</el-menu-item>
        <el-menu-item index="/chat">AI 聊天</el-menu-item>
        <!-- 管理后台入口：按权限码显隐，学生无 admin:user:list / admin:role:list 时不渲染 -->
        <el-menu-item v-if="hasAnyPermission(ADMIN_MENU_PERMISSIONS)" index="/admin/dashboard">
          管理后台
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="layout__header">
        <span class="layout__title">考研 AI 导学系统</span>
        <div class="layout__user">
          <!-- 通知入口：进入主布局只拉一次未读数，列表等打开抽屉再拉（契约 7.1） -->
          <NotificationBell @open="drawerVisible = true" />
          <span class="layout__user-name" :title="displayName">{{ displayName }}</span>
          <el-button link type="primary" :loading="loggingOut" @click="handleLogout">
            退出登录
          </el-button>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
    <NotificationDrawer v-model="drawerVisible" />
  </el-container>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore } from '../stores/notification'
import { usePermission } from '../../shared/utils/permission'
import { PERMISSIONS } from '../../shared/constants/permissions'
import { STUDY_PLAN_PERMISSIONS } from '../router/access'
import NotificationBell from '../../features/notification/components/NotificationBell.vue'
import NotificationDrawer from '../../features/notification/components/NotificationDrawer.vue'
import { isNotificationMockEnabled } from '../../features/notification/notification.mock'
import {
  bindNotificationRealtime,
  createWindowRealtimeSource,
} from '../../features/notification/realtime/notificationRealtime'
import {
  createNotificationMockRealtime,
  type NotificationMockRealtime,
} from '../../features/notification/realtime/notificationRealtime.mock'

const router = useRouter()
const authStore = useAuthStore()
const notificationStore = useNotificationStore()
const { hasAnyPermission, hasPermission } = usePermission()

/**
 * 管理后台入口所需权限码（数组为「或」逻辑）。
 * 与 router meta 中 /admin 的权限要求（admin:user:list）保持一致，
 * 额外带上 admin:role:list，使仅具备角色管理权限的账号也能看到入口。
 */
const ADMIN_MENU_PERMISSIONS: string[] = ['admin:user:list', 'admin:role:list']

/** 右上角展示名：优先昵称，其次用户名 */
const displayName = computed(() => authStore.nickname || authStore.username || '未登录')

/** 退出请求进行中标记，防止重复点击 */
const loggingOut = ref(false)

/** 通知抽屉显隐：由铃铛打开，抽屉自己负责拉列表 */
const drawerVisible = ref(false)

/** Mock 实时推送实例：只在开发环境 + 开关打开时创建，卸载时必须 stop */
let mockRealtime: NotificationMockRealtime | null = null
/** 真实事件的解绑函数（当前由单连接 owner 往 window 事件里投递，本组件不建连） */
let unbindRealtime: (() => void) | null = null

/**
 * 进入主布局只做三件事：
 * 1. 拉一次未读数（契约 7.1，不轮询）；
 * 2. 开了通知 Mock 就启动模拟推送；
 * 3. 订阅全局实时事件源，为"聊天连接提升为全局连接"留好入口——这里不新建第二条 WebSocket。
 */
onMounted(() => {
  if (!hasPermission(PERMISSIONS.NOTIFICATION_VIEW)) return

  void notificationStore.loadUnreadCount()

  unbindRealtime = bindNotificationRealtime(createWindowRealtimeSource(), {
    onCreated: (event) => notificationStore.applyNotificationCreated(event),
  })

  if (isNotificationMockEnabled()) {
    mockRealtime = createNotificationMockRealtime({
      onCreated: (event) => notificationStore.applyNotificationCreated(event),
      nextUnreadCount: () => notificationStore.unreadCount + 1,
    })
    mockRealtime.start()
  }
})

onBeforeUnmount(() => {
  mockRealtime?.stop()
  mockRealtime = null
  unbindRealtime?.()
  unbindRealtime = null
})

/** 退出登录：调 /auth/logout，无论成败都清本地登录态，然后回登录页 */
async function handleLogout(): Promise<void> {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await authStore.logout()
  } finally {
    loggingOut.value = false
  }
  // 换账号必须清通知，否则角标会带着上一个人的未读数进登录页
  notificationStore.reset()
  await router.replace({ name: 'Login' })
}
</script>

<style scoped>
.layout {
  height: 100vh;
}

.layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--el-border-color-light);
}

.layout__title {
  font-size: 16px;
  font-weight: 600;
}

.layout__user {
  display: flex;
  align-items: center;
  gap: 12px;
}

.layout__user-name {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-text-color-regular);
}

/**
 * 窄屏（≤768）：侧栏 220px 会把内容挤成一条，改成顶部横向菜单。
 * 只调整壳层布局，不动任何业务组件。
 */
@media (max-width: 768px) {
  .layout {
    flex-direction: column !important;
  }

  .layout > .el-aside {
    width: 100% !important;
    height: auto;
    flex: none;
    border-bottom: 1px solid var(--el-border-color-light);
  }

  .layout > .el-aside :deep(.el-menu) {
    display: flex;
    flex-direction: row;
    overflow-x: auto;
    border-right: none;
  }

  .layout > .el-aside :deep(.el-menu-item) {
    flex: none;
    height: 44px;
    line-height: 44px;
  }

  .layout__title {
    font-size: 14px;
  }

  .layout__user {
    gap: 8px;
  }
}

@media (max-width: 480px) {
  .layout__user-name {
    display: none;
  }

  .layout :deep(.el-header) {
    padding: 0 12px;
  }
}
</style>
