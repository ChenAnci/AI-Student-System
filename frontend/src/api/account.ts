// ===== 账号管理相关接口（account） =====
// 职责：封装账号（教师/学生）的增删改查、状态启停、密码重置与 Excel 导入导出接口。
import http from './http'
import type { Staff, Student } from '@/types'

/** 新增账号表单：姓名、角色（教师/学生）及可选的基础信息 */
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

/** 调用 GET /api/accounts/staffs：按关键字模糊查询教师账号列表（keyword 为空则全量） */
export function listStaffs(keyword?: string) {
  return http.get('/accounts/staffs', { params: { keyword } }) as Promise<Staff[]>
}

/** 调用 GET /api/accounts/students：按关键字模糊查询学生账号列表（keyword 为空则全量） */
export function listStudents(keyword?: string) {
  return http.get('/accounts/students', { params: { keyword } }) as Promise<Student[]>
}

/** 调用 POST /api/accounts：新增账号（教师或学生），返回 null 表示成功 */
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

/** 调用 PUT /api/accounts/{userType}/{id}：按用户类型与 id 编辑账号信息 */
export function updateAccount(userType: 'STAFF' | 'STUDENT', id: number, data: AccountUpdateForm) {
  return http.put(`/accounts/${userType}/${id}`, data) as Promise<null>
}

/** 调用 PUT /api/accounts/{userType}/{id}/password：重置指定账号密码为随机初始密码 */
export function resetPassword(userType: 'STAFF' | 'STUDENT', id: number) {
  return http.put(`/accounts/${userType}/${id}/password`) as Promise<null>
}

/** 修改本人密码（学生/教师/管理员通用） */
export interface ChangePasswordForm {
  oldPassword: string
  newPassword: string
}

/** 调用 PUT /api/accounts/me/password：修改当前登录用户的密码（需校验旧密码） */
export function changePassword(data: ChangePasswordForm) {
  return http.put('/accounts/me/password', data) as Promise<null>
}

/** 调用 PUT /api/accounts/{userType}/{id}/status：启用/禁用指定账号（status 传状态值） */
export function toggleStatus(userType: 'STAFF' | 'STUDENT', id: number, status: string) {
  return http.put(`/accounts/${userType}/${id}/status`, null, { params: { status } }) as Promise<null>
}

// ==================== Excel 导入导出 ====================

/** 调用 GET /api/accounts/students/export：导出全部学生账号为 Excel（Blob） */
export function exportStudents() {
  return http.get('/accounts/students/export', { responseType: 'blob' }) as Promise<Blob>
}

/** 调用 GET /api/accounts/students/template：下载学生导入模板 Excel（Blob） */
export function downloadStudentTemplate() {
  return http.get('/accounts/students/template', { responseType: 'blob' }) as Promise<Blob>
}

/** 调用 POST /api/accounts/students/import：上传学生 Excel 批量导入，返回每条记录的随机初始密码 */
export function importStudents(file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post('/accounts/students/import', form) as Promise<AccountImportResult[]>
}

/** 调用 GET /api/accounts/staffs/export：导出全部教师账号为 Excel（Blob） */
export function exportStaffs() {
  return http.get('/accounts/staffs/export', { responseType: 'blob' }) as Promise<Blob>
}

/** 调用 GET /api/accounts/staffs/template：下载教师导入模板 Excel（Blob） */
export function downloadStaffTemplate() {
  return http.get('/accounts/staffs/template', { responseType: 'blob' }) as Promise<Blob>
}

/** 调用 POST /api/accounts/staffs/import：上传教师 Excel 批量导入，返回每条记录的随机初始密码 */
export function importStaffs(file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post('/accounts/staffs/import', form) as Promise<AccountImportResult[]>
}
