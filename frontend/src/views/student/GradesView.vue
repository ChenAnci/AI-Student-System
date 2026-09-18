<template>
  <div v-loading="loading">
    <!-- 成绩统计汇总：已修学分 + 平均学分绩（GPA） -->
    <el-row :gutter="16" class="summary-row">
      <el-col :xs="12" :sm="8">
        <el-card shadow="never">
          <div class="stat-label">已修总学分</div>
          <div class="stat-value">{{ earnedCredits.toFixed(2) }}</div>
          <div class="stat-note">已发布且通过（≥60 分）课程学分之和</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8">
        <el-card shadow="never">
          <div class="stat-label">平均学分绩 GPA</div>
          <div class="stat-value success">{{ gpa.toFixed(2) }}</div>
          <div class="stat-note">按学分加权：Σ(单科绩点 × 学分) ÷ 总学分</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="never">
          <div class="stat-label">已修通过课程</div>
          <div class="stat-value">{{ passedCount }} 门</div>
          <div class="stat-note">成绩 60 分及以上可获得学分与绩点</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" header="成绩单">
      <el-table :data="grades">
        <el-table-column prop="courseName" label="课程名称" min-width="140" />
        <el-table-column label="学分" width="80">
          <template #default="{ row }">{{ row.credit }}</template>
        </el-table-column>
        <el-table-column label="成绩" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.auditStatus === 'PUBLISHED' && row.score != null" :type="row.passed ? 'success' : 'danger'">
              {{ markText(row) }}
            </el-tag>
            <span v-else class="gray">-</span>
          </template>
        </el-table-column>
        <el-table-column label="绩点" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.gradePoint != null">{{ Number(row.gradePoint).toFixed(1) }}</span>
            <span v-else class="gray">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.auditStatus)">{{ statusText(row.auditStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="是否通过" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.passed" type="success" size="small">通过</el-tag>
            <el-tag v-else-if="row.auditStatus === 'PUBLISHED'" type="danger" size="small">未通过</el-tag>
            <span v-else class="gray">-</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && grades.length === 0" description="暂无成绩记录" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 成绩单（学生）
 * 职责：展示当前学生的成绩汇总（已修总学分、平均学分绩 GPA、已修通过课程数）与明细成绩表；
 *       成绩表按审核状态展示录入中 / 待审核 / 已审核 / 已发布，并处理缓考、缺考、舞弊等特殊标记。
 */
import { computed, onMounted, ref } from 'vue'
import { myGrades } from '@/api/grade'
import type { GradeVO } from '@/types'

const loading = ref(false)
const grades = ref<GradeVO[]>([])

// 已修学分 = 已发布且通过课程学分之和（与后端重算口径一致）
const earnedCredits = computed(() =>
  grades.value.filter(g => g.passed).reduce((sum, g) => sum + Number(g.credit || 0), 0)
)
// 平均学分绩 GPA = Σ(绩点×学分) / Σ(学分)，仅统计已发布通过课程
const gpa = computed(() => {
  const passed = grades.value.filter(g => g.passed && g.gradePoint != null)
  if (passed.length === 0) return 0
  const total = passed.reduce((s, g) => s + Number(g.credit || 0), 0)
  if (total === 0) return 0
  const weighted = passed.reduce((s, g) => s + Number(g.gradePoint) * Number(g.credit || 0), 0)
  return weighted / total
})
const passedCount = computed(() => grades.value.filter(g => g.passed).length)

// 成绩审核状态 → 中文文案（录入中 / 待审核 / 已审核 / 已发布），未识别状态兜底为"未发布"
function statusText(status: string) {
  const map: Record<string, string> = {
    DRAFT: '录入中',
    SUBMITTED: '待审核',
    APPROVED: '已审核',
    PUBLISHED: '已发布'
  }
  return map[status] || '未发布'
}

// 成绩审核状态 → el-tag 颜色类型（录入中=灰 info、待审核=橙 warning、已审核=灰 info、已发布=绿 success）
function statusTagType(status: string) {
  const map: Record<string, 'info' | 'warning' | 'success'> = {
    DRAFT: 'info',
    SUBMITTED: 'warning',
    APPROVED: 'info',
    PUBLISHED: 'success'
  }
  return map[status] || 'info'
}

// 成绩列展示文案：缓考 / 缺考 / 舞弊为特殊标记，其余返回实际分数
function markText(row: GradeVO) {
  if (row.mark === 'DEFER') return '缓考'
  if (row.mark === 'ABSENT') return '缺考'
  if (row.mark === 'CHEAT') return '舞弊'
  return row.score
}

// 页面挂载后请求当前学生的成绩列表，期间展示全局 loading
onMounted(async () => {
  loading.value = true
  try {
    grades.value = await myGrades()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.summary-row {
  margin-bottom: 16px;
}
.stat-label {
  font-size: 13px;
  color: #909399;
}
.stat-value {
  font-size: 24px;
  font-weight: 600;
  margin-top: 4px;
  color: #303133;
}
.stat-value.success {
  color: #67c23a;
}
.stat-note {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 4px;
}
.gray {
  color: #909399;
}
</style>
