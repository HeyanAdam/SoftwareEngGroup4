/**
 * 聊天接口适配层（C-1）
 *
 * 分层约定：
 * - 本文件只做「请求 → 类型化数据」的转换，不写页面逻辑
 * - 页面与 store 通过 ChatPort 取数（C-1 mock / C-2 realtimeClient）
 *
 * 路径来源（已对照远程 main 的 ChatController 核实，不是猜的）：
 * - GET    /api/v1/chat/conversations             仅需登录
 * - GET    /api/v1/chat/conversations/{id}/messages  权限 chat:send
 * - DELETE /api/v1/chat/conversations/{id}          权限 chat:send
 * - POST   /api/v1/chat/ws-ticket                  权限 chat:send（B 冻结）
 * - 当前没有 POST /api/v1/chat/conversations：新建会话由服务端在首次 chat.send 时创建
 *
 * 统一响应 R 已在 shared/api/request.ts 里拆掉，这里拿到的就是 data 本身。
 */

import { del, get, post } from '../../shared/api/request'
import type {
  ChatConversationRecord,
  ChatMessageRecord,
  ChatSendCommand,
  ChatSendPayload,
  ChatWsTicket,
} from './types'

/** 会话相关路径集中管理，联调时只改这里 */
export const CHAT_PATHS = {
  conversations: '/chat/conversations',
  messages: (conversationId: number): string => `/chat/conversations/${conversationId}/messages`,
  conversation: (conversationId: number): string => `/chat/conversations/${conversationId}`,
  wsTicket: '/chat/ws-ticket',
} as const

/** 申请一次性票据（B 冻结：chat:send，TTL 60s，只能消费一次） */
export function fetchWsTicket(): Promise<ChatWsTicket> {
  return post<ChatWsTicket>(CHAT_PATHS.wsTicket)
}

/** 会话列表（本人会话，后端已按登录用户过滤） */
export function fetchConversations(): Promise<ChatConversationRecord[]> {
  return get<ChatConversationRecord[]>(CHAT_PATHS.conversations)
}

/** 会话历史消息；referencesJson 是字符串，调用方需 parseReferences */
export function fetchHistoryMessages(conversationId: number): Promise<ChatMessageRecord[]> {
  return get<ChatMessageRecord[]>(CHAT_PATHS.messages(conversationId))
}

/** 删除会话（逻辑删除，后端置 deleted） */
export function deleteConversation(conversationId: number): Promise<void> {
  return del<void>(CHAT_PATHS.conversation(conversationId))
}

/** 发送载荷统一构造，避免页面各自拼字段 */
export function buildSendPayload(
  content: string,
  clientMessageId: string,
  conversationId: number | null,
  kbId: number | null,
): ChatSendPayload {
  return {
    conversationId,
    kbId,
    content,
    clientMessageId,
    requestId: clientMessageId,
  }
}

/** 载荷 → WebSocket 上行命令（C-2 直接发这个对象） */
export function buildChatSendCommand(payload: ChatSendPayload): ChatSendCommand {
  return {
    version: 1,
    type: 'chat.send',
    requestId: payload.requestId,
    clientMessageId: payload.clientMessageId,
    data: {
      conversationId: payload.conversationId,
      kbId: payload.kbId,
      content: payload.content,
    },
  }
}

/**
 * 停止生成目前只能做前端中断。
 * 当前 main 的 `RealtimeProtocol` 没有 `chat.cancel` 类型（B-2 只处理 chat.send），
 * 发过去会被当作未知类型丢弃，所以这里不构造该消息；C-2 联调前需与 B 确认后再补。
 */
