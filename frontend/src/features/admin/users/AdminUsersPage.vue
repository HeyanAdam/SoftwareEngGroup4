<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AdminPageHeader from '../layout/components/AdminPageHeader.vue'
import LazyGate from '../perf/LazyGate.vue'
import { createLazyComponent } from '../perf/lazyComponent'
import { listRoles, listUsers, updateUserStatus } from '../api/admin.api'
import type { AdminRole, AdminUserVO } from '../api/types'
import { useAdminAuthStore } from '../../../app/stores/adminAuth'
import { ApiError } from '../../../shared/api/request'
import { buildUserQuery, roleLabel, toggleUserStatus, USER_STATUS } from './userActions'

const adminAuthStore = useAdminAuthStore()

/** 修改（启停、分配角色）需要 admin:user:update；只读角色只有 list 权限 */
const canUpdate = computed(() => adminAuthStore.hasPermission('admin:user:update'))

const keyword = ref('')
const statusFilter = ref<string | number>('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const users = ref<AdminUserVO[]>([])
const roleDictionary = ref<AdminRole[]>([])
const statusLoading = reactive<Record<number, boolean>>({})

const drawerVisible = ref(false)
const activeUser = ref<AdminUserVO | null>(null)

/** 角色抽屉只在需要时加载；首屏不发该 chunk 请求 */
const roleDrawer = createLazyComponent(() => import('./components/AssignRoleDrawer.vue'))

/** 分页 + 筛选拉取用户列表 */
async function loadUsers(): Promise<void> {
  loading.value = true
  try {
    const data = await listUsers(
      buildUserQuery({
        keyword: keyword.value,
        status: statusFilter.value,
        pageNum: pageNum.value,
        pageSize: pageSize.value,
      }),
    )
    users.value = data?.list ?? []
    total.value = data?.total ?? 0
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '用户列表加载失败')
  } finally {
    loading.value = false
  }
}

/** 角色字典只用于把角色码翻成中文名；无 admin:role:list 时静默降级为内置文案 */
async function loadRoleDictionary(): Promise<void> {
  if (!adminAuthStore.hasPermission('admin:role:list')) return
  try {
    roleDictionary.value = (await listRoles()) ?? []
  } catch {
    roleDictionary.value = []
  }
}

function handleSearch(): void {
  pageNum.value = 1
  void loadUsers()
}

function handleReset(): void {
  keyword.value = ''
  statusFilter.value = ''
  pageNum.value = 1
  void loadUsers()
}

function handlePageChange(page: number): void {
  pageNum.value = page
  void loadUsers()
}

function handleSizeChange(size: number): void {
  pageSize.value = size
  pageNum.value = 1
  void loadUsers()
}

/** 状态开关：成功才落库到 UI，失败保留原状态（toggleUserStatus 内部已回滚） */
async function beforeStatusChange(row: AdminUserVO): Promise<boolean> {
  statusLoading[row.user_id] = true
  try {
    const result = await toggleUserStatus(row, updateUserStatus)
    if (result.ok) {
      ElMessage.success(
        result.status === USER_STATUS.ACTIVE
          ? `已启用 ${row.username}`
          : `已禁用 ${row.username}`,
      )
      return true
    }
    ElMessage.error(
      result.error instanceof ApiError ? result.error.message : '状态更新失败，已恢复原状态',
    )
    return false
  } finally {
    statusLoading[row.user_id] = false
  }
}

/** 只读账号没有修改权限：不加载抽屉组件，也不打开抽屉 */
function openRoleDrawer(row: AdminUserVO): void {
  if (!canUpdate.value) return
  activeUser.value = row
  drawerVisible.value = true
  void roleDrawer.load()
}

/** 悬停 / 键盘聚焦按钮时预加载，点击仍保留完整加载流程；失败不影响点击后重试 */
function preloadRoleDrawer(): void {
  if (!canUpdate.value) return
  roleDrawer.preload()
}

/** 抽屉保存成功后刷新该行角色标签，并把新角色并入角色字典 */
function handleRolesSaved(roles: AdminRole[]): void {
  if (!activeUser.value) return
  const codes = roles.map((role) => role.roleCode)
  const row = users.value.find((user) => user.user_id === activeUser.value?.user_id)
  if (row) row.roles = codes

  const merged = new Map(roleDictionary.value.map((role) => [role.id, role]))
  roles.forEach((role) => merged.set(role.id, role))
  roleDictionary.value = [...merged.values()]
}

