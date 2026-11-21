<script setup lang="ts">
import { computed } from 'vue'
import type { SysPermission } from '../../api/admin.types'
import {
  groupPermissions,
  groupSelectionState,
  toggleGroup,
} from './permissionModules'

const props = defineProps<{
  permissions: SysPermission[]
  selectedIds: number[]
  /** 只读：没有 admin:role:update，或当前是 SYSTEM_ADMIN */
  readonly?: boolean
}>()

const emit = defineEmits<{
  (event: 'update:selectedIds', ids: number[]): void
}>()

const groups = computed(() => groupPermissions(props.permissions))
const selectedSet = computed(() => new Set(props.selectedIds))

/** 单条勾选/取消：保持 ID 数组升序，便于「未保存」比较与保存 */
function togglePermission(id: number, checked: boolean | string | number): void {
  if (props.readonly) return
  const next = new Set(props.selectedIds)
  if (Boolean(checked)) next.add(id)
  else next.delete(id)
  emit('update:selectedIds', [...next].sort((a, b) => a - b))
}

/** 模块全选/取消：半选状态点一下即补齐 */
function toggleModule(permissions: SysPermission[]): void {
  if (props.readonly) return
  emit('update:selectedIds', toggleGroup(props.selectedIds, permissions))
}
</script>

<template>
  <div class="permission-matrix">
    <section
      v-for="group in groups"
      :key="group.module"
      class="permission-matrix__card"
    >
      <header class="permission-matrix__head">
        <div class="permission-matrix__title">
          <span class="permission-matrix__module">{{ group.module }}</span>
          <span class="permission-matrix__count">
            {{ groupSelectionState(selectedIds, group.permissions).selected }}
            /
            {{ group.permissions.length }}
          </span>
        </div>
        <el-checkbox
          :model-value="groupSelectionState(selectedIds, group.permissions).allSelected"
          :indeterminate="groupSelectionState(selectedIds, group.permissions).indeterminate"
          :disabled="readonly"
          @change="toggleModule(group.permissions)"
        >
          全选
        </el-checkbox>
      </header>

      <ul class="permission-matrix__list">
        <li v-for="permission in group.permissions" :key="permission.id">
          <el-checkbox
            :model-value="selectedSet.has(permission.id)"
            :disabled="readonly"
            class="permission-matrix__item"
            @change="(checked: boolean | string | number) => togglePermission(permission.id, checked)"
          >
            <span class="permission-matrix__name">{{ permission.permName }}</span>
            <code class="permission-matrix__code">{{ permission.permCode }}</code>
          </el-checkbox>
        </li>
      </ul>
    </section>

    <el-empty
      v-if="groups.length === 0"
      description="暂无权限字典数据"
      :image-size="80"
    />
  </div>
</template>

<style scoped>
.permission-matrix {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
  align-content: start;
}

.permission-matrix__card {
  padding: 12px 14px 10px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(31, 43, 38, 0.04);
}

.permission-matrix__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.permission-matrix__title {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}

.permission-matrix__module {
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  color: #1f2b26;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.permission-matrix__count {
  flex: 0 0 auto;
  padding: 0 6px;
  border-radius: 8px;
  background: #f0f4f2;
  font-size: 11px;
  line-height: 18px;
  color: #64756d;
}

.permission-matrix__head :deep(.el-checkbox__label) {
  font-size: 12px;
  color: #64756d;
}

.permission-matrix__list {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.permission-matrix__list li + li {
  border-top: 1px solid #f3f6f5;
}

.permission-matrix__item {
  display: flex;
  align-items: center;
  width: 100%;
  height: auto;
  min-height: 34px;
  padding: 2px 0;
}

.permission-matrix__item :deep(.el-checkbox__label) {
  display: flex;
  flex: 1 1 auto;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
  padding-left: 4px;
}

.permission-matrix__name {
  flex: 0 1 auto;
  overflow: hidden;
  font-size: 13px;
  color: #33413b;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.permission-matrix__code {
  flex: 0 1 auto;
  overflow: hidden;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  color: #9aa8a1;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
