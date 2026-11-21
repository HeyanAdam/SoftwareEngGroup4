<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ApiError } from '../../../../shared/api/request'
import { createRole, updateRole } from '../../api/admin.api'
import type { AdminRoleSummary } from '../../api/admin.types'

const props = defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  role: AdminRoleSummary | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved', role: AdminRoleSummary | null): void
}>()

const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive({
  roleCode: '',
  roleName: '',
  description: '',
})

const isCreate = computed(() => props.mode === 'create')
const title = computed(() =>
  isCreate.value ? '新建角色' : `编辑角色 · ${props.role?.roleName ?? ''}`,
)

/** 角色码规则：大写字母开头，3~32 位大写字母 / 数字 / 下划线（与后端 role_code 约束一致） */
const ROLE_CODE_PATTERN = /^[A-Z][A-Z0-9_]{2,31}$/

const rules: FormRules = {
  roleCode: [
    { required: true, message: '请输入角色码', trigger: 'blur' },
    {
      pattern: ROLE_CODE_PATTERN,
      message: '需以大写字母开头，3~32 位大写字母、数字或下划线',
      trigger: 'blur',
    },
  ],
  roleName: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { max: 30, message: '角色名称不超过 30 个字符', trigger: 'blur' },
  ],
  description: [{ max: 200, message: '描述不超过 200 个字符', trigger: 'blur' }],
}

/** 打开时用当前角色回填；编辑模式角色码只读展示 */
watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    form.roleCode = props.role?.roleCode ?? ''
    form.roleName = props.role?.roleName ?? ''
    form.description = props.role?.description ?? ''
    void nextTick(() => formRef.value?.clearValidate())
  },
  { immediate: true },
)

function close(): void {
  emit('update:modelValue', false)
}

async function handleSubmit(): Promise<void> {
  if (saving.value) return

  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const roleName = form.roleName.trim()
  const description = form.description.trim()
  saving.value = true

  try {
    if (isCreate.value) {
      const created = await createRole({
        roleCode: form.roleCode.trim(),
        roleName,
        description,
      })
      ElMessage.success('角色已创建')
      emit('saved', {
        ...created,
        description: created?.description ?? description,
        permissionCount: 0,
        userCount: 0,
      })
    } else if (props.role) {
      const updated = await updateRole(props.role.id, { roleName, description })
      ElMessage.success('角色已更新')
      emit('saved', {
        ...props.role,
        roleName: updated?.roleName ?? roleName,
        description: updated?.description ?? description,
      })
    }
    close()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="480px"
    :close-on-click-modal="false"
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
    @closed="formRef?.clearValidate()"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="84px">
      <el-form-item label="角色码" prop="roleCode">
        <el-input
          v-model="form.roleCode"
          :disabled="!isCreate"
          maxlength="32"
          placeholder="如 DATA_AUDITOR"
          show-word-limit
        />
        <p class="role-form__hint">
          {{ isCreate ? '角色码创建后不可修改，请使用全大写字母、数字或下划线。' : '角色码用于鉴权与迁移，创建后不可修改。' }}
        </p>
      </el-form-item>

      <el-form-item label="角色名称" prop="roleName">
        <el-input v-model="form.roleName" maxlength="30" placeholder="如 数据审计员" show-word-limit />
      </el-form-item>

      <el-form-item label="描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          maxlength="200"
          placeholder="这个角色负责什么（选填）"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">
        {{ isCreate ? '创建' : '保存' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.role-form__hint {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #93a09a;
}

.role-form__hint + * {
  margin-top: 0;
}
</style>
