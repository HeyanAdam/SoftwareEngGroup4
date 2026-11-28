<template>
  <el-card class="countdown" shadow="never">
    <div class="countdown__body">
      <div class="countdown__text">
        <span class="countdown__label">考试倒计时</span>
        <span class="countdown__days">{{ daysText }}</span>
        <span v-if="examDate" class="countdown__date">考试日期 {{ examDate }}</span>
      </div>
      <div class="countdown__mark">备考中</div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 考试倒计时：只展示服务端给的 examDate / daysUntilExam，
 * 前端不按本地时间推算“还有多少天”（契约 1.4）。
 */
interface Props {
  examDate: string | null
  daysUntilExam: number | null
}

const props = defineProps<Props>()

const daysText = computed(() => {
  if (props.daysUntilExam === null) return '未设置考试日期'
  if (props.daysUntilExam <= 0) return '考试日期已到，进入冲刺复盘'
  return `距考试还有 ${props.daysUntilExam} 天`
})
</script>

<style scoped>
.countdown {
  border-radius: 8px;
  border-left: 4px solid var(--el-color-primary);
}

.countdown__body {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.countdown__text {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
}

.countdown__label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.countdown__days {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.countdown__date {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.countdown__mark {
  flex: none;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

@media (max-width: 600px) {
  .countdown__body {
    align-items: flex-start;
  }
}
</style>
