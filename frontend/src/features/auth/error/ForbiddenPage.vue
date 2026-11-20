<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../../app/stores/auth'
import '../styles/auth.css'

/**
 * 403 页面按业务码分支渲染：
 * 10008 无接口访问权限（真实返回）
 * 10013 账号已被禁用（真实返回：管理员将账号 status 置为禁用后登录）
 */
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const UNAUTHORIZED_MESSAGE = '无操作权限，请联系管理员'
const DISABLED_MESSAGE = '账号已被禁用，请联系管理员'

const code = computed<number>(() => {
  const raw = route.query.code
  const value = Array.isArray(raw) ? raw[0] : raw

  if (value === '10008') return 10008
  if (value === '10013') return 10013
  return 10008
})

const desc = computed<string>(() => {
  if (code.value === 10013) return DISABLED_MESSAGE
  return UNAUTHORIZED_MESSAGE
})

/** 后端返回的 X-Request-Id，便于把报错反馈给队友排查 */
const requestId = computed<string>(() => {
  const raw = route.query.rid
  return Array.isArray(raw) ? (raw[0] ?? '') : (raw ?? '')
})

/**
 * 路由守卫拦截（权限不足）时不会产生真实请求，也就没有 request_id。
 * 兜底展示「访问时间 + 当前账号」，便于向管理员定位问题。
 */
const accessTime = new Date().toLocaleString('zh-CN', { hour12: false })

const accountName = computed<string>(() => authStore.username || authStore.nickname || '')

function goLogin(): void {
  void router.replace({ name: 'Login' })
}

/** 重试：留在当前错误页，仅刷新页面重新渲染 */
function retry(): void {
  location.reload()
}
</script>

<template>
  <div class="auth-page auth-error">
    <div class="auth-card">
      <div class="auth-error__code">403</div>
      <h1 class="auth-error__title">访问被拒绝</h1>
      <p class="auth-error__desc">{{ desc }}</p>
      <p v-if="requestId" class="auth-error__rid">请求编号：{{ requestId }}</p>
      <p v-else class="auth-error__rid">
        访问时间：{{ accessTime }}<template v-if="accountName">　当前账号：{{ accountName }}</template>
      </p>
      <p v-if="!requestId" class="auth-error__rid">如有疑问请联系管理员，并提供以上信息</p>
      <div class="auth-error__actions">
        <el-button type="primary" @click="goLogin">返回登录</el-button>
        <el-button @click="router.replace('/dashboard')">回到首页</el-button>
        <el-button @click="retry">重试</el-button>
      </div>
    </div>
  </div>
</template>
