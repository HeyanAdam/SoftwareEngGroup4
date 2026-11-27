<template>
  <section class="rec-rail" :aria-busy="loading">
    <header class="rec-rail__head">
      <h3 class="rec-rail__title">{{ contextLabel || 'AI 导学推荐' }}</h3>
      <span v-if="!loading && !errorMessage && visibleItems.length" class="rec-rail__count">
        {{ visibleItems.length }} 条
      </span>
    </header>

    <div v-if="loading" class="rec-rail__skeleton">
      <div v-for="index in 2" :key="index" class="rec-rail__skeleton-card">
        <span class="rec-rail__bone rec-rail__bone--title" />
        <span class="rec-rail__bone rec-rail__bone--line" />
        <span class="rec-rail__bone rec-rail__bone--short" />
      </div>
    </div>

    <div v-else-if="errorMessage" class="rec-rail__error">
      <p class="rec-rail__error-text">{{ errorMessage }}</p>
      <el-button size="small" @click="emit('retry')">重试</el-button>
    </div>

    <div v-else-if="!visibleItems.length" class="rec-rail__empty">
      <p class="rec-rail__empty-title">暂时没有推荐</p>
      <p class="rec-rail__empty-hint">
        完成今天的任务后，导学助手会根据你的目标和薄弱点给出下一步建议。
      </p>
    </div>

    <div v-else class="rec-rail__list">
      <RecommendationCard
        v-for="item in visibleItems"
        :key="item.id"
        :item="item"
        :accepting="acceptingIds.includes(item.id)"
        @accept="handleAccept"
        @dismiss="handleDismiss"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import RecommendationCard from './RecommendationCard.vue'
import type { Recommendation } from '../types'
import { normalizeRecommendations } from '../utils'

/**
 * 共享推荐栏：学习计划页（前端 A）和聊天页（前端 B）共用，不要另外复制推荐卡。
 * props / emits 与《前端 A 指南》3.3 节一致，前端 B 直接 import 使用。
 * 注意：按指南本栏只透传 accept / dismiss / retry；OPEN_CHAT、OPEN_RESOURCE 类推荐
 * 的跳转由页面用 RecommendationCard 自行处理。若前端 B 需要栏层透传 open，
 * 先在群里确认后加，不在本 PR 里私加第 4 个 emit。
 */
interface Props {
  items: Recommendation[]
  loading?: boolean
  errorMessage?: string
  contextLabel?: string
  acceptingIds?: number[]
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  errorMessage: '',
  contextLabel: '',
  acceptingIds: () => [],
})

const emit = defineEmits<{
  accept: [item: Recommendation]
  dismiss: [item: Recommendation]
  retry: []
}>()

/** 去重 + score 排序 + 最多 3 条（契约 1.6） */
const visibleItems = computed(() => normalizeRecommendations(props.items))

function handleAccept(item: Recommendation): void {
  emit('accept', item)
}

function handleDismiss(item: Recommendation): void {
  emit('dismiss', item)
}
</script>

<style scoped>
.rec-rail {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.rec-rail__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.rec-rail__title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.rec-rail__count {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.rec-rail__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rec-rail__skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rec-rail__skeleton-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.rec-rail__bone {
  display: block;
  height: 10px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
}

.rec-rail__bone--title {
  width: 60%;
  height: 14px;
}

.rec-rail__bone--line {
  width: 90%;
}

.rec-rail__bone--short {
  width: 40%;
}

.rec-rail__error,
.rec-rail__empty {
  padding: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
}

.rec-rail__error-text {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--el-color-danger);
}

.rec-rail__empty-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.rec-rail__empty-hint {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
</style>
