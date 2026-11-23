<template>
  <div class="dashboard-page">
    <header class="dashboard-page__head">
      <div class="dashboard-page__title-wrap">
        <h2 class="dashboard-page__title">运营概览</h2>
        <p class="dashboard-page__sub">
          近 {{ days }} 天趋势 · 数据生成于 {{ generatedAtText }}
        </p>
      </div>
      <DashboardRefreshBar
        :loading="loading"
        :days="days"
        :last-success-at="lastSuccessAt"
        @refresh="refresh"
        @update:days="handleDaysChange"
      />
    </header>

    <el-alert
      v-if="errorMessage"
      class="dashboard-page__alert"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
    />

    <section class="dashboard-page__kpis" :class="{ 'is-empty': !kpis.length }">
      <DashboardKpiCard
        v-for="item in kpis"
        :key="item.key"
        :title="item.title"
        :value="item.value"
        :hint="item.hint"
        :tone="item.tone"
      />
    </section>

    <section class="dashboard-page__charts" v-loading="loading && !summary">
      <LazyGate
        :state="trendChart"
        label="登录趋势图表"
        :height="336"
        title="登录趋势"
        :dates="trend.dates"
        :values="trend.logins"
        color="#409eff"
      />
      <LazyGate
        :state="trendChart"
        label="文件上传趋势图表"
        :height="336"
        title="文件上传趋势"
        :dates="trend.dates"
        :values="trend.uploads"
        color="#67c23a"
      />
      <LazyGate
        :state="trendChart"
        label="聊天消息趋势图表"
        :height="336"
        title="聊天消息趋势"
        :dates="trend.dates"
        :values="trend.chatMessages"
        color="#e6a23c"
      />
    </section>

    <section class="dashboard-page__panels" v-loading="loading && !summary">
      <DashboardSystemPanel v-if="summary" :system="summary.system" />
      <DashboardQueuePanel v-if="summary" :system="summary.system" />
    </section>

    <p v-if="!summary && !loading && !errorMessage" class="dashboard-page__placeholder">
      暂无数据
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import DashboardKpiCard from './components/DashboardKpiCard.vue'
import LazyGate from '../perf/LazyGate.vue'
import { createLazyComponent } from '../perf/lazyComponent'
import DashboardSystemPanel from './components/DashboardSystemPanel.vue'
import DashboardQueuePanel from './components/DashboardQueuePanel.vue'
import DashboardRefreshBar from './components/DashboardRefreshBar.vue'
import { getDashboardSummary } from './api/dashboard.api'
import { formatBytes, formatDateTime } from './utils/format'
import type { AdminDashboardSummary, DashboardDays, KpiItem } from './types'

/* ==========================================================================
   【本地调试区｜提交前必须保持为注释状态，不要取消注释后 commit】
   本页面默认只调用真实接口。想在没有后端的情况下看样式时，
   在你本机临时改两处即可（改完调试完请恢复原样）：
     1) 取消下一行注释：
        // import { mockDashboardNormal } from './mock/dashboard.mock'
     2) 在 refresh() 成功分支前临时插入一行：
        // summary.value = mockDashboardNormal; lastSuccessAt.value = new Date(); return
   ========================================================================== */

/** 轮询间隔：15 秒（与后端缓存 TTL 一致） */
const REFRESH_MS = 15_000

/**
 * 图表组件含 ECharts，单独拆 chunk。
 * 图表属于首屏内容，所以挂载后立即加载，不延迟到用户交互。
 */
const trendChart = createLazyComponent(() => import('./components/DashboardTrendChart.vue'))

const days = ref<DashboardDays>(7)
const summary = ref<AdminDashboardSummary | null>(null)
const loading = ref(false)
const lastSuccessAt = ref<Date | null>(null)
const errorMessage = ref('')

let refreshTimer: ReturnType<typeof setInterval> | undefined
/** 单飞标记：上一次请求未结束时不重复发起，避免并发打爆接口 */
let inFlight = false

