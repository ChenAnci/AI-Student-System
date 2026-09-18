// ===== 课程管理相关接口（course） =====
// 职责：封装课程的新增/编辑/发布/删除，以及"我的课程"与全部课程的查询接口。
import http from './http'
import type { Course, MyCourseVO } from '@/types'

/** 课程表单：课程代码/名称/学分/学时/容量等基础信息，封面与排课可选 */
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

/** 调用 POST /api/courses：新增课程，返回创建的课程对象 */
export function createCourse(data: CourseForm) {
  return http.post('/courses', data) as Promise<Course>
}

/** 调用 PUT /api/courses/{id}：按 id 编辑课程信息 */
export function updateCourse(id: number, data: CourseForm) {
  return http.put(`/courses/${id}`, data) as Promise<null>
}

/** 调用 POST /api/courses/{id}/publish：发布课程（发布后学生可选课） */
export function publishCourse(id: number) {
  return http.post(`/courses/${id}/publish`) as Promise<null>
}

/** 调用 DELETE /api/courses/{id}：删除课程 */
export function deleteCourse(id: number) {
  return http.delete(`/courses/${id}`) as Promise<null>
}

/** 调用 GET /api/courses/my：查询当前登录用户相关的课程（老师=所授课程；学生=已选课程） */
export function listMyCourses() {
  return http.get('/courses/my') as Promise<MyCourseVO[]>
}

/** 调用 GET /api/courses/all：按关键字查询全部课程（管理员/老师用） */
export function listAllCourses(keyword?: string) {
  return http.get('/courses/all', { params: { keyword } }) as Promise<MyCourseVO[]>
}
