// ===== AI 助手相关接口（ai） =====
// 职责：封装 AI 问答接口，供学生/教师/管理员的 AI 助手使用。
import http from './http'

/** AI 对话历史消息：角色（user/assistant）+ 内容 */
export interface AiChatMessage {
  role: 'user' | 'assistant'
  content: string
}

/** AI 问答结果：LLM 生成的回答文本 */
export interface AiChatResult {
  answer: string
}

/** AI 问答：单独覆盖 60s 超时（LLM 生成较慢）；targetStudentNo 仅管理员查询指定学生时传 */
export function aiChat(message: string, history: AiChatMessage[], targetStudentNo?: string) {
  return http.post('/ai/chat', { message, history, targetStudentNo }, { timeout: 60000 }) as Promise<AiChatResult>
}
