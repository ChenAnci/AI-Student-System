<template>
  <div v-loading="loading">
    <!-- 顶部统计卡片 -->
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-label">已修总学分</div>
            <div class="stat-value primary">{{ data.totalEarnedCredits ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-label">毕业要求学分</div>
            <div class="stat-value">{{ data.requiredCredits ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-label">平均绩点 GPA</div>
            <div class="stat-value success">{{ data.gpa ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-label">已修课程</div>
            <div class="stat-value warning">{{ data.gradeList?.length ?? 0 }} 门</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="mt16">
      <!-- 学分完成度环形图 -->
      <el-col :span="10">
        <el-card shadow="hover" header="学分完成进度">
          <div class="ring-wrap">
            <svg viewBox="0 0 160 160" width="180" height="180">
              <circle cx="80" cy="80" r="68" fill="none" stroke="#ecf2fa" stroke-width="16" />
              <circle
                cx="80"
                cy="80"
                r="68"
                fill="none"
                stroke="#409eff"
                stroke-width="16"
                stroke-linecap="round"
                :stroke-dasharray="circumference"
                :stroke-dashoffset="dashOffset"
                transform="rotate(-90 80 80)"
              />
              <text x="80" y="76" text-anchor="middle" font-size="26" font-weight="700" fill="#303133">
                {{ progressText }}
              </text>
              <text x="80" y="100" text-anchor="middle" font-size="12" fill="#909399">已完成</text>
            </svg>
          </div>
          <div class="ring-note">
            已修 {{ data.totalEarnedCredits ?? 0 }} / 要求 {{ data.requiredCredits ?? 0 }} 学分
          </div>
        </el-card>
      </el-col>

      <!-- 最近成绩 -->
      <el-col :span="14">
        <el-card shadow="hover" header="最近成绩">
          <el-table :data="recentGrades" size="small" max-height="300">
            <el-table-column prop="courseName" label="课程" min-width="120" />
            <el-table-column label="学分" width="70">
              <template #default="{ row }">{{ row.credit }}</template>
            </el-table-column>
            <el-table-column label="成绩" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.passed" type="success">{{ row.score }}</el-tag>
                <el-tag v-else-if="row.auditStatus === 'PUBLISHED'" type="danger">{{ row.score }}</el-tag>
                <span v-else class="gray">未发布</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTagType(row.auditStatus)">{{ statusText(row.auditStatus) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { dashboard as getDashboard } from '@/api/grade'
import type { DashboardData, GradeVO } from '@/types'

const loading = ref(false)
const data = ref<DashboardData>({} as DashboardData)

const circumference = 2 * Math.PI * 68

const progress = computed(() => {
  const p = Number(data.value.progressPercent ?? 0)
  return Math.min(100, Math.max(0, p))
})

const dashOffset = computed(() => circumference * (1 - progress.value / 100))

const progressText = computed(() => `${progress.value.toFixed(1)}%`)

const recentGrades = computed<GradeVO[]>(() => (data.value.gradeList ?? []).slice(0, 6))

function statusText(status: string) {
  const map: Record<string, string> = {
    DRAFT: '录入中',
    SUBMITTED: '待审核',
    APPROVED: '已审核',
    PUBLISHED: '已发布'
  }
  return map[status] || status || '未发布'
}

function statusTagType(status: string) {
  const map: Record<string, 'info' | 'warning' | 'success'> = {
    DRAFT: 'info',
    SUBMITTED: 'warning',
    APPROVED: 'info',
    PUBLISHED: 'success'
  }
  return map[status] || 'info'
}

onMounted(async () => {
  loading.value = true
  try {
    data.value = await getDashboard()
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
  font-size: 28px;
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
.ring-wrap {
  display: flex;
  justify-content: center;
  padding: 12px 0;
}
.ring-note {
  text-align: center;
  color: #909399;
  font-size: 13px;
}
.gray {
  color: #909399;
}
</style>
