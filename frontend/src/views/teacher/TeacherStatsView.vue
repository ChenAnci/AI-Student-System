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
import { computed, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import { getTeacherStats } from '@/api/stats'
import EChart from '@/components/EChart.vue'
import type { TeacherStats } from '@/types'

const loading = ref(false)
const data = ref<TeacherStats>({} as TeacherStats)

const avgPassRate = computed(() => {
  const rows = data.value.courseScores || []
  if (!rows.length) return 0
  return rows.reduce((s, r) => s + (r.passRate || 0), 0) / rows.length
})

const avgScore = computed(() => {
  const rows = data.value.courseScores || []
  if (!rows.length) return 0
  return rows.reduce((s, r) => s + (r.avgScore || 0), 0) / rows.length
})

const scoreOption = computed<EChartsOption>(() => {
  const rows = data.value.courseScores || []
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}<br/>平均分：{c}' },
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

const bandOption = computed<EChartsOption>(() => {
  const items = data.value.scoreBands || []
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}<br/>人数：{c}' },
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

async function load() {
  loading.value = true
  try {
    data.value = await getTeacherStats()
  } finally {
    loading.value = false
  }
}

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
