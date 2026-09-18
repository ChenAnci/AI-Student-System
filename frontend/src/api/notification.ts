// ===== 站内通知相关接口（notification） =====
// 职责：封装通知收件箱/未读数/已读/发件箱/发送等接口，以及发送对象类型定义。
import http from './http'
import type { NotificationItem, PageResult } from '@/types'

// 通知接收对象类型：按学生 ID / 班级 / 专业 / 院系 / 课程 / 全体学生；老师角色只允许 COURSE（按课程）
export type TargetKind = 'STUDENT_IDS' | 'CLASS' | 'MAJOR' | 'DEPARTMENT' | 'COURSE' | 'ALL'

/** 通知接收目标：kind 指定维度，其余字段按 kind 对应的维度填写 */
export interface NotificationTarget {
  kind: TargetKind
  studentIds?: number[]
  className?: string
  major?: string
  department?: string
  courseId?: number
}

/** 发送通知请求体：标题 + 内容 + 接收目标 */
export interface SendNotificationBody {
  title: string
  content: string
  target: NotificationTarget
}

// 学生收件箱：分页获取当前用户收到的通知（学生角色使用）
export function inbox(page = 1, size = 10) {
  return http.get('/notifications', { params: { page, size } }) as Promise<PageResult<NotificationItem>>
}

// 查询未读数：用于铃铛角标初始值（WS 连接建立前/异常时的兜底）
export function unreadCount() {
  return http.get('/notifications/unread-count') as Promise<number>
}

// 标记单条通知已读（按收件记录 receiverId）
export function markRead(receiverId: number) {
  return http.put(`/notifications/${receiverId}/read`) as Promise<null>
}

// 一键全部已读：清空当前用户的未读状态
export function readAll() {
  return http.put('/notifications/read-all') as Promise<null>
}

// 发送记录：老师/教秘分页查看自己发过的通知（发件箱）
export function sent(page = 1, size = 10) {
  return http.get('/notifications/sent', { params: { page, size } }) as Promise<PageResult<NotificationItem>>
}

// 发送通知：body.target 指定接收对象（课程/班级/专业/院系/全体）
export function sendNotification(body: SendNotificationBody) {
  return http.post('/notifications/send', body) as Promise<null>
}
