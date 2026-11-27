<template>
  <el-card class="task-list" shadow="never">
    <div class="task-list__head">
      <h3 class="task-list__title">今日任务</h3>
      <span class="task-list__hint">按优先级和截止时间排序</span>
    </div>

    <div v-if="!tasks.length" class="task-list__empty">今日暂无任务</div>

    <div v-else class="task-list__items">
      <StudyTaskItem
        v-for="task in sortedTasks"
        :key="task.id"
        :task="task"
        :submitting="submittingIds.includes(task.id)"
        @toggle="emit('toggle', $event)"
      />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import StudyTaskItem from './StudyTaskItem.vue'
import type { StudyTask } from '../types'
import { sortTasks } from '../utils'

/** 今日任务列表：排序按契约 1.6 在前端兜底一次，顺序以服务端为准 */
interface Props {
  tasks: StudyTask[]
  submittingIds?: number[]
}

const props = withDefaults(defineProps<Props>(), {
  submittingIds: () => [],
})

const emit = defineEmits<{
  toggle: [task: StudyTask]
}>()

const sortedTasks = computed(() => sortTasks(props.tasks))
</script>

<style scoped>
.task-list {
  border-radius: 8px;
}

.task-list__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.task-list__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.task-list__hint {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.task-list__empty {
  padding: 18px 0 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
