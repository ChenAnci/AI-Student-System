import http from './http'
import type { CourseCardVO, EnrollMonitorVO } from '@/types'

/** 选课中心查询参数（关键词 / 学分范围，均为可选） */
export interface CourseCenterQuery {
  keyword?: string
  minCredit?: number
  maxCredit?: number
}

export function courseCenter(params?: CourseCenterQuery) {
  return http.get('/enrollments/center', { params }) as Promise<CourseCardVO[]>
}

export function enroll(courseId: number) {
  return http.post(`/enrollments/${courseId}`) as Promise<null>
}

export function drop(courseId: number) {
  return http.delete(`/enrollments/${courseId}`) as Promise<null>
}

export function myCourses() {
  return http.get('/enrollments/my') as Promise<CourseCardVO[]>
}

export function monitor() {
  return http.get('/enrollments/monitor') as Promise<EnrollMonitorVO[]>
}

export function adminDrop(courseId: number, studentId: number) {
  return http.delete(`/enrollments/${courseId}/students/${studentId}`) as Promise<null>
}

export function adminEnroll(courseId: number, studentId: number) {
  return http.post(`/enrollments/${courseId}/students/${studentId}`) as Promise<null>
}
