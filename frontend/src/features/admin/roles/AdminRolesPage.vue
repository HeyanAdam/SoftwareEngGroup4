<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminPageHeader from '../layout/components/AdminPageHeader.vue'
import {
  getRolePermissionCodes,
  listPermissions,
  listRoleSummaries,
  removeRole,
  saveRolePermissions,
} from '../api/admin.api'
import type { AdminRoleSummary, SysPermission } from '../api/admin.types'
import { ApiError } from '../../../shared/api/request'
import { useAdminAuthStore } from '../../../app/stores/adminAuth'
import RoleListPanel from './components/RoleListPanel.vue'
import PermissionMatrix from './components/PermissionMatrix.vue'
import RoleFormDialog from './components/RoleFormDialog.vue'
import {
  isSameSelection,
  permissionCodesToIds,
  unmatchedPermissionCodes,
} from './components/permissionModules'
import { isSystemAdminRole } from './components/roleMeta'

const adminAuthStore = useAdminAuthStore()

/** 查看需要 admin:role:list（路由已校验）；保存与角色增删改需要 admin:role:update */
const canUpdate = computed(() => adminAuthStore.hasPermission('admin:role:update'))

const roles = ref<AdminRoleSummary[]>([])
const permissions = ref<SysPermission[]>([])
const activeId = ref<number | null>(null)
const selectedIds = ref<number[]>([])
/** 服务端当前授权（保存成功或重置后更新），用于判定未保存 */
const baselineIds = ref<number[]>([])

const loading = ref(false)
const roleLoading = ref(false)
const saving = ref(false)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingRole = ref<AdminRoleSummary | null>(null)

const activeRole = computed(() => roles.value.find((role) => role.id === activeId.value) ?? null)
const isSystemAdmin = computed(() => isSystemAdminRole(activeRole.value?.roleCode ?? ''))
const isReadonly = computed(() => !canUpdate.value || isSystemAdmin.value)
const dirty = computed(
  () => !isSystemAdmin.value && !isSameSelection(selectedIds.value, baselineIds.value),
)

/** 全部权限 ID（系统管理员全选展示用） */
function allPermissionIds(): number[] {
  return permissions.value.map((permission) => permission.id).sort((a, b) => a - b)
}

