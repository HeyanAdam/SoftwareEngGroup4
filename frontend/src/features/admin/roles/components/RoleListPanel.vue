<script setup lang="ts">
import { computed, ref } from 'vue'
import type { AdminRoleSummary } from '../../api/admin.types'
import { formatUserCount, isBuiltinRole, isSystemAdminRole } from './roleMeta'

const props = defineProps<{
  roles: AdminRoleSummary[]
  activeId: number | null
  /** admin:role:update：没有该权限时不显示新建 / 编辑 / 删除 */
  canUpdate: boolean
  loading?: boolean
}>()

const emit = defineEmits<{
  (event: 'select', role: AdminRoleSummary): void
  (event: 'create'): void
  (event: 'edit', role: AdminRoleSummary): void
  (event: 'remove', role: AdminRoleSummary): void
}>()

const keyword = ref('')

/** 搜索角色名称或角色码（本地过滤，不额外打接口） */
const filteredRoles = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) return props.roles
  return props.roles.filter(
    (role) =>
      role.roleName.toLowerCase().includes(query) ||
      role.roleCode.toLowerCase().includes(query),
  )
})

const systemAdminCount = computed(
  () => props.roles.filter((role) => isSystemAdminRole(role.roleCode)).length,
)
</script>

<template>
  <section class="role-list">
    <div class="role-list__head">
      <div class="role-list__title">
        角色
        <span class="role-list__total">{{ filteredRoles.length }}</span>
      </div>
      <el-button
        v-if="canUpdate"
        size="small"
        type="primary"
        plain
        @click="emit('create')"
      >
        新建角色
      </el-button>
    </div>

    <el-input
      v-model="keyword"
      class="role-list__search"
      size="small"
      placeholder="搜索角色名称 / 角色码"
      clearable
    />

    <div v-loading="loading" class="role-list__items">
      <div
        v-for="role in filteredRoles"
        :key="role.id"
        class="role-list__item"
        :class="{ 'is-active': role.id === activeId }"
        role="button"
        tabindex="0"
        @click="emit('select', role)"
        @keyup.enter="emit('select', role)"
      >
        <div class="role-list__top">
          <span class="role-list__name" :title="role.roleName">{{ role.roleName }}</span>
          <el-tag
            v-if="isSystemAdminRole(role.roleCode)"
            size="small"
            type="success"
            disable-transitions
          >
            全权
          </el-tag>
          <el-tag
            v-else-if="isBuiltinRole(role.roleCode)"
            size="small"
            type="info"
            disable-transitions
          >
            内置
          </el-tag>
        </div>

        <div class="role-list__code">{{ role.roleCode }}</div>

        <div class="role-list__meta">
          <span>{{ role.permissionCount }} 项权限</span>
          <span class="role-list__divider">·</span>
          <span
            :class="{ 'is-unknown': role.userCount === null }"
            :title="role.userCount === null ? '后端尚未提供 /admin/roles/summary，绑定人数待接入' : ''"
          >
            {{ formatUserCount(role.userCount) }}
          </span>
        </div>

        <div v-if="canUpdate" class="role-list__actions">
          <el-button link type="primary" size="small" @click.stop="emit('edit', role)">
            编辑
          </el-button>
          <el-button
            v-if="!isBuiltinRole(role.roleCode)"
            link
            type="danger"
            size="small"
            @click.stop="emit('remove', role)"
          >
            删除
          </el-button>
        </div>
      </div>

      <el-empty
        v-if="!loading && filteredRoles.length === 0"
        :description="roles.length === 0 ? '暂无角色' : '没有匹配的角色'"
        :image-size="72"
      />
    </div>

    <p v-if="systemAdminCount > 0" class="role-list__foot-tip">
      内置角色不可删除；系统管理员由系统自动补齐全部权限。
    </p>
  </section>
</template>

<style scoped>
.role-list {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  padding: 14px 14px 10px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: #fff;
}

.role-list__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.role-list__title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2b26;
}

.role-list__total {
  margin-left: 4px;
  font-weight: 400;
  font-size: 12px;
  color: #93a09a;
}

.role-list__search {
  margin-bottom: 10px;
}

.role-list__items {
  flex: 1 1 auto;
  min-height: 120px;
  overflow-y: auto;
  padding-right: 2px;
}

.role-list__item {
  margin-bottom: 8px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: #fbfcfc;
  cursor: pointer;
  outline: none;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.role-list__item:hover {
  border-color: var(--el-color-primary-light-5);
}

.role-list__item:focus-visible {
  box-shadow: 0 0 0 2px var(--el-color-primary-light-7);
}

.role-list__item.is-active {
  border-color: var(--el-color-primary);
  background: #f2f8f5;
  box-shadow: inset 3px 0 0 var(--el-color-primary);
}

.role-list__top {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.role-list__name {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  color: #1f2b26;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-list__code {
  margin-top: 2px;
  overflow: hidden;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  color: #93a09a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-list__meta {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  font-size: 12px;
  color: #64756d;
}

.role-list__divider {
  color: #c3cdc8;
}

.role-list__meta .is-unknown {
  color: #9aa8a1;
  border-bottom: 1px dotted #cbd4cf;
}

.role-list__actions {
  display: flex;
  gap: 4px;
  margin-top: 6px;
}

.role-list__foot-tip {
  margin: 8px 0 0;
  padding-top: 8px;
  border-top: 1px dashed var(--el-border-color-lighter);
  font-size: 11px;
  line-height: 1.6;
  color: #a0aca6;
}
</style>
