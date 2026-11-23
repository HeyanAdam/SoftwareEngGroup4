<template>
  <div class="chat-page">
    <aside class="chat-page__aside">
      <ChatConversationList
        :conversations="chat.conversations"
        :active-id="chat.activeConversationId"
        :loading="chat.loadingConversations"
        :creating-disabled="chat.sending"
        @select="handleSelect"
        @remove="handleRemove"
        @create="handleCreate"
      />
    </aside>

    <section class="chat-page__main">
      <header class="chat-page__head">
        <div class="chat-page__title-wrap">
          <h2 class="chat-page__title">AI 答疑</h2>
          <p class="chat-page__sub">当前会话：{{ activeTitle }}</p>
        </div>
        <div class="chat-page__status">
          <el-tag v-if="chat.dataMode === 'mock'" size="small" type="warning" effect="plain">
            演示数据（C-1）
          </el-tag>
          <el-tag :type="statusTag.type" size="small">{{ statusTag.text }}</el-tag>
        </div>
      </header>

      <el-alert
        v-if="chat.errorMessage"
        class="chat-page__alert"
        :title="chat.errorMessage"
        type="error"
        show-icon
        :closable="false"
      />

      <div
        ref="scrollRef"
        v-loading="chat.loadingMessages"
        class="chat-page__messages"
        @scroll.passive="handleScroll"
      >
        <div
          v-if="chat.hasMoreMessages || chat.loadingEarlier || chat.earlierError"
          class="chat-page__earlier"
        >
          <el-button
            v-if="chat.earlierError"
            size="small"
            type="danger"
            plain
            @click="loadEarlier"
          >
            更早消息加载失败，点击重试
          </el-button>
          <span v-else-if="chat.loadingEarlier" class="chat-page__earlier-text">
            正在加载更早消息…
          </span>
          <el-button v-else size="small" text @click="loadEarlier">加载更早消息</el-button>
        </div>
        <p v-else-if="chat.messages.length" class="chat-page__earlier-text">
          没有更早的消息了
        </p>
        <el-empty
          v-if="!chat.messages.length && !chat.loadingMessages"
          :description="emptyText"
          :image-size="80"
        />
        <ChatMessageItem
          v-for="message in chat.messages"
          :key="message.localId"
          :message="message"
          :retry-target-id="retryTargetFor(message)"
          :retry-disabled="chat.sending"
          @retry="handleRetry"
        />
      </div>

      <ChatComposer
        v-model="draft"
        :sending="chat.sending"
        :disabled="chat.status === 'closed'"
        :kb-id="chat.selectedKbId"
        @send="handleSend"
        @stop="chat.stop"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { getChatPort, setChatPort, useChatStore } from '../../app/stores/chat'
import { TOKEN_STORAGE_KEY } from '../../shared/api/request'
import { computeRestoredScrollTop } from './chat.history'
import ChatComposer from './components/ChatComposer.vue'
import ChatConversationList from './components/ChatConversationList.vue'
import ChatMessageItem from './components/ChatMessageItem.vue'
import { createRealtimeChatClient } from './realtimeClient'
import type { ChatViewMessage } from './types'

const chat = useChatStore()

const draft = ref('')
const scrollRef = ref<HTMLDivElement | null>(null)

onMounted(() => {
  // 已登录走真实链路；未登录（如直接打开演示页）继续用 C-1 的 mock
  if (localStorage.getItem(TOKEN_STORAGE_KEY) && getChatPort().kind !== 'realtime') {
    setChatPort(createRealtimeChatClient())
  }
  void chat.init()
})

/** 连接状态 → 顶部标签 */
const statusTag = computed<{ text: string; type: 'success' | 'warning' | 'info' | 'danger' }>(() => {
  switch (chat.status) {
    case 'connected':
      return { text: '在线', type: 'success' }
    case 'connecting':
      return { text: '连接中', type: 'warning' }
    case 'reconnecting':
      return { text: '重连中', type: 'warning' }
    case 'closed':
      return { text: '已关闭', type: 'danger' }
    default:
      return { text: '离线', type: 'info' }
  }
})