onMounted(() => {
  void loadUsers()
  void loadRoleDictionary()
})
</script>

<template>
  <div class="admin-users">
    <AdminPageHeader
      title="用户管理"
      description="查看账号状态、分配角色。启用状态与角色绑定均以服务端为准。"
    />

    <div class="admin-users__filters">
      <el-input
        v-model="keyword"
        class="admin-users__keyword"
        placeholder="用户名 / 昵称 / 邮箱关键词"
        clearable
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-select v-model="statusFilter" class="admin-users__status" placeholder="全部状态">
        <el-option label="全部状态" value="" />
        <el-option label="正常" :value="USER_STATUS.ACTIVE" />
        <el-option label="禁用" :value="USER_STATUS.DISABLED" />
      </el-select>
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
      <span v-if="!canUpdate" class="admin-users__readonly">
        当前账号为只读权限，可查看不可修改
      </span>
    </div>

    <el-table v-loading="loading" :data="users" border stripe class="admin-users__table">
      <el-table-column prop="user_id" label="用户 ID" width="68" />
      <el-table-column prop="username" label="用户名" min-width="96" />
      <el-table-column prop="nickname" label="昵称" min-width="88" show-overflow-tooltip>
        <template #default="{ row }">{{ row.nickname || '—' }}</template>
      </el-table-column>
      <el-table-column prop="email" label="脱敏邮箱" min-width="126">
        <template #default="{ row }">{{ row.email || '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="92">
        <template #default="{ row }">
          <div class="admin-users__status-cell">
            <el-switch
              :model-value="row.status === USER_STATUS.ACTIVE"
              :loading="!!statusLoading[row.user_id]"
              :disabled="!canUpdate"
              :before-change="() => beforeStatusChange(row)"
            />
            <span
              class="admin-users__status-text"
              :class="row.status === USER_STATUS.ACTIVE ? 'is-active' : 'is-disabled'"
            >
              {{ row.status === USER_STATUS.ACTIVE ? '正常' : '禁用' }}
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="110">
        <template #default="{ row }">
          <div v-if="row.roles && row.roles.length" class="admin-users__roles">
            <el-tag
              v-for="code in row.roles"
              :key="code"
              size="small"
              disable-transitions
              :type="code === 'SYSTEM_ADMIN' ? 'success' : 'info'"
            >
              {{ roleLabel(code, roleDictionary) }}
            </el-tag>
          </div>
          <span v-else class="admin-users__empty-cell">未分配</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="92" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="canUpdate"
            link
            type="primary"
            @click="openRoleDrawer(row)"
            @mouseenter="preloadRoleDrawer"
            @focus="preloadRoleDrawer"
          >
            分配角色
          </el-button>
          <span v-else class="admin-users__empty-cell">只读</span>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="没有符合条件的用户" />
      </template>
    </el-table>

    <div class="admin-users__pager">
      <el-pagination
        :current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <LazyGate
      :state="roleDrawer"
      :active="drawerVisible"
      v-model="drawerVisible"
      label="角色分配抽屉"
      variant="overlay"
      :height="200"
      :user="activeUser"
      @saved="handleRolesSaved"
    />
  </div>
</template>

<style scoped>
.admin-users__filters {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.admin-users__keyword {
  width: 260px;
}

.admin-users__status {
  width: 150px;
}

.admin-users__readonly {
  margin-left: auto;
  font-size: 12px;
  color: #a5b1ab;
}

.admin-users__table {
  width: 100%;
}

.admin-users__status-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.admin-users__status-text {
  font-size: 12px;
}

.admin-users__status-text.is-active {
  color: #2c6a52;
}

.admin-users__status-text.is-disabled {
  color: #b07c72;
}

.admin-users__roles {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  width: 100%;
}

.admin-users__roles :deep(.el-tag) {
  flex: 0 0 auto;
  white-space: nowrap;
}

.admin-users__empty-cell {
  color: #b4bfba;
  font-size: 12px;
}

.admin-users__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>


