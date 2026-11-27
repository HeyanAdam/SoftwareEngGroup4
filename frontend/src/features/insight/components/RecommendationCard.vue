<template>
  <el-card class="rec-card" shadow="never">
    <div class="rec-card__head">
      <span class="rec-card__source">{{ sourceLabel }}</span>
      <h3 class="rec-card__title" :title="item.title">{{ item.title }}</h3>
    </div>

    <p class="rec-card__reason">{{ item.reason }}</p>

    <div class="rec-card__meta">
      <span v-if="item.subject" class="rec-card__tag">{{ item.subject }}</span>
      <span v-if="item.knowledgePoint" class="rec-card__tag">{{ item.knowledgePoint }}</span>
      <span v-if="item.estimatedMinutes !== null" class="rec-card__minutes">
        预计 {{ item.estimatedMinutes }} 分钟
      </span>
    </div>

    <div class="rec-card__actions">
      <el-button
        type="primary"
        size="small"
        :loading="accepting"
        :disabled="disabled || accepting"
        @click="handlePrimary"
      >
        {{ item.actionLabel }}
      </el-button>
      <el-button size="small" :disabled="disabled || accepting" @click="emit('dismiss', item)">
        暂不需要
      </el-button>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Recommendation } from '../types'
import { isSafeActionPath, RECOMMENDATION_SCENE_LABELS } from '../utils'

/** 单条推荐卡：只展示契约 4.3 的字段，score 只用于排序，不上屏 */
interface Props {
  item: Recommendation
  accepting?: boolean
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  accepting: false,
  disabled: false,
})

const emit = defineEmits<{
  accept: [item: Recommendation]
  dismiss: [item: Recommendation]
  open: [item: Recommendation]
}>()

const sourceLabel = computed(() => RECOMMENDATION_SCENE_LABELS[props.item.scene])

/**
 * 主按钮行为由 actionType 决定（契约 3.5）：
 * CREATE_TASK → 走接受推荐接口；OPEN_CHAT / OPEN_RESOURCE → 交给页面跳转。
 * actionPath 不安全时按禁用处理，不发起任何跳转。
 */
const primaryDisabled = computed(
  () =>
    props.disabled ||
    (props.item.actionType !== 'CREATE_TASK' && !isSafeActionPath(props.item.actionPath)),
)

function handlePrimary(): void {
  if (props.disabled || props.accepting || primaryDisabled.value) return
  if (props.item.actionType === 'CREATE_TASK') {
    emit('accept', props.item)
    return
  }
  emit('open', props.item)
}
</script>

<style scoped>
.rec-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.rec-card__head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.rec-card__source {
  flex: none;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.rec-card__title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rec-card__reason {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.rec-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.rec-card__tag {
  padding: 0 6px;
  border-radius: 4px;
  font-size: 11px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
}

.rec-card__minutes {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.rec-card__actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

@media (max-width: 600px) {
  .rec-card__actions {
    flex-wrap: wrap;
  }
}
</style>
