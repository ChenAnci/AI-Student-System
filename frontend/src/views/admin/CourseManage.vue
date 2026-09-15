<template>
  <div v-loading="loading">
    <el-card shadow="never">
      <div class="toolbar">
        <div class="search-bar">
          <el-input
            v-model="keyword"
            placeholder="按课程名称/编号搜索"
            clearable
            style="width: 280px"
            @keyup.enter="load"
          />
          <el-button type="primary" @click="load">查询</el-button>
        </div>
        <el-button type="primary" @click="openCreate">新增课程</el-button>
      </div>
      <el-table :data="courses">
        <el-table-column prop="courseCode" label="课程编号" width="110" />
        <el-table-column prop="courseName" label="课程名称" min-width="140" />
        <el-table-column label="学分/学时" width="100">
          <template #default="{ row }">{{ row.credit }} / {{ row.hours }}</template>
        </el-table-column>
        <el-table-column label="授课教师" width="110">
          <template #default="{ row }">
            {{ teacherName(row.teacherId) }}
          </template>
        </el-table-column>
        <el-table-column prop="schedule" label="上课时间" min-width="120" />
        <el-table-column prop="location" label="地点" min-width="100" />
        <el-table-column label="人数" width="90">
          <template #default="{ row }">{{ row.currentEnrolled }} / {{ row.capacity }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">
              {{ row.status === 'PUBLISHED' ? '已发布' : '未发布' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="成绩状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="auditTagType(row.auditStatus)">{{ auditText(row.auditStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.status === 'PUBLISHED'" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button link type="success" :disabled="row.status === 'PUBLISHED'" @click="handlePublish(row)">
              发布
            </el-button>
            <el-button link type="danger" :disabled="row.status === 'PUBLISHED'" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && courses.length === 0" description="暂无课程" />
    </el-card>

    <!-- 新增 / 编辑课程弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑课程' : '新增课程'" width="560px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="课程编号" prop="courseCode">
          <el-input v-model="form.courseCode" placeholder="如 CS101" />
        </el-form-item>
        <el-form-item label="课程名称" prop="courseName">
          <el-input v-model="form.courseName" placeholder="如 Java 程序设计" />
        </el-form-item>
        <el-form-item label="授课教师" prop="teacherId">
          <el-select v-model="form.teacherId" filterable placeholder="请选择授课教师" style="width: 100%">
            <el-option v-for="t in teacherOptions" :key="t.id" :label="t.realName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学分" prop="credit">
          <el-input-number v-model="form.credit" :min="0.5" :max="20" :step="0.5" :precision="1" />
        </el-form-item>
        <el-form-item label="学时" prop="hours">
          <el-input-number v-model="form.hours" :min="1" :max="200" />
        </el-form-item>
        <el-form-item label="容量" prop="capacity">
          <el-input-number v-model="form.capacity" :min="1" :max="1000" />
        </el-form-item>
        <el-form-item label="上课时间" prop="schedule">
          <SchedulePicker v-model="form.schedule" />
        </el-form-item>
        <el-form-item label="地点" prop="location">
          <el-input v-model="form.location" placeholder="如 A101" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createCourse, deleteCourse, listAllCourses, publishCourse, updateCourse, type CourseForm } from '@/api/course'
import { listStaffs } from '@/api/account'
import SchedulePicker from '@/components/SchedulePicker.vue'
import type { MyCourseVO, Staff } from '@/types'

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const courses = ref<MyCourseVO[]>([])
const teacherMap = ref<Record<number, string>>({})
const teacherOptions = ref<Staff[]>([])
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()

const emptyForm = (): CourseForm & { id?: number } => ({
  courseCode: '',
  courseName: '',
  teacherId: undefined,
  credit: 2,
  hours: 32,
  capacity: 60,
  schedule: '',
  location: ''
})
const form = reactive<CourseForm & { id?: number }>(emptyForm())

const rules: FormRules = {
  courseCode: [{ required: true, message: '请输入课程编号', trigger: 'blur' }],
  courseName: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  teacherId: [{ required: true, message: '请选择授课教师', trigger: 'change' }]
}

function teacherName(id: number) {
  return teacherMap.value[id] || `教师#${id}`
}

function auditText(status?: string) {
  const map: Record<string, string> = {
    DRAFT: '录入中',
    SUBMITTED: '待审核',
    APPROVED: '已审核',
    PUBLISHED: '已发布'
  }
  return status ? map[status] || status : '未发起'
}

function auditTagType(status?: string) {
  const map: Record<string, 'info' | 'warning' | 'success'> = {
    DRAFT: 'info',
    SUBMITTED: 'warning',
    APPROVED: 'info',
    PUBLISHED: 'success'
  }
  return status ? map[status] || 'info' : 'info'
}

function openCreate() {
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row: MyCourseVO) {
  Object.assign(form, {
    id: row.id,
    courseCode: row.courseCode,
    courseName: row.courseName,
    teacherId: row.teacherId,
    credit: row.credit,
    hours: row.hours,
    capacity: row.capacity,
    schedule: row.schedule,
    location: row.location
  })
  dialogVisible.value = true
}

function resetForm() {
  Object.assign(form, emptyForm())
  formRef.value?.clearValidate()
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload: CourseForm = {
      courseCode: form.courseCode,
      courseName: form.courseName,
      teacherId: form.teacherId,
      credit: form.credit,
      hours: form.hours,
      capacity: form.capacity,
      schedule: form.schedule,
      location: form.location
    }
    if (form.id) {
      await updateCourse(form.id, payload)
      ElMessage.success('课程已更新')
    } else {
      await createCourse(payload)
      ElMessage.success('课程已创建')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

function handlePublish(row: MyCourseVO) {
  ElMessageBox.confirm(`发布后课程信息将永久锁定，确定发布「${row.courseName}」吗？`, '发布课程', {
    type: 'warning'
  }).then(async () => {
    await publishCourse(row.id)
    ElMessage.success('课程已发布')
    load()
  })
}

function handleDelete(row: MyCourseVO) {
  ElMessageBox.confirm(`确定删除课程「${row.courseName}」吗？删除后不可恢复。`, '删除课程', {
    type: 'warning'
  }).then(async () => {
    await deleteCourse(row.id)
    ElMessage.success('课程已删除')
    load()
  })
}

async function load() {
  loading.value = true
  try {
    const list = await listAllCourses(keyword.value || undefined)
    courses.value = list
    const staffs = await listStaffs()
    teacherMap.value = {}
    staffs.forEach((s) => (teacherMap.value[s.id] = s.realName))
    teacherOptions.value = staffs.filter((s) => s.roleType === 'TEACHER')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}
.search-bar {
  display: flex;
  gap: 10px;
}
</style>
