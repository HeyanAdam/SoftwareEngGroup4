<template>
  <el-card class="panel" shadow="never">
    <div class="panel__head">
      <span class="panel__title">系统状态</span>
    <span class="panel__hint">探活结果，仅展示 UP / DOWN</span>
    </div>
    <div class="panel__rows">
      <div v-for="item in services" :key="item.key" class="panel__row">
        <span class="panel__label">{{ item.label }}</span>
        <el-tag :type="item.up ? 'success' : 'danger'" size="small" effect="dark">
          {{ item.up ? 'UP' : 'DOWN' }}
        </el-tag>
      </div>
    </div>
    <div v-if="downList.length" class="panel__warn">
      异常服务：{{ downList.join('、') }}
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DashboardSystem } from '../types'

interface Props {
  system: DashboardSystem
}

const props = defineProps<Props>()

/** 仅展示服务名与探活结果，不输出任何连接串、地址、账号等敏感信息 */
const services = computed(() => [
  { key: 'mysql', label: 'MySQL', up: props.system.mysql === 'UP' },
  { key: 'redis', label: 'Redis', up: props.system.redis === 'UP' },
  { key: 'rabbitmq', label: 'RabbitMQ', up: props.system.rabbitmq === 'UP' },
])

const downList = computed(() => services.value.filter((item) => !item.up).map((item) => item.label))
</script>

<style scoped>
.panel__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 8px;
}

.panel__title {
  font-size: 15px;
  font-weight: 600;
}

.panel__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.panel__rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.panel__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.panel__row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.panel__label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.panel__warn {
  margin-top: 10px;
  font-size: 12px;
  color: var(--el-color-danger);
}
</style>
