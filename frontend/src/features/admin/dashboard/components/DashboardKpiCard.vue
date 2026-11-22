<template>
  <el-card class="kpi" :class="`kpi--${tone}`" shadow="never">
    <div class="kpi__title">{{ title }}</div>
    <div class="kpi__value">{{ value }}</div>
    <div v-if="hint" class="kpi__hint">{{ hint }}</div>
  </el-card>
</template>

<script setup lang="ts">
/** 单个 KPI 统计卡片，只负责展示 */
interface Props {
  title: string
  value: string | number
  hint?: string
  tone?: 'primary' | 'success' | 'warning' | 'danger'
}

withDefaults(defineProps<Props>(), {
  hint: '',
  tone: 'primary',
})
</script>

<style scoped>
.kpi {
  border-left: 4px solid var(--el-color-primary);
  border-radius: 6px;
}

.kpi--success {
  border-left-color: var(--el-color-success);
}

.kpi--warning {
  border-left-color: var(--el-color-warning);
}

.kpi--danger {
  border-left-color: var(--el-color-danger);
}

.kpi__title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.kpi__value {
  margin-top: 6px;
  font-size: 28px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--el-text-color-primary);
}

.kpi__hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
