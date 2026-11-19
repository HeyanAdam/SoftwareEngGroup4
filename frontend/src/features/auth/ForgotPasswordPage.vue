<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { resetPassword, sendVerificationCode } from './api/auth.api'
import {
  EMAIL_PATTERN,
  PASSWORD_PATTERN,
  confirmPasswordRule,
  patternRule,
  requiredRule,
} from './utils/validators'
import { ApiError } from '../../shared/api/request'
import './styles/auth.css'

const router = useRouter()
const formRef = ref<FormInstance>()

const form = reactive({
  email: '',
  verify_code: '',
  new_password: '',
  confirm_password: '',
})

const rules: FormRules = {
  email: [
    requiredRule('请输入邮箱'),
    patternRule('邮箱格式不正确', (value) => EMAIL_PATTERN.test(value)),
  ],
  verify_code: [
    requiredRule('请输入邮箱验证码'),
    patternRule('邮箱验证码为 6 位数字', (value) => /^\d{6}$/.test(value)),
  ],
  new_password: [
    requiredRule('请输入新密码'),
    patternRule('密码为 8-32 位，且必须同时包含字母和数字', (value) =>
      PASSWORD_PATTERN.test(value),
    ),
  ],
  confirm_password: [
    requiredRule('请再次输入新密码'),
    confirmPasswordRule(() => form.new_password),
  ],
}

const countdown = ref(0)
const submitting = ref(false)
let countdownTimer: number | undefined

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

async function sendCode(): Promise<void> {
  if (countdown.value > 0) return
  if (!form.email) {
    ElMessage.warning('请先输入邮箱')
    return
  }
  if (!EMAIL_PATTERN.test(form.email)) {
    ElMessage.warning('邮箱格式不正确')
    return
  }

  try {
    const data = await sendVerificationCode({
      email: form.email,
      purpose: 'RESET_PASSWORD',
    })
    startCountdown(data.retry_after || 60)
    ElMessage.success('验证码已发送，请查收邮箱')
  } catch (error) {
    handleError(error)
  }
}

function handleError(error: unknown): void {
  if (!(error instanceof ApiError)) {
    ElMessage.error('操作失败，请稍后重试')
    return
  }

  if (error.code === 10005) {
    form.verify_code = ''
    ElMessage.error('验证码错误或已过期，请重新获取')
    return
  }

  if (error.code === 10006) {
    ElMessage.error('两次输入的密码不一致')
    return
  }

  if (error.code === 10007) {
    ElMessage.error('该邮箱尚未注册')
    return
  }

  if (error.code === 10011) {
    ElMessage.warning('请求正在处理中，请稍后重试')
    return
  }

  if (error.code === 10010 || error.httpStatus === 429) {
    const retry = error.retryAfter > 0 ? error.retryAfter : 60
    void router.push({ name: 'TooManyRequests', query: { retry: String(retry) } })
    return
  }

  ElMessage.error(error.message || '重置失败，请稍后重试')
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await resetPassword({
      email: form.email,
      verify_code: form.verify_code,
      new_password: form.new_password,
      confirm_password: form.confirm_password,
    })
    ElMessage.success('密码已重置，请重新登录')
    void router.replace({ name: 'Login' })
  } catch (error) {
    handleError(error)
  } finally {
    submitting.value = false
  }
}

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
      <h1 class="auth-title">找回密码</h1>
      <p class="auth-sub">通过注册邮箱验证身份后重设密码</p>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item label="注册邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入注册邮箱" clearable autocomplete="email" />
        </el-form-item>

        <el-form-item label="邮箱验证码" prop="verify_code">
          <div class="captcha-row">
            <el-input v-model="form.verify_code" placeholder="请输入 6 位验证码" maxlength="6" />
            <el-button :disabled="countdown > 0" @click="sendCode">
              {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="新密码" prop="new_password">
          <el-input
            v-model="form.new_password"
            type="password"
            placeholder="8-32 位，需含字母和数字"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>

        <el-form-item label="确认新密码" prop="confirm_password">
          <el-input
            v-model="form.confirm_password"
            type="password"
            placeholder="请再次输入新密码"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>

        <el-button class="auth-submit" type="primary" :loading="submitting" native-type="submit">
          重置密码
        </el-button>
      </el-form>

      <p class="auth-footer">
        <span class="auth-link" @click="router.push({ name: 'Login' })">返回登录</span>
      </p>
    </div>
  </div>
</template>
