<template>
  <div v-loading="loading">
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
import { onMounted, ref } from 'vue'
import { myGrades } from '@/api/grade'
import type { GradeVO } from '@/types'

const loading = ref(false)
const grades = ref<GradeVO[]>([])

function statusText(status: string) {
  const map: Record<string, string> = {
    DRAFT: '录入中',
    SUBMITTED: '待审核',
    APPROVED: '已审核',
    PUBLISHED: '已发布'
  }
  return map[status] || '未发布'
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

function markText(row: GradeVO) {
  if (row.mark === 'DEFER') return '缓考'
  if (row.mark === 'ABSENT') return '缺考'
  if (row.mark === 'CHEAT') return '舞弊'
  return row.score
}

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
.gray {
  color: #909399;
}
</style>
