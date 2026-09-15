<template>
  <div v-loading="loading">
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="load">
        <el-tab-pane label="待审核" name="pending">
          <el-table :data="pendingList">
            <el-table-column prop="courseCode" label="课程编号" width="110" />
            <el-table-column prop="courseName" label="课程名称" min-width="140" />
            <el-table-column prop="teacherName" label="授课教师" width="110" />
            <el-table-column label="提交时间" width="170">
              <template #default="{ row }">{{ row.submittedAt }}</template>
            </el-table-column>
            <el-table-column label="操作" width="260" align="center">
              <template #default="{ row }">
                <el-button link type="success" @click="handleAudit(row, true)">审核通过</el-button>
                <el-button link type="danger" @click="handleAudit(row, false)">退回</el-button>
                <el-button link type="primary" @click="viewStudents(row.courseId)">查看成绩</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && pendingList.length === 0" description="暂无待审核的成绩" />
        </el-tab-pane>

        <el-tab-pane label="全部流程" name="all">
          <el-table :data="allList">
            <el-table-column prop="courseCode" label="课程编号" width="110" />
            <el-table-column prop="courseName" label="课程名称" min-width="140" />
            <el-table-column prop="teacherName" label="授课教师" width="110" />
            <el-table-column label="当前状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="auditTagType(row.status)">{{ auditText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="退回原因" min-width="140">
              <template #default="{ row }">
                <span class="reject">{{ row.rejectReason || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" align="center">
              <template #default="{ row }">
                <el-button v-if="row.status === 'APPROVED'" link type="primary" @click="handlePublish(row)">
                  发布成绩
                </el-button>
                <el-button link type="primary" @click="viewStudents(row.courseId)">查看成绩</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 退回原因弹窗 -->
    <el-dialog v-model="rejectVisible" title="退回成绩" width="440px">
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="3"
        placeholder="请填写退回原因（必填），退回后教师可重新修改成绩"
      />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="confirmReject">确认退回</el-button>
      </template>
    </el-dialog>

    <!-- 成绩明细弹窗 -->
    <el-dialog v-model="detailVisible" title="课程成绩明细" width="560px">
      <el-table :data="detailStudents" size="small">
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column label="成绩" min-width="80">
          <template #default="{ row }">
            <el-tag v-if="row.mark === 'NORMAL'" size="small" :type="Number(row.score) >= 60 ? 'success' : 'danger'">
              {{ row.score ?? '-' }}
            </el-tag>
            <el-tag v-else size="small" type="info">{{ markLabel(row.mark) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标记" width="90">
          <template #default="{ row }">{{ markLabel(row.mark) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { allAudits, audit, courseStudents, pendingAudits, publishGrades } from '@/api/grade'
import type { AuditVO, CourseStudentItem } from '@/types'

const loading = ref(false)
const saving = ref(false)
const activeTab = ref('pending')
const pendingList = ref<AuditVO[]>([])
const allList = ref<AuditVO[]>([])

const rejectVisible = ref(false)
const rejectReason = ref('')
const rejectTarget = ref<AuditVO | null>(null)

const detailVisible = ref(false)
const detailStudents = ref<CourseStudentItem[]>([])

function auditText(status: string) {
  const map: Record<string, string> = {
    DRAFT: '录入中',
    SUBMITTED: '待审核',
    APPROVED: '已审核',
    PUBLISHED: '已发布'
  }
  return map[status] || status
}

function auditTagType(status: string) {
  const map: Record<string, 'info' | 'warning' | 'success'> = {
    DRAFT: 'info',
    SUBMITTED: 'warning',
    APPROVED: 'info',
    PUBLISHED: 'success'
  }
  return map[status] || 'info'
}

function markLabel(mark: string) {
  const map: Record<string, string> = { NORMAL: '正常', DEFER: '缓考', ABSENT: '缺考', CHEAT: '舞弊' }
  return map[mark] || mark
}

async function load() {
  loading.value = true
  try {
    pendingList.value = await pendingAudits()
    allList.value = await allAudits()
  } finally {
    loading.value = false
  }
}

function handleAudit(row: AuditVO, approved: boolean) {
  if (approved) {
    ElMessageBox.confirm(`确认通过「${row.courseName}」的成绩审核？通过后可发布成绩`, '审核通过', {
      type: 'success'
    }).then(async () => {
      await audit(row.courseId, true)
      ElMessage.success('审核已通过')
      load()
    })
  } else {
    rejectTarget.value = row
    rejectReason.value = ''
    rejectVisible.value = true
  }
}

async function confirmReject() {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写退回原因')
    return
  }
  saving.value = true
  try {
    await audit(rejectTarget.value!.courseId, false, rejectReason.value.trim())
    ElMessage.success('已退回，教师可重新修改成绩')
    rejectVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

function handlePublish(row: AuditVO) {
  ElMessageBox.confirm(
    `发布后将按成绩自动累加学生学分并永久锁定，确认发布「${row.courseName}」的成绩？`,
    '发布确认',
    { type: 'warning' }
  ).then(async () => {
    await publishGrades(row.courseId)
    ElMessage.success('成绩发布成功，学分已累加')
    load()
  })
}

async function viewStudents(courseId: number) {
  detailStudents.value = await courseStudents(courseId)
  detailVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.reject {
  color: #f56c6c;
}
</style>
