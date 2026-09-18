// ===== 选课/退课相关接口（enroll） =====
// 职责：封装学生选课中心的课程查询、选课/退课、我的课程，
// 以及管理员的选课监控与学生代选/代退接口。
import http from './http'
import type { CourseCardVO, EnrollMonitorVO } from '@/types'

/** 选课中心查询参数（关键词 / 学分范围，均为可选） */
export interface CourseCenterQuery {
  keyword?: string
  minCredit?: number
  maxCredit?: number
}

/** 调用 GET /api/enrollments/center：按关键词/学分范围查询可选课程（选课中心卡片） */
export function courseCenter(params?: CourseCenterQuery) {
  return http.get('/enrollments/center', { params }) as Promise<CourseCardVO[]>
}

/** 调用 POST /api/enrollments/{courseId}：学生选课 */
export function enroll(courseId: number) {
  return http.post(`/enrollments/${courseId}`) as Promise<null>
}

/** 调用 DELETE /api/enrollments/{courseId}：学生退课 */
export function drop(courseId: number) {
  return http.delete(`/enrollments/${courseId}`) as Promise<null>
}

/** 调用 GET /api/enrollments/my：查询当前学生已选的课程 */
export function myCourses() {
  return http.get('/enrollments/my') as Promise<CourseCardVO[]>
}

/** 调用 GET /api/enrollments/monitor：查询选课监控数据（各课程选课人数/状态，管理员用） */
export function monitor() {
  return http.get('/enrollments/monitor') as Promise<EnrollMonitorVO[]>
}

/** 调用 DELETE /api/enrollments/{courseId}/students/{studentId}：管理员代退指定学生的选课 */
export function adminDrop(courseId: number, studentId: number) {
  return http.delete(`/enrollments/${courseId}/students/${studentId}`) as Promise<null>
}

/** 调用 POST /api/enrollments/{courseId}/students/{studentId}：管理员代选课程 */
export function adminEnroll(courseId: number, studentId: number) {
  return http.post(`/enrollments/${courseId}/students/${studentId}`) as Promise<null>
}
