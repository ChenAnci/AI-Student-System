<template>
  <div v-loading="loading">
    <el-row :gutter="16">
      <el-col v-for="course in courses" :key="course.id" :span="6" class="mb16">
        <el-card :body-style="{ padding: '0px' }" shadow="hover" class="course-card">
          <div class="cover">
            <img v-if="course.coverImageUrl" :src="course.coverImageUrl" alt="封面" />
            <el-icon v-else :size="40" color="#a8abb2"><Picture /></el-icon>
            <span class="status-tag">{{ course.status === 'PUBLISHED' ? '已发布' : '未发布' }}</span>
          </div>
          <div class="body">
            <div class="name">{{ course.courseName }}</div>
            <div class="meta">
              <span class="teacher">
                <el-icon><User /></el-icon>{{ course.teacherName }}
              </span>
            </div>
            <div class="credit">{{ course.credit }}学分 / {{ course.hours }}学时</div>
            <div class="meta">{{ course.schedule || '时间待定' }} · {{ course.location || '地点待定' }}</div>
            <div class="meta">
              已选 <b :class="course.full ? 'full' : ''">{{ course.currentEnrolled }}</b> / {{ course.capacity }}
              <el-tag v-if="course.full" size="small" type="danger" class="ml8">已满</el-tag>
            </div>
            <div class="actions">
              <el-button
                v-if="course.enrolled"
                type="warning"
                plain
                size="small"
                @click="handleDrop(course)"
              >
                退课
              </el-button>
              <el-button
                v-else
                type="primary"
                size="small"
                :disabled="course.full"
                @click="handleEnroll(course)"
              >
                选课
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-empty v-if="!loading && courses.length === 0" description="暂无已发布的课程" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { courseCenter, enroll, drop } from '@/api/enroll'
import type { CourseCardVO } from '@/types'

const loading = ref(false)
const courses = ref<CourseCardVO[]>([])

async function load() {
  loading.value = true
  try {
    courses.value = await courseCenter()
  } finally {
    loading.value = false
  }
}

async function handleEnroll(course: CourseCardVO) {
  await enroll(course.id)
  ElMessage.success(`选课成功：${course.courseName}`)
  load()
}

function handleDrop(course: CourseCardVO) {
  ElMessageBox.confirm(`确定退选「${course.courseName}」吗？退课后名额立即释放`, '退课确认', {
    type: 'warning'
  }).then(async () => {
    await drop(course.id)
    ElMessage.success('退课成功')
    load()
  })
}

onMounted(load)
</script>

<style scoped>
.mb16 {
  margin-bottom: 16px;
}
.course-card :deep(.el-card__body) {
  padding: 0;
}
.cover {
  height: 110px;
  background: linear-gradient(135deg, #dbe8ff, #f0f6ff);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}
.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.status-tag {
  position: absolute;
  top: 8px;
  right: 8px;
  background: rgba(64, 158, 255, 0.9);
  color: #fff;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
}
.body {
  padding: 12px 14px 14px;
}
.name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}
.meta {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 2px;
}
.credit {
  font-size: 13px;
  color: #409eff;
  font-weight: 600;
  margin: 6px 0;
}
.full {
  color: #f56c6c;
}
.actions {
  margin-top: 10px;
  text-align: right;
}
.ml8 {
  margin-left: 8px;
}
</style>
