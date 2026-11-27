<template>
  <el-card class="weekly" shadow="never">
    <div class="weekly__head">
      <div>
        <h3 class="weekly__title">本周学习诊断</h3>
        <p v-if="report" class="weekly__range">{{ report.weekStart }} ~ {{ report.weekEnd }}</p>
      </div>
      <el-button v-if="errorMessage" size="small" @click="emit('retry')">重试</el-button>
    </div>

    <div v-if="loading" class="weekly__skeleton">
      <span class="weekly__bone weekly__bone--line" />
      <span class="weekly__bone weekly__bone--short" />
    </div>

    <div v-else-if="errorMessage" class="weekly__error">{{ errorMessage }}</div>

    <div v-else-if="!report || report.reportId === null" class="weekly__empty">
      <p class="weekly__empty-title">本周报告尚未生成</p>
      <p class="weekly__empty-hint">周日 20:00 后生成，生成后会通过站内通知提醒你。</p>
    </div>

    <div v-else class="weekly__body">
      <div class="weekly__stats">
        <div class="weekly__stat">
          <span class="weekly__value">{{ formatPercent(report.completionRate) }}</span>
          <span class="weekly__label">任务完成率</span>
        </div>
        <div class="weekly__stat">
          <span class="weekly__value">{{ report.studyMinutes }}<small> 分钟</small></span>
          <span class="weekly__label">本周学习时长</span>
        </div>
        <div class="weekly__stat">
          <span class="weekly__value">{{ report.chatQuestions }}</span>
          <span class="weekly__label">答疑提问次数</span>
        </div>
      </div>

      <div v-if="report.weakSubjects.length" class="weekly__block">
        <span class="weekly__block-label">薄弱学科</span>
        <span v-for="subject in report.weakSubjects" :key="subject" class="weekly__tag">{{ subject }}</span>
      </div>

      <p v-if="report.summary" class="weekly__summary">{{ report.summary }}</p>

      <div v-if="report.nextActions.length" class="weekly__block weekly__block--column">
        <span class="weekly__block-label">下周动作</span>
        <ul class="weekly__actions">
          <li v-for="action in report.nextActions" :key="action">{{ action }}</li>
        </ul>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import type { WeeklyStudyReportSummary } from '../types'
import { formatPercent } from '../utils'

/**
 * 本周诊断：reportId = null 表示尚未生成，不是接口错误（契约 5.11）。
 * 失败只在卡片内提示，不阻塞今日任务与推荐（契约 9.1）。
 */
interface Props {
  report: WeeklyStudyReportSummary | null
  loading?: boolean
  errorMessage?: string
}

withDefaults(defineProps<Props>(), {
  loading: false,
  errorMessage: '',
})

const emit = defineEmits<{
  retry: []
}>()
</script>

<style scoped>
.weekly {
  border-radius: 8px;
}

.weekly__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.weekly__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.weekly__range {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.weekly__skeleton {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.weekly__bone {
  display: block;
  height: 10px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
}

.weekly__bone--line {
  width: 70%;
}

.weekly__bone--short {
  width: 40%;
}

.weekly__error {
  font-size: 12px;
  color: var(--el-color-danger);
}

.weekly__empty-title {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.weekly__empty-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.weekly__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.weekly__stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.weekly__value {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.weekly__value small {
  font-size: 11px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.weekly__label {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.weekly__block {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
}

.weekly__block--column {
  flex-direction: column;
  align-items: flex-start;
}

.weekly__block-label {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.weekly__tag {
  padding: 0 6px;
  border-radius: 4px;
  font-size: 11px;
  line-height: 18px;
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.weekly__summary {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

.weekly__actions {
  margin: 4px 0 0;
  padding-left: 16px;
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-regular);
}

@media (max-width: 600px) {
  .weekly__stats {
    grid-template-columns: 1fr;
  }
}
</style>
