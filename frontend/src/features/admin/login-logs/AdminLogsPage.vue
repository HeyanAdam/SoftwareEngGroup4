<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AdminPageHeader from '../layout/components/AdminPageHeader.vue'
import { listLoginLogs } from '../api/admin.api'
import type { AdminLoginLog } from '../api/admin.types'
import { ApiError } from '../../../shared/api/request'
import { formatLoginTime, isLoginSuccess } from './logUtils'

const ACCOUNT_MAX = 50
const DEFAULT_LIMIT = 20
const LIMIT_OPTIONS = [10, 20, 50, 100]

const account = ref('')
const limit = ref<number>(DEFAULT_LIMIT)
const logs = ref<AdminLoginLog[]>([])
const loading = ref(false)
/** 上次查询条件，用于表尾说明，避免把未提交的输入当成结果口径 */
const queriedAccount = ref('')
const queriedLimit = ref<number>(DEFAULT_LIMIT)

const failedCount = computed(
  () => logs.value.filter((log) => !isLoginSuccess(log.success)).length,
)

async function loadLogs(): Promise<void> {
  loading.value = true
  const trimmed = account.value.trim()
  const safeLimit = LIMIT_OPTIONS.includes(limit.value) ? limit.value : DEFAULT_LIMIT
  try {
    logs.value = (await listLoginLogs({ account: trimmed || undefined, limit: safeLimit })) ?? []
    queriedAccount.value = trimmed
    queriedLimit.value = safeLimit
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '登录日志加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function handleReset(): void {
  account.value = ''
  limit.value = DEFAULT_LIMIT
  void loadLogs()
}

onMounted(() => {
  void loadLogs()
})
</script>

<template>
  <div class="admin-logs">
    <AdminPageHeader
      title="登录日志"
      description="按账号查询最近的登录记录；成功与失败结果以服务端记录为准。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadLogs">刷新</el-button>
      </template>
    </AdminPageHeader>

    <div class="admin-logs__filters">
      <el-input
        v-model="account"
        class="admin-logs__account"
        :maxlength="ACCOUNT_MAX"
        placeholder="账号（留空查询全部）"
        clearable
        @keyup.enter="loadLogs"
        @clear="loadLogs"
      />
      <el-select v-model="limit" class="admin-logs__limit" placeholder="条数">
        <el-option
          v-for="value in LIMIT_OPTIONS"
          :key="value"
          :label="`最近 ${value} 条`"
          :value="value"
        />
      </el-select>
      <el-button type="primary" :loading="loading" @click="loadLogs">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
      <span class="admin-logs__summary">
        共 {{ logs.length }} 条<span v-if="failedCount > 0">，其中失败 {{ failedCount }} 条</span>
      </span>
    </div>

    <el-table
      v-loading="loading"
      :data="logs"
      row-key="id"
      border
      stripe
      class="admin-logs__table"
    >
      <el-table-column label="登录时间" width="172">
        <template #default="{ row }">
          <span class="admin-logs__time">{{ formatLoginTime(row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="account" label="账号" min-width="130">
        <template #default="{ row }">
          <span class="admin-logs__account-cell">{{ row.account || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="ipAddress" label="IP" width="136">
        <template #default="{ row }">{{ row.ipAddress || '—' }}</template>
      </el-table-column>
      <el-table-column
        prop="userAgent"
        label="User-Agent"
        min-width="220"
        show-overflow-tooltip
      >
        <template #default="{ row }">{{ row.userAgent || '—' }}</template>
      </el-table-column>
      <el-table-column label="结果" width="92" align="center">
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="isLoginSuccess(row.success) ? 'success' : 'danger'"
            disable-transitions
          >
            {{ isLoginSuccess(row.success) ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="失败原因" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ 'admin-logs__reason': !isLoginSuccess(row.success) }">
            {{ row.failureReason || '—' }}
          </span>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty
          :description="queriedAccount ? '该账号暂无登录记录' : '暂无登录日志'"
          :image-size="80"
        />
      </template>
    </el-table>

    <p class="admin-logs__foot">
      <span v-if="queriedAccount">账号「{{ queriedAccount }}」，</span>
      查询最近 {{ queriedLimit }} 条记录（服务端上限以接口为准）
    </p>
  </div>
</template>

<style scoped>
.admin-logs__filters {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.admin-logs__account {
  width: 240px;
}

.admin-logs__limit {
  width: 150px;
}

.admin-logs__summary {
  margin-left: auto;
  font-size: 12px;
  color: #8a9a93;
}

.admin-logs__table {
  width: 100%;
  background: #fff;
}

.admin-logs__time,
.admin-logs__account-cell {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

.admin-logs__reason {
  color: #b07c72;
}

.admin-logs__foot {
  margin: 12px 0 0;
  font-size: 12px;
  color: #93a09a;
}

@media (max-width: 1024px) {
  .admin-logs__account {
    width: 100%;
  }

  .admin-logs__summary {
    margin-left: 0;
  }
}
</style>
