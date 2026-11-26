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
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { usePermission } from '../../shared/utils/permission'
import { STUDY_PLAN_PERMISSIONS } from '../router/access'

const router = useRouter()
const authStore = useAuthStore()
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

/** 退出登录：调 /auth/logout，无论成败都清本地登录态，然后回登录页 */
async function handleLogout(): Promise<void> {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await authStore.logout()
  } finally {
    loggingOut.value = false
  }
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
</style>
