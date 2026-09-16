import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import type { RoleType } from '@/types'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Portal',
    component: () => import('@/views/PortalView.vue'),
    meta: { public: true, title: '门户首页' }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/oauth/callback',
    name: 'OAuthCallback',
    component: () => import('@/views/OAuthCallback.vue'),
    meta: { public: true, title: '登录中' }
  },
  {
    path: '/oauth/bind',
    name: 'OAuthBind',
    component: () => import('@/views/OAuthBind.vue'),
    meta: { public: true, title: '绑定账号' }
  },
  {
    // 登录后的主框架布局：所有需要登录的页面都挂在其 children 下，统一共享侧栏/顶栏
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/student/dashboard',
    children: [
      // 学生端
      { path: 'student/dashboard', name: 'StudentDashboard', component: () => import('@/views/student/DashboardView.vue'), meta: { roles: ['STUDENT'], title: '学业仪表盘' } },
      { path: 'student/center', name: 'CourseCenter', component: () => import('@/views/student/CourseCenter.vue'), meta: { roles: ['STUDENT'], title: '选课中心' } },
      { path: 'student/my-courses', name: 'StudentCourses', component: () => import('@/views/student/MyCoursesView.vue'), meta: { roles: ['STUDENT'], title: '我的课表' } },
      { path: 'student/grades', name: 'StudentGrades', component: () => import('@/views/student/GradesView.vue'), meta: { roles: ['STUDENT'], title: '成绩查询' } },
      { path: 'student/ai-assistant', name: 'StudentAiAssistant', component: () => import('@/views/student/AiAssistant.vue'), meta: { roles: ['STUDENT'], title: 'AI 智能助手' } },
      // 教师端
      { path: 'teacher/courses', name: 'TeacherCourses', component: () => import('@/views/teacher/MyCoursesView.vue'), meta: { roles: ['TEACHER'], title: '我的课程' } },
      { path: 'teacher/grade-entry/:courseId?', name: 'GradeEntry', component: () => import('@/views/teacher/GradeEntryView.vue'), meta: { roles: ['TEACHER'], title: '成绩管理' } },
      { path: 'teacher/stats', name: 'TeacherStats', component: () => import('@/views/teacher/TeacherStatsView.vue'), meta: { roles: ['TEACHER'], title: '成绩统计' } },
      // 教秘端
      { path: 'admin/stats', name: 'AdminStats', component: () => import('@/views/admin/StatsView.vue'), meta: { roles: ['ADMIN'], title: '数据统计' } },
      { path: 'admin/accounts', name: 'AccountManage', component: () => import('@/views/admin/AccountManage.vue'), meta: { roles: ['ADMIN'], title: '账号管理' } },
      { path: 'admin/courses', name: 'AdminCourses', component: () => import('@/views/admin/CourseManage.vue'), meta: { roles: ['ADMIN'], title: '课程管理' } },
      { path: 'admin/grade-audit', name: 'GradeAudit', component: () => import('@/views/admin/GradeAudit.vue'), meta: { roles: ['ADMIN'], title: '成绩审核' } },
      { path: 'admin/enroll-monitor', name: 'EnrollMonitor', component: () => import('@/views/admin/EnrollMonitor.vue'), meta: { roles: ['ADMIN'], title: '选课监控' } },
      { path: 'admin/ai-assistant', name: 'AdminAiAssistant', component: () => import('@/views/student/AiAssistant.vue'), meta: { roles: ['ADMIN'], title: 'AI 智能助手' } },
      // 站内通知（三角色）
      { path: 'notifications', name: 'Notifications', component: () => import('@/views/NotificationsView.vue'), meta: { roles: ['STUDENT', 'TEACHER', 'ADMIN'], title: '通知中心' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局路由守卫：登录态 + 角色权限两道关卡
// 1) 公开页（meta.public）：已登录则跳转到角色首页，未登录放行（门户/登录/OAuth 页）
// 2) 非公开页：未登录一律踢到 /login
// 3) 已登录但角色不在该路由 meta.roles 白名单内：重定向到该角色的默认首页（防越权，如学生直闯教师页）
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  const isLogin = userStore.isLogin()
  if (to.meta.public) {
    return isLogin ? next(homeByRole(userStore.role())) : next()
  }
  if (!isLogin) {
    return next('/login')
  }
  const roles = to.meta.roles as RoleType[] | undefined
  if (roles && userStore.role() && !roles.includes(userStore.role()!)) {
    return next(homeByRole(userStore.role()))
  }
  next()
})

// 各角色的默认首页（登录后跳转 / 越权访问时的兜底重定向目标）
function homeByRole(role?: RoleType): string {
  switch (role) {
    case 'ADMIN':
      return '/admin/accounts'
    case 'TEACHER':
      return '/teacher/courses'
    case 'STUDENT':
      return '/student/dashboard'
    default:
      return '/login'
  }
}

export default router
