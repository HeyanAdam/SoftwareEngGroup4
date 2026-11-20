<!--
  UploadEntry：文件页面的上传插槽（D 维护）

  B 交付 UploadTaskCenter.vue 之后的替换方法，只改这一个文件：
  1. 打开下面这行 import（路径按 B 实际交付位置调整）：
     import UploadTaskCenter from '../components/UploadTaskCenter.vue'
  2. 模板里把 <el-button ...>上传文件</el-button> 与隐藏 input 整段替换成：
     <UploadTaskCenter
       :files="pendingFiles"
       :scene="props.scene"
       :auto-start="true"
       @completed="(file: FileObjectVO) => emit('completed', file)"
       @failed="(message: string) => emit('failed', message)"
     />
  3. 删除本文件里的临时直传逻辑（handlePicked 及 uploadDirect/uploadBatch 调用）。
  FilePage.vue 不需要任何改动。
-->
<template>
  <div class="upload-entry">
    <el-button
      v-permission="'file:upload'"
      type="primary"
      :loading="uploading"
      @click="openPicker"
    >
      上传文件
    </el-button>
    <input ref="inputRef" class="hidden-input" type="file" multiple @change="handlePicked" />
    <p class="hint">
      当前为临时直传（单文件上限 50MB）。大文件分片、暂停、续传由 B 的 UploadTaskCenter 接入后启用。
    </p>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  DIRECT_MAX_SIZE,
  uploadBatch,
  uploadDirect,
  type FileObjectVO,
  type FileScene,
} from '../api/tempFileApi'

const props = defineProps<{ scene: FileScene }>()

const emit = defineEmits<{
  completed: [file: FileObjectVO]
  failed: [message: string]
}>()

const inputRef = ref<HTMLInputElement | null>(null)
const uploading = ref(false)

function openPicker(): void {
  inputRef.value?.click()
}

function errorMessage(error: unknown): string {
  if (error instanceof Error && error.message) return error.message
  return '上传失败，请稍后重试'
}

async function handlePicked(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const picked = Array.from(input.files ?? [])
  // 清空 input，允许再次选择同一个文件
  input.value = ''
  if (picked.length === 0) return

  const directFiles = picked.filter((file) => file.size <= DIRECT_MAX_SIZE)
  const oversizeFiles = picked.filter((file) => file.size > DIRECT_MAX_SIZE)
  if (oversizeFiles.length > 0) {
    ElMessage.warning(
      `${oversizeFiles.length} 个文件超过 50MB，需要分片上传，待 B 的组件接入后再传`,
    )
  }
  if (directFiles.length === 0) return

  uploading.value = true
  try {
    if (directFiles.length === 1) {
      const file = await uploadDirect(directFiles[0], props.scene)
      emit('completed', file)
    } else {
      const uploaded = await uploadBatch(directFiles, props.scene)
      uploaded.forEach((file) => emit('completed', file))
    }
    ElMessage.success('上传完成')
  } catch (error) {
    const message = errorMessage(error)
    emit('failed', message)
    ElMessage.error(message)
  } finally {
    uploading.value = false
  }
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
