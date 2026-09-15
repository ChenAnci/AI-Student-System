<template>
  <div v-loading="loading">
    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" @click="openForm()">
          <el-icon><Plus /></el-icon>新建课程
        </el-button>
      </div>
      <el-table :data="courses">
        <el-table-column prop="courseCode" label="课程编号" width="110" />
        <el-table-column prop="courseName" label="课程名称" min-width="140" />
        <el-table-column label="学分/学时" width="110">
          <template #default="{ row }">{{ row.credit }} / {{ row.hours }}</template>
        </el-table-column>
        <el-table-column prop="schedule" label="上课时间" min-width="120" />
        <el-table-column prop="location" label="地点" min-width="100" />
        <el-table-column label="人数" width="90">
          <template #default="{ row }">{{ row.currentEnrolled }} / {{ row.capacity }}</template>
        </el-table-column>
        <el-table-column label="课程状态" width="100">
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
        <el-table-column label="操作" width="230" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push(`/teacher/grade-entry/${row.id}`)">
              成绩管理
            </el-button>
            <template v-if="row.status === 'UNPUBLISHED'">
              <el-button link type="primary" @click="openForm(row)">编辑</el-button>
              <el-button link type="success" @click="handlePublish(row)">发布</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && courses.length === 0" description="暂无课程" />
    </el-card>

    <!-- 新建/编辑课程弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑课程' : '新建课程'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="课程编号" prop="courseCode">
          <el-input v-model="form.courseCode" placeholder="如 CS201" />
        </el-form-item>
        <el-form-item label="课程名称" prop="courseName">
          <el-input v-model="form.courseName" />
        </el-form-item>
        <el-form-item label="学分" prop="credit">
          <el-input-number v-model="form.credit" :min="0.5" :max="10" :step="0.5" />
        </el-form-item>
        <el-form-item label="学时" prop="hours">
          <el-input-number v-model="form.hours" :min="8" :max="200" :step="8" />
        </el-form-item>
        <el-form-item label="容量" prop="capacity">
          <el-input-number v-model="form.capacity" :min="1" :max="200" />
        </el-form-item>
        <el-form-item label="上课时间">
          <SchedulePicker v-model="form.schedule" />
        </el-form-item>
        <el-form-item label="上课地点">
          <el-input v-model="form.location" />
        </el-form-item>
        <el-form-item label="封面图URL">
          <el-input v-model="form.coverImageUrl" placeholder="可选" />
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
import { createCourse, deleteCourse, listMyCourses, publishCourse, updateCourse } from '@/api/course'
import SchedulePicker from '@/components/SchedulePicker.vue'
import type { CourseForm } from '@/api/course'
import type { MyCourseVO } from '@/types'

const loading = ref(false)
const saving = ref(false)
const courses = ref<MyCourseVO[]>([])
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<CourseForm & { id?: number }>({
  id: undefined,
  courseCode: '',
  courseName: '',
  credit: 3,
  hours: 48,
  capacity: 30,
  schedule: '',
  location: '',
  coverImageUrl: ''
})

const rules: FormRules = {
  courseCode: [{ required: true, message: '请输入课程编号', trigger: 'blur' }],
  courseName: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  credit: [{ required: true, message: '请输入学分', trigger: 'blur' }],
  hours: [{ required: true, message: '请输入学时', trigger: 'blur' }],
  capacity: [{ required: true, message: '请输入容量', trigger: 'blur' }]
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

async function load() {
  loading.value = true
  try {
    courses.value = await listMyCourses()
  } finally {
    loading.value = false
  }
}

function openForm(row?: MyCourseVO) {
  form.id = row?.id
  form.courseCode = row?.courseCode ?? ''
  form.courseName = row?.courseName ?? ''
  form.credit = row?.credit ?? 3
  form.hours = row?.hours ?? 48
  form.capacity = row?.capacity ?? 30
  form.schedule = row?.schedule ?? ''
  form.location = row?.location ?? ''
  form.coverImageUrl = row?.coverImageUrl ?? ''
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (form.id) {
      await updateCourse(form.id, form)
      ElMessage.success('课程已更新')
    } else {
      await createCourse(form)
      ElMessage.success('课程创建成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

function handlePublish(row: MyCourseVO) {
  ElMessageBox.confirm(
    `发布后课程信息将永久锁定，且会出现在学生选课中心，确定发布「${row.courseName}」吗？`,
    '发布确认',
    { type: 'warning' }
  ).then(async () => {
    await publishCourse(row.id)
    ElMessage.success('发布成功')
    load()
  })
}

function handleDelete(row: MyCourseVO) {
  ElMessageBox.confirm(`确定删除课程「${row.courseName}」吗？`, '删除确认', { type: 'warning' }).then(
    async () => {
      await deleteCourse(row.id)
      ElMessage.success('删除成功')
      load()
    }
  )
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  margin-bottom: 14px;
}
</style>
