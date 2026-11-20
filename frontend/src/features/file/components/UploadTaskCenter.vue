<!--
  B：上传任务中心

  交给 D 的接入方式（属性 / 事件见 README）：
  <UploadTaskCenter :files="files" :scene="scene" @completed="onCompleted" @failed="onFailed" />

  建议由 D 在 MainLayout 里挂一份，跨页面保持任务不中断；组件内部维护上传状态机，
  页面只负责传文件和消费结果。
-->
<template>
  <div class="upload-task-center">
    <div v-if="resumeRecords.length > 0" class="resume-panel">
      <div class="resume-title">
        有 {{ resumeRecords.length }} 个未完成的上传会话，重新选择原文件可继续（只补缺失分片）
      </div>
      <ul class="resume-list">
        <li v-for="record in resumeRecords" :key="record.sessionId">
          {{ record.fileName }}（{{ formatSize(record.fileSize) }}，会话 #{{ record.sessionId }}）
        </li>
      </ul>
      <div class="resume-actions">
        <el-button size="small" type="primary" plain @click="triggerResumePick">
          选择文件继续上传
        </el-button>
        <el-button size="small" @click="clearResumeRecords">放弃这些会话</el-button>
      </div>
      <input ref="resumeInputRef" type="file" multiple hidden @change="handleResumeFiles" />
    </div>

    <div v-if="tasks.length === 0" class="task-empty">暂无上传任务</div>

    <div v-for="task in tasks" :key="task.id" class="task-row">
      <div class="task-head">
        <span class="task-name" :title="task.fileName">{{ task.fileName }}</span>
        <span class="task-size">{{ formatSize(task.fileSize) }}</span>
        <el-tag size="small" :type="tagType(task.status)">{{ statusText(task.status) }}</el-tag>
      </div>

      <el-progress :percentage="task.progress" :status="progressStatus(task.status)" :stroke-width="8" />

      <div class="task-meta">
        <span v-if="task.partCount > 1">分片 {{ task.uploadedParts.length }}/{{ task.partCount }}</span>
        <span v-if="task.sessionId">会话 #{{ task.sessionId }}</span>
        <span v-if="task.fileId">文件 #{{ task.fileId }}</span>
      </div>

      <div class="task-actions">
        <el-button v-if="canPause(task)" size="small" @click="engine.pauseTask(task.id)">暂停</el-button>
        <el-button
          v-if="task.status === 'PAUSED'"
          size="small"
          type="primary"
          @click="engine.resumeTask(task.id)"
        >
          继续
        </el-button>
        <el-button
          v-if="task.status === 'FAILED'"
          size="small"
          type="primary"
          @click="engine.retryTask(task.id)"
        >
          重试
        </el-button>
        <el-button
          v-if="isActive(task)"
          size="small"
          type="danger"
          plain
          @click="engine.cancelTask(task.id)"
        >
          取消
        </el-button>
        <el-button v-if="isFinished(task)" size="small" @click="engine.removeTask(task.id)">
          移除
        </el-button>
      </div>

      <p v-if="task.error" class="task-error">{{ task.error }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  DEFAULT_CONCURRENCY,
  UploadEngine,
  readResumeRecords,
  removeResumeRecord,
} from '../uploader'
import {
  DEFAULT_FILE_SCENE,
  UPLOAD_STATUS_TEXT,
  type FileObject,
  type FileScene,
  type ResumeRecord,
  type UploadStatus,
  type UploadTask,
} from '../types'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

const props = withDefaults(
  defineProps<{
    /** 用户选择的文件，按数组引用变化触发入队 */
    files?: File[]
    /** 文件场景，默认 STUDY_MATERIAL */
    scene?: FileScene
    /** 入队后是否自动开始 */
    autoStart?: boolean
    /** 同时上传的分片数，默认 3 */
    concurrency?: number
  }>(),
  {
    files: () => [],
    scene: DEFAULT_FILE_SCENE,
    autoStart: true,
    concurrency: DEFAULT_CONCURRENCY,
  },
)

const emit = defineEmits<{
  completed: [file: FileObject]
  failed: [task: UploadTask, error: Error]
  progress: [task: UploadTask]
}>()

const engine = new UploadEngine({
  concurrency: props.concurrency,
  onCompleted: (_task, file) => emit('completed', file),
  onFailed: (task, error) => emit('failed', task, error),
  onProgress: (task) => emit('progress', task),
})

const tasks = computed(() => engine.tasks)

const resumeRecords = ref<ResumeRecord[]>([])
const resumeInputRef = ref<HTMLInputElement | null>(null)

watch(
  () => props.files,
  (files) => {
    if (!files || files.length === 0) return
    engine.addFiles(files, props.scene)
    if (props.autoStart) engine.start()
  },
  { immediate: true },
)

onMounted(() => {
  resumeRecords.value = readResumeRecords()
})

function reloadResumeRecords(): void {
  resumeRecords.value = readResumeRecords()
}

function triggerResumePick(): void {
  resumeInputRef.value?.click()
}

function handleResumeFiles(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (files.length > 0) {
    const result = engine.resumeWithFiles(resumeRecords.value, files)
    const startedSessions = new Set(result.started.map((task) => task.sessionId))
    resumeRecords.value = readResumeRecords().filter(
      (record) => !startedSessions.has(record.sessionId),
    )
  }
  input.value = ''
}

function clearResumeRecords(): void {
  for (const record of resumeRecords.value) {
    removeResumeRecord(record.sessionId)
  }
  resumeRecords.value = []
}

function formatSize(size: number): string {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  return `${(size / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function statusText(status: UploadStatus): string {
  return UPLOAD_STATUS_TEXT[status]
}

function tagType(status: UploadStatus): TagType {
  switch (status) {
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'PAUSED':
      return 'warning'
    case 'UPLOADING':
    case 'MERGING':
    case 'HASHING':
      return 'primary'
    default:
      return 'info'
  }
}

function progressStatus(status: UploadStatus): '' | 'success' | 'exception' | 'warning' {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'exception'
  if (status === 'PAUSED') return 'warning'
  return ''
}

/** 只有分片任务能暂停（小文件是单个请求，暂停没有语义） */
function canPause(task: UploadTask): boolean {
  return task.status === 'UPLOADING' && Boolean(task.sessionId)
}

function isActive(task: UploadTask): boolean {
  return ['WAITING', 'HASHING', 'UPLOADING', 'PAUSED', 'MERGING'].includes(task.status)
}

function isFinished(task: UploadTask): boolean {
  return ['COMPLETED', 'FAILED', 'CANCELLED'].includes(task.status)
}

defineExpose({ engine, tasks, reloadResumeRecords })
</script>

<style scoped>
.upload-task-center {
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-size: 13px;
}

.resume-panel {
  padding: 12px;
  border: 1px dashed var(--el-color-warning);
  border-radius: 6px;
  background: var(--el-color-warning-light-9, #fdf6ec);
}

.resume-title {
  margin-bottom: 6px;
  color: var(--el-color-warning-dark-2, #b88230);
}

.resume-list {
  margin: 0 0 8px;
  padding-left: 18px;
  color: var(--el-text-color-regular);
}

.task-empty {
  padding: 16px 0;
  color: var(--el-text-color-secondary);
}

.task-row {
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.task-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.task-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.task-size,
.task-meta {
  color: var(--el-text-color-secondary);
}

.task-meta {
  display: flex;
  gap: 12px;
  margin-top: 4px;
}

.task-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.task-error {
  margin: 6px 0 0;
  color: var(--el-color-danger);
}
</style>
