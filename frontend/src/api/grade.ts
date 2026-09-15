import http from './http'
import type { AuditVO, CourseStudentItem, DashboardData, GradeVO } from '@/types'

export interface GradeItem {
  studentId: number
  score?: number
  mark: 'NORMAL' | 'DEFER' | 'ABSENT' | 'CHEAT'
}

export function courseStudents(courseId: number) {
  return http.get(`/grades/course/${courseId}/students`) as Promise<CourseStudentItem[]>
}

export function entryGrades(courseId: number, items: GradeItem[]) {
  return http.post('/grades/entry', { courseId, items }) as Promise<null>
}

export function submitGrades(courseId: number) {
  return http.post(`/grades/${courseId}/submit`) as Promise<null>
}

export function myAudits() {
  return http.get('/grades/my-audits') as Promise<AuditVO[]>
}

export function pendingAudits() {
  return http.get('/grades/pending') as Promise<AuditVO[]>
}

export function allAudits() {
  return http.get('/grades/audits') as Promise<AuditVO[]>
}

export function audit(courseId: number, approved: boolean, rejectReason?: string) {
  return http.post('/grades/audit', { courseId, approved, rejectReason }) as Promise<null>
}

export function publishGrades(courseId: number) {
  return http.post(`/grades/${courseId}/publish`) as Promise<null>
}

export function myGrades() {
  return http.get('/grades/my') as Promise<GradeVO[]>
}

export function dashboard() {
  return http.get('/grades/dashboard') as Promise<DashboardData>
}

// ==================== Excel 导入导出 ====================

export function exportCourseStudents(courseId: number) {
  return http.get(`/grades/course/${courseId}/export`, { responseType: 'blob' }) as Promise<Blob>
}

export function downloadGradeTemplate(courseId: number) {
  return http.get(`/grades/course/${courseId}/template`, { responseType: 'blob' }) as Promise<Blob>
}

export function importGrades(courseId: number, file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post(`/grades/course/${courseId}/import`, form) as Promise<number>
}
