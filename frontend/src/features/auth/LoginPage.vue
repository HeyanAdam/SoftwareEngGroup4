<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  fetchCaptcha,
  loginWithEmailCode,
  loginWithPassword,
  sendVerificationCode,
} from './api/auth.api'
import {
  EMAIL_PATTERN,
  formatWait,
  patternRule,
  requiredRule,
} from './utils/validators'
import { useAuthStore } from '../../app/stores/auth'
import { ApiError } from '../../shared/api/request'
import './styles/auth.css'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const activeTab = ref<'password' | 'email'>('password')
const passwordFormRef = ref<FormInstance>()
const emailFormRef = ref<FormInstance>()

const passwordForm = reactive({
  account: '',
  password: '',
  captcha_code: '',
})

const emailForm = reactive({
  email: '',
  verify_code: '',
})

const passwordRules: FormRules = {
  account: [requiredRule('请输入用户名或邮箱')],
  password: [requiredRule('请输入密码')],
  captcha_code: [
    requiredRule('请输入图形验证码'),
    patternRule('图形验证码为 4 位', (value) => /^[A-Za-z0-9]{4}$/.test(value)),
  ],
}

const emailRules: FormRules = {
  email: [
    requiredRule('请输入邮箱'),
    patternRule('邮箱格式不正确', (value) => EMAIL_PATTERN.test(value)),
  ],
  verify_code: [
    requiredRule('请输入邮箱验证码'),
    patternRule('邮箱验证码为 6 位数字', (value) => /^\d{6}$/.test(value)),
  ],
}

const captchaImage = ref('')
const captchaKey = ref('')
const captchaLoading = ref(false)
const submitting = ref(false)
const countdown = ref(0)
let countdownTimer: number | undefined

async function loadCaptcha(): Promise<void> {
  captchaLoading.value = true
  try {
    const data = await fetchCaptcha()
    captchaImage.value = data.image_base64
    captchaKey.value = data.captcha_key
  } catch {
    ElMessage.error('验证码加载失败，请稍后重试')
  } finally {
    captchaLoading.value = false
  }
}

function startCountdown(seconds: number): void {
  countdown.value = Math.max(1, seconds)
  if (countdownTimer) window.clearInterval(countdownTimer)
  countdownTimer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      countdown.value = 0
      if (countdownTimer) {
        window.clearInterval(countdownTimer)
        countdownTimer = undefined
      }
    }
  }, 1000)
}

function redirectTarget(): string {
  const raw = route.query.redirect
  const value = Array.isArray(raw) ? raw[0] : raw
  if (typeof value === 'string' && value.startsWith('/')) {
    return value
  }
  return '/dashboard'
}

function handleError(error: unknown): void {
  if (!(error instanceof ApiError)) {
    ElMessage.error('操作失败，请稍后重试')
    return
  }

  // 验证码错误 / 过期：清空并刷新图形码
  if (error.code === 10005) {
    passwordForm.captcha_code = ''
    void loadCaptcha()
    ElMessage.error('验证码错误或已过期，请重新输入')
    return
  }

  // 限流
  if (error.code === 10010 || error.httpStatus === 429) {
    const retry = error.retryAfter > 0 ? error.retryAfter : 60
    void router.push({ name: 'TooManyRequests', query: { retry: String(retry) } })
    return
  }

  // 账号锁定（HTTP 423）
  if (error.code === 10003) {
    const wait = error.retryAfter > 0 ? `，约 ${formatWait(error.retryAfter)}后可再试` : ''
    ElMessage.error(`${error.message || '账号已被锁定'}${wait}`)
    return
  }

  // 账号禁用（HTTP 403）
  if (error.code === 10013 || error.code === 10008) {
    void router.push({ name: 'Forbidden', query: { code: String(error.code) } })
    return
  }

  // 账号或密码错误：验证码已被服务端作废，清空输入并换一张新图
  if (error.code === 10002) {
    passwordForm.captcha_code = ''
    void loadCaptcha()
    ElMessage.error(error.message || '账号或密码错误')
    return
  }

  // 未登录 / Token 失效（只认业务码 10001；10002 账号密码错误不能走到这里）
  if (error.code === 10001) {
    authStore.clearAuth()
    ElMessage.error('登录状态已失效，请重新登录')
    return
  }

  // 其它业务错误（如 10002 账号或密码错误）直接展示后端返回的文案
  ElMessage.error(error.message || '登录失败，请稍后重试')
}

