<template>
  <div class="refresh-bar">
    <el-radio-group :model-value="days" size="small" :disabled="loading" @change="onDaysChange">
      <el-radio-button :value="7">7 天</el-radio-button>
      <el-radio-button :value="14">14 天</el-radio-button>
      <el-radio-button :value="30">30 天</el-radio-button>
    </el-radio-group>

    <span class="refresh-bar__time">最近更新：{{ lastSuccessText }}</span>

    <el-button size="small" :loading="loading" @click="emit('refresh')">立即刷新</el-button>
    <el-button size="small" plain @click="toggleFullscreen">
      {{ isFullscreen ? '退出全屏' : '全屏' }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { DashboardDays } from '../types'
import { formatClock } from '../utils/format'

interface Props {
  loading: boolean
  days: DashboardDays
  lastSuccessAt: Date | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  refresh: []
  'update:days': [days: DashboardDays]
}>()

const isFullscreen = ref(false)

const lastSuccessText = computed(() => formatClock(props.lastSuccessAt))

function onDaysChange(value: string | number | boolean | undefined): void {
  const next = Number(value)
  if (next === 7 || next === 14 || next === 30) {
    emit('update:days', next as DashboardDays)
  }
}

function syncFullscreen(): void {
  isFullscreen.value = document.fullscreenElement !== null
}

async function toggleFullscreen(): Promise<void> {
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen()
    } else {
      await document.documentElement.requestFullscreen()
    }
  } catch {
    // 浏览器不支持或用户拒绝全屏时静默忽略，不影响其他功能
  }
}

onMounted(() => {
  document.addEventListener('fullscreenchange', syncFullscreen)
})

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', syncFullscreen)
})
</script>

<style scoped>
.refresh-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.refresh-bar__time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
