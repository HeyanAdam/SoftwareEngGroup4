<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import '../styles/auth.css'

const route = useRoute()
const router = useRouter()

/** 后端返回的 X-Request-Id，便于把报错反馈给队友排查 */
const requestId = computed<string>(() => {
  const raw = route.query.rid
  return Array.isArray(raw) ? (raw[0] ?? '') : (raw ?? '')
})

function goLogin(): void {
  void router.replace({ name: 'Login' })
}
</script>

<template>
  <div class="auth-page auth-error">
    <div class="auth-card">
      <div class="auth-error__code">401</div>
      <h1 class="auth-error__title">登录状态已失效</h1>
      <p class="auth-error__desc">你的登录凭证已过期或无效，请重新登录后继续使用。</p>
      <p v-if="requestId" class="auth-error__rid">请求编号：{{ requestId }}</p>
      <div class="auth-error__actions">
        <el-button type="primary" @click="goLogin">重新登录</el-button>
      </div>
    </div>
  </div>
</template>
