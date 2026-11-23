<template>
  <div class="msg" :class="[`msg--${message.role}`, `msg--${message.status}`]">
    <div class="msg__head">
      <span class="msg__role">{{ message.role === 'user' ? '我' : 'AI 助手' }}</span>
      <el-tag v-if="statusTag" size="small" :type="statusTag.type" effect="plain">
        {{ statusTag.text }}
      </el-tag>
    </div>

    <div class="msg__body">
      <span class="msg__content">{{ message.content }}</span>
      <span v-if="message.status === 'streaming'" class="msg__cursor">▌</span>
    </div>

    <ChatReferenceList
      v-if="message.role === 'assistant' && message.references?.length"
      :references="message.references"
    />

    <!-- 失败：错误文案 + 重试 -->
    <div v-if="message.status === 'failed'" class="msg__footer">
      <span class="msg__error-text">{{ message.errorMessage || '生成失败' }}</span>
      <el-button
        v-if="retryTargetId"
        size="small"
        type="primary"
        plain
        :disabled="retryDisabled"
        @click="emit('retry', retryTargetId)"
      >
        重新生成
      </el-button>
    </div>

    <!-- 用户主动停止：中性态，不提示错误，但同样允许重新生成 -->
    <div v-else-if="message.status === 'cancelled'" class="msg__footer">
      <span class="msg__cancelled-text">已停止生成</span>
      <el-button
        v-if="retryTargetId"
        size="small"
        plain
        :disabled="retryDisabled"
        @click="emit('retry', retryTargetId)"
      >
        重新生成
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ChatReferenceList from './ChatReferenceList.vue'
import type { ChatViewMessage } from '../types'

interface Props {
  message: ChatViewMessage
  /** 失败/停止后重试要复用的用户消息 localId（保证 clientMessageId 不变） */
  retryTargetId?: string
  /** 正在生成时禁用重试，避免并发 */
  retryDisabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  retryTargetId: '',
  retryDisabled: false,
})

const emit = defineEmits<{
  retry: [userLocalId: string]
}>()

/** 状态标签：completed 不展示标签，streaming 用动画光标代替 */
const statusTag = computed<
  { text: string; type: 'info' | 'success' | 'warning' | 'danger' } | null
>(() => {
  switch (props.message.status) {
    case 'queued':
      return { text: '排队中', type: 'info' }
    case 'sending':
      return { text: '已送达', type: 'info' }
    case 'streaming':
      return { text: '生成中', type: 'warning' }
    case 'cancelled':
      return { text: '已停止', type: 'info' }
    case 'failed':
      return { text: '失败', type: 'danger' }
    default:
      return null
  }
})
</script>

<style scoped>
.msg {
  display: flex;
  flex-direction: column;
  margin-bottom: 16px;
}

.msg--user {
  align-items: flex-end;
}

.msg--assistant {
  align-items: flex-start;
}

.msg__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.msg__role {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.msg__body {
  max-width: 78%;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.msg--user .msg__body {
  background: var(--el-color-primary);
  color: #fff;
}

.msg--assistant .msg__body {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

.msg--failed .msg__body {
  border: 1px dashed var(--el-color-danger-light-5);
}

.msg--cancelled .msg__body {
  border: 1px dashed var(--el-border-color);
}

.msg__content {
  vertical-align: baseline;
}

.msg__cursor {
  display: inline-block;
  margin-left: 2px;
  color: var(--el-color-primary);
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0;
  }
}

.msg__footer {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
  max-width: 78%;
}

.msg__error-text {
  font-size: 12px;
  color: var(--el-color-danger);
}

.msg__cancelled-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
