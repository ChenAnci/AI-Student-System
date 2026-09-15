<template>
  <div v-loading="loading">
    <el-card shadow="never">
      <el-table :data="courses">
        <el-table-column prop="courseCode" label="课程编号" width="110" />
        <el-table-column prop="courseName" label="课程名称" min-width="140" />
        <el-table-column label="学分" width="80">
          <template #default="{ row }">{{ row.credit }}</template>
        </el-table-column>
        <el-table-column prop="teacherName" label="授课教师" width="110" />
        <el-table-column prop="schedule" label="上课时间" min-width="120" />
        <el-table-column prop="location" label="上课地点" min-width="110" />
        <el-table-column label="人数" width="90">
          <template #default="{ row }">{{ row.currentEnrolled }} / {{ row.capacity }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button type="warning" link @click="handleDrop(row)">退课</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && courses.length === 0" description="还没有选修课程，去选课中心看看吧" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { myCourses, drop } from '@/api/enroll'
import type { CourseCardVO } from '@/types'

const loading = ref(false)
const courses = ref<CourseCardVO[]>([])

async function load() {
  loading.value = true
  try {
    courses.value = await myCourses()
  } finally {
    loading.value = false
  }
}

function handleDrop(row: CourseCardVO) {
  ElMessageBox.confirm(`确定退选「${row.courseName}」吗？`, '退课确认', { type: 'warning' }).then(
    async () => {
      await drop(row.id)
      ElMessage.success('退课成功')
      load()
    }
  )
}

onMounted(load)
</script>
