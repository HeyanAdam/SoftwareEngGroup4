<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AdminPageHeader from '../layout/components/AdminPageHeader.vue'
import { listPermissions } from '../api/admin.api'
import type { SysPermission } from '../api/admin.types'
import { ApiError } from '../../../shared/api/request'
import {
  groupPermissions,
  moduleOfPermission,
} from '../roles/components/permissionModules'

const permissions = ref<SysPermission[]>([])
const loading = ref(false)
const keyword = ref('')

/** 只读行：模块顺序 + 模块内 sort/id 顺序，和后端字典一致 */
const rows = computed<SysPermission[]>(() =>
  groupPermissions(permissions.value).flatMap((group) => group.permissions),
)

const filteredRows = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) return rows.value
  return rows.value.filter(
    (permission) =>
      permission.permCode.toLowerCase().includes(query) ||
      permission.permName.toLowerCase().includes(query) ||
      moduleOfPermission(permission.permCode).includes(keyword.value.trim()),
  )
})

/** 统计口径：全量字典，不随筛选变化 */
const moduleCount = computed(
  () => new Set(permissions.value.map((permission) => moduleOfPermission(permission.permCode))).size,
)

function resetKeyword(): void {
  keyword.value = ''
}

async function loadPermissions(): Promise<void> {
  loading.value = true
  try {
    permissions.value = (await listPermissions()) ?? []
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '权限字典加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadPermissions()
})
</script>

<template>
  <div class="admin-permissions">
    <AdminPageHeader
      title="权限字典"
      description="系统全部权限码的只读清单，模块按权限码前缀归类，不使用后端历史排序分组。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadPermissions">刷新</el-button>
      </template>
    </AdminPageHeader>

    <div class="admin-permissions__stats">
      <div class="admin-permissions__stat">
        <span class="admin-permissions__stat-value">{{ permissions.length }}</span>
        <span class="admin-permissions__stat-label">条权限码</span>
      </div>
      <div class="admin-permissions__stat">
        <span class="admin-permissions__stat-value">{{ moduleCount }}</span>
        <span class="admin-permissions__stat-label">个模块</span>
      </div>
      <div class="admin-permissions__search">
        <el-input
          v-model="keyword"
          placeholder="搜索权限码 / 名称 / 模块"
          clearable
          @clear="resetKeyword"
        />
      </div>
    </div>

    <el-table
      v-loading="loading"
      :data="filteredRows"
      row-key="id"
      border
      stripe
      class="admin-permissions__table"
    >
      <el-table-column label="模块" width="120">
        <template #default="{ row }">
          <el-tag size="small" type="info" disable-transitions>
            {{ moduleOfPermission(row.permCode) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="permCode" label="权限码" min-width="200">
        <template #default="{ row }">
          <code class="admin-permissions__code">{{ row.permCode }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="permName" label="权限名称" min-width="160" />
      <el-table-column prop="permType" label="权限类型" width="110" />
      <el-table-column prop="sort" label="排序号" width="96" align="right" />
      <template #empty>
        <el-empty description="没有匹配的权限码" :image-size="80" />
      </template>
    </el-table>

    <p class="admin-permissions__foot">
      <span v-if="keyword.trim()">筛选出 {{ filteredRows.length }} 条 / </span>
      共 {{ permissions.length }} 条权限码，{{ moduleCount }} 个模块
    </p>
  </div>
</template>

<style scoped>
.admin-permissions__stats {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.admin-permissions__stat {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: #fff;
}

.admin-permissions__stat-value {
  font-size: 22px;
  font-weight: 600;
  color: #2c6a52;
}

.admin-permissions__stat-label {
  font-size: 12px;
  color: #64756d;
}

.admin-permissions__search {
  width: 260px;
  margin-left: auto;
}

.admin-permissions__table {
  width: 100%;
  background: #fff;
}

.admin-permissions__code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  color: #33413b;
}

.admin-permissions__foot {
  margin: 12px 0 0;
  font-size: 12px;
  color: #93a09a;
}

@media (max-width: 1024px) {
  .admin-permissions__search {
    width: 100%;
    margin-left: 0;
  }
}
</style>
