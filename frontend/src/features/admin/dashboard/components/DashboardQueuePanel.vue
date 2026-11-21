<template>
  <el-card class="panel" shadow="never">
    <div class="panel__head">
      <span class="panel__title">文档解析队列 / Outbox</span>
      <span v-if="hasAlarm" class="panel__alarm">存在异常指标</span>
    </div>

    <div class="panel__rows">
      <div v-for="row in rows" :key="row.key" class="panel__row">
        <span class="panel__label">{{ row.label }}</span>
        <el-tag :type="row.tagType" size="small" :effect="row.level === 'normal' ? 'plain' : 'dark'">
          {{ row.value }}
        </el-tag>
      </div>
    </div>

    <div class="panel__tips">
      规则：0 正常（绿）｜1–10 积压（黄）｜&gt;10 或死信 / 投递失败（红）
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DashboardSystem } from '../types'

interface Props {
  system: DashboardSystem
}

const props = defineProps<Props>()

type Level = 'normal' | 'warning' | 'danger'

interface Row {
  key: string
  label: string
  value: number
  level: Level
  tagType: 'success' | 'warning' | 'danger'
}

const TAG_TYPE: Record<Level, 'success' | 'warning' | 'danger'> = {
  normal: 'success',
  warning: 'warning',
  danger: 'danger',
}

/** 普通计数：0 正常，1–10 积压，>10 严重 */
function backlogLevel(value: number, dangerAbove: number): Level {
  if (value <= 0) return 'normal'
  if (value <= dangerAbove) return 'warning'
  return 'danger'
}

const rows = computed<Row[]>(() => {
  const { system } = props
  const build = (key: string, label: string, value: number, level: Level): Row => ({
    key,
    label,
    value,
    level,
    tagType: TAG_TYPE[level],
  })

  return [
    // 待解析：>10 条积压即视为严重，可能消费者不足
    build('ready', '待解析消息数', system.docParseReady, backlogLevel(system.docParseReady, 10)),
    // 消费者为 0 说明解析服务没起来，即便队列为空也要提示
    build(
      'consumers',
      '在线消费者数',
      system.docParseConsumers,
      system.docParseConsumers <= 0 ? 'warning' : 'normal',
    ),
    // 死信只要非 0 就是红色
    build('dlq', '死信队列数', system.docParseDlq, system.docParseDlq > 0 ? 'danger' : 'normal'),
    // Outbox 待投递
    build('pending', 'Outbox 待投递', system.outboxPending, backlogLevel(system.outboxPending, 10)),
    // Outbox 失败只要非 0 就是红色
    build('failed', 'Outbox 失败数', system.outboxFailed, system.outboxFailed > 0 ? 'danger' : 'normal'),
  ]
})

const hasAlarm = computed(() => rows.value.some((row) => row.level === 'danger'))
</script>

<style scoped>
.panel__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 8px;
}

.panel__title {
  font-size: 15px;
  font-weight: 600;
}

.panel__alarm {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-danger);
}

.panel__rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.panel__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.panel__row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.panel__label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.panel__tips {
  margin-top: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
