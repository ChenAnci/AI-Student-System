/** 通用类型定义 */

// 后端统一响应包装：code===200 成功，data 为业务数据；拦截器已解包 data
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

// 系统三种角色：教学秘书（ADMIN）/ 教师（TEACHER）/ 学生（STUDENT）
export type RoleType = 'ADMIN' | 'TEACHER' | 'STUDENT'

// 登录响应：token 用于后续请求鉴权，其余为用户基础信息（角色、姓名、学号/工号等）
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

// 教职员工（老师/教学秘书）账号信息，用于账号管理列表
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

// 学生账号信息，用于账号管理列表与详情展示
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

// 课程基础信息：容量/已选人数用于余量判断，status 控制是否开放选课
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

// 选课监控列表项：展示每门课的选课余量（remain）
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

// 开课审核（Audit）列表项：展示课程审核流程各节点时间与退回原因
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

// 学生某门课程的成绩视图：score 分数 + mark 标记（缓考/缺考/舞弊）+ 审核状态 + 是否通过
export interface GradeVO {
  courseId: number
  courseName: string
  credit: number
  score?: number
  mark: string
  auditStatus: string
  passed: boolean
  /** 单科绩点（已发布且有成绩时返回，否则 null） */
  gradePoint?: number | null
}

export interface DashboardData {
  totalEarnedCredits: number
  requiredCredits: number
  progressPercent: number
  gpa: number
  gradeList: GradeVO[]
}

// 成绩录入表格行：某课程下的一位学生及其成绩/标记；locked 表示该行是否已锁定不可改
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
// 站内通知类型：手动通知 / 成绩发布 / 调课 / 选课 四类
export type NotificationType = 'MANUAL' | 'GRADE_PUBLISH' | 'COURSE_CHANGE' | 'ENROLL'

// 单条通知（含收件记录 receiverId 与已读标记 read）
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

// 通用分页结果：records 为当前页数据，total 总数，其余为分页信息
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}