const activeTitle = computed(() => {
  if (chat.isDraftConversation) return '新会话（未发送）'
  const current = chat.conversations.find((item) => item.id === chat.activeConversationId)
  return current?.title || '新会话'
})

const emptyText = computed(() =>
  chat.isDraftConversation ? '这是新会话，直接在下方提问，发送后才会在服务端创建' : '选择左侧会话，或直接在下方提问',
)

/**
 * 重试需要复用「上一条用户消息」的 localId：
 * 用户消息自身失败/停止 → 用自己的；助手消息失败/停止 → 往前找最近一条用户消息。
 */
function retryTargetFor(message: ChatViewMessage): string {
  const retryableStatus = message.status === 'failed' || message.status === 'cancelled'
  if (message.role === 'user') return retryableStatus ? message.localId : ''
  if (!retryableStatus) return ''
  const index = chat.messages.findIndex((item) => item.localId === message.localId)
  for (let cursor = index - 1; cursor >= 0; cursor -= 1) {
    if (chat.messages[cursor].role === 'user') return chat.messages[cursor].localId
  }
  return ''
}

async function scrollToBottom(): Promise<void> {
  await nextTick()
  const container = scrollRef.value
  if (container) container.scrollTop = container.scrollHeight
}

/**
 * 只有「末尾消息」变化时才滚到底部：
 * 插入更早消息时末尾不变，不会把用户从阅读位置甩到最底部；
 * 新发送/流式追加时末尾是新的助手消息，仍然自动跟随。
 */
watch(
  () => {
    const last = chat.messages[chat.messages.length - 1]
    return `${last?.localId ?? ''}:${last?.content.length ?? 0}`
  },
  () => {
    void scrollToBottom()
  },
)

watch(
  () => chat.activeConversationId,
  () => {
    void scrollToBottom()
  },
)

/** 触发一次加载更早消息，并在插入后恢复滚动位置 */
let loadingEarlierLocally = false

async function loadEarlier(): Promise<void> {
  if (loadingEarlierLocally) return
  const container = scrollRef.value
  const previousHeight = container?.scrollHeight ?? 0
  const previousTop = container?.scrollTop ?? 0
  loadingEarlierLocally = true
  try {
    const added = await chat.loadEarlierMessages()
    if (!added) return
    await nextTick()
    if (container) {
      container.scrollTop = computeRestoredScrollTop(
        previousHeight,
        previousTop,
        container.scrollHeight,
      )
    }
  } finally {
    loadingEarlierLocally = false
  }
}

/** 滚动接近顶部时自动加载更早消息；同时保留按钮入口，避免自动触发难以验收 */
function handleScroll(): void {
  const container = scrollRef.value
  if (!container) return
  if (container.scrollTop > 48) return
  if (!chat.hasMoreMessages || chat.loadingEarlier || chat.earlierError) return
  void loadEarlier()
}

function handleSelect(conversationId: number): void {
  void chat.selectConversation(conversationId)
}

function handleRemove(conversationId: number): void {
  void chat.removeConversation(conversationId)
}

function handleCreate(): void {
  chat.startNewConversation()
}

function handleRetry(userLocalId: string): void {
  void chat.retry(userLocalId)
}

function handleSend(): void {
  const text = draft.value
  if (!text.trim() || chat.sending) return
  draft.value = ''
  void chat.send(text)
}
</script>

<style scoped>
.chat-page {
  display: flex;
  gap: 16px;
  height: calc(100vh - 100px);
  min-height: 460px;
}

.chat-page__aside {
  width: 260px;
  flex: 0 0 260px;
  min-height: 0;
}

.chat-page__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

.chat-page__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.chat-page__title {
  margin: 0;
  font-size: 18px;
}

.chat-page__sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-page__status {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-page__messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.chat-page__earlier {
  display: flex;
  justify-content: center;
  padding: 4px 0 12px;
}

.chat-page__earlier-text {
  margin: 0 0 12px;
  text-align: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 窄屏：会话列表移到上方，消息区占满 */
@media (max-width: 900px) {
  .chat-page {
    flex-direction: column;
    height: auto;
  }

  .chat-page__aside {
    width: 100%;
    flex: none;
    height: 200px;
  }

  .chat-page__messages {
    min-height: 320px;
  }
}
</style>
