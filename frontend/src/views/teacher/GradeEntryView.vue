<template>
  <div v-loading="loading">
    <el-card shadow="never">
      <div class="header-bar">
        <div>
          <el-page-header @back="$router.push('/teacher/courses')" :content="`成绩管理 - ${courseName}`" />
          <div class="audit-info">
            成绩流程状态：
            <el-tag size="small" :type="auditTagType">{{ auditText }}</el-tag>
            <template v-if="rejectReason">
              <span class="reject">退回原因：{{ rejectReason }}</span>
            </template>
          </div>
        </div>
        <div class="actions">
          <el-button :disabled="students.length === 0" @click="handleTemplate">下载模板</el-button>
          <el-button type="success" :loading="importing" :disabled="locked || students.length === 0" @click="gradeFileRef?.click()">
            批量导入
          </el-button>
          <input ref="gradeFileRef" type="file" accept=".xlsx,.xls" hidden @change="handleImport" />
          <el-button :disabled="students.length === 0" @click="handleExport">导出名单</el-button>
          <el-button type="primary" :disabled="locked" @click="handleSave">保存成绩</el-button>
          <el-button type="success" :disabled="locked" @click="handleSubmit">提交成绩</el-button>
        </div>
      </div>

      <el-table :data="students" border>
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="className" label="班级" min-width="120" />
        <el-table-column label="总评成绩（0-100）" min-width="180">
          <template #default="{ row }">
            <el-input
              v-if="row.mark === 'NORMAL'"
              v-model="row.score"
              type="number"
              :min="0"
              :max="100"
              :disabled="locked"
              size="small"
              placeholder="请输入成绩"
            />
            <span v-else class="gray">{{ markLabel(row.mark) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="标记" width="130">
          <template #default="{ row }">
            <el-select v-model="row.mark" :disabled="locked" size="small" @change="(val: string) => onMarkChange(row, val)">
              <el-option label="正常" value="NORMAL" />
              <el-option label="缓考" value="DEFER" />
              <el-option label="缺考" value="ABSENT" />
              <el-option label="舞弊" value="CHEAT" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && students.length === 0" description="该课程暂无学生选课" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  courseStudents,
  downloadGradeTemplate,
  entryGrades,
  exportCourseStudents,
  importGrades,
  myAudits,
  submitGrades
} from '@/api/grade'
import { datedFilename, saveBlob } from '@/api/http'
import type { CourseStudentItem } from '@/types'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const students = ref<CourseStudentItem[]>([])
const audits = ref<Awaited<ReturnType<typeof myAudits>>>([])
const locked = ref(false)
const gradeFileRef = ref<HTMLInputElement>()

const courseId = computed(() => Number(route.params.courseId))
const courseName = computed(() => route.query.name as string || '课程')

const currentAudit = computed(() => audits.value.find((a) => a.courseId === courseId.value))

const auditText = computed(() => {
  const status = currentAudit.value?.status
  const map: Record<string, string> = {
    DRAFT: '录入中（可修改）',
    SUBMITTED: '待教学秘书审核（已锁定）',
    APPROVED: '审核通过（待发布）',
    PUBLISHED: '已发布（永久锁定）'
  }
  return status ? map[status] || status : '未发起'
})

const auditTagType = computed(() => {
  const status = currentAudit.value?.status
  const map: Record<string, 'info' | 'warning' | 'success'> = {
    DRAFT: 'info',
    SUBMITTED: 'warning',
    APPROVED: 'info',
    PUBLISHED: 'success'
  }
  return status ? map[status] || 'info' : 'info'
})

const rejectReason = computed(() => currentAudit.value?.rejectReason)

function markLabel(mark: string) {
  const map: Record<string, string> = { DEFER: '缓考', ABSENT: '缺考', CHEAT: '舞弊' }
  return map[mark] || mark
}

function onMarkChange(row: CourseStudentItem, val: string) {
  if (val === 'NORMAL') {
    row.score = row.score ?? undefined
  } else {
    row.score = undefined
  }
}

async function load() {
  loading.value = true
  try {
    audits.value = await myAudits()
    const items = await courseStudents(courseId.value)
    students.value = items.map((i) => ({
      ...i,
      score: i.score != null ? Number(i.score) : undefined,
      mark: i.mark || 'NORMAL'
    }))
    locked.value = currentAudit.value?.status ? currentAudit.value.status !== 'DRAFT' : false
  } finally {
    loading.value = false
  }
}

function validate(): boolean {
  for (const s of students.value) {
    if (s.mark === 'NORMAL' && (s.score === undefined || s.score === null)) {
      ElMessage.warning(`请为 ${s.realName} 录入成绩，或标记为缓考/缺考/舞弊`)
      return false
    }
    if (s.mark === 'NORMAL' && (Number(s.score) < 0 || Number(s.score) > 100)) {
      ElMessage.warning(`${s.realName} 的成绩需在 0-100 之间`)
      return false
    }
  }
  return true
}

async function handleSave() {
  if (!validate()) return
  saving.value = true
  try {
    await entryGrades(
      courseId.value,
      students.value.map((s) => ({
        studentId: s.studentId,
        score: s.score,
        mark: s.mark as 'NORMAL' | 'DEFER' | 'ABSENT' | 'CHEAT'
      }))
    )
    ElMessage.success('成绩已保存')
    load()
  } finally {
    saving.value = false
  }
}

function handleSubmit() {
  if (!validate()) return
  ElMessageBox.confirm('提交后成绩将锁定，不可再修改，确定提交给教学秘书审核吗？', '提交确认', {
    type: 'warning'
  }).then(async () => {
    await submitGrades(courseId.value)
    ElMessage.success('已提交，等待教学秘书审核')
    load()
  })
}

async function handleTemplate() {
  const blob = await downloadGradeTemplate(courseId.value)
  saveBlob(blob, datedFilename(`成绩导入模板_${courseName.value}`))
}

async function handleExport() {
  const blob = await exportCourseStudents(courseId.value)
  saveBlob(blob, datedFilename(`成绩_${courseName.value}`))
}

async function handleImport(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  importing.value = true
  try {
    const count = await importGrades(courseId.value, file)
    ElMessage.success(`成功导入 ${count} 名学生的成绩`)
    load()
  } finally {
    importing.value = false
    input.value = ''
  }
}

watch(
  () => route.params.courseId,
  () => courseId.value && load()
)

onMounted(() => {
  if (courseId.value) {
    load()
  } else {
    // 直接从侧边栏进入（无 courseId）：跳回课程列表并提示选择
    ElMessage.warning('请先选择要管理的课程')
    router.push('/teacher/courses')
  }
})
</script>

<style scoped>
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.audit-info {
  margin-top: 12px;
  font-size: 13px;
  color: #606266;
}
.reject {
  margin-left: 12px;
  color: #f56c6c;
}
.gray {
  color: #909399;
}
</style>
