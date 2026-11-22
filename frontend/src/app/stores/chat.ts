/**
 * 聊天 Store（C-1）
 *
 * 职责：会话列表、当前会话、消息数组、连接状态、发送/重试/停止/新建会话。
 * 分层：数据来源通过 ChatPort 注入（C-1 = mock，C-2 = 真实 WebSocket 客户端），
 *       本文件不出现任何 WebSocket 协议细节，只消费 ChatServerEvent。
 */

import { defineStore } from 'pinia'
import { buildSendPayload } from '../../features/chat/chat.api'
import {
  applyServerMessage,
  createAssistantMessage,
  createUserMessage,
  markMessageCancelled,
  markMessageFailed,
  prepareRetry,
  toViewMessage,
} from '../../features/chat/chat.messages'
import { createMockChatPort } from '../../features/chat/chat.mock'
import type {
  ChatConversationRecord,
  ChatPort,
  ChatViewMessage,
  RealtimeStatus,
  ServerMessage,
} from '../../features/chat/types'

/** 当前生效的数据端口；C-1 默认 mock，C-2 用 setChatPort 替换 */
let activePort: ChatPort = createMockChatPort()

/** 供 C-2 注入真实客户端，页面与 store 无需改动 */
export function setChatPort(port: ChatPort): void {
  activePort = port
}

/** 测试或调试时读取当前端口 */
export function getChatPort(): ChatPort {
  return activePort
}

interface ChatState {
  conversations: ChatConversationRecord[]
  activeConversationId: number | null
  messages: ChatViewMessage[]
  status: RealtimeStatus
  loadingConversations: boolean
  loadingMessages: boolean
  sending: boolean
  errorMessage: string
  /** 知识库范围（后端列表接口未就绪，C-1 固定 null 且选择器禁用） */
  selectedKbId: number | null
  /** 数据来源标识，C-1 为 mock */
  dataMode: 'mock' | 'realtime'
  /** 当前正在生成的助手消息 localId，用于停止生成 */
  activeAssistantLocalId: string | null
  /** 本地草稿会话：还没拿到服务端会话 ID（后端无 POST /conversations） */
  isDraftConversation: boolean
}

/** 会话标题：新会话用首个问题截断生成 */
function deriveTitle(content: string): string {
  const trimmed = content.trim().replace(/\s+/g, ' ')
  return trimmed.length > 24 ? `${trimmed.slice(0, 24)}…` : trimmed
}

/** 新建本地会话记录（后端首次 chat.send 时才真正创建会话） */
function createDraftConversation(title: string): ChatConversationRecord {
  const now = new Date().toISOString()
  return {
    id: -1,
    userId: 0,
    title: title || '新会话',
    kbId: null,
    deleted: 0,
    createTime: now,
    updateTime: now,
  }
}

