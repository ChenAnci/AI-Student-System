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
/**
 * 我的课程（学生）
 * 职责：以表格展示当前学生已选修的课程（编号、名称、学分、教师、时间地点、人数）；
 *       提供退课操作（二次确认后调用退课接口并刷新列表），空列表时给出引导提示。
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { myCourses, drop } from '@/api/enroll'
import type { CourseCardVO } from '@/types'

const loading = ref(false)
const courses = ref<CourseCardVO[]>([])

// 加载当前学生已选修的课程列表（加载期间展示全局 loading）
async function load() {
  loading.value = true
  try {
    courses.value = await myCourses()
  } finally {
    loading.value = false
  }
}

// 退课：二次确认（type: warning）后调用退课接口，成功提示并刷新课程列表
function handleDrop(row: CourseCardVO) {
  ElMessageBox.confirm(`确定退选「${row.courseName}」吗？`, '退课确认', { type: 'warning' }).then(
    async () => {
      await drop(row.id)
      ElMessage.success('退课成功')
      load()
    }
  )
}

// 页面挂载后加载我的课程列表
onMounted(load)
</script>