async function submitPassword(): Promise<void> {
  if (!passwordFormRef.value) return
  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const result = await loginWithPassword({
      login_type: 'PASSWORD',
      account: passwordForm.account,
      password: passwordForm.password,
      captcha_key: captchaKey.value,
      captcha_code: passwordForm.captcha_code,
    })
    authStore.setLoginResult(result)
    ElMessage.success('登录成功')
    void router.replace(redirectTarget())
  } catch (error) {
    handleError(error)
  } finally {
    submitting.value = false
  }
}

async function sendCode(): Promise<void> {
  if (countdown.value > 0) return
  if (!emailForm.email) {
    ElMessage.warning('请先输入邮箱')
    return
  }
  if (!EMAIL_PATTERN.test(emailForm.email)) {
    ElMessage.warning('邮箱格式不正确')
    return
  }

  try {
    const data = await sendVerificationCode({
      email: emailForm.email,
      purpose: 'EMAIL_LOGIN',
    })
    startCountdown(data.retry_after || 60)
    ElMessage.success('验证码已发送，请查收邮箱')
  } catch (error) {
    handleError(error)
  }
}

async function submitEmail(): Promise<void> {
  if (!emailFormRef.value) return
  const valid = await emailFormRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const result = await loginWithEmailCode({
      login_type: 'EMAIL_CODE',
      email: emailForm.email,
      verify_code: emailForm.verify_code,
    })
    authStore.setLoginResult(result)
    ElMessage.success('登录成功')
    void router.replace(redirectTarget())
  } catch (error) {
    handleError(error)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void loadCaptcha()
})

onBeforeUnmount(() => {
  if (countdownTimer) {
    window.clearInterval(countdownTimer)
    countdownTimer = undefined
  }
})
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-brand">
        <span class="auth-brand__dot"></span>
        <span>考研 AI 导学系统</span>
      </div>
      <h1 class="auth-title">欢迎回来</h1>
      <p class="auth-sub">登录后继续你的备考计划</p>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="账号密码登录" name="password">
          <el-form
            ref="passwordFormRef"
            :model="passwordForm"
            :rules="passwordRules"
            label-position="top"
            @submit.prevent="submitPassword"
          >
            <el-form-item label="账号" prop="account">
              <el-input
                v-model="passwordForm.account"
                placeholder="请输入用户名或邮箱"
                clearable
                autocomplete="username"
              />
            </el-form-item>

            <el-form-item label="密码" prop="password">
              <el-input
                v-model="passwordForm.password"
                type="password"
                placeholder="请输入密码"
                show-password
                autocomplete="current-password"
              />
            </el-form-item>

            <el-form-item label="图形验证码" prop="captcha_code">
              <div class="captcha-row">
                <el-input
                  v-model="passwordForm.captcha_code"
                  placeholder="请输入图形验证码"
                  maxlength="4"
                />
                <div v-if="captchaImage" class="captcha-box">
                  <img
                    class="captcha-img"
                    :src="captchaImage"
                    alt="图形验证码"
                    @click="loadCaptcha"
                  />
                  <span class="captcha-refresh" @click="loadCaptcha">看不清？换一张</span>
                </div>
                <div v-else class="captcha-box">
                  <el-button text :loading="captchaLoading" @click="loadCaptcha">
                    获取验证码
                  </el-button>
                  <span class="captcha-refresh" @click="loadCaptcha">看不清？换一张</span>
                </div>
              </div>
            </el-form-item>

            <div class="auth-extra">
              <span></span>
              <span class="auth-link" @click="router.push({ name: 'ForgotPassword' })">
                忘记密码？
              </span>
            </div>

            <el-button
              class="auth-submit"
              type="primary"
              :loading="submitting"
              native-type="submit"
            >
              登 录
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="邮箱验证码登录" name="email">
          <el-form
            ref="emailFormRef"
            :model="emailForm"
            :rules="emailRules"
            label-position="top"
            @submit.prevent="submitEmail"
          >
            <el-form-item label="邮箱" prop="email">
              <el-input
                v-model="emailForm.email"
                placeholder="请输入注册邮箱"
                clearable
                autocomplete="email"
              />
            </el-form-item>

            <el-form-item label="邮箱验证码" prop="verify_code">
              <div class="captcha-row">
                <el-input
                  v-model="emailForm.verify_code"
                  placeholder="请输入 6 位验证码"
                  maxlength="6"
                />
                <el-button
                  :disabled="countdown > 0"
                  @click="sendCode"
                >
                  {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>

            <el-button
              class="auth-submit"
              type="primary"
              :loading="submitting"
              native-type="submit"
            >
              登 录
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <p class="auth-footer">
        还没有账号？
        <span class="auth-link" @click="router.push({ name: 'Register' })">立即注册</span>
      </p>
    </div>
  </div>
</template>
