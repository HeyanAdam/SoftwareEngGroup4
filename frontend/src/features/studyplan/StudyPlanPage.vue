<template>
  <div class="study-plan">
    <header class="study-plan__header">
      <div class="study-plan__heading">
        <h2 class="study-plan__title">学习计划</h2>
        <p v-if="plan && !emptyPlan" class="study-plan__subtitle">
          {{ plan.title || '今日学习计划' }}
          <span class="study-plan__date">{{ plan.planDate }}</span>
        </p>
      </div>
      <div class="study-plan__header-actions">
        <span v-if="dataMode === 'mock'" class="study-plan__mode">Mock 数据</span>
        <el-button size="small" @click="openSettings">学习设置</el-button>
        <el-button v-if="plan && !emptyPlan" size="small" :loading="loadingPlan" @click="handleRefresh">
          刷新
        </el-button>
      </div>
    </header>

    <div class="study-plan__grid">
      <main class="study-plan__main">
        <el-skeleton v-if="loadingPlan && !plan" :rows="6" animated />

        <template v-else>
          <ExamCountdown
            v-if="plan && (plan.examDate || plan.daysUntilExam !== null)"
            :exam-date="plan.examDate"
            :days-until-exam="plan.daysUntilExam"
          />

          <div v-if="errorMessage" class="study-plan__error">
            <p class="study-plan__error-text">{{ errorMessage }}</p>
            <div class="study-plan__error-actions">
              <el-button v-if="needsGoal" size="small" type="primary" @click="openSettings">
                填写备考目标
              </el-button>
              <el-button size="small" :loading="loadingPlan" @click="handleRefresh">重试</el-button>
            </div>
          </div>

          <!-- 空态与错误互斥：网络/500 只显示错误卡，不能同时显示“还没有今日学习计划”（契约 9.1） -->
          <div v-if="emptyPlan && !errorMessage" class="study-plan__empty">
            <p class="study-plan__empty-title">还没有今日学习计划</p>
            <p class="study-plan__empty-hint">
              根据你的备考目标、基础水平和每周可学习时间，生成 1～3 项今日任务。
            </p>
            <el-button
              v-if="canGeneratePlan"
              type="primary"
              :loading="loadingPlan"
              @click="handleGenerate"
            >
              生成学习计划
            </el-button>
            <el-button v-if="canGeneratePlan" @click="openSettings">先完善学习目标</el-button>
            <p v-else class="study-plan__empty-hint">当前账号没有生成学习计划的权限。</p>
          </div>

          <template v-else-if="plan">
            <TodayStudySummary :summary="plan.summary" />

            <TodayTaskList
              :tasks="plan.tasks"
              :submitting-ids="submittingTaskIds"
              :can-manage="canManageTasks"
              @toggle="handleToggle"
              @add="openCreateTask"
              @edit="openEditTask"
              @remove="handleRemoveTask"
            />
          </template>

          <WeeklyDiagnosisCard
            :report="weeklyReport"
            :loading="loadingWeeklyReport"
            :error-message="weeklyReportErrorMessage"
            @retry="handleRetryWeeklyReport"
          />
        </template>
      </main>

      <aside v-if="canViewRecommendations" class="study-plan__rail">
        <RecommendationRail
          :items="recommendations"
          :loading="railLoading"
          :error-message="recommendationErrorMessage"
          :accepting-ids="acceptingRecommendationIds"
          context-label="根据今日计划推荐"
          @accept="handleAccept"
          @dismiss="handleDismiss"
          @retry="handleRetryRecommendations"
        />
      </aside>
    </div>

    <StudyTaskEditor
      v-model="taskEditorVisible"
      :task="editingTask"
      :loading="savingTask"
      @save="handleSaveTask"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { PERMISSIONS } from '../../shared/constants/permissions'
import { usePermission } from '../../shared/utils/permission'
import { useInsightStore } from '../../app/stores/insight'
import RecommendationRail from '../insight/components/RecommendationRail.vue'
import type { Recommendation } from '../insight/types'
import ExamCountdown from './components/ExamCountdown.vue'
import StudyTaskEditor from './components/StudyTaskEditor.vue'
import TodayStudySummary from './components/TodayStudySummary.vue'
import TodayTaskList from './components/TodayTaskList.vue'
import WeeklyDiagnosisCard from './components/WeeklyDiagnosisCard.vue'
import type { StudyTask, StudyTaskWritePayload } from './types'
import { isPlanEmpty, nextToggleStatus } from './utils'

/** 契约 8.1：403 缺少接口权限时的业务码 */
const PERMISSION_DENIED_CODE = 10008
const NO_EXAM_GOAL_CODE = 10000

/**
 * 学习计划页（前端 A）
 * 契约：5.1 今日计划、5.2 生成、5.3 任务状态、5.4 推荐、5.11 周报；状态处理 9.1 / 9.2。
 * 加载顺序：今日计划 → 推荐 → 周报；推荐与周报失败不阻塞任务列表。
 */
