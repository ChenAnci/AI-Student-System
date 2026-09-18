<template>
  <div v-loading="loading">
    <div class="stats-toolbar">
      <el-button :loading="loading" @click="load">刷新数据</el-button>
    </div>
    <!-- 统计卡片 -->
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-label">所授课程数</div>
            <div class="stat-value primary">{{ data.courseCount ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-label">已评分学生人次</div>
            <div class="stat-value">{{ data.studentTotal ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-label">平均通过率</div>
            <div class="stat-value success">{{ avgPassRate.toFixed(1) }}%</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-label">总平均分</div>
            <div class="stat-value warning">{{ avgScore.toFixed(1) }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="mt16">
      <el-col :span="12">
        <el-card shadow="hover" header="各课程平均分">
          <EChart :option="scoreOption" height="320px" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover" header="成绩分数段分布">
          <EChart :option="bandOption" height="320px" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover" header="各课程成绩明细" class="mt16">
      <el-table :data="data.courseScores || []" size="small">
        <el-table-column prop="courseName" label="课程" min-width="180" />
        <el-table-column label="平均分" width="110">
          <template #default="{ row }">
            <span :class="{ 'low-score': row.avgScore < 70 }">{{ row.avgScore }}</span>
          </template>
        </el-table-column>
        <el-table-column label="学生数" width="110">
          <template #default="{ row }">{{ row.studentCount }} 人</template>
        </el-table-column>
        <el-table-column label="通过率" min-width="200">
          <template #default="{ row }">
            <div class="pass-wrap">
              <el-progress :percentage="Number(row.passRate)" :color="row.passRate >= 90 ? '#67c23a' : row.passRate >= 60 ? '#e6a23c' : '#f56c6c'" :stroke-width="10" />
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!(data.courseScores || []).length" description="暂无已评分成绩" :image-size="80" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 教师成绩统计
 * 职责：展示当前教师的授课统计（所授课程数、已评分学生人次、平均通过率、总平均分）；
 *       用 ECharts 展示各课程平均分与成绩分数段分布，下方表格列出各课程明细；
 *       支持手动刷新数据。
 */
import { computed, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import { getTeacherStats } from '@/api/stats'
import EChart from '@/components/EChart.vue'
import type { TeacherStats } from '@/types'
import { escapeHtml } from '@/utils/escape'

const loading = ref(false)
const data = ref<TeacherStats>({} as TeacherStats)

// 平均通过率 = 各课程通过率的算术平均（无已评分课程时为 0）
const avgPassRate = computed(() => {
  const rows = data.value.courseScores || []
  if (!rows.length) return 0
  return rows.reduce((s, r) => s + (r.passRate || 0), 0) / rows.length
})

// 总平均分 = 各课程平均分的算术平均（无已评分课程时为 0）
const avgScore = computed(() => {
  const rows = data.value.courseScores || []
  if (!rows.length) return 0
  return rows.reduce((s, r) => s + (r.avgScore || 0), 0) / rows.length
})

// 各课程平均分柱状图配置：以课程名为横轴，柱顶用 markLine 标出整体平均值
const scoreOption = computed<EChartsOption>(() => {
  const rows = data.value.courseScores || []
  return {
    // formatter 函数 + HTML 转义：课程名来自数据库，直接 {b} 插值会作为 HTML 渲染（存储型 XSS，S-8）
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: (params: unknown) => {
      const p = Array.isArray(params) ? params[0] : params
      const name = p?.name ?? ''
      const value = p?.value ?? ''
      return `${escapeHtml(name)}<br/>平均分：${escapeHtml(value)}`
    } },
    grid: { left: 10, right: 20, top: 30, bottom: 10, containLabel: true },
    xAxis: { type: 'category', data: rows.map((r) => r.courseName), axisLabel: { interval: 0, rotate: rows.length > 4 ? 25 : 0 } },
    yAxis: { type: 'value', min: 40, max: 100 },
    series: [
      {
        type: 'bar',
        data: rows.map((r) => r.avgScore),
        itemStyle: { color: '#409eff', borderRadius: [4, 4, 0, 0] },
        barMaxWidth: 40,
        markLine: { data: [{ type: 'average', name: '平均' }], lineStyle: { color: '#e6a23c' } }
      }
    ]
  }
})

// 成绩分数段分布柱状图配置：数据直接取自后端的 scoreBands 统计结果
const bandOption = computed<EChartsOption>(() => {
  const items = data.value.scoreBands || []
  return {
    // scoreBands.name 由系统生成（分数段），但统一转义保持一致
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: (params: unknown) => {
      const p = Array.isArray(params) ? params[0] : params
      return `${escapeHtml(p?.name ?? '')}<br/>人数：${escapeHtml(p?.value ?? '')}`
    } },
    grid: { left: 10, right: 20, top: 30, bottom: 10, containLabel: true },
    xAxis: { type: 'category', data: items.map((it) => it.name) },
    yAxis: { type: 'value' },
    series: [
      {
        type: 'bar',
        data: items.map((it) => it.value),
        itemStyle: { color: '#67c23a', borderRadius: [4, 4, 0, 0] },
        barMaxWidth: 44
      }
    ]
  }
})

// 加载教师统计：请求后端聚合数据，刷新全部卡片与图表
async function load() {
  loading.value = true
  try {
    data.value = await getTeacherStats()
  } finally {
    loading.value = false
  }
}

// 页面挂载后加载一次统计数据
onMounted(load)
</script>

<style scoped>
.mt16 {
  margin-top: 16px;
}
.stats-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
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
.low-score {
  color: #f56c6c;
  font-weight: 600;
}
.pass-wrap {
  display: flex;
  align-items: center;
  height: 22px;
}
</style>
