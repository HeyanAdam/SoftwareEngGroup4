/**
 * 聊天历史增量加载（纯函数，可单测）
 *
 * 与页面/Store 解耦，只处理两件事：
 * 1. 把「更早一页」合并到现有消息前面，并按消息 ID 去重；
 * 2. 计算插入更早消息后的滚动位置，保证阅读位置不跳动。
 */

import type { ChatViewMessage } from './types'

/** 历史分页默认页大小（与后端 DEFAULT_HISTORY_PAGE_SIZE 一致） */
export const HISTORY_PAGE_SIZE = 20

/** 去重键：服务端消息用 ID，本地消息用 localId */
function messageKey(message: ChatViewMessage): string {
  return message.serverId != null ? `server:${message.serverId}` : `local:${message.localId}`
}

/**
 * 合并更早消息：按顺序前置到现有列表，重复消息（同 serverId）直接丢弃。
 * 不修改入参；没有新增消息时返回原数组引用，便于调用方判断是否需要滚动修正。
 */
export function mergeEarlierMessages(
  current: ChatViewMessage[],
  earlier: ChatViewMessage[],
): ChatViewMessage[] {
  if (!earlier.length) return current
  const seen = new Set(current.map(messageKey))
  const fresh = earlier.filter((message) => {
    const key = messageKey(message)
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
  if (!fresh.length) return current
  return [...fresh, ...current]
}

/**
 * 插入更早消息后恢复滚动位置：
 * 容器高度增加了 delta，滚动距离同步加 delta，用户看到的内容保持不动。
 */
export function computeRestoredScrollTop(
  previousHeight: number,
  previousTop: number,
  nextHeight: number,
): number {
  const delta = nextHeight - previousHeight
  return Math.max(0, previousTop + Math.max(0, delta))
}
