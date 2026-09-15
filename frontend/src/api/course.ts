import http from './http'
import type { Course, MyCourseVO } from '@/types'

export interface CourseForm {
  courseCode: string
  courseName: string
  credit: number
  hours: number
  coverImageUrl?: string
  schedule?: string
  location?: string
  capacity: number
  teacherId?: number
}

export function createCourse(data: CourseForm) {
  return http.post('/courses', data) as Promise<Course>
}

export function updateCourse(id: number, data: CourseForm) {
  return http.put(`/courses/${id}`, data) as Promise<null>
}

export function publishCourse(id: number) {
  return http.post(`/courses/${id}/publish`) as Promise<null>
}

export function deleteCourse(id: number) {
  return http.delete(`/courses/${id}`) as Promise<null>
}

export function listMyCourses() {
  return http.get('/courses/my') as Promise<MyCourseVO[]>
}

export function listAllCourses(keyword?: string) {
  return http.get('/courses/all', { params: { keyword } }) as Promise<MyCourseVO[]>
}
