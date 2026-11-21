<template>
  <div v-if="canUpload" class="upload-entry">
    <el-button type="primary" @click="openPicker">
      选择文件上传
    </el-button>
    <input ref="inputRef" class="hidden-input" type="file" multiple @change="handlePicked" />
    <p class="hint">
      支持批量上传；50MB 以上文件自动使用分片、重试和断点续传。
    </p>

    <div v-if="resumeRecords.length > 0" class="resume-note">
      <span>有 {{ resumeRecords.length }} 个未完成分片上传，可选择原文件继续。</span>
      <el-button link type="primary" @click="triggerResumePick">继续上传</el-button>
      <el-button link @click="clearResumeRecords">放弃</el-button>
      <input ref="resumeInputRef" type="file" multiple hidden @change="handleResumeFiles" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { usePermission } from '../../../shared/utils/permission'
import { readResumeRecords, removeResumeRecord, type UploadEngine } from '../uploader'
import type { FileScene, ResumeRecord } from '../types'

const props = defineProps<{
  scene: FileScene
  engine: UploadEngine
}>()

const { hasPermission } = usePermission()
const canUpload = hasPermission('file:upload')
const inputRef = ref<HTMLInputElement | null>(null)
const resumeInputRef = ref<HTMLInputElement | null>(null)
const resumeRecords = ref<ResumeRecord[]>([])

onMounted(() => {
  resumeRecords.value = readResumeRecords()
})

function openPicker(): void {
  inputRef.value?.click()
}

function handlePicked(event: Event): void {
  const input = event.target as HTMLInputElement
  const picked = Array.from(input.files ?? [])
  // 清空 input，允许再次选择同一个文件
  input.value = ''
  if (picked.length === 0) return
  props.engine.addFiles(picked, props.scene)
  props.engine.start()
}

function triggerResumePick(): void {
  resumeInputRef.value?.click()
}

function handleResumeFiles(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (files.length > 0) {
    const result = props.engine.resumeWithFiles(resumeRecords.value, files)
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
</script>

<style scoped>
.upload-entry {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.hidden-input {
  display: none;
}

.hint {
  margin: 0;
  font-size: 12px;
  color: #909399;
}

.resume-note {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--el-color-warning-light-9, #fdf6ec);
  color: var(--el-color-warning-dark-2, #b88230);
  font-size: 12px;
}
</style>
