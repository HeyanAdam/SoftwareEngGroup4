<template>
  <div class="refs">
    <div class="refs__head">
      <span class="refs__title">引用来源</span>
      <span class="refs__count">{{ references.length }} 条</span>
    </div>
    <ul class="refs__items">
      <li v-for="(item, index) in references" :key="`${item.index}-${index}`" class="refs__item">
        <div class="refs__name">{{ item.fileName }}</div>
        <div v-if="item.summary" class="refs__snippet">{{ item.summary }}</div>
        <div class="refs__score">相似度 {{ formatScore(item.score) }}</div>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import type { ReferenceItem } from '../types'

interface Props {
  /** 展示层引用（学生脱敏文件名 / 管理员真实文档名已抹平） */
  references: ReferenceItem[]
}

defineProps<Props>()

/** 相似度统一保留一位小数展示 */
function formatScore(score: number): string {
  if (!Number.isFinite(score)) return '—'
  return `${(score * 100).toFixed(1)}%`
}
</script>

<style scoped>
.refs {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}

.refs__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.refs__title {
  font-weight: 600;
}

.refs__items {
  list-style: none;
  margin: 6px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.refs__item {
  padding-left: 8px;
  border-left: 2px solid var(--el-color-primary-light-5);
}

.refs__name {
  font-size: 12px;
  color: var(--el-color-primary);
  word-break: break-all;
}

.refs__snippet {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
}

.refs__score {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
