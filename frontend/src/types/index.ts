/** 通用类型定义 */

export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

export type RoleType = 'ADMIN' | 'TEACHER' | 'STUDENT'

export interface LoginResponse {
  token: string
  userId: number
  userNo: string
  realName: string
  roleType: RoleType
  department?: string
  major?: string
  className?: string
}

export interface Staff {
  id: number
  staffNo: string
  realName: string
  roleType: string
  status: string
  department?: string
  phone?: string
  createdAt?: string
}

export interface Student {
  id: number
  studentNo: string
  realName: string
  status: string
  gender?: string
  phone?: string
  department?: string
  major?: string
  className?: string
  enrollmentYear?: number
  totalEarnedCredits?: number
  requiredCredits?: number
  gpa?: number
}

export interface Course {
  id: number
  courseCode: string
  courseName: string
  credit: number
  hours: number
  coverImageUrl?: string
  teacherId: number
  schedule?: string
  location?: string
  capacity: number
  currentEnrolled: number
  status: 'UNPUBLISHED' | 'PUBLISHED'
}

export interface MyCourseVO extends Course {
  auditStatus?: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'PUBLISHED'
}

export interface CourseCardVO {
  id: number
  courseCode: string
  courseName: string
  credit: number
  hours: number
  coverImageUrl?: string
  teacherName: string
  schedule?: string
  location?: string
  capacity: number
  currentEnrolled: number
  status: string
  enrolled: boolean
  full: boolean
}

export interface EnrollMonitorVO {
  courseId: number
  courseCode: string
  courseName: string
  teacherName: string
  capacity: number
  currentEnrolled: number
  remain: number
  status: string
}

export interface AuditVO {
  courseId: number
  courseCode: string
  courseName: string
  teacherName: string
  status: string
  submittedAt?: string
  approvedAt?: string
  publishedAt?: string
  rejectReason?: string
}

export interface GradeVO {
  courseId: number
  courseName: string
  credit: number
  score?: number
  mark: string
  auditStatus: string
  passed: boolean
}

export interface DashboardData {
  totalEarnedCredits: number
  requiredCredits: number
  progressPercent: number
  gpa: number
  gradeList: GradeVO[]
}

export interface CourseStudentItem {
  studentId: number
  studentNo: string
  realName: string
  major?: string
  className?: string
  score?: number
  mark: string
  locked: boolean
}
