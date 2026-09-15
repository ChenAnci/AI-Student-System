import http from './http'
import type { AdminStats, TeacherStats } from '@/types'

/** 教秘端：全校数据统计 */
export function getAdminStats() {
  return http.get('/stats/admin') as Promise<AdminStats>
}

/** 教师端：所授课程成绩统计 */
export function getTeacherStats() {
  return http.get('/stats/teacher') as Promise<TeacherStats>
}
