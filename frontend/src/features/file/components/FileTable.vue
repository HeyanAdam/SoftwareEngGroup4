<template>
  <el-table
    v-loading="loading"
    :data="rows"
    row-key="key"
    border
    stripe
    empty-text="暂无文件"
    style="width: 100%"
  >
    <el-table-column label="文件名" min-width="360">
      <template #default="{ row }">
        <div class="file-name-cell">
          <span class="file-name" :title="row.fileName">{{ row.fileName }}</span>
          <div v-if="row.upload" class="inline-progress">
            <el-progress
              class="mini-progress"
              :percentage="row.upload.progress"
              :status="progressStatus(row.upload.status)"
              :show-text="false"
              :stroke-width="4"
            />
            <span class="progress-text">{{ progressText(row.upload) }}</span>
            <button
              class="task-icon-button"
              type="button"
              :disabled="!canToggle(row.upload)"
              :title="toggleTitle(row.upload)"
              :aria-label="toggleTitle(row.upload)"
              @click="emit('toggleUpload', row.upload)"
            >
              <svg
                v-if="showsPlayIcon(row.upload)"
                class="task-icon-symbol"
                viewBox="0 0 12 12"
                aria-hidden="true"
              >
                <path d="M3 1.5 10 6 3 10.5Z" />
              </svg>
              <svg
                v-else
                class="task-icon-symbol"
                viewBox="0 0 12 12"
                aria-hidden="true"
              >
                <rect x="2" y="1.5" width="2.5" height="9" rx="0.6" />
                <rect x="7.5" y="1.5" width="2.5" height="9" rx="0.6" />
              </svg>
            </button>
            <button
              class="task-icon-button task-icon-button--danger"
              type="button"
              title="取消上传"
              aria-label="取消上传"
              @click="emit('cancelUpload', row.upload)"
            >
              ■
            </button>
          </div>
        </div>
      </template>
    </el-table-column>
    <el-table-column label="类型" width="140">
      <template #default="{ row }">
        {{ row.contentType || '未知' }}
      </template>
    </el-table-column>
    <el-table-column label="大小" width="110">
      <template #default="{ row }">
        {{ formatSize(row.fileSize) }}
      </template>
    </el-table-column>
    <el-table-column label="场景" width="120">
      <template #default="{ row }">
        {{ sceneLabel(row.scene) }}
      </template>
    </el-table-column>
    <el-table-column label="状态" width="110">
      <template #default="{ row }">
        <el-tag :type="statusTagType(row.status)" size="small">
          {{ row.upload ? uploadStatusLabel(row.upload.status) : statusLabel(row.status) }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="上传时间" width="170">
      <template #default="{ row }">
        {{ formatTime(row.createdAt) }}
      </template>
    </el-table-column>
    <el-table-column label="操作" width="140" fixed="right">
      <template #default="{ row }">
        <template v-if="row.file">
          <el-button
            v-permission="'file:download'"
            link
            type="primary"
            :disabled="row.file.status !== 'READY'"
            @click="emit('download', row.file)"
          >
            下载
          </el-button>
          <el-button
            v-permission="'file:delete'"
            link
            type="danger"
            @click="emit('remove', row.file)"
          >
            删除
          </el-button>
        </template>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  UPLOAD_STATUS_TEXT,
  type FileObject,
  type FileScene,
  type UploadStatus,
  type UploadTask,
} from '../types'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

interface FileTableRow {
  key: string
  fileName: string
  contentType: string
  fileSize: number
  scene: FileScene
  status: string
  createdAt: string | number
  file?: FileObject
  upload?: UploadTask
}

const props = defineProps<{
  files: FileObject[]
  uploadTasks: UploadTask[]
  loading: boolean
}>()

const emit = defineEmits<{
  download: [file: FileObject]
  remove: [file: FileObject]
  toggleUpload: [task: UploadTask]
  cancelUpload: [task: UploadTask]
}>()

