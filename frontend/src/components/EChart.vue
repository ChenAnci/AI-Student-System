<template>
  <div ref="el" class="echart" :style="{ height }" />
</template>

<script setup lang="ts">
import * as echarts from 'echarts'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{ option: echarts.EChartsOption; height?: string }>(),
  { height: '320px' }
)

const el = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

function render() {
  if (!el.value) return
  if (!chart) chart = echarts.init(el.value)
  chart.setOption(props.option, true)
}

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  render()
  window.addEventListener('resize', handleResize)
})

watch(
  () => props.option,
  () => render(),
  { deep: true }
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.echart {
  width: 100%;
}
</style>
