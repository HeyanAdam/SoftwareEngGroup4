<template>
  <el-dialog
    v-model="visible"
    :title="task ? '编辑今日任务' : '添加今日任务'"
    width="520px"
    destroy-on-close
    :close-on-click-modal="!loading"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="任务标题" prop="title">
        <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="例如：复习拉格朗日中值定理" />
      </el-form-item>

      <div class="editor-grid">
        <el-form-item label="科目" prop="subject">
          <el-select
            v-model="form.subject"
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入科目"
            style="width: 100%"
          >
            <el-option v-for="item in subjects" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="预计时长">
          <el-input-number
            v-model="form.estimatedMinutes"
            :min="1"
            :max="600"
            :step="5"
            style="width: 100%"
          />
        </el-form-item>
      </div>

      <el-form-item label="知识点">
        <el-input v-model="form.knowledgePoint" maxlength="100" placeholder="例如：微分中值定理" />
      </el-form-item>

      <div class="editor-grid">
        <el-form-item label="截止时间">
          <el-date-picker
            v-model="form.dueAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="可不设置"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number
            v-model="form.priority"
            :min="0"
            :max="100"
            :step="10"
            style="width: 100%"
          />
        </el-form-item>
      </div>
    </el-form>

    <template #footer>
      <el-button :disabled="loading" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">
        {{ task ? '保存修改' : '添加任务' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { StudyTask, StudyTaskWritePayload } from '../types'

interface Props {
  modelValue: boolean
  task?: StudyTask | null
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  task: null,
  loading: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  save: [payload: StudyTaskWritePayload]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const formRef = ref<FormInstance>()
const subjects = ['数学', '英语', '政治', '专业课', '综合']

const form = reactive({
  title: '',
  subject: '数学',
  knowledgePoint: '',
  estimatedMinutes: 30,
  dueAt: null as string | null,
  priority: 50,
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入任务标题', trigger: 'blur' }],
  subject: [{ required: true, message: '请选择任务科目', trigger: 'change' }],
}

function localDateTime(hour: number, minute: number): string {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const hh = String(hour).padStart(2, '0')
  const mm = String(minute).padStart(2, '0')
  return `${year}-${month}-${day}T${hh}:${mm}:00`
}

function resetForm(): void {
  const task = props.task
  form.title = task?.title ?? ''
  form.subject = task?.subject ?? '数学'
  form.knowledgePoint = task?.knowledgePoint ?? ''
  form.estimatedMinutes = task?.estimatedMinutes ?? 30
  form.dueAt = task?.dueAt ? task.dueAt.slice(0, 19) : localDateTime(21, 30)
  form.priority = task?.priority ?? 50
  formRef.value?.clearValidate()
}

watch(
  () => [props.modelValue, props.task] as const,
  ([open]) => {
    if (open) resetForm()
  },
  { immediate: true },
)

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  emit('save', {
    title: form.title.trim(),
    subject: form.subject.trim(),
    knowledgePoint: form.knowledgePoint.trim() || null,
    estimatedMinutes: form.estimatedMinutes,
    dueAt: form.dueAt,
    priority: form.priority,
    ...(props.task ? { expectedVersion: props.task.version } : {}),
  })
}
</script>

<style scoped>
.editor-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

@media (max-width: 600px) {
  .editor-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
