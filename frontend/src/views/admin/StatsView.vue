<template>
  <div v-loading="loading">
    <el-row :gutter="16" class="mt16">
      <el-col :span="24">
        <WeatherCard />
      </el-col>
    </el-row>
    <!-- 统计卡片 -->
    <el-row :gutter="16">
      <el-col v-for="card in cards" :key="card.label" :span="4">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-label">{{ card.label }}</div>
            <div class="stat-value" :class="card.color">{{ card.value }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="mt16">
      <el-col :span="8">
        <el-card shadow="hover" header="学生专业分布">
          <EChart :option="majorOption" height="300px" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" header="课程状态分布">
          <EChart :option="statusOption" height="300px" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" header="成绩分数段分布">
          <EChart :option="scoreOption" height="300px" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="mt16">
      <el-col :span="12">
        <el-card shadow="hover" header="选课人数 Top 课程">
          <EChart :option="topOption" height="340px" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover" header="各院系课程数">
          <EChart :option="deptOption" height="340px" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import { getAdminStats } from '@/api/stats'
import EChart from '@/components/EChart.vue'
import WeatherCard from '@/components/WeatherCard.vue'
import type { AdminStats, NameValue } from '@/types'
import { escapeHtml } from '@/utils/escape'

const loading = ref(false)
const data = ref<AdminStats>({} as AdminStats)

const palette = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#a471e3', '#53b8c7', '#d4a017']

const cards = computed(() => [
  { label: '学生总数', value: data.value.studentCount ?? 0, color: 'primary' },
  { label: '教职工数', value: data.value.staffCount ?? 0, color: '' },
  { label: '教师数', value: data.value.teacherCount ?? 0, color: 'success' },
  { label: '课程总数', value: data.value.courseCount ?? 0, color: 'warning' },
  { label: '选课记录数', value: data.value.enrollmentCount ?? 0, color: 'danger' }
])

function pieOption(items: NameValue[]): EChartsOption {
  return {
    // item name 来自数据库（院系/专业/课程名），转义防存储型 XSS（S-8）
    tooltip: { trigger: 'item', formatter: (params: unknown) => {
      const p = params as { name?: unknown; value?: unknown; percent?: unknown } | undefined
      return `${escapeHtml(p?.name ?? '')}: ${escapeHtml(p?.value ?? '')} (${escapeHtml(p?.percent ?? '')}%)`
    } },
    legend: { orient: 'vertical', right: 4, top: 'center', type: 'scroll' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '68%'],
        center: ['40%', '50%'],
        label: { show: false },
        itemStyle: { borderRadius: 4 },
        data: (items || []).map((it, i) => ({ name: it.name, value: it.value, itemStyle: { color: palette[i % palette.length] } }))
      }
    ]
  }
}

function hbarOption(items: NameValue[]): EChartsOption {
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 10, right: 30, top: 10, bottom: 10, containLabel: true },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: (items || []).map((it) => it.name), inverse: true },
    series: [
      {
        type: 'bar',
        data: (items || []).map((it) => it.value),
        itemStyle: { color: palette[0], borderRadius: [0, 4, 4, 0] },
        barMaxWidth: 18
      }
    ]
  }
}

function vbarOption(items: NameValue[], color?: string): EChartsOption {
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 10, right: 20, top: 30, bottom: 10, containLabel: true },
    xAxis: { type: 'category', data: (items || []).map((it) => it.name) },
    yAxis: { type: 'value' },
    series: [
      {
        type: 'bar',
        data: (items || []).map((it) => it.value),
        itemStyle: { color: color || palette[1], borderRadius: [4, 4, 0, 0] },
        barMaxWidth: 34
      }
    ]
  }
}

const majorOption = computed(() => pieOption(data.value.majorDistribution || []))
const statusOption = computed(() => pieOption(data.value.courseStatus || []))
const scoreOption = computed(() => vbarOption(data.value.scoreBands || [], palette[3]))
const topOption = computed(() => hbarOption(data.value.topEnrolledCourses || []))
const deptOption = computed(() => vbarOption(data.value.departmentCourses || [], palette[5]))

onMounted(async () => {
  loading.value = true
  try {
    data.value = await getAdminStats()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.mt16 {
  margin-top: 16px;
}
.stat-item {
  text-align: center;
  padding: 6px 0;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #303133;
}
.stat-value.primary {
  color: #409eff;
}
.stat-value.success {
  color: #67c23a;
}
.stat-value.warning {
  color: #e6a23c;
}
.stat-value.danger {
  color: #f56c6c;
}
</style>
