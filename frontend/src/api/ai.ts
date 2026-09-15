import http from './http'

export interface AiChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface AiChatResult {
  answer: string
}

/** AI 问答：单独覆盖 60s 超时（LLM 生成较慢）；targetStudentNo 仅管理员查询指定学生时传 */
export function aiChat(message: string, history: AiChatMessage[], targetStudentNo?: string) {
  return http.post('/ai/chat', { message, history, targetStudentNo }, { timeout: 60000 }) as Promise<AiChatResult>
}
