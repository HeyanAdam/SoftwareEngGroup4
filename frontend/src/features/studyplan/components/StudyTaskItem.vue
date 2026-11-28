<template>
  <div class="task-item" :class="{ 'task-item--done': task.status === 'COMPLETED' }">
    <el-checkbox
      class="task-item__check"
      :model-value="task.status === 'COMPLETED'"
      :disabled="submitting || task.status === 'CANCELLED'"
      :aria-label="`切换任务状态：${task.title}`"
      @change="emit('toggle', task)"
    />

    <div class="task-item__body">
      <div class="task-item__head">
        <span class="task-item__title" :title="task.title">{{ task.title }}</span>
        <span class="task-item__status" :class="`task-item__status--${task.status.toLowerCase()}`">
          {{ TASK_STATUS_LABELS[task.status] }}
        </span>
        <span v-if="task.overdue" class="task-item__overdue">已逾期</span>
      </div>

      <div class="task-item__meta">
        <span class="task-item__tag">{{ task.subject }}</span>
        <span v-if="task.knowledgePoint" class="task-item__tag">{{ task.knowledgePoint }}</span>
        <span v-if="task.estimatedMinutes > 0" class="task-item__hint">约 {{ task.estimatedMinutes }} 分钟</span>
        <span v-if="dueText" class="task-item__hint">截止 {{ dueText }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { StudyTask } from '../types'
import { formatTaskTime, TASK_STATUS_LABELS } from '../utils'

/**
 * 单条任务行：状态、逾期标记全部读服务端字段（契约 3.1 / 4.1），
 * 前端不按浏览器时间判断 overdue。
 */
interface Props {
  task: StudyTask
  submitting?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  submitting: false,
})

const emit = defineEmits<{
  toggle: [task: StudyTask]
}>()

const dueText = computed(() => formatTaskTime(props.task.dueAt))
</script>

<style scoped>
.task-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.task-item:last-child {
  border-bottom: none;
}

.task-item--done .task-item__title {
  color: var(--el-text-color-secondary);
  text-decoration: line-through;
}

.task-item__check {
  flex: none;
  height: auto;
}

.task-item__body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  flex: 1;
}

.task-item__head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.task-item__title {
  min-width: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-item__status {
  flex: none;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 11px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
}

.task-item__status--completed {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.task-item__status--in_progress {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.task-item__overdue {
  flex: none;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 11px;
  line-height: 18px;
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}

.task-item__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 11px;
}

.task-item__tag {
  padding: 0 6px;
  border-radius: 4px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
}

.task-item__hint {
  color: var(--el-text-color-secondary);
}

@media (max-width: 600px) {
  .task-item__head {
    flex-wrap: wrap;
  }

  .task-item__title {
    white-space: normal;
    word-break: break-word;
  }
}
</style>
