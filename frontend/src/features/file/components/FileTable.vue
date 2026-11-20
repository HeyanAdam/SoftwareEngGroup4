<template>
  <el-table v-loading="loading" :data="files" border stripe empty-text="暂无文件" style="width: 100%">
    <el-table-column prop="originalFilename" label="文件名" min-width="200" show-overflow-tooltip />
    <el-table-column label="类型" width="140">
      <template #default="{ row }">
        {{ row.contentType || row.fileExt || '未知' }}
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
    <el-table-column label="状态" width="100">
      <template #default="{ row }">
        <el-tag :type="row.status === 'READY' ? 'success' : 'warning'" size="small">
          {{ statusLabel(row.status) }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="上传时间" width="170">
      <template #default="{ row }">
        {{ formatTime(row.createTime) }}
      </template>
    </el-table-column>
    <el-table-column label="操作" width="200" fixed="right">
      <template #default="{ row }">
        <el-button
          v-permission="'file:download'"
          link
          type="primary"
          :disabled="row.status !== 'READY'"
          @click="emit('download', row)"
        >
          下载
        </el-button>
        <el-button
          v-permission="'file:delete'"
          link
          type="danger"
          @click="emit('remove', row)"
        >
          删除
        </el-button>
        <el-button
          v-if="row.status !== 'READY'"
          link
          type="warning"
          @click="emit('retry', row)"
        >
          重试
        </el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import type { FileObjectVO } from '../api/tempFileApi'

defineProps<{
  files: FileObjectVO[]
  loading: boolean
}>()

const emit = defineEmits<{
  download: [file: FileObjectVO]
  remove: [file: FileObjectVO]
  retry: [file: FileObjectVO]
}>()

const SCENE_LABELS: Record<string, string> = {
  STUDY_MATERIAL: '学习资料',
  CHAT_ATTACHMENT: '聊天附件',
  QUESTION_IMAGE: '题目图片',
  KNOWLEDGE_DOCUMENT: '知识文档',
  AVATAR: '头像',
}

const STATUS_LABELS: Record<string, string> = {
  READY: '可用',
  UPLOADING: '上传中',
  MERGING: '合并中',
  FAILED: '失败',
  CANCELLED: '已取消',
}

function sceneLabel(scene: string): string {
  return SCENE_LABELS[scene] ?? scene
}

function statusLabel(status: string): string {
  return STATUS_LABELS[status] ?? status
}

function formatSize(size: number): string {
  if (!size || size <= 0) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(1)} MB`
  return `${(size / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

/** 后端 LocalDateTime 默认序列化成 2026-11-20T11:11:51，这里转成本地可读格式 */
function formatTime(value: string): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
</script>
