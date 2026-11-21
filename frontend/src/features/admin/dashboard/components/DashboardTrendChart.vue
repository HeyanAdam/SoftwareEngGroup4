<template>
  <el-card class="chart-card" shadow="never">
    <div class="chart-card__head">
      <span class="chart-card__title">{{ title }}</span>
      <span class="chart-card__unit">单位：次</span>
    </div>
    <div class="chart-card__body" :style="{ height: `${height}px` }">
      <div ref="containerRef" class="chart-card__canvas"></div>
      <div v-if="isEmpty" class="chart-card__empty">暂无趋势数据</div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsOption } from 'echarts'

/** 只注册用到的模块，避免打进整包 echarts */
echarts.use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

interface Props {
  title: string
  /** 横轴日期，与 values 下标一一对应 */
  dates: string[]
  values: number[]
  color?: string
  height?: number
}

const props = withDefaults(defineProps<Props>(), {
  color: '#409eff',
  height: 280,
})

const containerRef = ref<HTMLDivElement | null>(null)
let chart: ReturnType<typeof echarts.init> | null = null
let observer: ResizeObserver | null = null

const isEmpty = computed(() => props.values.length === 0)

function buildOption(): EChartsOption {
  return {
    grid: { left: 48, right: 20, top: 24, bottom: 28 },
    tooltip: {
      trigger: 'axis',
      formatter: (params: unknown): string => {
        const list = Array.isArray(params) ? params : [params]
        const first = list[0] as { axisValue?: string; value?: number } | undefined
        if (!first) return ''
        return `${first.axisValue ?? ''}<br/>${props.title}：${first.value ?? 0} 次`
      },
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: props.dates,
      axisLabel: { color: '#606266', fontSize: 12 },
    },
    yAxis: {
      type: 'value',
      min: 0,
      axisLabel: { color: '#606266', fontSize: 12 },
      splitLine: { lineStyle: { color: '#ebeef5' } },
    },
    series: [
      {
        name: props.title,
        type: 'line',
        smooth: true,
        showSymbol: props.values.length <= 14,
        data: props.values,
        itemStyle: { color: props.color },
        lineStyle: { width: 2, color: props.color },
        areaStyle: { opacity: 0.08, color: props.color },
      },
    ],
  }
}

function render(): void {
  if (!containerRef.value) return
  if (!chart) chart = echarts.init(containerRef.value)
  // notMerge: true 保证切换天数后不残留旧线条
  chart.setOption(buildOption(), { notMerge: true })
}

onMounted(() => {
  render()
  if (containerRef.value && typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(() => {
      chart?.resize()
    })
    observer.observe(containerRef.value)
  }
})

watch(() => [props.dates, props.values] as const, render, { deep: true })

onUnmounted(() => {
  observer?.disconnect()
  observer = null
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.chart-card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 8px;
}

.chart-card__title {
  font-size: 15px;
  font-weight: 600;
}

.chart-card__unit {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.chart-card__body {
  position: relative;
  width: 100%;
}

.chart-card__canvas {
  width: 100%;
  height: 100%;
}

.chart-card__empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  background: var(--el-bg-color);
}
</style>
