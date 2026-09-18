<!-- ===== ECharts 图表封装组件（EChart） =====
  职责：对 ECharts 做轻量封装，接收 option 配置与高度，
  自动完成初始化、深监听重绘、窗口自适应与卸载销毁。 -->
<template>
  <div ref="el" class="echart" :style="{ height }" />
</template>

<script setup lang="ts">
import * as echarts from 'echarts'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

// 接收 ECharts 配置对象与容器高度（默认 320px）
const props = withDefaults(
  defineProps<{ option: echarts.EChartsOption; height?: string }>(),
  { height: '320px' }
)

// 图表挂载容器；chart 实例句柄
const el = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

// 渲染图表：首次初始化实例，之后以 notMerge 模式整体替换配置
function render() {
  if (!el.value) return
  if (!chart) chart = echarts.init(el.value)
  chart.setOption(props.option, true)
}

// 窗口尺寸变化时调用 resize，保证图表自适应容器
function handleResize() {
  chart?.resize()
}

// 挂载后完成首次渲染，并监听窗口 resize
onMounted(() => {
  render()
  window.addEventListener('resize', handleResize)
})

// 深度监听 option 变化，配置更新后自动重绘图表
watch(
  () => props.option,
  () => render(),
  { deep: true }
)

// 卸载前移除 resize 监听并销毁图表实例，避免内存泄漏
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
/* 图表容器占满父级宽度 */
.echart {
  width: 100%;
}
</style>
