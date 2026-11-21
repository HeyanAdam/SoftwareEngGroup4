<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAdminAuthStore } from '../../../app/stores/adminAuth'

const router = useRouter()
const route = useRoute()
const adminAuthStore = useAdminAuthStore()

const COLLAPSE_STORAGE_KEY = 'admin_sidebar_collapsed'

/** 侧栏折叠：刷新后从 localStorage 恢复 */
const collapsed = ref(localStorage.getItem(COLLAPSE_STORAGE_KEY) === '1')
watch(collapsed, (value) => {
  localStorage.setItem(COLLAPSE_STORAGE_KEY, value ? '1' : '0')
})

interface AdminMenuItem {
  path: string
  label: string
  permission: string
}

/** 侧栏菜单：显隐只按权限码，不看角色名 */
const MENU_ITEMS: AdminMenuItem[] = [
  { path: '/admin/dashboard', label: '运营概览', permission: 'admin:user:list' },
  { path: '/admin/users', label: '用户管理', permission: 'admin:user:list' },
  { path: '/admin/roles', label: '角色权限', permission: 'admin:role:list' },
  { path: '/admin/permissions', label: '权限字典', permission: 'admin:role:list' },
  { path: '/admin/logs', label: '登录日志', permission: 'admin:user:list' },
]

const visibleMenuItems = computed(() =>
  MENU_ITEMS.filter((item) => adminAuthStore.hasPermission(item.permission)),
)

/** 面包屑：后台 / 当前菜单名，未匹配菜单时退回路由 path */
const currentTitle = computed(() => {
  const item = MENU_ITEMS.find((menu) => route.path.startsWith(menu.path))
  return item?.label ?? (route.name ? String(route.name) : '')
})

const displayName = computed(
  () => adminAuthStore.nickname || adminAuthStore.username || '管理员',
)

const loggingOut = ref(false)

/** 退出后台：只清 sa_admin_token，回后台登录页，不影响学生端会话 */
async function handleLogout(): Promise<void> {
  if (loggingOut.value) return
  try {
    await ElMessageBox.confirm('退出后需要重新登录后台，确定退出？', '退出后台', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }

  loggingOut.value = true
  try {
    await adminAuthStore.logout()
  } finally {
    loggingOut.value = false
  }
  ElMessage.success('已退出后台')
  void router.replace({ name: 'AdminLogin' })
}
</script>

<template>
  <el-container class="admin-layout">
    <el-header class="admin-layout__header" height="56px">
      <div class="admin-layout__bar">
        <button
          class="admin-layout__toggle"
          type="button"
          :title="collapsed ? '展开菜单' : '收起菜单'"
          @click="collapsed = !collapsed"
        >
          {{ collapsed ? '»' : '«' }}
        </button>

        <div class="admin-layout__brand">
          <span class="admin-layout__brand-dot"></span>
          <span class="admin-layout__brand-name">考研 AI 导学系统</span>
          <span class="admin-layout__brand-tag">后台管理</span>
        </div>

        <el-breadcrumb class="admin-layout__crumb" separator="/">
          <el-breadcrumb-item>后台</el-breadcrumb-item>
          <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
        </el-breadcrumb>

        <div class="admin-layout__user">
          <span class="admin-layout__user-name" :title="displayName">{{ displayName }}</span>
          <el-button link @click="router.push({ name: 'Dashboard' })">返回学生端</el-button>
          <el-button link type="danger" :loading="loggingOut" @click="handleLogout">
            退出后台
          </el-button>
        </div>
      </div>
    </el-header>

    <el-container class="admin-layout__body">
      <el-aside
        class="admin-layout__aside"
        :width="collapsed ? '64px' : '180px'"
      >
        <el-menu
          class="admin-layout__menu"
          router
          :default-active="route.path"
          :collapse="collapsed"
          :collapse-transition="false"
        >
          <el-menu-item
            v-for="item in visibleMenuItems"
            :key="item.path"
            :index="item.path"
          >
            <span class="admin-layout__menu-dot"></span>
            <template #title>
              <span class="admin-layout__menu-label">{{ item.label }}</span>
            </template>
          </el-menu-item>
        </el-menu>

        <div v-if="!collapsed" class="admin-layout__aside-tip">
          仅展示当前账号有权限的菜单
        </div>
      </el-aside>

      <el-main class="admin-layout__main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-layout {
  --admin-deep: #1f4d3d;
  --admin-mid: #2c6a52;
  min-height: 100vh;
  background: #f4f6f5;
}

.admin-layout__header {
  padding: 0;
  background: var(--admin-deep);
  color: #fff;
  box-shadow: 0 1px 4px rgba(15, 40, 30, 0.16);
  z-index: 10;
}

.admin-layout__bar {
  display: flex;
  align-items: center;
  gap: 16px;
  height: 100%;
  padding: 0 16px;
}

.admin-layout__toggle {
  width: 28px;
  height: 28px;
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: 4px;
  background: transparent;
  color: rgba(255, 255, 255, 0.85);
  cursor: pointer;
  line-height: 1;
}

.admin-layout__toggle:hover {
  background: rgba(255, 255, 255, 0.1);
}

.admin-layout__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.admin-layout__brand-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #7fb69c;
}

.admin-layout__brand-name {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.admin-layout__brand-tag {
  padding: 1px 8px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 10px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.75);
}

.admin-layout__crumb {
  flex: 1 1 auto;
  min-width: 0;
}

.admin-layout__crumb :deep(.el-breadcrumb__inner),
.admin-layout__crumb :deep(.el-breadcrumb__separator) {
  color: rgba(255, 255, 255, 0.62);
}

.admin-layout__crumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: #fff;
  font-weight: 500;
}

.admin-layout__user {
  display: flex;
  align-items: center;
  gap: 10px;
  white-space: nowrap;
}

.admin-layout__user-name {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  color: rgba(255, 255, 255, 0.86);
}

.admin-layout__user :deep(.el-button.is-link) {
  color: rgba(255, 255, 255, 0.78);
}

.admin-layout__user :deep(.el-button.is-link.el-button--danger) {
  color: #f2a6a0;
}

.admin-layout__body {
  min-height: calc(100vh - 56px);
}

.admin-layout__aside {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  border-right: 1px solid var(--el-border-color-light);
  background: #fff;
  transition: width 0.2s ease;
  overflow: hidden;
}

.admin-layout__menu {
  border-right: none;
}

.admin-layout__menu-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 10px;
  border-radius: 2px;
  background: #b7c6bf;
}

.admin-layout__menu :deep(.el-menu-item.is-active) {
  background: #eef4f1;
  color: var(--admin-mid);
}

.admin-layout__menu :deep(.el-menu-item.is-active) .admin-layout__menu-dot {
  background: var(--admin-mid);
}

.admin-layout__menu-label {
  font-size: 14px;
}

.admin-layout__aside-tip {
  padding: 12px 16px 20px;
  font-size: 11px;
  line-height: 1.7;
  color: #a5b1ab;
}

.admin-layout__main {
  padding: 20px 16px 32px;
}
</style>

