<template>
  <el-drawer
    :model-value="modelValue"
    :title="drawerTitle"
    size="420px"
    destroy-on-close
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
    @open="handleOpen"
  >
    <div v-loading="loading" class="role-drawer">
      <p class="role-drawer__tip">
        勾选后保存，会以当前勾选结果覆盖该用户的角色绑定。
      </p>

      <el-empty v-if="!loading && roles.length === 0" description="暂无可用角色" />

      <el-checkbox-group v-model="selectedIds" class="role-drawer__list">
        <label
          v-for="role in roles"
          :key="role.id"
          class="role-drawer__item"
          :class="{ 'is-selected': selectedIds.includes(role.id) }"
        >
          <el-checkbox :value="role.id" :disabled="role.status === 0">
            <span class="role-drawer__code">{{ role.roleCode }}</span>
            <span class="role-drawer__name">{{ role.roleName }}</span>
          </el-checkbox>
          <span v-if="role.description" class="role-drawer__desc">{{ role.description }}</span>
        </label>
      </el-checkbox-group>

      <div v-if="boundIds.length > 0" class="role-drawer__bound">
        当前已绑定：
        <el-tag
          v-for="role in boundRoles"
          :key="role.id"
          class="role-drawer__bound-tag"
          size="small"
          type="info"
          disable-transitions
        >
          {{ role.roleName }}
        </el-tag>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ApiError } from '../../../../shared/api/request'
import { getUserRoles, listRoles, updateUserRoles } from '../../api/admin.api'
import type { AdminRole, AdminUserVO } from '../../api/types'
import { saveUserRolesWithRefresh } from '../userActions'

const props = defineProps<{
  modelValue: boolean
  user: AdminUserVO | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved', roles: AdminRole[]): void
}>()

const roles = ref<AdminRole[]>([])
const boundRoles = ref<AdminRole[]>([])
const selectedIds = ref<number[]>([])
const loading = ref(false)
const saving = ref(false)

const drawerTitle = computed(() =>
  props.user ? `分配角色 · ${props.user.username}` : '分配角色',
)

const boundIds = computed(() => boundRoles.value.map((role) => role.id))

/** 打开抽屉：并行拉全部角色与该用户当前绑定，已绑定项预勾选 */
async function handleOpen(): Promise<void> {
  if (!props.user) return
  loading.value = true
  try {
    const [allRoles, current] = await Promise.all([
      listRoles(),
      getUserRoles(props.user.user_id),
    ])
    roles.value = allRoles ?? []
    boundRoles.value = current ?? []
    selectedIds.value = boundRoles.value.map((role) => role.id)
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '角色加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

/** 保存：成功后才刷新表格标签；失败原样展示后端文案（如最后一个 SYSTEM_ADMIN 保护） */
async function handleSave(): Promise<void> {
  if (!props.user || saving.value) return
  saving.value = true
  try {
    const refreshed = await saveUserRolesWithRefresh({
      userId: props.user.user_id,
      ids: [...selectedIds.value],
      update: updateUserRoles,
      reload: getUserRoles,
    })
    boundRoles.value = refreshed
    emit('saved', refreshed)
    ElMessage.success('角色已更新')
    emit('update:modelValue', false)
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '角色保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.role-drawer {
  min-height: 120px;
}

.role-drawer__tip {
  margin: 0 0 16px;
  font-size: 12px;
  line-height: 1.7;
  color: #8a9a93;
}

.role-drawer__list {
  display: grid;
  gap: 10px;
}

.role-drawer__item {
  display: block;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  background: #fafcfb;
  cursor: pointer;
}

.role-drawer__item.is-selected {
  border-color: var(--el-color-primary-light-3);
  background: #f2f8f5;
}

.role-drawer__code {
  margin-left: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  color: #8a9a93;
}

.role-drawer__name {
  margin-left: 6px;
  font-size: 14px;
  color: #1f2b26;
}

.role-drawer__desc {
  display: block;
  margin: 4px 0 0 24px;
  font-size: 12px;
  color: #93a09a;
}

.role-drawer__bound {
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px dashed var(--el-border-color-light);
  font-size: 12px;
  color: #8a9a93;
}

.role-drawer__bound-tag {
  margin: 0 4px;
}
</style>

