<template>
  <el-card shadow="hover" header="实时天气">
    <div v-if="loading" class="w-card">天气加载中…</div>
    <div v-else-if="weather" class="w-card">
      <div class="w-main">
        <span class="w-icon">{{ iconFor(weather.text) }}</span>
        <span class="w-text">{{ weather.text }}</span>
        <span class="w-temp">{{ weather.temp }}°C</span>
      </div>
      <div class="w-sub">
        <span>{{ weather.city }}</span>
        <span>体感 {{ weather.feelsLike }}°C</span>
        <span>{{ weather.windDir }} {{ weather.windClass }}</span>
        <span>湿度 {{ weather.humidity }}%</span>
        <span>更新 {{ fmtTime(weather.updateTime) }}</span>
      </div>
    </div>
    <div v-else class="w-card error">天气服务暂不可用</div>
  </el-card>
</template>

<script setup lang="ts">
/**
 * 实时天气卡片（通用）：调用后端 /api/weather 代理接口，三个角色页面复用。
 * 降级策略：接口失败或 data 为 null 时展示"天气服务暂不可用"，不影响页面其余数据。
 */
import { onMounted, ref } from 'vue'
import { getWeather } from '@/api/weather'
import type { WeatherInfo } from '@/types'

const loading = ref(true)
const weather = ref<WeatherInfo | null>(null)

// 天气现象 → 简单文字图标（子串匹配，兼容"多云转晴"等组合文案）
function iconFor(text: string) {
  if (!text) return '🌡️'
  if (text.includes('晴')) return '☀️'
  if (text.includes('多云')) return '⛅'
  if (text.includes('阴')) return '☁️'
  if (text.includes('雷')) return '⛈️'
  if (text.includes('雪')) return '❄️'
  if (text.includes('雨')) return '🌧️'
  if (text.includes('雾') || text.includes('霾')) return '🌫️'
  return '🌡️'
}

// 百度更新时间 yyyyMMddHHmmss → HH:mm
function fmtTime(uptime: string) {
  if (!uptime || uptime.length < 12) return uptime || ''
  return `${uptime.slice(8, 10)}:${uptime.slice(10, 12)}`
}

onMounted(async () => {
  try {
    weather.value = await getWeather()
  } catch {
    weather.value = null
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.w-card {
  min-height: 64px;
}
.w-main {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.w-icon {
  font-size: 28px;
}
.w-text {
  font-size: 16px;
  color: #303133;
}
.w-temp {
  font-size: 26px;
  font-weight: 700;
  color: #409eff;
}
.w-sub {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
  color: #909399;
}
.error {
  color: #909399;
}
</style>
