<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import '../styles/auth.css'

const route = useRoute()
const router = useRouter()

const DEFAULT_RETRY = 60
const remain = ref(DEFAULT_RETRY)
let timer: number | undefined

/** 后端返回的 X-Request-Id，便于把报错反馈给队友排查 */
const requestId = computed<string>(() => {
  const raw = route.query.rid
  return Array.isArray(raw) ? (raw[0] ?? '') : (raw ?? '')
})

function readRetry(): number {
  const raw = route.query.retry
  const value = Array.isArray(raw) ? raw[0] : raw
  const seconds = Number(value)
  if (Number.isFinite(seconds) && seconds > 0) {
    return Math.floor(seconds)
  }
  return DEFAULT_RETRY
}

function start(): void {
  remain.value = readRetry()
  timer = window.setInterval(() => {
    remain.value -= 1
    if (remain.value <= 0) {
      remain.value = 0
      if (timer) {
        window.clearInterval(timer)
        timer = undefined
      }
    }
  }, 1000)
}

function goLogin(): void {
  void router.replace({ name: 'Login' })
}

/** 重试：倒计时结束后留在当前错误页，仅刷新页面重新渲染 */
function retry(): void {
  if (remain.value > 0) return
  location.reload()
}

onMounted(start)

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <div class="auth-page auth-error">
    <div class="auth-card">
      <div class="auth-error__code">429</div>
      <h1 class="auth-error__title">请求过于频繁</h1>
      <p class="auth-error__desc">
        为保护账号安全，操作已被临时限制。<br />
        请在
        <strong>{{ remain }}</strong>
        秒后再试。
      </p>
      <p v-if="requestId" class="auth-error__rid">请求编号：{{ requestId }}</p>
      <div class="auth-error__actions">
        <el-button type="primary" :disabled="remain > 0" @click="retry">重试</el-button>
        <el-button @click="goLogin">返回登录</el-button>
      </div>
    </div>
  </div>
</template>
