<template>
  <div class="file-page">
    <div class="page-header">
      <h2>我的文件</h2>
      <UploadEntry
        :scene="currentScene"
        @completed="handleUploadCompleted"
        @failed="handleUploadFailed"
      />
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
      <span class="tip">共 {{ total }} 个文件，当前页过滤结果 {{ visibleFiles.length }} 条</span>
    </div>

    <FileTable
      :files="visibleFiles"
      :loading="loading"
      @download="handleDownload"
      @remove="handleRemove"
      @retry="handleRetry"
    />

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadFiles"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import FileTable from './components/FileTable.vue'
import UploadEntry from './upload/UploadEntry.vue'
import {
  deleteFile,
  getDownloadUrl,
  listFiles,
  type FileObjectVO,
  type FileScene,
} from './api/tempFileApi'

const SCENE_OPTIONS = [
  { value: '', label: '全部场景' },
  { value: 'STUDY_MATERIAL', label: '学习资料' },
  { value: 'CHAT_ATTACHMENT', label: '聊天附件' },
  { value: 'QUESTION_IMAGE', label: '题目图片' },
  { value: 'KNOWLEDGE_DOCUMENT', label: '知识文档' },
  { value: 'AVATAR', label: '头像' },
]

const loading = ref(false)
const files = ref<FileObjectVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const scene = ref('')
const keyword = ref('')

/** 上传时使用的场景：筛选为“全部”时默认落到学习资料 */
const currentScene = computed<FileScene>(
  () => (scene.value || 'STUDY_MATERIAL') as FileScene,
)

/** 后端没有 keyword 参数，只能对当前页数据做前端过滤 */
const visibleFiles = computed<FileObjectVO[]>(() => {
  const key = keyword.value.trim().toLowerCase()
  if (!key) return files.value
  return files.value.filter((file) =>
    file.originalFilename.toLowerCase().includes(key),
  )
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

function handleSizeChange(): void {
  page.value = 1
  void loadFiles()
}

function handleUploadCompleted(file: FileObjectVO): void {
  const duplicated = files.value.some((item) => item.id === file.id)
  if (duplicated) {
    // 后端同用户同 hash 会秒传复用旧记录，列表不会新增行
    ElMessage.info(`「${file.originalFilename}」已存在，已复用已有文件（秒传）`)
  }
  void loadFiles()
}

function handleUploadFailed(message: string): void {
  ElMessage.error(message)
}

async function handleDownload(file: FileObjectVO): Promise<void> {
  try {
    const { url } = await getDownloadUrl(file.id)
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

async function handleRemove(file: FileObjectVO): Promise<void> {
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

function handleRetry(file: FileObjectVO): void {
  ElMessage.warning(
    `「${file.originalFilename}」当前状态为 ${file.status}，请重新选择该文件上传。后端暂不支持失败会话直接重试合并。`,
  )
}

onMounted(() => {
  void loadFiles()
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