/** 加载角色汇总 → 权限字典 → 第一个可读角色的权限码（顺序与任务文档一致） */
async function loadAll(): Promise<void> {
  loading.value = true
  try {
    roles.value = (await listRoleSummaries()) ?? []
    permissions.value = (await listPermissions()) ?? []

    if (roles.value.length === 0) {
      activeId.value = null
      selectedIds.value = []
      baselineIds.value = []
      return
    }

    const first = roles.value[0]
    await selectRole(first, true)
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '角色权限加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

/** 拉取角色当前授权：GET 返回权限码，映射成权限 ID；返回是否成功 */
async function fetchRolePermissions(roleId: number): Promise<boolean> {
  roleLoading.value = true
  try {
    const codes = (await getRolePermissionCodes(roleId)) ?? []
    const unknown = unmatchedPermissionCodes(codes, permissions.value)
    if (unknown.length > 0) {
      ElMessage.warning(`以下权限码不在权限字典中，已跳过：${unknown.join('、')}`)
    }
    const ids = permissionCodesToIds(codes, permissions.value)
    activeId.value = roleId
    selectedIds.value = ids
    baselineIds.value = [...ids]
    return true
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '角色授权加载失败，请稍后重试')
    return false
  } finally {
    roleLoading.value = false
  }
}

/** 切换角色：存在未保存修改时先确认，取消则保持当前角色 */
async function selectRole(role: AdminRoleSummary, skipConfirm = false): Promise<void> {
  if (role.id === activeId.value && !skipConfirm) return

  if (!skipConfirm && dirty.value) {
    try {
      await ElMessageBox.confirm('当前修改未保存，是否放弃？', '未保存的修改', {
        confirmButtonText: '放弃修改',
        cancelButtonText: '继续编辑',
        type: 'warning',
      })
    } catch {
      return
    }
  }

  if (isSystemAdminRole(role.roleCode)) {
    activeId.value = role.id
    selectedIds.value = allPermissionIds()
    baselineIds.value = [...selectedIds.value]
    return
  }

  await fetchRolePermissions(role.id)
}

/** 保存：PUT 接收权限主键数组 */
async function handleSave(): Promise<void> {
  if (!activeId.value || saving.value || isReadonly.value) return
  saving.value = true
  try {
    await saveRolePermissions(activeId.value, [...selectedIds.value].sort((a, b) => a - b))
    baselineIds.value = [...selectedIds.value]
    if (activeRole.value) activeRole.value.permissionCount = selectedIds.value.length
    ElMessage.success('保存成功，相关账号下一次请求即生效')
  } catch (error) {
    if (error instanceof ApiError) {
      ElMessage.error(
        error.code === 10008
          ? '当前账号没有授权权限（10008），请联系系统管理员'
          : error.message,
      )
    } else {
      ElMessage.error('保存失败，请稍后重试')
    }
  } finally {
    saving.value = false
  }
}

/** 重置：重新拉取该角色权限码，恢复服务端当前选择 */
async function handleReset(): Promise<void> {
  if (!activeId.value || isSystemAdmin.value) return
  const restored = await fetchRolePermissions(activeId.value)
  if (restored) ElMessage.success('已恢复为服务端当前授权')
}

function handleSelectionChange(ids: number[]): void {
  if (isReadonly.value) return
  selectedIds.value = ids
}

function openCreate(): void {
  dialogMode.value = 'create'
  editingRole.value = null
  dialogVisible.value = true
}

function openEdit(role: AdminRoleSummary): void {
  dialogMode.value = 'edit'
  editingRole.value = role
  dialogVisible.value = true
}

/** 新建成功直接并入列表并选中；编辑成功只更新本地名称与描述 */
async function handleRoleSaved(role: AdminRoleSummary | null): Promise<void> {
  if (!role) {
    await loadAll()
    return
  }

  if (dialogMode.value === 'edit') {
    const target = roles.value.find((item) => item.id === role.id)
    if (target) {
      target.roleName = role.roleName
      target.description = role.description
    }
    return
  }

  if (!roles.value.some((item) => item.id === role.id)) {
    roles.value = [...roles.value, role].sort((a, b) => a.id - b.id)
  }
  await selectRole(role, true)
}

/** 删除：二次确认，内置角色没有删除入口；有用户绑定时原样展示后端错误 */
async function handleRemove(role: AdminRoleSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除角色「${role.roleName}」？该角色已配置的授权会一并清理。`,
      '删除角色',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  try {
    await removeRole(role.id)
    ElMessage.success('角色已删除')
    roles.value = roles.value.filter((item) => item.id !== role.id)

    if (activeId.value === role.id) {
      const nextRole = roles.value[0]
      if (nextRole) await selectRole(nextRole, true)
      else {
        activeId.value = null
        selectedIds.value = []
        baselineIds.value = []
      }
    }
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '删除失败，请稍后重试')
  }
}

/** 刷新：有未保存修改时先确认 */
async function handleRefresh(): Promise<void> {
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('当前修改未保存，刷新会丢弃修改，是否继续？', '刷新', {
        confirmButtonText: '丢弃并刷新',
        cancelButtonText: '取消',
        type: 'warning',
      })
    } catch {
      return
    }
  }
  await loadAll()
}

onMounted(() => {
  void loadAll()
})
</script>

<template>
  <div class="admin-roles">
    <AdminPageHeader
      title="角色权限"
      description="按权限码前缀分组授权；保存后服务端会失效该角色相关账号的权限快照，下一次请求即生效。"
    >
      <template #actions>
        <el-button :loading="loading" @click="handleRefresh">刷新</el-button>
      </template>
    </AdminPageHeader>

    <div v-loading="loading" class="admin-roles__layout">
      <RoleListPanel
        class="admin-roles__panel"
        :roles="roles"
        :active-id="activeId"
        :can-update="canUpdate"
        :loading="roleLoading"
        @select="selectRole($event)"
        @create="openCreate"
        @edit="openEdit"
        @remove="handleRemove"
      />

      <section
        v-loading="roleLoading"
        class="admin-roles__matrix"
        :class="{ 'is-empty': !activeRole }"
      >
        <div v-if="!activeRole" class="admin-roles__placeholder">
          <el-empty description="请选择左侧角色查看授权" :image-size="96" />
        </div>

        <template v-else>
          <div class="admin-roles__matrix-head">
            <h3 class="admin-roles__matrix-title">
              {{ activeRole.roleName }}
              <code class="admin-roles__matrix-code">{{ activeRole.roleCode }}</code>
            </h3>
            <span v-if="isSystemAdmin" class="admin-roles__badge is-lock">只读 · 全权角色</span>
            <span v-else-if="!canUpdate" class="admin-roles__badge is-readonly">只读</span>
            <span v-else-if="dirty" class="admin-roles__badge is-dirty">有未保存修改</span>
          </div>

          <div v-if="isSystemAdmin" class="admin-roles__notice">
            全权角色由系统自动补齐全部权限（共 {{ permissions.length }} 项），此处只读展示，不提供保存。
          </div>

          <PermissionMatrix
            :permissions="permissions"
            :selected-ids="selectedIds"
            :readonly="isReadonly"
            @update:selected-ids="handleSelectionChange"
          />
        </template>
      </section>

      <aside class="admin-roles__summary">
        <h3 class="admin-roles__summary-title">授权摘要</h3>

        <dl class="admin-roles__summary-list">
          <div>
            <dt>当前角色</dt>
            <dd>{{ activeRole?.roleName ?? '未选择' }}</dd>
          </div>
          <div>
            <dt>已选权限</dt>
            <dd>
              <strong class="admin-roles__selected">{{ selectedIds.length }}</strong>
              <span class="admin-roles__total"> / {{ permissions.length }}</span>
            </dd>
          </div>
          <div>
            <dt>绑定人数</dt>
            <dd>{{ activeRole?.userCount ?? '—' }}</dd>
          </div>
          <div>
            <dt>授权状态</dt>
            <dd>
              <el-tag v-if="dirty" size="small" type="warning" disable-transitions>未保存</el-tag>
              <el-tag v-else-if="activeRole" size="small" type="success" disable-transitions>已同步</el-tag>
              <span v-else class="admin-roles__muted">—</span>
            </dd>
          </div>
        </dl>

        <div v-if="isSystemAdmin" class="admin-roles__summary-tip">
          系统管理员由系统自动补齐全部权限，任何修改都不会被写回，避免被误改。
        </div>
        <div v-else-if="!canUpdate" class="admin-roles__summary-tip">
          当前账号只有 admin:role:list，可查看角色与授权，保存和角色增删改已隐藏。
        </div>
        <div v-else class="admin-roles__summary-actions">
          <el-button
            type="primary"
            :loading="saving"
            :disabled="!activeRole || !dirty"
            @click="handleSave"
          >
            保存授权
          </el-button>
          <el-button :disabled="!activeRole" @click="handleReset">重置</el-button>
        </div>

        <p class="admin-roles__summary-note">
          权限数以服务端字典为准；保存成功会同步本页权限数与角色卡片计数。
        </p>
      </aside>
    </div>

    <RoleFormDialog
      v-model="dialogVisible"
      :mode="dialogMode"
      :role="editingRole"
      @saved="handleRoleSaved"
    />
  </div>
</template>

<style scoped>
.admin-roles {
  --admin-panel-gap: 14px;
}

.admin-roles__layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr) 216px;
  gap: var(--admin-panel-gap);
  align-items: start;
  min-height: 520px;
}

.admin-roles__panel {
  position: sticky;
  top: 12px;
  max-height: calc(100vh - 200px);
  min-height: 420px;
}

.admin-roles__matrix {
  min-height: 420px;
  padding: 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: #fff;
}

.admin-roles__matrix.is-empty {
  display: flex;
  align-items: center;
  justify-content: center;
}

.admin-roles__placeholder {
  width: 100%;
}

.admin-roles__matrix-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.admin-roles__matrix-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #1f2b26;
}

.admin-roles__matrix-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  font-weight: 400;
  color: #9aa8a1;
}

.admin-roles__badge {
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 11px;
  line-height: 18px;
}

.admin-roles__badge.is-dirty {
  background: #fdf3e6;
  color: #a06a29;
}

.admin-roles__badge.is-readonly {
  background: #f0f2f3;
  color: #6b7a80;
}

.admin-roles__badge.is-lock {
  background: #eef4f1;
  color: #2c6a52;
}

.admin-roles__notice {
  margin-bottom: 12px;
  padding: 8px 10px;
  border: 1px solid #dfece4;
  border-radius: 6px;
  background: #f5faf7;
  font-size: 12px;
  line-height: 1.7;
  color: #3f6b57;
}

.admin-roles__summary {
  position: sticky;
  top: 12px;
  padding: 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: #fff;
}

.admin-roles__summary-title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2b26;
}

.admin-roles__summary-list {
  margin: 0;
  font-size: 12px;
  color: #64756d;
}

.admin-roles__summary-list > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.admin-roles__summary-list dt {
  flex: 0 0 auto;
}

.admin-roles__summary-list dd {
  margin: 0;
  overflow: hidden;
  color: #33413b;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-roles__selected {
  font-size: 18px;
  font-weight: 600;
  color: #2c6a52;
}

.admin-roles__total {
  color: #93a09a;
}

.admin-roles__muted {
  color: #b4bfba;
}

.admin-roles__summary-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 14px;
}

.admin-roles__summary-actions :deep(.el-button) {
  width: 100%;
  margin-left: 0;
}

.admin-roles__summary-tip {
  margin-top: 14px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f7f9f8;
  font-size: 11px;
  line-height: 1.7;
  color: #8a9a93;
}

.admin-roles__summary-note {
  margin: 12px 0 0;
  font-size: 11px;
  line-height: 1.6;
  color: #a5b1ab;
}

@media (max-width: 1280px) {
  .admin-roles__layout {
    grid-template-columns: 232px minmax(0, 1fr);
  }

  .admin-roles__summary {
    position: static;
    grid-column: 1 / -1;
  }

  .admin-roles__summary-actions {
    flex-direction: row;
  }

  .admin-roles__summary-actions :deep(.el-button) {
    width: auto;
  }
}

@media (max-width: 1024px) {
  .admin-roles__layout {
    grid-template-columns: 1fr;
  }

  .admin-roles__panel {
    position: static;
    max-height: 320px;
  }
}
</style>
