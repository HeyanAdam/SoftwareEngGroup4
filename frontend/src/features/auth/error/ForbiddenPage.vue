<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import '../styles/auth.css'

/**
 * 403 页面按业务码分支渲染：
 * 10008 无接口访问权限（真实返回）
 * 10013 账号已被禁用（真实返回：管理员将账号 status 置为禁用后登录）
 */
const route = useRoute()
const router = useRouter()

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

function goLogin(): void {
  void router.replace({ name: 'Login' })
}
</script>

<template>
  <div class="auth-page auth-error">
    <div class="auth-card">
      <div class="auth-error__code">403</div>
      <h1 class="auth-error__title">访问被拒绝</h1>
      <p class="auth-error__desc">{{ desc }}</p>
      <div class="auth-error__actions">
        <el-button type="primary" @click="goLogin">返回登录</el-button>
        <el-button @click="router.replace('/dashboard')">回到首页</el-button>
      </div>
    </div>
  </div>
</template>
