<template>
  <div class="admin-page-header">
    <div class="admin-page-header__text">
      <h2 class="admin-page-header__title">{{ title }}</h2>
      <p v-if="description" class="admin-page-header__desc">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="admin-page-header__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
/** 后台页面通用标题栏：标题 + 说明 + 右侧操作区插槽 */
defineProps<{
  title: string
  description?: string
}>()
</script>

<style scoped>
.admin-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.admin-page-header__title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1f2b26;
}

.admin-page-header__desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: #8a9a93;
}

.admin-page-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