export const useChatStore = defineStore('chat', {
  state: (): ChatState => ({
    conversations: [],
    activeConversationId: null,
    messages: [],
    status: 'disconnected',
    loadingConversations: false,
    loadingMessages: false,
    sending: false,
    errorMessage: '',
    selectedKbId: null,
    dataMode: 'mock',
    activeAssistantLocalId: null,
    isDraftConversation: false,
  }),

  actions: {
    /** 页面初始化：拉会话列表并默认打开第一个会话 */
    async init(): Promise<void> {
      await this.loadConversations()
      const first = this.conversations[0]
      if (first && this.activeConversationId === null) {
        await this.selectConversation(first.id)
      }
    },

    async loadConversations(): Promise<void> {
      this.loadingConversations = true
      this.errorMessage = ''
      try {
        this.conversations = await activePort.listConversations()
        // C-1 mock 端口视为已连接；C-2 由 realtimeClient 驱动状态
        this.status = 'connected'
      } catch (error) {
        this.status = 'disconnected'
        this.errorMessage = error instanceof Error ? error.message : '会话列表加载失败'
      } finally {
        this.loadingConversations = false
      }
    },

    /** 切换会话：清空消息后加载历史 */
    async selectConversation(conversationId: number): Promise<void> {
      if (this.activeConversationId === conversationId && this.messages.length > 0) return
      this.activeConversationId = conversationId
      this.isDraftConversation = false
      this.loadingMessages = true
      this.errorMessage = ''
      try {
        const history = await activePort.listMessages(conversationId)
        this.messages = history.map(toViewMessage)
      } catch (error) {
        this.messages = []
        this.errorMessage = error instanceof Error ? error.message : '历史消息加载失败'
      } finally {
        this.loadingMessages = false
      }
    },

    /**
     * 新建会话：后端没有 POST /conversations，
     * 所以先只建本地草稿会话，首次发送时 conversationId 传 null 由服务端创建。
     */
    startNewConversation(): void {
      if (this.sending) return
      this.activeConversationId = null
      this.isDraftConversation = true
      this.messages = []
      this.errorMessage = ''
    },

    /** 删除会话：先删远端，再从本地列表移除 */
    async removeConversation(conversationId: number): Promise<void> {
      try {
        await activePort.deleteConversation(conversationId)
      } catch {
        // 删除失败不打断列表操作，由顶部错误条统一提示
      }
      this.conversations = this.conversations.filter((item) => item.id !== conversationId)
      if (this.activeConversationId === conversationId) {
        this.activeConversationId = null
        this.messages = []
        const next = this.conversations[0]
        if (next) await this.selectConversation(next.id)
      }
    },

    /** 选择知识库范围（C-1 无真实列表，只保存值） */
    setKbId(kbId: number | null): void {
      this.selectedKbId = kbId
    },

    /** 发送新问题：本地先插用户消息和助手占位，再流式填充 */
    async send(content: string): Promise<void> {
      const text = content.trim()
      if (!text || this.sending) return
      const userMessage = createUserMessage(text, this.activeConversationId)
      this.messages = [...this.messages, userMessage]
      await this.streamAnswer(userMessage)
    },

    /** 失败/停止后重试：复用原用户消息与 clientMessageId，只清掉助手残留 */
    async retry(userLocalId: string): Promise<void> {
      if (this.sending) return
      const userMessage = this.messages.find((item) => item.localId === userLocalId)
      if (!userMessage || userMessage.role !== 'user') return
      this.messages = prepareRetry(this.messages, userLocalId)
      const kept = this.messages.find((item) => item.localId === userLocalId)
      if (!kept) return
      await this.streamAnswer(kept)
    },

    /**
     * 停止生成：交给端口 abort（真实链路由 C-2 发 chat.cancel，服务端回 done+cancelled）。
     * 端口若没回事件，这里兜底把助手消息落为 cancelled——中性态，不写 errorMessage。
     */
    stop(): void {
      if (!this.sending) return
      activePort.abort()
      const assistantLocalId = this.activeAssistantLocalId
      if (assistantLocalId) {
        const current = this.messages.find((item) => item.localId === assistantLocalId)
        if (current && current.status !== 'cancelled' && current.status !== 'completed') {
          this.messages = markMessageCancelled(this.messages, assistantLocalId)
        }
      }
      this.sending = false
      this.activeAssistantLocalId = null
    },

    /** 统一流式入口：send 与 retry 共用，避免两套逻辑漂移 */
    async streamAnswer(userMessage: ChatViewMessage): Promise<void> {
      const assistant = createAssistantMessage()
      this.messages = [...this.messages, assistant]
      this.activeAssistantLocalId = assistant.localId
      this.sending = true
      this.errorMessage = ''

      const payload = buildSendPayload(
        userMessage.content,
        userMessage.clientMessageId,
        this.activeConversationId,
        this.selectedKbId,
      )

      try {
        await activePort.sendQuestion(payload, (message: ServerMessage) => {
          this.messages = applyServerMessage(this.messages, message)
          this.syncConversationFromEvent(message, userMessage.content)
        })
      } catch (error) {
        const message = error instanceof Error ? error.message : '发送失败'
        this.messages = markMessageFailed(this.messages, assistant.localId, message)
      } finally {
        this.sending = false
        this.activeAssistantLocalId = null
        this.syncConversationMeta(userMessage.content)
      }
    },

    /**
     * ack / meta 带回 conversationId 时更新当前会话。
     * ack 是连接层消息（data 里有 streamId），meta 是聊天事件，两者都带 conversationId。
     */
    syncConversationFromEvent(message: ServerMessage, content: string): void {
      if (message.type !== 'ack' && message.type !== 'meta') return
      const conversationId = message.data.conversationId
      if (!conversationId) return

      const previousId = this.activeConversationId
      if (previousId && previousId > 0 && previousId !== conversationId) {
        // 服务端把旧会话换了 ID（理论上不会发生），直接替换
        this.conversations = this.conversations.map((item) =>
          item.id === previousId ? { ...item, id: conversationId } : item,
        )
      }

      this.activeConversationId = conversationId
      this.isDraftConversation = false
      this.messages = this.messages.map((item) =>
        item.conversationId === undefined ? { ...item, conversationId } : item,
      )

      if (!this.conversations.some((item) => item.id === conversationId)) {
        const draft = createDraftConversation(deriveTitle(content))
        this.conversations = [
          { ...draft, id: conversationId, updateTime: new Date().toISOString() },
          ...this.conversations.filter((item) => item.id !== -1),
        ]
      }
    },

    /** 发送结束后刷新会话更新时间与标题 */
    syncConversationMeta(content: string): void {
      const conversationId = this.activeConversationId
      if (!conversationId) return
      this.conversations = this.conversations.map((item) =>
        item.id === conversationId
          ? {
              ...item,
              updateTime: new Date().toISOString(),
              title: item.title || deriveTitle(content),
            }
          : item,
      )
    },
  },
})
