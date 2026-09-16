import http from './http'
import type { NotificationItem, PageResult } from '@/types'

export type TargetKind = 'STUDENT_IDS' | 'CLASS' | 'MAJOR' | 'DEPARTMENT' | 'COURSE' | 'ALL'

export interface NotificationTarget {
  kind: TargetKind
  studentIds?: number[]
  className?: string
  major?: string
  department?: string
  courseId?: number
}

export interface SendNotificationBody {
  title: string
  content: string
  target: NotificationTarget
}

export function inbox(page = 1, size = 10) {
  return http.get('/notifications', { params: { page, size } }) as Promise<PageResult<NotificationItem>>
}

export function unreadCount() {
  return http.get('/notifications/unread-count') as Promise<number>
}

export function markRead(receiverId: number) {
  return http.put(`/notifications/${receiverId}/read`) as Promise<null>
}

export function readAll() {
  return http.put('/notifications/read-all') as Promise<null>
}

export function sent(page = 1, size = 10) {
  return http.get('/notifications/sent', { params: { page, size } }) as Promise<PageResult<NotificationItem>>
}

export function sendNotification(body: SendNotificationBody) {
  return http.post('/notifications/send', body) as Promise<null>
}
