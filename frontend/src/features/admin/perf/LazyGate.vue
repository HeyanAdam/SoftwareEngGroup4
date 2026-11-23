<template>
  <!-- 已就绪：始终挂载真实组件，保证抽屉/弹窗关闭动画与内部状态不被卸载打断 -->
  <component :is="loadedComponent" v-if="statusValue === 'ready' && loadedComponent" v-bind="$attrs" />

  <!-- 未就绪：只在用户真正需要（active）时显示占位/错误，避免页面常驻空白块 -->
  <div
    v-else-if="active && statusValue === 'error'"
    class="lazy-gate__block lazy-gate__block--error"
    :class="{ 'is-overlay': variant === 'overlay' }"
    :style="blockStyle"
    role="alert"
  >
    <p class="lazy-gate__title">{{ label }}加载失败</p>
    <p class="lazy-gate__detail">{{ errorText }}</p>
    <el-button size="small" type="primary" plain @click="handleRetry">重试</el-button>
  </div>

  <div
    v-else-if="active"
    class="lazy-gate__block lazy-gate__block--loading"
    :class="{ 'is-overlay': variant === 'overlay' }"
    :style="blockStyle"
    role="status"
    aria-live="polite"
  >
    <el-skeleton :rows="2" animated class="lazy-gate__skeleton" />
    <p class="lazy-gate__title">正在加载{{ label }}…</p>
  </div>
</template>

<script setup lang="ts">
/**
 * 异步组件的外壳：
 * - 未就绪时显示固定高度的占位（`height` 保证布局不跳动）
 * - 失败时显示原因与「重试」入口，重试会重新发起一次加载
 * - 就绪后把父组件的 props / v-model / 事件原样转发给真实组件
 *
 * `variant="overlay"` 用于抽屉、弹窗这类覆盖层，占位居中显示，避免出现页面中间的空白块。
 */
import { computed } from 'vue'
import type { LazyComponentState } from './lazyComponent'

defineOptions({ inheritAttrs: false })

const props = withDefaults(
  defineProps<{
    state: LazyComponentState
    label?: string
    /** 占位高度（px），与真实组件高度一致时布局不跳动 */
    height?: number
    variant?: 'inline' | 'overlay'
    /** 是否正在被用户需要；false 时不显示占位，避免关着的抽屉在页面上留空块 */
    active?: boolean
  }>(),
  {
    label: '组件',
    height: 0,
    variant: 'inline',
    active: true,
  },
)

const blockStyle = computed(() => ({
  minHeight: props.height > 0 ? `${props.height}px` : '120px',
}))

/** 模板里嵌套的 ref 不会自动解包，这里显式取值 */
const statusValue = computed(() => props.state.status.value)
const loadedComponent = computed(() => props.state.component.value)

const errorText = computed(() => {
  const message = props.state.error.value?.message?.trim()
  return message ? `原因：${message}` : '网络请求失败或资源不存在'
})

function handleRetry(): void {
  void props.state.retry()
}
</script>

<style scoped>
.lazy-gate__block {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 16px;
  border: 1px dashed var(--el-border-color-light);
  border-radius: 8px;
  background: #fff;
}

.lazy-gate__block.is-overlay {
  position: fixed;
  top: 50%;
  left: 50%;
  z-index: 2100;
  width: min(360px, calc(100vw - 48px));
  transform: translate(-50%, -50%);
  box-shadow: 0 8px 24px rgb(31 43 38 / 12%);
}

.lazy-gate__title {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.lazy-gate__detail {
  margin: 0;
  max-width: 100%;
  overflow: hidden;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
  text-overflow: ellipsis;
}

.lazy-gate__skeleton {
  width: 100%;
  max-width: 240px;
}
</style>
