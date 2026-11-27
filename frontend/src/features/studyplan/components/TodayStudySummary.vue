<template>
  <el-card class="summary" shadow="never">
    <div class="summary__head">
      <div>
        <h3 class="summary__title">今日学习概览</h3>
        <p class="summary__sub">已完成 {{ summary.completedTasks }} / {{ summary.totalTasks }} 项任务</p>
      </div>
      <span class="summary__streak">连续学习 {{ summary.streakDays }} 天</span>
    </div>

    <el-progress
      class="summary__progress"
      :percentage="percent"
      :stroke-width="8"
      :show-text="false"
      color="var(--el-color-primary)"
    />

    <div class="summary__stats">
      <div class="summary__stat">
        <span class="summary__value">{{ summary.completedTasks }}</span>
        <span class="summary__label">已完成任务</span>
      </div>
      <div class="summary__stat">
        <span class="summary__value">{{ summary.totalTasks }}</span>
        <span class="summary__label">今日任务（不含已取消）</span>
      </div>
      <div class="summary__stat">
        <span class="summary__value">{{ summary.studyMinutes }}<small> 分钟</small></span>
        <span class="summary__label">已完成学习时长</span>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { TodayStudySummary } from '../types'
import { progressPercent } from '../utils'

/** 今日概览：数值全部来自契约 4.2 的 summary，前端只做展示 */
interface Props {
  summary: TodayStudySummary
}

const props = defineProps<Props>()

const percent = computed(() => progressPercent(props.summary))
</script>

<style scoped>
.summary {
  border-radius: 8px;
}

.summary__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.summary__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.summary__sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.summary__streak {
  flex: none;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.summary__progress {
  margin: 12px 0 4px;
}

.summary__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 8px;
}

.summary__stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.summary__value {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.summary__value small {
  font-size: 11px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.summary__label {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 600px) {
  .summary__stats {
    grid-template-columns: 1fr;
  }

  .summary__label {
    white-space: normal;
  }
}
</style>
