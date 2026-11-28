<template>
  <div class="settings-page" v-loading="loading">
    <header class="settings-page__header">
      <div>
        <h2>学习目标与画像</h2>
        <p>学习计划将根据这里的目标、基础水平和每周可用时间生成。</p>
      </div>
      <el-button @click="router.push('/study-plans')">返回学习计划</el-button>
    </header>

    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      show-icon
      :closable="false"
      class="settings-page__alert"
    />

    <el-form label-position="top" class="settings-grid">
      <el-card shadow="never" class="settings-card">
        <template #header>
          <div class="settings-card__head">
            <strong>个人学习画像</strong>
            <span>用于控制每日任务量和基础难度</span>
          </div>
        </template>

        <div class="settings-fields settings-fields--two">
          <el-form-item label="本科专业">
            <el-input v-model="profile.major" maxlength="100" placeholder="例如：软件工程" />
          </el-form-item>
          <el-form-item label="年级">
            <el-input v-model="profile.grade" maxlength="32" placeholder="例如：大四 / 二战" />
          </el-form-item>
          <el-form-item label="当前基础">
            <el-select v-model="profile.baseLevel" style="width: 100%">
              <el-option label="零基础" :value="1" />
              <el-option label="一般" :value="2" />
              <el-option label="较好" :value="3" />
              <el-option label="优秀" :value="4" />
            </el-select>
          </el-form-item>
          <el-form-item label="每周可用学习时间（分钟）">
            <el-input-number
              v-model="profile.weeklyStudyMinutes"
              :min="0"
              :max="10080"
              :step="30"
              style="width: 100%"
            />
          </el-form-item>
        </div>

        <el-form-item label="当前薄弱点">
          <el-select
            v-model="profile.weakPoints"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="可多选或直接输入"
            style="width: 100%"
          >
            <el-option
              v-for="item in weakPointOptions"
              :key="item"
              :label="item"
              :value="item"
            />
          </el-select>
        </el-form-item>
      </el-card>

      <el-card shadow="never" class="settings-card">
        <template #header>
          <div class="settings-card__head">
            <strong>备考目标</strong>
            <span>同一考试年度只保留一条进行中的目标</span>
          </div>
        </template>

        <div class="settings-fields settings-fields--two">
          <el-form-item label="考试年度" required>
            <el-input-number
              v-model="goal.examYear"
              :min="2000"
              :max="2100"
              :step="1"
              step-strictly
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="考试日期">
            <el-date-picker
              v-model="goal.examDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择预计考试日期"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="目标院校">
            <el-input v-model="goal.targetSchoolName" maxlength="100" placeholder="例如：某某大学" />
          </el-form-item>
          <el-form-item label="目标专业">
            <el-input v-model="goal.targetMajor" maxlength="100" placeholder="例如：计算机技术" />
          </el-form-item>
          <el-form-item label="目标总分">
            <el-input-number
              v-model="goal.targetScore"
              :min="0"
              :max="500"
              :step="5"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="备考节奏">
            <el-select v-model="goal.planType" style="width: 100%">
              <el-option label="常规备考" :value="1" />
              <el-option label="冲刺备考" :value="2" />
              <el-option label="在职备考" :value="3" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="单科目标分">
          <div class="subject-scores">
            <div v-for="item in subjectScoreFields" :key="item.key" class="subject-score">
              <span>{{ item.label }}</span>
              <el-input-number
                v-model="goal.subjectScores[item.key]"
                :min="0"
                :max="150"
                :step="5"
                controls-position="right"
              />
            </div>
          </div>
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model="goal.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="例如：数学一，目标进入复试"
          />
        </el-form-item>
      </el-card>
    </el-form>

    <footer class="settings-page__footer">
      <el-button :loading="loading" @click="load">重新加载</el-button>
      <el-button type="primary" :loading="saving" @click="save">
        保存并返回学习计划
      </el-button>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  createGoal,
  getProfile,
  listActiveGoals,
  saveProfile,
  updateGoal,
} from './settings.api'
import type { StudyGoalPayload, UserProfilePayload } from './types'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const activeGoalId = ref<number | null>(null)

const weakPointOptions = ['高等数学', '线性代数', '概率论', '英语阅读', '英语作文', '政治']
const subjectScoreFields = [
  { key: 'math', label: '数学' },
  { key: 'english', label: '英语' },
  { key: 'politics', label: '政治' },
  { key: 'professional', label: '专业课' },
] as const

const profile = reactive({
  gender: null as number | null,
  major: '',
  grade: '',
  baseLevel: 2,
  weeklyStudyMinutes: 600,
  weakPoints: [] as string[],
  extra: {} as Record<string, unknown>,
})

