<template>
  <div v-loading="loading">
    <!-- 选课概览统计 -->
    <el-row :gutter="16" class="mb16">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <div class="stat-label">已选课程</div>
            <div class="stat-value primary">{{ enrolledCount }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <div class="stat-label">已获学分</div>
            <div class="stat-value success">{{ earnedCredits }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <div class="stat-label">可选余量</div>
            <div class="stat-value warning">{{ availableCount }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <div class="stat-label">课程总数</div>
            <div class="stat-value">{{ totalCount }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索与筛选 -->
    <el-card shadow="never" class="mb16">
      <div class="filters">
        <el-input
          v-model="keyword"
          placeholder="搜索课程名 / 教师 / 课程号"
          clearable
          class="filter-keyword"
          :prefix-icon="Search"
        />
        <el-select v-model="creditRange" class="filter-select" placeholder="学分范围">
          <el-option label="不限学分" value="" />
          <el-option label="1 - 2 学分" value="1-2" />
          <el-option label="3 - 4 学分" value="3-4" />
          <el-option label="5 学分及以上" value="5+" />
        </el-select>
        <el-select v-model="statusFilter" class="filter-select" placeholder="状态">
          <el-option label="全部" value="all" />
          <el-option label="可选" value="available" />
          <el-option label="已满" value="full" />
          <el-option label="已选" value="enrolled" />
        </el-select>
      </div>
    </el-card>

    <!-- 课程卡片 -->
    <el-row :gutter="16">
      <el-col v-for="course in displayCourses" :key="course.id" :span="6" class="mb16">
        <el-card :body-style="{ padding: '0px' }" shadow="hover" class="course-card">
          <div class="cover">
            <img v-if="course.coverImageUrl" :src="course.coverImageUrl" alt="封面" />
            <el-icon v-else :size="40" color="#a8abb2"><Picture /></el-icon>
            <span class="status-tag" :class="cardStatus(course).tagClass">{{ cardStatus(course).text }}</span>
          </div>
          <div class="body">
            <div class="code">{{ course.courseCode }}</div>
            <div class="name">{{ course.courseName }}</div>
            <div class="credit">{{ course.credit }} 学分 · {{ course.hours }} 学时</div>
            <div class="meta">
              <el-icon><User /></el-icon>{{ course.teacherName }}
            </div>
            <div class="meta">
              <el-icon><Clock /></el-icon>{{ course.schedule || '时间待定' }}
            </div>
            <div class="meta">
              <el-icon><Location /></el-icon>{{ course.location || '地点待定' }}
            </div>
            <div class="meta">
              剩余 <b :class="course.full ? 'full' : 'ok'">{{ course.remain }}</b> 人
              <el-tag v-if="course.enrolled" type="success" size="small" class="ml8">已选</el-tag>
              <el-tag v-else-if="course.full" type="danger" size="small" class="ml8">已满</el-tag>
              <el-tag v-else-if="course.conflict" type="warning" size="small" class="ml8">时间冲突</el-tag>
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
                :disabled="course.full || course.conflict"
                @click="handleEnroll(course)"
              >
                {{ course.full ? '已满' : course.conflict ? '时间冲突' : '选课' }}
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-empty v-if="!loading && displayCourses.length === 0" description="没有符合条件的课程" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { courseCenter, enroll, drop, type CourseCenterQuery } from '@/api/enroll'
import type { CourseCardVO } from '@/types'

// 状态筛选项：全部 / 可选 / 已满 / 已选（前端本地过滤，不请求后端）
type StatusFilter = 'all' | 'available' | 'full' | 'enrolled'

// 卡片展示用扩展：remain=剩余名额（展示/余量判断），conflict=与已选课程时间冲突（禁用选课）
interface DisplayCourse extends CourseCardVO {
  remain: number
  conflict: boolean
}

const loading = ref(false)
const courses = ref<CourseCardVO[]>([])
const keyword = ref('')
const creditRange = ref('')
const statusFilter = ref<StatusFilter>('all')

// ===== 选课概览统计 =====
// 全部基于已加载课程列表在前端计算（无需额外接口）：已选门数、已获学分合计、可选余量、课程总数
const enrolledList = computed(() => courses.value.filter((c) => c.enrolled))
const enrolledCount = computed(() => enrolledList.value.length)
const earnedCredits = computed(() => enrolledList.value.reduce((s, c) => s + Number(c.credit || 0), 0))
const availableCount = computed(() => courses.value.filter((c) => !c.enrolled && !c.full).length)
const totalCount = computed(() => courses.value.length)

// ===== schedule 解析（与 SchedulePicker 一致：如 "周一 1-2节;周三 3-4节"）=====
// 把排课文本解析为「周X-第N节」的时段集合，供后续做选课时间冲突检测
function scheduleSlots(text?: string): Set<string> {
  const set = new Set<string>()
  if (!text) return set
  const re = /周[一二三四五六日天]\s*(\d+)\s*[-~—至]\s*(\d+)节/g
  let m: RegExpExecArray | null
  while ((m = re.exec(text))) {
    const day = m[0].match(/周[一二三四五六日天]/)![0]
    const start = Number(m[1])
    const end = Number(m[2])
    for (let i = start; i <= end; i++) set.add(`${day}-${i}`)
  }
  return set
}

// 已选课程占用的全部时段（用于冲突标记）
// 把每门已选课的时段并入同一集合，供 hasConflict 做交集比对
const enrolledSlots = computed(() => {
  const set = new Set<string>()
  for (const c of enrolledList.value) scheduleSlots(c.schedule).forEach((k) => set.add(k))
  return set
})

// 判断某课程是否与已选课程时间冲突（已选课程自身不算冲突）
function hasConflict(course: CourseCardVO): boolean {
  if (course.enrolled) return false
  for (const k of scheduleSlots(course.schedule)) if (enrolledSlots.value.has(k)) return true
  return false
}

// ===== 状态筛选（前端过滤）=====
const filteredByStatus = computed(() => {
  const list = courses.value
  if (statusFilter.value === 'available') return list.filter((c) => !c.enrolled && !c.full)
  if (statusFilter.value === 'full') return list.filter((c) => c.full)
  if (statusFilter.value === 'enrolled') return list.filter((c) => c.enrolled)
  return list
})

// 最终展示列表：补全剩余名额（remain，Math.max 下限 0 防负数）与时间冲突标记，供卡片渲染与选课按钮禁用判断
const displayCourses = computed<DisplayCourse[]>(() =>
  filteredByStatus.value.map((c) => ({
    ...c,
    remain: Math.max(0, (c.capacity || 0) - (c.currentEnrolled || 0)),
    conflict: hasConflict(c)
  }))
)

function cardStatus(c: DisplayCourse): { text: string; tagClass: string } {
  if (c.enrolled) return { text: '已选', tagClass: 'tag-success' }
  if (c.full) return { text: '已满', tagClass: 'tag-danger' }
  if (c.conflict) return { text: '冲突', tagClass: 'tag-warning' }
  return { text: '可选', tagClass: 'tag-primary' }
}

// ===== 加载（关键词 / 学分范围变化防抖后重新请求后端）=====
// 400ms 防抖：避免每敲一个字符都触发一次接口请求；状态筛选（statusFilter）走前端过滤，不走接口
let debounceTimer: ReturnType<typeof setTimeout> | undefined
watch([keyword, creditRange], () => {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(load, 400)
})

// 从后端加载选课中心课程：仅把关键词与学分区间传给后端过滤；
// 学分范围 "1-2" / "5+" 拆成 minCredit / maxCredit（"5+" 无上限则不传 max）
async function load() {
  loading.value = true
  try {
    const params: CourseCenterQuery = {}
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (creditRange.value) {
      const [min, max] = creditRange.value.split('-').map(Number)
      params.minCredit = min
      if (!Number.isNaN(max)) params.maxCredit = max
    }
    courses.value = await courseCenter(params)
  } finally {
    loading.value = false
  }
}

// 选课：成功后重新加载列表（剩余名额、已选标记、冲突状态都会随之变化）
async function handleEnroll(course: CourseCardVO) {
  await enroll(course.id)
  ElMessage.success(`选课成功：${course.courseName}`)
  load()
}

// 退课：二次确认（提示"名额立即释放"，防止误操作）后调用退课接口并刷新列表
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
  color: #fff;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
}
.tag-primary {
  background: rgba(64, 158, 255, 0.9);
}
.tag-success {
  background: rgba(103, 194, 58, 0.9);
}
.tag-danger {
  background: rgba(245, 108, 108, 0.9);
}
.tag-warning {
  background: rgba(230, 162, 60, 0.9);
}
.body {
  padding: 12px 14px 14px;
}
.code {
  font-size: 12px;
  color: #a8abb2;
  margin-bottom: 2px;
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
  gap: 4px;
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
.ok {
  color: #67c23a;
}
.actions {
  margin-top: 10px;
  text-align: right;
}
.ml8 {
  margin-left: 8px;
}
.filters {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.filter-keyword {
  width: 300px;
}
.filter-select {
  width: 150px;
}
.stat-item {
  text-align: center;
  padding: 6px 0;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #303133;
}
.stat-value.primary {
  color: #409eff;
}
.stat-value.success {
  color: #67c23a;
}
.stat-value.warning {
  color: #e6a23c;
}
</style>