const rows = computed<FileTableRow[]>(() => {
  const uploadRows = props.uploadTasks
    .slice()
    .reverse()
    .map<FileTableRow>((task) => ({
      key: `upload-${task.id}`,
      fileName: task.fileName,
      contentType: task.contentType,
      fileSize: task.fileSize,
      scene: task.scene,
      status: task.status,
      createdAt: task.createdAt,
      upload: task,
    }))

  const fileRows = props.files.map<FileTableRow>((file) => ({
    key: `file-${file.id}`,
    fileName: file.originalFilename,
    contentType: file.contentType || file.fileExt || '',
    fileSize: file.fileSize,
    scene: file.scene,
    status: file.status,
    createdAt: file.createTime,
    file,
  }))

  return [...uploadRows, ...fileRows]
})

const SCENE_LABELS: Record<string, string> = {
  STUDY_MATERIAL: '学习资料',
  CHAT_ATTACHMENT: '聊天附件',
  QUESTION_IMAGE: '题目图片',
  KNOWLEDGE_DOCUMENT: '知识文档',
  AVATAR: '头像',
}

const FILE_STATUS_LABELS: Record<string, string> = {
  READY: '可用',
  FAILED: '失败',
}

function sceneLabel(scene: string): string {
  return SCENE_LABELS[scene] ?? scene
}

function statusLabel(status: string): string {
  return FILE_STATUS_LABELS[status] ?? status
}

function uploadStatusLabel(status: UploadStatus): string {
  return UPLOAD_STATUS_TEXT[status]
}

function statusTagType(status: string): TagType {
  if (status === 'READY' || status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PAUSED') return 'warning'
  if (status === 'HASHING' || status === 'UPLOADING' || status === 'MERGING') return 'primary'
  return 'info'
}

function progressStatus(status: UploadStatus): '' | 'success' | 'exception' | 'warning' {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'exception'
  if (status === 'PAUSED') return 'warning'
  return ''
}

function progressText(task: UploadTask): string {
  if (task.status === 'HASHING') return `校验 ${task.progress}%`
  if (task.status === 'MERGING') return '正在合并分片'
  if (task.status === 'PAUSED') return `已暂停 · ${task.uploadedParts.length}/${task.partCount} 片`
  if (task.status === 'FAILED') return task.error || '上传失败'
  if (task.status === 'WAITING') return '等待上传'
  if (task.partCount === 1) return `小文件直传 ${task.progress}%`
  return `${task.uploadedParts.length}/${task.partCount} 片 · ${task.progress}%`
}

function canToggle(task: UploadTask): boolean {
  if (task.status === 'PAUSED' || task.status === 'FAILED') return true
  return task.status === 'UPLOADING' && task.partCount > 1 && Boolean(task.sessionId)
}

function showsPlayIcon(task: UploadTask): boolean {
  return task.status === 'PAUSED' || task.status === 'FAILED'
}

function toggleTitle(task: UploadTask): string {
  if (task.status === 'PAUSED') return '继续上传'
  if (task.status === 'FAILED') return '重试上传'
  if (task.status === 'HASHING') return '计算校验值完成后可暂停'
  if (task.partCount === 1) return '小文件直传不支持暂停'
  return '暂停上传'
}

function formatSize(size: number): string {
  if (!size || size <= 0) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(1)} MB`
  return `${(size / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

/** 后端 LocalDateTime 默认序列化成 2026-11-20T11:11:51，这里转成本地可读格式。 */
function formatTime(value: string | number): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
</script>

<style scoped>
.file-name-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.inline-progress {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.mini-progress {
  width: 112px;
  flex: none;
}

.progress-text {
  min-width: 92px;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-icon-button {
  display: inline-flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-blank);
  color: var(--el-color-primary);
  cursor: pointer;
  font-size: 12px;
  line-height: 1;
}

.task-icon-symbol {
  width: 12px;
  height: 12px;
  fill: currentColor;
}

.task-icon-button:hover:not(:disabled) {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.task-icon-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.task-icon-button--danger {
  color: var(--el-color-danger);
}

.task-icon-button--danger:hover:not(:disabled) {
  border-color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}
</style>