const trend = computed(() => summary.value?.trend ?? { dates: [], logins: [], uploads: [], chatMessages: [] })

const generatedAtText = computed(() =>
  summary.value ? formatDateTime(summary.value.generatedAt) : '—',
)

/**
 * 拉取大屏数据。
 * 失败时保留旧数据，仅在顶部提示，不让页面变成空白。
 */
async function refresh(): Promise<void> {
  if (inFlight) return
  inFlight = true
  loading.value = true
  try {
    const data = await getDashboardSummary(days.value)
    summary.value = data
    lastSuccessAt.value = new Date()
    errorMessage.value = ''
  } catch (error) {
    const message = error instanceof Error ? error.message : '未知错误'
    errorMessage.value = `数据刷新失败：${message}（当前展示的是上次成功数据）`
  } finally {
    loading.value = false
    inFlight = false
  }
}

function startTimer(): void {
  stopTimer()
  refreshTimer = setInterval(() => {
    // 页面被切到后台时不发请求，节省资源也避免无意义报错
    if (document.visibilityState === 'visible') {
      void refresh()
    }
  }, REFRESH_MS)
}

function stopTimer(): void {
  if (refreshTimer !== undefined) {
    clearInterval(refreshTimer)
    refreshTimer = undefined
  }
}

/** 页面隐藏则暂停轮询；重新可见立刻补一次刷新并恢复轮询 */
function handleVisibilityChange(): void {
  if (document.visibilityState === 'visible') {
    void refresh()
    startTimer()
  } else {
    stopTimer()
  }
}

function handleDaysChange(next: DashboardDays): void {
  if (days.value === next) return
  days.value = next
}

/** KPI 卡片：主指标放大，其余指标收进 hint，保持首排 4 张卡的版式 */
const kpis = computed<KpiItem[]>(() => {
  const cards = summary.value?.cards
  if (!cards) return []
  return [
    {
      key: 'users',
      title: '注册用户',
      value: cards.totalUsers,
      hint: `启用 ${cards.enabledUsers}｜今日登录 ${cards.todayLogins}｜失败 ${cards.todayLoginFailures}`,
      tone: cards.todayLoginFailures > 0 ? 'warning' : 'primary',
    },
    {
      key: 'files',
      title: '文件总数',
      value: cards.files,
      hint: `占用存储 ${formatBytes(cards.storageBytes)}`,
      tone: 'success',
    },
    {
      key: 'documents',
      title: '知识库文档',
      value: cards.documents,
      hint: `解析失败 ${cards.failedDocuments}`,
      tone: cards.failedDocuments > 0 ? 'warning' : 'success',
    },
    {
      key: 'chat',
      title: '聊天消息',
      value: cards.chatMessages,
      hint: `今日 ${cards.todayChatMessages}｜会话 ${cards.conversations}`,
      tone: 'primary',
    },
  ]
})

watch(days, () => {
  void refresh()
})

onMounted(() => {
  void trendChart.load()
  void refresh()
  startTimer()
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  stopTimer()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<style scoped>
.dashboard-page {
  padding: 16px 20px 24px;
  box-sizing: border-box;
}

.dashboard-page__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.dashboard-page__title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.dashboard-page__sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.dashboard-page__alert {
  margin-bottom: 12px;
}

.dashboard-page__kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.dashboard-page__charts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

/* 第三张图表占满整行，避免出现半格空白 */
.dashboard-page__charts > :nth-child(3) {
  grid-column: 1 / -1;
}

.dashboard-page__panels {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.dashboard-page__placeholder {
  margin-top: 24px;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

/* 1024 宽度：KPI 两列，图表仍两列 */
@media (max-width: 1200px) {
  .dashboard-page__kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/* 768 宽度：全部单列，便于手机查看 */
@media (max-width: 900px) {
  .dashboard-page {
    padding: 12px;
  }

  .dashboard-page__charts,
  .dashboard-page__panels {
    grid-template-columns: minmax(0, 1fr);
  }

  .dashboard-page__charts > :nth-child(3) {
    grid-column: auto;
  }
}
</style>
