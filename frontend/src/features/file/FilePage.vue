<template>
  <div class="file-page">
    <div class="page-header">
      <h2>我的文件</h2>
      <UploadEntry :scene="currentScene" :engine="uploadEngine" />
    </div>

    <div class="filters">
      <el-select v-model="scene" style="width: 160px" @change="handleSceneChange">
        <el-option
          v-for="option in SCENE_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索当前页文件名（后端暂不支持服务端搜索）"
        style="width: 320px"
      />
      <el-button :loading="loading" @click="loadFiles">刷新</el-button>
      <span class="tip">
        共 {{ total }} 个文件，当前页过滤结果 {{ visibleFiles.length }} 条
        <template v-if="visibleUploadTasks.length > 0">
          ，上传任务 {{ visibleUploadTasks.length }} 个
        </template>
      </span>
    </div>

    <FileTable
      :files="visibleFiles"
      :upload-tasks="visibleUploadTasks"
      :loading="loading"
      @download="handleDownload"
      @remove="handleRemove"
      @toggle-upload="handleToggleUpload"
      @cancel-upload="handleCancelUpload"
    />

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import FileTable from './components/FileTable.vue'
import UploadEntry from './upload/UploadEntry.vue'
import { deleteFile, getDownloadUrl, listFiles } from './file.api'
import { UploadEngine } from './uploader'
import type { FileObject, FileScene, UploadTask } from './types'

const SCENE_OPTIONS: Array<{ value: '' | FileScene; label: string }> = [
  { value: '', label: '全部场景' },
  { value: 'STUDY_MATERIAL', label: '学习资料' },
  { value: 'CHAT_ATTACHMENT', label: '聊天附件' },
  { value: 'QUESTION_IMAGE', label: '题目图片' },
  { value: 'KNOWLEDGE_DOCUMENT', label: '知识文档' },
  { value: 'AVATAR', label: '头像' },
]

const loading = ref(false)
const files = ref<FileObject[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const scene = ref<'' | FileScene>('')
const keyword = ref('')

const uploadEngine = new UploadEngine({
  onCompleted: (task, file) => handleUploadCompleted(task, file),
  onFailed: (_task, error) => handleUploadFailed(error),
})

/** 上传时使用的场景：筛选为“全部”时默认落到学习资料 */
const currentScene = computed<FileScene>(() => scene.value || 'STUDY_MATERIAL')

/** 后端没有 keyword 参数，只能对当前页数据做前端过滤 */
const visibleFiles = computed<FileObject[]>(() => {
  const key = keyword.value.trim().toLowerCase()
  if (!key) return files.value
  return files.value.filter((file) =>
    file.originalFilename.toLowerCase().includes(key),
  )
})

const visibleUploadTasks = computed<UploadTask[]>(() => {
  const key = keyword.value.trim().toLowerCase()
  return uploadEngine.tasks.filter((task) => {
    if (task.status === 'COMPLETED') return false
    if (scene.value && task.scene !== scene.value) return false
    return !key || task.fileName.toLowerCase().includes(key)
  })
})

function errorMessage(error: unknown): string {
  if (error instanceof Error && error.message) return error.message
  return '操作失败，请稍后重试'
}

async function loadFiles(): Promise<void> {
  loading.value = true
  try {
    const data = await listFiles({ scene: scene.value || undefined, page: page.value, size: size.value })
    files.value = data.list ?? []
    total.value = data.total ?? 0
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function handleSceneChange(): void {
  page.value = 1
  void loadFiles()
}

function handlePageChange(nextPage: number): void {
  page.value = nextPage
  void loadFiles()
}

function handleSizeChange(): void {
  page.value = 1
  void loadFiles()
}

function handleUploadCompleted(_task: UploadTask, file: FileObject): void {
  const duplicated = files.value.some((item) => item.id === file.id)
  if (duplicated) {
    // 后端同用户同 hash 会秒传复用旧记录，列表不会新增行
    ElMessage.info(`「${file.originalFilename}」已存在，已复用已有文件（秒传）`)
  } else {
    ElMessage.success(`「${file.originalFilename}」上传完成`)
  }
  void loadFiles()
}

function handleUploadFailed(error: Error): void {
  ElMessage.error(error.message || '上传失败，请稍后重试')
}

function handleToggleUpload(task: UploadTask): void {
  if (task.status === 'PAUSED') {
    uploadEngine.resumeTask(task.id)
    return
  }
  if (task.status === 'FAILED') {
    uploadEngine.retryTask(task.id)
    return
  }
  uploadEngine.pauseTask(task.id)
}

async function handleCancelUpload(task: UploadTask): Promise<void> {
  const cancelRequest = uploadEngine.cancelTask(task.id)
  uploadEngine.removeTask(task.id)
  await cancelRequest
}

async function handleDownload(file: FileObject): Promise<void> {
  try {
    const url = await getDownloadUrl(file.id)
    if (!url) {
      throw new Error('未获取到下载地址')
    }
    const link = document.createElement('a')
    link.href = url
    link.download = file.originalFilename
    link.target = '_blank'
    link.rel = 'noopener'
    document.body.appendChild(link)
    link.click()
    link.remove()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

async function handleRemove(file: FileObject): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除「${file.originalFilename}」？删除后不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  try {
    await deleteFile(file.id)
    ElMessage.success('已删除')
    // 当前页删空且不是第一页时回到上一页
    if (files.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await loadFiles()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

onMounted(() => {
  void loadFiles()
})

onUnmounted(() => {
  uploadEngine.dispose()
})
</script>

<style scoped>
.file-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}

.page-header h2 {
  margin: 0;
}

.filters {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.tip {
  font-size: 12px;
  color: #909399;
}

.pager {
  display: flex;
  justify-content: flex-end;
}
</style>
