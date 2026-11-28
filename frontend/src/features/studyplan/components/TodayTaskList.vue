<template>
  <el-card class="task-list" shadow="never">
    <div class="task-list__head">
      <h3 class="task-list__title">今日任务</h3>
      <div class="task-list__actions">
        <span class="task-list__hint">按优先级和截止时间排序</span>
        <el-button
          v-if="canManage"
          size="small"
          type="primary"
          plain
          data-test="add-study-task"
          @click="emit('add')"
        >
          添加任务
        </el-button>
      </div>
    </div>

    <div v-if="!tasks.length" class="task-list__empty">
      <span>今日暂无任务，可以添加一个明确的小目标。</span>
      <el-button v-if="canManage" size="small" type="primary" @click="emit('add')">
        添加第一个任务
      </el-button>
    </div>

    <div v-else class="task-list__items">
      <StudyTaskItem
        v-for="task in sortedTasks"
        :key="task.id"
        :task="task"
        :submitting="submittingIds.includes(task.id)"
        :editable="canManage"
        @toggle="emit('toggle', $event)"
        @edit="emit('edit', $event)"
        @remove="emit('remove', $event)"
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
  canManage?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  submittingIds: () => [],
  canManage: false,
})

const emit = defineEmits<{
  toggle: [task: StudyTask]
  add: []
  edit: [task: StudyTask]
  remove: [task: StudyTask]
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

.task-list__actions {
  display: flex;
  align-items: center;
  gap: 10px;
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 0 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 600px) {
  .task-list__head,
  .task-list__empty {
    align-items: flex-start;
    flex-direction: column;
  }

  .task-list__actions {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
