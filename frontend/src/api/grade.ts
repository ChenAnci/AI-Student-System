// ===== 成绩管理相关接口（grade） =====
// 职责：封装成绩录入/提交/审核/发布/查询/仪表盘，以及成绩的 Excel 导入导出接口。
import http from './http'
import type { AuditVO, CourseStudentItem, DashboardData, GradeVO } from '@/types'

/** 单条成绩录入项：学生 id、分数（可空）与成绩标记（正常/缓考/缺考/作弊） */
export interface GradeItem {
  studentId: number
  score?: number
  mark: 'NORMAL' | 'DEFER' | 'ABSENT' | 'CHEAT'
}

/** 调用 GET /api/grades/course/{courseId}/students：查询某课程的学生名单及已有成绩（录入用） */
export function courseStudents(courseId: number) {
  return http.get(`/grades/course/${courseId}/students`) as Promise<CourseStudentItem[]>
}

/** 调用 POST /api/grades/entry：批量录入某课程的学生成绩 */
export function entryGrades(courseId: number, items: GradeItem[]) {
  return http.post('/grades/entry', { courseId, items }) as Promise<null>
}

/** 调用 POST /api/grades/{courseId}/submit：教师提交某课程成绩（进入审核流程） */
export function submitGrades(courseId: number) {
  return http.post(`/grades/${courseId}/submit`) as Promise<null>
}

/** 调用 GET /api/grades/my-audits：查询当前教师提交的成绩审核记录 */
export function myAudits() {
  return http.get('/grades/my-audits') as Promise<AuditVO[]>
}

/** 调用 GET /api/grades/pending：查询待审核的成绩列表（管理员用） */
export function pendingAudits() {
  return http.get('/grades/pending') as Promise<AuditVO[]>
}

/** 调用 GET /api/grades/audits：查询全部成绩审核记录（管理员用） */
export function allAudits() {
  return http.get('/grades/audits') as Promise<AuditVO[]>
}

/** 调用 POST /api/grades/audit：审核成绩（approved 是否通过，不通过时附拒绝原因） */
export function audit(courseId: number, approved: boolean, rejectReason?: string) {
  return http.post('/grades/audit', { courseId, approved, rejectReason }) as Promise<null>
}

/** 调用 POST /api/grades/{courseId}/publish：发布某课程成绩（发布后学生可见） */
export function publishGrades(courseId: number) {
  return http.post(`/grades/${courseId}/publish`) as Promise<null>
}

/** 调用 GET /api/grades/my：查询当前学生的已发布成绩 */
export function myGrades() {
  return http.get('/grades/my') as Promise<GradeVO[]>
}

/** 调用 GET /api/grades/dashboard：查询学生端成绩统计仪表盘数据 */
export function dashboard() {
  return http.get('/grades/dashboard') as Promise<DashboardData>
}

// ==================== Excel 导入导出 ====================

/** 调用 GET /api/grades/course/{courseId}/export：导出某课程成绩表 Excel（Blob） */
export function exportCourseStudents(courseId: number) {
  return http.get(`/grades/course/${courseId}/export`, { responseType: 'blob' }) as Promise<Blob>
}

/** 调用 GET /api/grades/course/{courseId}/template：下载某课程成绩导入模板 Excel（Blob） */
export function downloadGradeTemplate(courseId: number) {
  return http.get(`/grades/course/${courseId}/template`, { responseType: 'blob' }) as Promise<Blob>
}

/** 调用 POST /api/grades/course/{courseId}/import：上传成绩 Excel 批量导入，返回导入条数 */
export function importGrades(courseId: number, file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post(`/grades/course/${courseId}/import`, form) as Promise<number>
}
