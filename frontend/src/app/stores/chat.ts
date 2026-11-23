/**
 * 聊天 Store（C-1）
 *
 * 职责：会话列表、当前会话、消息数组、连接状态、发送/重试/停止/新建会话。
 * 分层：数据来源通过 ChatPort 注入（C-1 = mock，C-2 = 真实 WebSocket 客户端），
 *       本文件不出现任何 WebSocket 协议细节，只消费 ChatServerEvent。
 */

import { defineStore } from 'pinia'
import { buildSendPayload } from '../../features/chat/chat.api'
import { HISTORY_PAGE_SIZE, mergeEarlierMessages } from '../../features/chat/chat.history'
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
import { isChatEvent } from '../../features/chat/types'
import type {
  ChatConversationRecord,
  ChatPort,
  ChatViewMessage,
  RealtimeStatus,
  ServerMessage,
} from '../../features/chat/types'

/** 当前生效的数据端口；C-1 默认 mock，C-2 用 setChatPort 替换 */
let activePort: ChatPort = createMockChatPort()

/** 端口是否已挂接连接/状态订阅，避免重复 connect */
let portAttached = false

/** 供 C-2 注入真实客户端，页面与 store 无需改动 */
export function setChatPort(port: ChatPort): void {
  if (port === activePort) return
  activePort.dispose?.()
  activePort = port
  portAttached = false
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
  /** 本次发送的原始问题，用于新会话标题 */
  pendingContent: string
  /** 本地草稿会话：还没拿到服务端会话 ID（后端无 POST /conversations） */
  isDraftConversation: boolean
  /** 当前会话是否还有更早消息 */
  hasMoreMessages: boolean
  /** 正在加载更早消息 */
  loadingEarlier: boolean
  /** 加载更早消息失败的原因（非空时展示重试入口） */
  earlierError: string
  /** 继续向前翻页的游标（当前列表最早一条的服务端 ID） */
  nextBeforeId: number | null
  /** 历史加载代次：切换会话或新建会话时自增，过期响应直接丢弃 */
  messageLoadToken: number
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
    pendingContent: '',
    isDraftConversation: false,
    hasMoreMessages: false,
    loadingEarlier: false,
    earlierError: '',
    nextBeforeId: null,
    messageLoadToken: 0,
  }),

  actions: {
    /**
     * 挂接端口（C-2）：真实链路订阅连接状态与全局事件，
     * 这样另一标签页发起的回答也能在当前页面流式显示。
     */
    attachPort(): void {
      if (portAttached) return
      portAttached = true
      this.dataMode = activePort.kind === 'realtime' ? 'realtime' : 'mock'
      activePort.onStatusChange?.((status) => {
        this.status = status
      })
      activePort.connect?.((message) => {
        this.handleServerMessage(message)
      })
    },

    /** 页面初始化：先挂接端口，再拉会话列表并默认打开第一个会话 */
    async init(): Promise<void> {
      this.attachPort()
      await this.loadConversations()
      if (this.dataMode === 'mock') this.status = 'connected'
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
        // mock 端口视为已连接；真实链路状态由 realtimeClient 驱动
        if (activePort.kind !== 'realtime') this.status = 'connected'
      } catch (error) {
        this.status = 'disconnected'
        this.errorMessage = error instanceof Error ? error.message : '会话列表加载失败'
      } finally {
        this.loadingConversations = false
      }
    },

    /**
     * 切换会话：清空消息后只加载最近一页。
     * 通过 messageLoadToken 标记代次，慢响应回到旧会话时直接丢弃，避免串写。
     */
    async selectConversation(conversationId: number): Promise<void> {
      if (this.activeConversationId === conversationId && this.messages.length > 0) return
      const token = this.messageLoadToken + 1
      this.messageLoadToken = token
      this.activeConversationId = conversationId
      this.isDraftConversation = false
      this.loadingMessages = true
      this.errorMessage = ''
      this.messages = []
      this.hasMoreMessages = false
      this.loadingEarlier = false
      this.earlierError = ''
      this.nextBeforeId = null
      try {
        const page = await activePort.listMessages(conversationId, { limit: HISTORY_PAGE_SIZE })
        if (token !== this.messageLoadToken) return
        this.messages = page.items.map(toViewMessage)
        this.hasMoreMessages = page.hasMore
        this.nextBeforeId = page.nextBeforeId
      } catch (error) {
        if (token !== this.messageLoadToken) return
        this.messages = []
        this.hasMoreMessages = false
        this.nextBeforeId = null
        this.errorMessage = error instanceof Error ? error.message : '历史消息加载失败'
      } finally {
        if (token === this.messageLoadToken) this.loadingMessages = false
      }
    },

    /**
     * 加载更早一页：以 nextBeforeId 为游标向前翻。
     * 返回是否真的插入了新消息（页面据此决定是否恢复滚动位置）。
     * 并发、切换会话、没有更多数据时都不会重复请求。
     */
    async loadEarlierMessages(): Promise<boolean> {
      const conversationId = this.activeConversationId
      const beforeId = this.nextBeforeId
      if (this.loadingEarlier || this.loadingMessages) return false
      if (!this.hasMoreMessages) return false
      if (!conversationId || conversationId <= 0 || beforeId == null) return false

      const token = this.messageLoadToken
      this.loadingEarlier = true
      this.earlierError = ''
      try {
        const page = await activePort.listMessages(conversationId, {
          beforeId,
          limit: HISTORY_PAGE_SIZE,
        })
        if (token !== this.messageLoadToken || this.activeConversationId !== conversationId) {
          return false
        }
        const merged = mergeEarlierMessages(this.messages, page.items.map(toViewMessage))
        const added = merged !== this.messages
        this.messages = merged
        this.hasMoreMessages = page.hasMore
        // 游标只允许向前（变小），防止重复/乱序响应把游标推回去
        if (page.nextBeforeId != null && page.nextBeforeId < beforeId) {
          this.nextBeforeId = page.nextBeforeId
        }
        return added
      } catch (error) {
        if (token === this.messageLoadToken) {
          this.earlierError = error instanceof Error ? error.message : '更早消息加载失败'
        }
        return false
      } finally {
        if (token === this.messageLoadToken) this.loadingEarlier = false
      }
    },

    /**
     * 新建会话：后端没有 POST /conversations，
     * 所以先只建本地草稿会话，首次发送时 conversationId 传 null 由服务端创建。
     */
    startNewConversation(): void {
      if (this.sending) return
      this.messageLoadToken += 1
      this.activeConversationId = null
      this.isDraftConversation = true
      this.messages = []
      this.errorMessage = ''
      this.hasMoreMessages = false
      this.loadingEarlier = false
      this.earlierError = ''
      this.nextBeforeId = null
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
        this.messageLoadToken += 1
        this.activeConversationId = null
        this.messages = []
        this.hasMoreMessages = false
        this.loadingEarlier = false
        this.earlierError = ''
        this.nextBeforeId = null
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
      this.pendingContent = userMessage.content
      this.sending = true
      this.errorMessage = ''

      const payload = buildSendPayload(
        userMessage.content,
        userMessage.clientMessageId,
        this.activeConversationId,
        this.selectedKbId,
      )

      try {
        // 真实链路已通过 attachPort 全局订阅事件，这里传空回调避免重复应用
        const onMessage =
          this.dataMode === 'realtime'
            ? () => {}
            : (message: ServerMessage) => this.handleServerMessage(message)
        await activePort.sendQuestion(payload, onMessage)
      } catch (error) {
        const message = error instanceof Error ? error.message : '发送失败'
        this.messages = markMessageFailed(this.messages, assistant.localId, message)
      } finally {
        this.sending = false
        this.activeAssistantLocalId = null
        this.syncConversationMeta(this.pendingContent)
      }
    },

    /** 所有端口事件的统一入口：更新消息、同步会话、刷新连接态提示 */
    handleServerMessage(message: ServerMessage): void {
      // 其他标签页可能在不同会话里提问：带了别的 conversationId 就不污染当前会话
      if (message.type === 'ack' || message.type === 'meta') {
        const eventConversationId = message.data.conversationId
        if (
          eventConversationId &&
          this.activeConversationId &&
          this.activeConversationId > 0 &&
          this.activeConversationId !== eventConversationId
        ) {
          return
        }
      }
      this.messages = applyServerMessage(this.messages, message)
      this.syncConversationFromEvent(message, this.pendingContent)
      if (message.type === 'error' && !isChatEvent(message)) {
        this.errorMessage = message.data.reason || '连接异常'
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
