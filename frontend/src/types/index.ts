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

/** GitHub OAuth 回调结果 */
export interface OAuthCallbackResult {
  status: 'LOGIN_SUCCESS' | 'NEED_BIND'
  token?: string
  providerUid?: string
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

/** 名称-数值统计项 */
export interface NameValue {
  name: string
  value: number
}

/** 教秘端全校数据统计 */
export interface AdminStats {
  studentCount: number
  staffCount: number
  teacherCount: number
  courseCount: number
  enrollmentCount: number
  majorDistribution: NameValue[]
  departmentCourses: NameValue[]
  topEnrolledCourses: NameValue[]
  scoreBands: NameValue[]
  courseStatus: NameValue[]
}

/** 教师课程成绩统计项 */
export interface CourseScoreStat {
  courseName: string
  avgScore: number
  studentCount: number
  passRate: number
}

/** 教师端成绩统计 */
export interface TeacherStats {
  courseCount: number
  studentTotal: number
  courseScores: CourseScoreStat[]
  scoreBands: NameValue[]
}

/** 站内通知 */
export type NotificationType = 'MANUAL' | 'GRADE_PUBLISH' | 'COURSE_CHANGE' | 'ENROLL'

export interface NotificationItem {
  id: number
  receiverId?: number
  type: NotificationType
  title: string
  content: string
  senderName?: string
  read?: boolean
  createdAt: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}
