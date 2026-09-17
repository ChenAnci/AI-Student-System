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
          <NotificationBell v-if="isStudent" />
          <el-tag size="small" :type="roleTagType" effect="dark">{{ roleLabel }}</el-tag>
          <span class="username">{{ userStore.displayName() }}</span>
          <el-button type="primary" link @click="openPasswordDialog">修改密码</el-button>
          <el-button type="danger" link @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <el-dialog v-model="passwordDialogVisible" title="修改密码" width="420px" :close-on-click-modal="false">
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="90px">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="8~20 位，含字母和数字" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSubmitting" @click="submitPassword">确定</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import NotificationBell from '@/components/NotificationBell.vue'
import { useNotificationStore } from '@/stores/notification'
import { changePassword } from '@/api/account'

const router = useRouter()
const userStore = useUserStore()

interface MenuItem {
  path: string
  title: string
  icon: string
}

// 侧栏菜单按角色动态生成：管理员/教师/学生各自只看到本角色的导航项，避免出现越权入口
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

// 角色中文名（顶栏标签展示）：ADMIN 对外叫"教学秘书"
const roleLabel = computed(() => {
  const role = userStore.role()
  if (role === 'ADMIN') return '教学秘书'
  if (role === 'TEACHER') return '教师'
  return '学生'
})

// 角色标签颜色：管理员红 / 教师橙 / 学生绿，便于在顶栏一眼区分当前身份
const roleTagType = computed(() => {
  const role = userStore.role()
  if (role === 'ADMIN') return 'danger'
  if (role === 'TEACHER') return 'warning'
  return 'success'
})

const notificationStore = useNotificationStore()
// 站内通知收件箱/未读/WS 实时推送仅面向学生角色（接口按 STUDENT 鉴权），管理员/教师不挂载铃铛
const isStudent = computed(() => userStore.role() === 'STUDENT')

// 挂载时（仅学生）先同步一次未读数，再建立 WS 长连接接收实时推送（见 NotificationBell 的 v-if="isStudent"）
onMounted(() => {
  if (isStudent.value) {
    notificationStore.refreshUnread()
    notificationStore.connect()
  }
})

// 离开主布局（登出/跳转到登录页）时断开 WS，避免连接泄漏
onUnmounted(() => {
  notificationStore.disconnect()
})

// 退出登录：二次确认后清空本地登录态（token + 用户信息）并跳回登录页
function handleLogout() {
  ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' }).then(() => {
    userStore.logout()
    router.push('/login')
  })
}

// ==================== 修改密码 ====================

const passwordDialogVisible = ref(false)
const passwordSubmitting = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 前端校验规则与后端 DTO 保持一致（8~20 位且同时包含字母和数字），并做二次确认一致性校验
const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 20, message: '新密码长度需为 8~20 位', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/,
      message: '新密码需同时包含字母和数字',
      trigger: 'blur'
    },
    {
      validator: (_rule, value: string, callback) => {
        if (value && value === passwordForm.oldPassword) {
          callback(new Error('新密码不能与旧密码相同'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function openPasswordDialog() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordDialogVisible.value = true
}

// 提交修改：前端校验通过后调用后端；成功则清空登录态要求重新登录（旧 token 中不含新密码，且改密后重新登录更安全）
async function submitPassword() {
  const form = passwordFormRef.value
  if (!form) return
  await form.validate(async (valid) => {
    if (!valid) return
    passwordSubmitting.value = true
    try {
      await changePassword({
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      })
      ElMessage.success('密码修改成功，请重新登录')
      passwordDialogVisible.value = false
      userStore.logout()
      router.push('/login')
    } catch (e) {
      // 业务错误（旧密码错误/强度不足等）由 http 层统一弹出提示，此处静默
      console.error('修改密码失败', e)
    } finally {
      passwordSubmitting.value = false
    }
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
