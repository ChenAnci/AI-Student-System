import http from './http'
import type { Staff, Student } from '@/types'

export interface AccountForm {
  realName: string
  roleType: 'TEACHER' | 'STUDENT'
  gender?: string
  department?: string
  major?: string
  className?: string
  enrollmentYear?: number
  phone?: string
}

/** 账号导入结果：每条含随机初始密码，供导入者线下分发 */
export interface AccountImportResult {
  realName: string
  userNo: string
  initPassword: string
}

export function listStaffs(keyword?: string) {
  return http.get('/accounts/staffs', { params: { keyword } }) as Promise<Staff[]>
}

export function listStudents(keyword?: string) {
  return http.get('/accounts/students', { params: { keyword } }) as Promise<Student[]>
}

export function createAccount(data: AccountForm) {
  return http.post('/accounts', data) as Promise<null>
}

/** 编辑账号信息（学号/工号与角色不可修改） */
export interface AccountUpdateForm {
  realName: string
  gender?: string
  department?: string
  phone?: string
  major?: string
  className?: string
  enrollmentYear?: number
}

export function updateAccount(userType: 'STAFF' | 'STUDENT', id: number, data: AccountUpdateForm) {
  return http.put(`/accounts/${userType}/${id}`, data) as Promise<null>
}

export function resetPassword(userType: 'STAFF' | 'STUDENT', id: number) {
  return http.put(`/accounts/${userType}/${id}/password`) as Promise<null>
}

export function toggleStatus(userType: 'STAFF' | 'STUDENT', id: number, status: string) {
  return http.put(`/accounts/${userType}/${id}/status`, null, { params: { status } }) as Promise<null>
}

// ==================== Excel 导入导出 ====================

export function exportStudents() {
  return http.get('/accounts/students/export', { responseType: 'blob' }) as Promise<Blob>
}

export function downloadStudentTemplate() {
  return http.get('/accounts/students/template', { responseType: 'blob' }) as Promise<Blob>
}

export function importStudents(file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post('/accounts/students/import', form) as Promise<AccountImportResult[]>
}

export function exportStaffs() {
  return http.get('/accounts/staffs/export', { responseType: 'blob' }) as Promise<Blob>
}

export function downloadStaffTemplate() {
  return http.get('/accounts/staffs/template', { responseType: 'blob' }) as Promise<Blob>
}

export function importStaffs(file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post('/accounts/staffs/import', form) as Promise<AccountImportResult[]>
}
