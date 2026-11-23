<template>
  <el-card class="conv-list" shadow="never">
    <template #header>
      <div class="conv-list__header">
        <span class="conv-list__title">会话（{{ conversations.length }}）</span>
        <el-button link type="primary" size="small" :disabled="creatingDisabled" @click="emit('create')">
          新建会话
        </el-button>
      </div>
    </template>

    <el-skeleton v-if="loading" :rows="4" animated />

    <el-empty v-else-if="!conversations.length" description="暂无会话" :image-size="60" />

    <ul v-else class="conv-list__items">
      <li
        v-for="item in conversations"
        :key="item.id"
        class="conv-list__item"
        :class="{ 'is-active': item.id === activeId }"
        @click="emit('select', item.id)"
      >
        <div class="conv-list__item-title" :title="item.title">{{ item.title || '未命名会话' }}</div>
        <div class="conv-list__item-meta">
          <span>{{ formatTime(item.updateTime) }}</span>
          <el-button link type="danger" size="small" @click.stop="emit('remove', item.id)">
            删除
          </el-button>
        </div>
      </li>
    </ul>
  </el-card>
</template>

<script setup lang="ts">
import type { ChatConversationRecord } from '../types'

interface Props {
  /** 后端会话记录（ChatController 返回的字段） */
  conversations: ChatConversationRecord[]
  activeId: number | null
  loading: boolean
  /** 正在生成时禁止新建会话，避免并发 */
  creatingDisabled?: boolean
}

withDefaults(defineProps<Props>(), {
  creatingDisabled: false,
})

const emit = defineEmits<{
  select: [conversationId: number]
  remove: [conversationId: number]
  create: []
}>()

/** 今天显示 HH:mm，其余显示 MM-DD（字段为后端 updateTime） */
function formatTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  const pad = (value: number): string => String(value).padStart(2, '0')
  const now = new Date()
  const sameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  if (sameDay) return `${pad(date.getHours())}:${pad(date.getMinutes())}`
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}
</script>

<style scoped>
.conv-list {
  height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.conv-list :deep(.el-card__body) {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conv-list__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.conv-list__title {
  font-size: 14px;
  font-weight: 600;
}

.conv-list__items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.conv-list__item {
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: background-color 0.15s ease;
}

.conv-list__item:hover {
  background: var(--el-fill-color-light);
}

.conv-list__item.is-active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}

.conv-list__item-title {
  font-size: 13px;
  line-height: 1.4;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.conv-list__item-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
