/**
 * 离线待发队列（C-2）
 *
 * 断网时把 chat.send 落 IndexedDB，恢复连接后用**同一个 clientMessageId** 重发，
 * 服务端靠 (conversation_id,user_id,client_message_id) 唯一约束去重。
 *
 * IndexedDB 不可用（隐私模式 / 单测）时退化为内存队列，保证页面不报错。
 */

import type { ChatSendPayload } from './types'

export interface PendingChatSend extends ChatSendPayload {
  /** 入队时间，便于排查滞留消息 */
  createdAt: number
}

export interface OfflineQueue {
  enqueue(item: PendingChatSend): Promise<void>
  list(): Promise<PendingChatSend[]>
  remove(clientMessageId: string): Promise<void>
  clear(): Promise<void>
}

/** 内存实现：单测与降级用 */
export function createMemoryOfflineQueue(): OfflineQueue {
  const items = new Map<string, PendingChatSend>()
  return {
    async enqueue(item) {
      items.set(item.clientMessageId, item)
    },
    async list() {
      return [...items.values()].sort((a, b) => a.createdAt - b.createdAt)
    },
    async remove(clientMessageId) {
      items.delete(clientMessageId)
    },
    async clear() {
      items.clear()
    },
  }
}

const DB_NAME = 'kaoyan-chat'
const STORE_NAME = 'pending-sends'
const DB_VERSION = 1

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME, { keyPath: 'clientMessageId' })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error ?? new Error('IndexedDB 打开失败'))
  })
}

function runTransaction<T>(
  db: IDBDatabase,
  mode: IDBTransactionMode,
  task: (store: IDBObjectStore) => IDBRequest<T>,
): Promise<T> {
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(STORE_NAME, mode)
    const request = task(transaction.objectStore(STORE_NAME))
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error ?? new Error('IndexedDB 操作失败'))
  })
}

/** 真实实现；环境不支持时自动降级为内存队列 */
export function createIndexedDbOfflineQueue(): OfflineQueue {
  if (typeof indexedDB === 'undefined') return createMemoryOfflineQueue()

  let dbPromise: Promise<IDBDatabase> | null = null
  const db = (): Promise<IDBDatabase> => {
    if (!dbPromise) dbPromise = openDatabase()
    return dbPromise
  }

  return {
    async enqueue(item) {
      const database = await db()
      await runTransaction(database, 'readwrite', (store) => store.put(item))
    },
    async list() {
      const database = await db()
      const all = await runTransaction<PendingChatSend[]>(
        database,
        'readonly',
        (store) => store.getAll() as IDBRequest<PendingChatSend[]>,
      )
      return all.sort((a, b) => a.createdAt - b.createdAt)
    },
    async remove(clientMessageId) {
      const database = await db()
      await runTransaction(database, 'readwrite', (store) => store.delete(clientMessageId))
    },
    async clear() {
      const database = await db()
      await runTransaction(database, 'readwrite', (store) => store.clear())
    },
  }
}
