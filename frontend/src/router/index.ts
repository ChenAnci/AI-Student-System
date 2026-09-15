import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import type { RoleType } from '@/types'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true }
  },
  {
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
      // 教秘端
      { path: 'admin/accounts', name: 'AccountManage', component: () => import('@/views/admin/AccountManage.vue'), meta: { roles: ['ADMIN'], title: '账号管理' } },
      { path: 'admin/courses', name: 'AdminCourses', component: () => import('@/views/admin/CourseManage.vue'), meta: { roles: ['ADMIN'], title: '课程管理' } },
      { path: 'admin/grade-audit', name: 'GradeAudit', component: () => import('@/views/admin/GradeAudit.vue'), meta: { roles: ['ADMIN'], title: '成绩审核' } },
      { path: 'admin/enroll-monitor', name: 'EnrollMonitor', component: () => import('@/views/admin/EnrollMonitor.vue'), meta: { roles: ['ADMIN'], title: '选课监控' } },
      { path: 'admin/ai-assistant', name: 'AdminAiAssistant', component: () => import('@/views/student/AiAssistant.vue'), meta: { roles: ['ADMIN'], title: 'AI 智能助手' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

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