const goal = reactive({
  examYear: new Date().getFullYear() + 1,
  examDate: null as string | null,
  targetSchoolName: '',
  targetMajor: '',
  targetScore: undefined as number | undefined,
  planType: 1,
  subjectScores: {
    math: undefined as number | undefined,
    english: undefined as number | undefined,
    politics: undefined as number | undefined,
    professional: undefined as number | undefined,
  },
  remark: '',
})

function toWeakPoints(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  return value.map((item) => String(item).trim()).filter(Boolean)
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const [profileRecord, goals] = await Promise.all([getProfile(), listActiveGoals()])
    profile.gender = profileRecord.gender
    profile.major = profileRecord.major ?? ''
    profile.grade = profileRecord.grade ?? ''
    profile.baseLevel = profileRecord.baseLevel ?? 2
    profile.weeklyStudyMinutes = profileRecord.weeklyStudyMinutes || 600
    profile.weakPoints = toWeakPoints(profileRecord.weakPointsJson)
    profile.extra = profileRecord.profileJson && typeof profileRecord.profileJson === 'object'
      ? { ...(profileRecord.profileJson as Record<string, unknown>) }
      : {}

    const current = goals[0] ?? null
    activeGoalId.value = current?.id ?? null
    if (current) {
      goal.examYear = current.examYear
      goal.examDate = current.examDate
      goal.targetSchoolName = current.targetSchoolName ?? ''
      goal.targetMajor = current.targetMajor ?? ''
      goal.targetScore = current.targetScore ?? undefined
      goal.planType = current.planType ?? 1
      goal.remark = current.remark ?? ''
      for (const item of subjectScoreFields) {
        goal.subjectScores[item.key] = current.targetSubjectScores?.[item.key]
      }
    }
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '学习设置加载失败'
  } finally {
    loading.value = false
  }
}

function buildProfilePayload(): UserProfilePayload {
  return {
    gender: profile.gender,
    major: profile.major.trim() || null,
    grade: profile.grade.trim() || null,
    baseLevel: profile.baseLevel,
    weeklyStudyMinutes: profile.weeklyStudyMinutes,
    weakPoints: profile.weakPoints,
    profile: profile.extra,
  }
}

function buildGoalPayload(): StudyGoalPayload {
  const scores: Record<string, number> = {}
  for (const item of subjectScoreFields) {
    const value = goal.subjectScores[item.key]
    if (typeof value === 'number' && value > 0) scores[item.key] = value
  }
  return {
    targetSchoolId: null,
    targetSchoolName: goal.targetSchoolName.trim() || null,
    targetMajor: goal.targetMajor.trim() || null,
    targetScore: goal.targetScore ?? null,
    targetSubjectScores: scores,
    examYear: goal.examYear,
    examDate: goal.examDate,
    planType: goal.planType,
    status: 1,
    remark: goal.remark.trim() || null,
  }
}

async function save(): Promise<void> {
  if (!goal.examYear) {
    ElMessage.warning('请填写考试年度')
    return
  }
  saving.value = true
  try {
    await saveProfile(buildProfilePayload())
    const payload = buildGoalPayload()
    if (activeGoalId.value) {
      await updateGoal(activeGoalId.value, payload)
    } else {
      const created = await createGoal(payload)
      activeGoalId.value = created.id
    }
    ElMessage.success('学习目标已保存')
    await router.push('/study-plans')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
.settings-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 1180px;
  margin: 0 auto;
}

.settings-page__header,
.settings-page__footer {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.settings-page__header h2 {
  margin: 0;
  font-size: 20px;
}

.settings-page__header p {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.settings-page__alert {
  margin: 0;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  align-items: start;
}

.settings-card {
  border-radius: 10px;
}

.settings-card__head {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.settings-card__head span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.settings-fields--two {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.subject-scores {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  width: 100%;
}

.subject-score {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.subject-score span {
  flex: none;
  font-size: 13px;
}

.subject-score :deep(.el-input-number) {
  width: 118px;
}

.settings-page__footer {
  justify-content: flex-end;
  padding-bottom: 24px;
}

@media (max-width: 900px) {
  .settings-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 600px) {
  .settings-page__header {
    flex-direction: column;
  }

  .settings-fields--two,
  .subject-scores {
    grid-template-columns: minmax(0, 1fr);
  }

  .settings-page__footer {
    justify-content: stretch;
  }

  .settings-page__footer :deep(.el-button) {
    flex: 1;
  }
}
</style>
