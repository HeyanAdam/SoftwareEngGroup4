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
          <!-- 窄屏：推荐栏收进抽屉，用这个按钮打开（1440/1024 显示右栏，768/375 用抽屉） -->
          <el-button
            v-if="!isWide && canRecommendation"
            size="small"
            plain
            type="primary"
            class="chat-page__coach-btn"
            data-test="open-coach-drawer"
            @click="coachDrawerVisible = true"
          >
            导学建议
          </el-button>
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

      <div ref="scrollRef" v-loading="chat.loadingMessages" class="chat-page__messages">
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

    <!-- 宽屏第三栏：即时导学推荐。数据来自前端 A 的 Insight Store，这里只负责何时触发 -->
    <aside v-if="isWide && canRecommendation" class="chat-page__coach" data-test="chat-coach-aside">
      <ChatCoachRail :conversation-id="chat.activeConversationId" />
    </aside>

    <!-- 窄屏：同一份推荐换抽屉展示 -->
    <el-drawer
      v-model="coachDrawerVisible"
      title="AI 导学建议"
      direction="rtl"
      :size="coachDrawerSize"
    >
      <ChatCoachRail :conversation-id="chat.activeConversationId" compact />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getChatPort, setChatPort, useChatStore } from '../../app/stores/chat'
import { TOKEN_STORAGE_KEY } from '../../shared/api/request'
import { usePermission } from '../../shared/utils/permission'
import { PERMISSIONS } from '../../shared/constants/permissions'
import ChatComposer from './components/ChatComposer.vue'
import ChatConversationList from './components/ChatConversationList.vue'
import ChatMessageItem from './components/ChatMessageItem.vue'
import ChatCoachRail from './components/ChatCoachRail.vue'
import { createRealtimeChatClient } from './realtimeClient'
import { loadCoachForConversation, loadCoachForMessage } from './chatCoach'
import type { ChatViewMessage } from './types'

const chat = useChatStore()
const { hasPermission } = usePermission()
const canRecommendation = computed(() => hasPermission(PERMISSIONS.RECOMMENDATION_VIEW))

const draft = ref('')
const scrollRef = ref<HTMLDivElement | null>(null)

/** 宽屏断点：≥1180 出第三栏推荐，窄于它改为顶部按钮 + 抽屉（保证 1024/768/375 都不挤） */
const WIDE_BREAKPOINT = 1180
const isWide = ref(typeof window !== 'undefined' && window.innerWidth >= WIDE_BREAKPOINT)
const coachDrawerVisible = ref(false)
/** 手机抽屉宽度：92vw 比固定像素稳，375 宽也不溢出 */
const coachDrawerSize = ref('420px')

function syncViewport(): void {
  isWide.value = window.innerWidth >= WIDE_BREAKPOINT
  coachDrawerSize.value = window.innerWidth < 768 ? '92%' : '440px'
}

onMounted(() => {
  syncViewport()
  window.addEventListener('resize', syncViewport)
  // 已登录走真实链路；未登录（如直接打开演示页）继续用 C-1 的 mock
  if (localStorage.getItem(TOKEN_STORAGE_KEY) && getChatPort().kind !== 'realtime') {
    setChatPort(createRealtimeChatClient())
  }
  void chat.init()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport)
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

watch(
  () => chat.messages.reduce((total, item) => total + item.content.length, 0),
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

/**
 * 已触发过推荐的那条助手消息签名（localId:serverId）。
 * 必须在下面的 immediate watcher 之前声明：immediate 会同步执行回调，晚声明会踩 TDZ。
 */
let lastCoachSignature = ''

/** 进入/切换会话：CONVERSATION + conversationId（草稿会话 id 为 null，不发请求） */
watch(
  () => chat.activeConversationId,
  (conversationId) => {
    lastCoachSignature = ''
    if (!canRecommendation.value) return
    void loadCoachForConversation(conversationId)
  },
  { immediate: true },
)

/**
 * 一条助手回答完成才触发 MESSAGE 级推荐：
 * - 只看最后一条消息，且必须是 assistant；
 * - status === 'completed' 且 serverId 存在（delta 阶段 status 还是 streaming，绝不会命中）；
 * - 同一个 localId:serverId 只触发一次，避免 store 其它字段变化时重复请求。
 */
const completedAssistantSignature = computed(() => {
  const last = chat.messages[chat.messages.length - 1]
  if (!last || last.role !== 'assistant') return ''
  if (last.status !== 'completed' || !last.serverId) return ''
  return `${last.localId}:${last.serverId}`
})

watch(completedAssistantSignature, (signature) => {
  if (!signature || signature === lastCoachSignature) return
  if (!canRecommendation.value) return
  const last = chat.messages[chat.messages.length - 1]
  if (!last?.serverId) return
  lastCoachSignature = signature
  void loadCoachForMessage(last.serverId)
})

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
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

/* 右栏导学建议：和消息区等高、独立滚动，避免长推荐把输入框顶下去 */
.chat-page__coach {
  flex: 0 0 320px;
  width: 320px;
  min-height: 0;
  overflow-y: auto;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
}

.chat-page__coach-btn {
  flex: none;
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