const store = useInsightStore()
const router = useRouter()
const {
  todayPlan,
  weeklyReport,
  recommendations,
  loadingPlan,
  loadingRecommendations,
  loadingWeeklyReport,
  submittingTaskIds,
  savingTask,
  acceptingRecommendationIds,
  errorMessage,
  recommendationErrorMessage,
  weeklyReportErrorMessage,
  lastErrorCode,
  dataMode,
} = storeToRefs(store)

const { hasPermission } = usePermission()
const canViewRecommendations = computed(() => hasPermission(PERMISSIONS.RECOMMENDATION_VIEW))
const canGeneratePlan = computed(() => hasPermission(PERMISSIONS.STUDY_PLAN_CREATE))
const canManageTasks = computed(() => hasPermission(PERMISSIONS.STUDY_TASK_UPDATE))

const plan = computed(() => todayPlan.value)
const emptyPlan = computed(() => isPlanEmpty(todayPlan.value))
const needsGoal = computed(
  () => lastErrorCode.value === NO_EXAM_GOAL_CODE || errorMessage.value.includes('备考目标'),
)

const taskEditorVisible = ref(false)
const editingTask = ref<StudyTask | null>(null)

/**
 * 推荐栏加载态：推荐请求未开始时（今日计划还在加载）也按 loading 渲染骨架屏。
 * 否则首屏会先闪一下“暂时没有推荐”的空态，再跳到骨架屏（指南 14：loading/empty 不闪烁）。
 */
const railLoading = computed(
  () => loadingRecommendations.value || (loadingPlan.value && recommendations.value.length === 0),
)

onMounted(async () => {
  await store.loadTodayStudy()
  // 契约 9.1：403 + 10008 走现有无权限页，不在页内伪装成普通错误
  if (store.lastErrorCode === PERMISSION_DENIED_CODE) {
    void router.push({ path: '/403', query: { code: String(PERMISSION_DENIED_CODE) } })
    return
  }
  void store.loadRecommendations('TODAY', 'NONE')
  void store.loadWeeklyReport()
})

function handleRefresh(): void {
  void store.loadTodayStudy()
}

function handleGenerate(): void {
  void store.generateTodayStudy()
}

function openSettings(): void {
  void router.push('/settings')
}

function openCreateTask(): void {
  editingTask.value = null
  taskEditorVisible.value = true
}

function openEditTask(task: StudyTask): void {
  editingTask.value = task
  taskEditorVisible.value = true
}

async function handleSaveTask(payload: StudyTaskWritePayload): Promise<void> {
  const ok = editingTask.value
    ? await store.editTask(editingTask.value.id, payload)
    : await store.createTask(payload)
  if (!ok) return
  taskEditorVisible.value = false
  ElMessage.success(editingTask.value ? '任务已更新' : '任务已添加')
  editingTask.value = null
}

async function handleRemoveTask(task: StudyTask): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除「${task.title}」？`, '删除任务', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  const ok = await store.removeTask(task.id)
  if (ok) ElMessage.success('任务已删除')
}

/** 任务完成 / 取消完成（契约 9.2，409 由 Store 统一处理） */
function handleToggle(task: StudyTask): void {
  void store.updateTaskStatus(task.id, nextToggleStatus(task.status))
}

/** 接受推荐：成功后提示“已加入今日任务”（契约指南 11 节）；失败由推荐区自行展示 */
async function handleAccept(item: Recommendation): Promise<void> {
  const accepted = await store.acceptRecommendation(item.id)
  if (accepted) ElMessage.success('已加入今日任务')
}

function handleDismiss(item: Recommendation): void {
  void store.dismissRecommendation(item.id)
}

function handleRetryRecommendations(): void {
  void store.loadRecommendations('TODAY', 'NONE')
}

function handleRetryWeeklyReport(): void {
  void store.loadWeeklyReport()
}
</script>

<style scoped>
.study-plan {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.study-plan__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.study-plan__title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.study-plan__subtitle {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.study-plan__date {
  margin-left: 8px;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
}

.study-plan__header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.study-plan__mode {
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.study-plan__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  align-items: start;
  gap: 16px;
}

.study-plan__main {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.study-plan__rail {
  position: sticky;
  top: 12px;
  min-width: 0;
}

.study-plan__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--el-color-danger-light-5);
  border-radius: 8px;
  background: var(--el-color-danger-light-9);
}

.study-plan__error-text {
  margin: 0;
  font-size: 12px;
  color: var(--el-color-danger);
}

.study-plan__error-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}

.study-plan__empty {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  padding: 24px 20px;
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
}

.study-plan__empty-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.study-plan__empty-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

@media (max-width: 1200px) {
  .study-plan__grid {
    grid-template-columns: minmax(0, 1fr) 260px;
  }
}

@media (max-width: 900px) {
  .study-plan__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .study-plan__rail {
    position: static;
  }
}

@media (max-width: 600px) {
  .study-plan__header {
    flex-direction: column;
    align-items: stretch;
  }

  .study-plan__header-actions {
    justify-content: flex-start;
  }

  .study-plan__empty {
    padding: 18px 14px;
  }
}
</style>
