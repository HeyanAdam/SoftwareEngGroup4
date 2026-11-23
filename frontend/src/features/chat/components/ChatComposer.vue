<template>
  <div class="composer">
    <div class="composer__toolbar">
      <el-select
        :model-value="kbId ?? undefined"
        placeholder="知识库范围（暂未开放）"
        size="small"
        disabled
        class="composer__kb"
      >
        <el-option label="全部知识库" :value="0" />
      </el-select>
      <span class="composer__hint">Enter 发送 · Shift + Enter 换行</span>
    </div>

    <el-input
      :model-value="modelValue"
      type="textarea"
      :autosize="{ minRows: 2, maxRows: 5 }"
      resize="none"
      maxlength="2000"
      show-word-limit
      placeholder="输入你的问题，例如：线性表的链式存储为什么更适合频繁插入？"
      :disabled="disabled"
      @update:model-value="onInput"
      @keydown="onKeydown"
    />

    <div class="composer__actions">
      <el-button v-if="!sending" type="primary" :disabled="disabled || !modelValue.trim()" @click="emit('send')">
        发送
      </el-button>
      <el-button v-else type="danger" plain @click="emit('stop')">停止生成</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  /** 输入框内容（v-model:modelValue） */
  modelValue: string
  /** 是否正在生成回答 */
  sending: boolean
  /** 连接断开等场景整体禁用 */
  disabled?: boolean
  /** 知识库范围，C-1 固定 null */
  kbId?: number | null
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  kbId: null,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  send: []
  stop: []
}>()

function onInput(value: string): void {
  emit('update:modelValue', value)
}

/** Enter 发送，Shift + Enter 换行；输入法组合中不拦截 */
function onKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter' || event.shiftKey) return
  if (event.isComposing) return
  event.preventDefault()
  if (props.disabled || props.sending || !props.modelValue.trim()) return
  emit('send')
}
</script>

<style scoped>
.composer {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-light);
}

.composer__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.composer__kb {
  width: 200px;
}

.composer__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.composer__actions {
  display: flex;
  justify-content: flex-end;
}
</style>
