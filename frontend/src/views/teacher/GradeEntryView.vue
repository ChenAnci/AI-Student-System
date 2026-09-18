<template>
  <div v-loading="loading">
    <el-card shadow="never">
      <el-page-header @back="$router.push('/teacher/courses')" content="成绩管理" />
      <div class="header-bar">
        <div class="left">
          <el-select v-model="selectedCourseId" filterable placeholder="请选择要管理的课程" style="width: 280px">
            <el-option v-for="c in myCourses" :key="c.id" :label="`${c.courseCode} ${c.courseName}`" :value="c.id" />
          </el-select>
          <div v-if="selectedCourseId" class="audit-info">
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
      <el-empty v-if="!loading && !selectedCourseId" description="请先选择要管理的课程" />
      <el-empty v-else-if="!loading && students.length === 0" description="该课程暂无学生选课" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 成绩管理（教师）
 * 职责：选择课程后加载其审核流程状态与选课学生名单，逐人录入成绩或标记（正常/缓考/缺考/舞弊）；
 *       支持批量导入 xlsx、导出名单、下载导入模板、保存成绩与提交审核；
 *       成绩仅"录入中（DRAFT）"状态可编辑，提交后锁定，退回原因会在页头展示。
 */
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
import { listMyCourses } from '@/api/course'
import type { CourseStudentItem, MyCourseVO } from '@/types'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const students = ref<CourseStudentItem[]>([])
const audits = ref<Awaited<ReturnType<typeof myAudits>>>([])
const myCourses = ref<MyCourseVO[]>([])
// 页内课程选择器当前值（0=未选）；locked=成绩是否锁定（非"录入中"状态不可编辑）；gradeFileRef=隐藏的文件输入框引用
const selectedCourseId = ref<number>(0)
const locked = ref(false)
const gradeFileRef = ref<HTMLInputElement>()

// 课程由页内选择器决定（支持从侧栏"成绩管理"直接进入并自行选课，不再强制从"我的课程"跳转）
const courseId = computed(() => selectedCourseId.value)
// 当前选中课程的名称（用于导出/模板文件名），未找到时兜底为"课程"
const courseName = computed(() => {
  const c = myCourses.value.find((m) => m.id === selectedCourseId.value)
  return c ? c.courseName : '课程'
})

// 当前课程对应的审核流程记录（无记录时为空）
const currentAudit = computed(() => audits.value.find((a) => a.courseId === courseId.value))

// 成绩流程状态 → 中文文案（含可编辑/锁定提示）
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

// 成绩流程状态 → el-tag 颜色类型（与 auditText 对应）
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

// 特殊标记 → 中文文案（缓考 / 缺考 / 舞弊），用于成绩列占位展示
function markLabel(mark: string) {
  const map: Record<string, string> = { DEFER: '缓考', ABSENT: '缺考', CHEAT: '舞弊' }
  return map[mark] || mark
}

// 标记变化联动：改为缓考/缺考/舞弊时清空成绩（这类学生不评成绩）；
// 改回 NORMAL 时保留原成绩（若有），避免误清
function onMarkChange(row: CourseStudentItem, val: string) {
  if (val === 'NORMAL') {
    row.score = row.score ?? undefined
  } else {
    row.score = undefined
  }
}

// 加载当前课程：并行获取审核流程状态与选课学生名单；
// score 统一转 number、mark 默认 NORMAL（保证输入框/下拉的初始值正确）；
// 仅 DRAFT（录入中）状态可编辑，其余（已提交/已通过/已发布）一律锁定
async function load() {
  if (!courseId.value) return
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

// 提交前校验：NORMAL 状态必须有 0-100 的成绩；缓考/缺考/舞弊等标记的学生无需成绩
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

// 保存成绩：通过校验后批量提交当前列表的成绩与标记，成功后重新加载最新数据
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

// 提交审核：提交后状态变为 SUBMITTED、成绩锁定不可再修改，故需二次确认
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

// 下载 Excel 导入模板（含该课程学生名单与填写格式）
async function handleTemplate() {
  const blob = await downloadGradeTemplate(courseId.value)
  saveBlob(blob, datedFilename(`成绩导入模板_${courseName.value}`))
}

// 导出当前课程成绩名单为 Excel
async function handleExport() {
  const blob = await exportCourseStudents(courseId.value)
  saveBlob(blob, datedFilename(`成绩_${courseName.value}`))
}

// 批量导入成绩：读取所选 xlsx 上传，成功后刷新列表；无论成败都清空 input.value 以便下次选择同一文件可再次触发 change
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

// 页内切换课程：立即加载该课程数据（含锁定状态切换）
watch(selectedCourseId, (val) => {
  if (val) load()
})

// 路由参数 courseId 变化（如从"我的课程"页点击某课程跳入）时同步到选择器；
// 加 !== 判断防止与选择器自身变更互相触发、重复加载
watch(
  () => route.params.courseId,
  (val) => {
    const id = Number(val)
    if (id && id !== selectedCourseId.value) {
      selectedCourseId.value = id
    }
  }
)

onMounted(async () => {
  // 初始化老师自己的课程列表（供页内选择器使用），再尝试从路由取 courseId 直接选中
  try {
    myCourses.value = await listMyCourses()
  } catch {
    // 已由拦截器提示
  }
  const fromRoute = Number(route.params.courseId)
  if (fromRoute) {
    // 从"我的课程"带 courseId 进入：同步到选择器并触发加载
    selectedCourseId.value = fromRoute
  }
})
</script>

<style scoped>
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-top: 16px;
  margin-bottom: 16px;
}
.left {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
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
