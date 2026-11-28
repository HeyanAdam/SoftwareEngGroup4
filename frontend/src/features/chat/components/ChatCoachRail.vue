<template>
  <!-- 没有 recommendation:view 时整栏不渲染，也不发请求（权限码来自 shared/constants，前端 B 只引用） -->
  <section v-if="canView" class="coach-rail" :class="{ 'coach-rail--compact': compact }" data-test="chat-coach-rail">
    <div class="coach-rail__head">
      <!-- 标题交给推荐栏自己渲染（避免出现两个标题），这里只说明这批建议基于什么上下文 -->
      <p class="coach-rail__subtitle">{{ subtitle }}</p>
      <el-button
        link
        type="primary"
        size="small"
        :disabled="coachLoading"
        data-test="coach-rail-refresh"
        @click="handleRefresh"
      >
        换一批
      </el-button>
    </div>

    <div v-if="coachLoading && !coachVisibleItems.length" class="coach-rail__skeleton">
      <div v-for="index in 2" :key="index" class="coach-rail__skeleton-card">
        <span class="coach-rail__bone coach-rail__bone--title" />
        <span class="coach-rail__bone coach-rail__bone--line" />
        <span class="coach-rail__bone coach-rail__bone--short" />
      </div>
    </div>

    <div v-else-if="coachError && !coachVisibleItems.length" class="coach-rail__error">
      <p class="coach-rail__error-text">{{ coachError }}</p>
      <el-button size="small" @click="handleRetry">重试</el-button>
    </div>

    <div v-else-if="!coachVisibleItems.length" class="coach-rail__empty">
      <p class="coach-rail__empty-title">暂时没有推荐</p>
      <p class="coach-rail__empty-hint">
        完成今天的任务后，导学助手会根据你的目标和薄弱点给出下一步建议。
      </p>
    </div>

    <div v-else class="coach-rail__list">
      <h3 class="coach-rail__title">AI 导学推荐</h3>
      <!-- 复用前端 A 的 RecommendationCard：不复制卡片、不给它加私有字段 -->
      <RecommendationCard
        v-for="item in coachVisibleItems"
        :key="item.id"
        :item="item"
        :accepting="coachAcceptingIds.includes(item.id)"
        @accept="handleAccept"
        @dismiss="handleDismiss"
        @open="handleOpen"
      />
      <p v-if="coachBackupCount > 0" class="coach-rail__backup" data-test="coach-backup">
        本组 {{ coachVisibleItems.length }} 条 · 还有 {{ coachBackupCount }} 条备选，可点“换一批”
      </p>
      <p v-else class="coach-rail__backup">已经是这一轮的全部建议了</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { usePermission } from '../../../shared/utils/permission'
import { PERMISSIONS } from '../../../shared/constants/permissions'
import RecommendationCard from '../../insight/components/RecommendationCard.vue'
import { isSafeActionPath } from '../../insight/utils'
import type { Recommendation } from '../../insight/types'
import {
  acceptCoachRecommendation,
  coachAcceptingIds,
  coachBackupCount,
  coachError,
  coachLoading,
  coachQuery,
  coachVisibleItems,
  dismissCoachRecommendation,
  refreshCoach,
  reloadCoach,
} from '../chatCoach'

interface Props {
  /** 当前会话 id；草稿会话为 null，此时不做"进入会话"级推荐 */
  conversationId: number | null
  /** 窄屏抽屉里用的紧凑样式 */
  compact?: boolean
}

const props = withDefaults(defineProps<Props>(), { compact: false })

const router = useRouter()
const { hasPermission } = usePermission()
const canView = computed(() => hasPermission(PERMISSIONS.RECOMMENDATION_VIEW))

/** 推荐栏副标题：只说明"这批建议基于什么上下文"，不解释业务规则 */
const subtitle = computed(() => {
  const query = coachQuery.value
  if (!query) return '发送一个问题后，这里会给出针对性的下一步建议'
  if (query.contextType === 'MESSAGE') return '基于本次答疑延伸的建议'
  return '基于当前会话的建议'
})

async function handleRefresh(): Promise<void> {
  await refreshCoach(props.conversationId)
}

async function handleRetry(): Promise<void> {
  await reloadCoach()
}

/** 接受：只调前端 A 的 Store（不另建推荐 Store），失败提示不吞 */
async function handleAccept(item: Recommendation): Promise<void> {
  const error = await acceptCoachRecommendation(item)
  if (error) ElMessage.error(error)
}

async function handleDismiss(item: Recommendation): Promise<void> {
  const error = await dismissCoachRecommendation(item)
  if (error) ElMessage.error(error)
}

/** 纯跳转型建议：actionPath 只允许站内路径（契约 2.4） */
function handleOpen(item: Recommendation): void {
  if (!isSafeActionPath(item.actionPath)) {
    ElMessage.warning('这条建议的跳转地址不可用')
    return
  }
  router.push(item.actionPath as string)
}
</script>

<style scoped>
.coach-rail {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.coach-rail__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.coach-rail__subtitle {
  margin: 0;
  flex: 1 1 auto;
  min-width: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.coach-rail__title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.coach-rail__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.coach-rail__backup {
  margin: 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.coach-rail__skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.coach-rail__skeleton-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.coach-rail__bone {
  display: block;
  height: 10px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
}

.coach-rail__bone--title {
  width: 60%;
  height: 14px;
}

.coach-rail__bone--line {
  width: 90%;
}

.coach-rail__bone--short {
  width: 40%;
}

.coach-rail__error,
.coach-rail__empty {
  padding: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
}

.coach-rail__error-text {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--el-color-danger);
}

.coach-rail__empty-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.coach-rail__empty-hint {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.coach-rail--compact {
  gap: 8px;
}
</style>
