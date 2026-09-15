<template>
  <div v-loading="loading">
    <el-card shadow="never">
      <el-table :data="monitors">
        <el-table-column prop="courseCode" label="课程编号" width="110" />
        <el-table-column prop="courseName" label="课程名称" min-width="140" />
        <el-table-column prop="teacherName" label="授课教师" width="110" />
        <el-table-column label="容量" width="80" align="center">
          <template #default="{ row }">{{ row.capacity }}</template>
        </el-table-column>
        <el-table-column label="已选人数" width="100" align="center">
          <template #default="{ row }">
            <span :class="row.currentEnrolled >= row.capacity ? 'full' : ''">{{ row.currentEnrolled }}</span>
          </template>
        </el-table-column>
        <el-table-column label="剩余名额" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.remain === 0 ? 'danger' : 'success'" size="small">{{ row.remain }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'PUBLISHED' ? 'success' : 'info'">
              {{ row.status === 'PUBLISHED' ? '已发布' : '未发布' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewStudents(row.courseId)">选课名单</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && monitors.length === 0" description="暂无课程" />
    </el-card>

    <!-- 选课名单弹窗 -->
    <el-dialog v-model="dialogVisible" title="选课学生名单（可手动退课 / 代选课）" width="600px">
      <div class="dialog-toolbar">
        <el-button type="primary" size="small" @click="openAddStudent">添加学生选课</el-button>
      </div>
      <el-table :data="students" size="small">
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="realName" label="姓名" width="90" />
        <el-table-column prop="className" label="班级" min-width="100" />
        <el-table-column label="成绩" width="90">
          <template #default="{ row }">{{ row.score ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleDrop(row)">退课</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="students.length === 0" description="暂无学生选课" />
    </el-dialog>

    <!-- 代选课：选择学生弹窗 -->
    <el-dialog v-model="addVisible" title="为学生代选本课程" width="460px">
      <el-select
        v-model="selectedStudentId"
        filterable
        placeholder="按姓名/学号搜索学生"
        style="width: 100%"
        size="large"
      >
        <el-option
          v-for="s in studentOptions"
          :key="s.id"
          :label="`${s.realName}（${s.studentNo} / ${s.className || '-'}）`"
          :value="s.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="adding" :disabled="!selectedStudentId" @click="handleAddStudent">
          确认选课
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminDrop, adminEnroll, monitor } from '@/api/enroll'
import { listStudents } from '@/api/account'
import { courseStudents } from '@/api/grade'
import type { CourseStudentItem, EnrollMonitorVO, Student } from '@/types'

const loading = ref(false)
const monitors = ref<EnrollMonitorVO[]>([])
const dialogVisible = ref(false)
const students = ref<CourseStudentItem[]>([])
const currentCourseId = ref(0)
const addVisible = ref(false)
const adding = ref(false)
const studentOptions = ref<Student[]>([])
const selectedStudentId = ref<number>()

async function load() {
  loading.value = true
  try {
    monitors.value = await monitor()
  } finally {
    loading.value = false
  }
}

async function viewStudents(courseId: number) {
  currentCourseId.value = courseId
  students.value = await courseStudents(courseId)
  dialogVisible.value = true
}

async function openAddStudent() {
  selectedStudentId.value = undefined
  if (studentOptions.value.length === 0) {
    studentOptions.value = await listStudents()
  }
  addVisible.value = true
}

async function handleAddStudent() {
  if (!selectedStudentId.value) return
  adding.value = true
  try {
    await adminEnroll(currentCourseId.value, selectedStudentId.value)
    ElMessage.success('代选课成功')
    addVisible.value = false
    students.value = await courseStudents(currentCourseId.value)
    load()
  } finally {
    adding.value = false
  }
}

function handleDrop(row: CourseStudentItem) {
  ElMessageBox.confirm(`确定将学生「${row.realName}」移出该课程（手动退课）？`, '手动退课', {
    type: 'warning'
  }).then(async () => {
    await adminDrop(currentCourseId.value, row.studentId)
    ElMessage.success('退课成功')
    students.value = await courseStudents(currentCourseId.value)
    load()
  })
}

onMounted(load)
</script>

<style scoped>
.full {
  color: #f56c6c;
  font-weight: 600;
}
.dialog-toolbar {
  margin-bottom: 10px;
  text-align: right;
}
</style>
