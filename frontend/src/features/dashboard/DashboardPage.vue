<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getHealth } from './health.api'

const health = ref<unknown>()

onMounted(async () => {
  health.value = await getHealth()
})
</script>

<template>
  <div>
    <h2>系统状态</h2>
    <pre>{{ health }}</pre>
  </div>
</template>
