<template>
  <div v-if="canUpload" class="upload-entry">
    <el-button type="primary" @click="openPicker">
      选择文件上传
    </el-button>
    <input ref="inputRef" class="hidden-input" type="file" multiple @change="handlePicked" />
    <p class="hint">
      支持批量上传；50MB 以上文件自动使用分片、重试和断点续传。
    </p>
    <UploadTaskCenter
      :files="pendingFiles"
      :scene="scene"
      :auto-start="true"
      @completed="handleCompleted"
      @failed="handleFailed"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { usePermission } from '../../../shared/utils/permission'
import UploadTaskCenter from '../components/UploadTaskCenter.vue'
import type { FileObject, FileScene, UploadTask } from '../types'

defineProps<{ scene: FileScene }>()

const emit = defineEmits<{
  completed: [file: FileObject]
  failed: [message: string]
}>()

const { hasPermission } = usePermission()
const canUpload = hasPermission('file:upload')
const inputRef = ref<HTMLInputElement | null>(null)
const pendingFiles = ref<File[]>([])

function openPicker(): void {
  inputRef.value?.click()
}

function handlePicked(event: Event): void {
  const input = event.target as HTMLInputElement
  const picked = Array.from(input.files ?? [])
  // 清空 input，允许再次选择同一个文件
  input.value = ''
  if (picked.length === 0) return
  pendingFiles.value = picked
}

function handleCompleted(file: FileObject): void {
  emit('completed', file)
}

function handleFailed(_task: UploadTask, error: Error): void {
  emit('failed', error.message || '上传失败，请稍后重试')
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
</style>
