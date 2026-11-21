<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { adminLogin, fetchAdminCaptcha } from '../api/admin.api'
import { useAdminAuthStore } from '../../../app/stores/adminAuth'
import { ApiError } from '../../../shared/api/request'
import { formatWait } from '../../auth/utils/validators'
import './styles/admin-login.css'

const router = useRouter()
const route = useRoute()
const adminAuthStore = useAdminAuthStore()

const formRef = ref<FormInstance>()
const form = reactive({
  account: '',
  password: '',
  captcha_code: '',
})

const rules: FormRules = {
  account: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captcha_code: [
    { required: true, message: '请输入图形验证码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9]{4}$/, message: '图形验证码为 4 位', trigger: 'blur' },
  ],
}

const captchaImage = ref('')
const captchaKey = ref('')
const captchaLoading = ref(false)
const submitting = ref(false)

/** 拉取图形验证码：失败时提示但不阻塞表单 */
async function loadCaptcha(): Promise<void> {
  captchaLoading.value = true
  try {
    const data = await fetchAdminCaptcha()
    // 后端返回裸 base64（不带 data URI 前缀），这里补齐以便直接作为 img src
    captchaImage.value = data.image_base64.startsWith('data:')
      ? data.image_base64
      : `data:image/png;base64,${data.image_base64}`
    captchaKey.value = data.captcha_key
  } catch {
    captchaImage.value = ''
    captchaKey.value = ''
    ElMessage.error('验证码加载失败，请稍后重试')
  } finally {
    captchaLoading.value = false
  }
}

/** 只接受 /admin 开头的站内地址，避免开放重定向 */
function redirectTarget(): string {
  const raw = route.query.redirect
  const value = Array.isArray(raw) ? raw[0] : raw
  if (typeof value === 'string' && value.startsWith('/admin')) {
    return value
  }
  return '/admin/dashboard'
}

/**
 * 登录错误分流：
 * 10005 验证码错误或过期 → 清空并换图；10010 / 429 → 429 页；
 * 10003 锁定 → 展示剩余时间；其它错误保留账号、清空密码（安全习惯）。
 */
function handleError(error: unknown): void {
  if (!(error instanceof ApiError)) {
    form.password = ''
    ElMessage.error('登录失败，请稍后重试')
    return
  }

  if (error.code === 10005) {
    form.captcha_code = ''
    form.password = ''
    void loadCaptcha()
    ElMessage.error('验证码错误或已过期，请重新输入')
    return
  }

  if (error.code === 10010 || error.httpStatus === 429) {
    const retry = error.retryAfter > 0 ? error.retryAfter : 60
    void router.push({
      name: 'TooManyRequests',
      query: { retry: String(retry), rid: error.requestId },
    })
    return
  }

  if (error.code === 10003) {
    const wait = error.retryAfter > 0 ? `，约 ${formatWait(error.retryAfter)}后可再试` : ''
    form.password = ''
    ElMessage.error(`${error.message || '账号已被锁定'}${wait}`)
    return
  }

  if (error.code === 10013 || error.code === 10008) {
    void router.push({
      name: 'Forbidden',
      query: { code: String(error.code), rid: error.requestId },
    })
    return
  }

  form.password = ''
  if (error.code === 10002) {
    form.captcha_code = ''
    void loadCaptcha()
  }
  ElMessage.error(error.message || '账号或密码错误')
}

/** 提交登录：先写后台 Store，再按「任意 admin:* 权限」判定准入 */
async function submit(): Promise<void> {
  if (!formRef.value || submitting.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const result = await adminLogin({
      login_type: 'PASSWORD',
      account: form.account.trim(),
      password: form.password,
      captcha_key: captchaKey.value,
      captcha_code: form.captcha_code,
    })

    adminAuthStore.setLoginResult(result)

    const canEnter = result.permissions.some((code) => code.startsWith('admin:'))
    if (!canEnter) {
      adminAuthStore.clearAuth()
      ElMessage.warning('当前账号没有后台管理权限')
      void router.replace({ name: 'Forbidden' })
      return
    }

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
</script>

<template>
  <div class="admin-login">
    <section class="admin-login__brand">
      <div class="admin-login__logo">
        <span class="admin-login__logo-dot"></span>
        <span>考研 AI 导学系统</span>
      </div>
      <h1 class="admin-login__brand-title">
        后台管理<br />
        控制台
      </h1>
      <p class="admin-login__brand-sub">
        用户、角色与权限的统一入口。仅具备任意 admin:* 权限的账号可进入后台，学生账号无权访问。
      </p>
      <ul class="admin-login__brand-points">
        <li>用户状态与角色分配的集中管理</li>
        <li>操作用权限码校验，前端只做显隐</li>
        <li>与学生端登录态完全隔离</li>
      </ul>
      <div class="admin-login__brand-foot">KAOYAN AI · ADMIN CONSOLE</div>
    </section>

    <section class="admin-login__panel">
      <div class="admin-login__card">
        <div class="admin-login__eyebrow">Admin Console</div>
        <h2 class="admin-login__title">管理员登录</h2>
        <p class="admin-login__hint">请使用管理员账号登录，登录后可管理用户与角色</p>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="submit"
        >
          <el-form-item label="管理员账号" prop="account">
            <el-input
              v-model="form.account"
              placeholder="请输入管理员用户名"
              clearable
              autocomplete="username"
              @keyup.enter="submit"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              show-password
              autocomplete="current-password"
              @keyup.enter="submit"
            />
          </el-form-item>

          <el-form-item label="图形验证码" prop="captcha_code">
            <div class="admin-login__captcha">
              <el-input
                v-model="form.captcha_code"
                class="admin-login__captcha-input"
                placeholder="4 位验证码"
                maxlength="4"
                @keyup.enter="submit"
              />
              <img
                v-if="captchaImage"
                class="admin-login__captcha-img"
                :src="captchaImage"
                alt="图形验证码"
                title="点击换一张"
                @click="loadCaptcha"
              />
              <el-button
                v-else
                class="admin-login__captcha-btn"
                :loading="captchaLoading"
                @click="loadCaptcha"
              >
                获取验证码
              </el-button>
            </div>
          </el-form-item>

          <el-button
            class="admin-login__submit"
            type="primary"
            :loading="submitting"
            native-type="submit"
          >
            {{ submitting ? '登录中…' : '登 录' }}
          </el-button>
        </el-form>

        <p class="admin-login__back">
          <a @click="router.push({ name: 'Login' })">← 返回学生端</a>
        </p>
      </div>
    </section>
  </div>
</template>
