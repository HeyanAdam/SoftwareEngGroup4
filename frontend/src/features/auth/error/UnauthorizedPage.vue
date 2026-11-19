<script setup lang="ts">
import { useRouter } from 'vue-router'
import '../styles/auth.css'

const router = useRouter()

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
      <div class="auth-error__actions">
        <el-button type="primary" @click="goLogin">重新登录</el-button>
      </div>
    </div>
  </div>
</template>
