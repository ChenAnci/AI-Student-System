<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <el-icon :size="24" color="#409eff"><School /></el-icon>
        <span>学生信息管理系统</span>
      </div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#001529"
        text-color="#a6adb4"
        active-text-color="#ffffff"
      >
        <template v-for="item in menus" :key="item.path">
          <el-menu-item :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="page-title">{{ $route.meta.title }}</div>
        <div class="user-area">
          <NotificationBell />
          <el-tag size="small" :type="roleTagType" effect="dark">{{ roleLabel }}</el-tag>
          <span class="username">{{ userStore.displayName() }}</span>
          <el-button type="danger" link @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import NotificationBell from '@/components/NotificationBell.vue'
import { useNotificationStore } from '@/stores/notification'

const router = useRouter()
const userStore = useUserStore()

interface MenuItem {
  path: string
  title: string
  icon: string
}

const menus = computed<MenuItem[]>(() => {
  const role = userStore.role()
  if (role === 'ADMIN') {
    return [
      { path: '/admin/stats', title: '数据统计', icon: 'PieChart' },
      { path: '/admin/accounts', title: '账号管理', icon: 'User' },
      { path: '/admin/courses', title: '课程管理', icon: 'Reading' },
      { path: '/admin/grade-audit', title: '成绩审核', icon: 'EditPen' },
      { path: '/admin/enroll-monitor', title: '选课监控', icon: 'DataAnalysis' },
      { path: '/admin/ai-assistant', title: 'AI 智能助手', icon: 'ChatDotRound' },
      { path: '/notifications', title: '通知中心', icon: 'Bell' }
    ]
  }
  if (role === 'TEACHER') {
    return [
      { path: '/teacher/courses', title: '我的课程', icon: 'Reading' },
      { path: '/teacher/grade-entry', title: '成绩管理', icon: 'EditPen' },
      { path: '/teacher/stats', title: '成绩统计', icon: 'TrendCharts' },
      { path: '/notifications', title: '通知中心', icon: 'Bell' }
    ]
  }
  return [
    { path: '/student/dashboard', title: '学业仪表盘', icon: 'Odometer' },
    { path: '/student/center', title: '选课中心', icon: 'ShoppingCart' },
    { path: '/student/my-courses', title: '我的课表', icon: 'Calendar' },
    { path: '/student/grades', title: '成绩查询', icon: 'Document' },
    { path: '/student/ai-assistant', title: 'AI 智能助手', icon: 'ChatDotRound' },
    { path: '/notifications', title: '通知中心', icon: 'Bell' }
  ]
})

const roleLabel = computed(() => {
  const role = userStore.role()
  if (role === 'ADMIN') return '教学秘书'
  if (role === 'TEACHER') return '教师'
  return '学生'
})

const roleTagType = computed(() => {
  const role = userStore.role()
  if (role === 'ADMIN') return 'danger'
  if (role === 'TEACHER') return 'warning'
  return 'success'
})

const notificationStore = useNotificationStore()

onMounted(() => {
  notificationStore.refreshUnread()
  notificationStore.connect()
})

onUnmounted(() => {
  notificationStore.disconnect()
})

function handleLogout() {
  ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' }).then(() => {
    userStore.logout()
    router.push('/login')
  })
}
</script>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  background: #001529;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}
.aside :deep(.el-menu) {
  border-right: none;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}
.main {
  background: #f0f2f5;
}
</style>
