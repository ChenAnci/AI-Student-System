<template>
  <div class="ai-page">
    <el-card shadow="never" class="chat-card">
      <template #header>
        <div class="chat-header">
          <span class="chat-title">AI 学业助手</span>
          <div class="chat-actions">
            <el-select
              v-if="isAdmin"
              v-model="selectedStudentNo"
              filterable
              clearable
              placeholder="选择要查询的学生（管理员）"
              class="student-select"
              size="small"
              @change="handleStudentChange"
            >
              <el-option
                v-for="s in students"
                :key="s.studentNo"
                :label="`${s.studentNo} - ${s.realName}`"
                :value="s.studentNo"
              />
            </el-select>
            <el-tag size="small" type="info">DeepSeek-V3</el-tag>
          </div>
        </div>
      </template>

      <div ref="listRef" class="msg-list">
        <div v-if="messages.length === 0" class="empty">
          <p class="empty-tip">
            {{ isAdmin ? '你好！选择学生后，可以向我提问该生的学习情况、选课建议等' : '你好！我是你的 AI 学业助手，可以问我：' }}
          </p>
          <div class="quick-wrap">
            <el-button v-for="q in quickQuestions" :key="q" size="small" round @click="send(q)">
              {{ q }}
            </el-button>
          </div>
        </div>

        <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
          <div class="bubble">{{ m.content }}</div>
        </div>

        <div v-if="loading" class="msg assistant">
          <div class="bubble typing">正在思考…</div>
        </div>
      </div>

      <div class="input-area">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          maxlength="500"
          show-word-limit
          placeholder="输入你的问题，Enter 发送，Shift+Enter 换行"
          @keydown.enter.exact.prevent="sendCurrent"
        />
        <el-button
          type="primary"
          :loading="loading"
          :disabled="!input.trim()"
          class="send-btn"
          @click="sendCurrent"
        >
          发送
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * AI 学业助手（学生 / 管理员）
 * 职责：提供与 DeepSeek-V3 的聊天式问答界面，支持快捷提问、回车发送；
 *       学生咨询自己的学习情况；管理员可先选择目标学生，再针对该生提问；
 *       发送时携带历史消息上下文，回答返回后自动滚动到最新消息。
 */
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { aiChat, type AiChatMessage } from '@/api/ai'
import { listStudents } from '@/api/account'
import { useUserStore } from '@/stores/user'
import type { Student } from '@/types'

const userStore = useUserStore()
const isAdmin = computed(() => userStore.role() === 'ADMIN')

const messages = ref<AiChatMessage[]>([])
const input = ref('')
const loading = ref(false)
const listRef = ref<HTMLElement>()
const students = ref<Student[]>([])
const selectedStudentNo = ref('')

// 快捷提问按钮文案：管理员针对学生提问，学生咨询自己的学习情况（与角色联动）
const quickQuestions = isAdmin.value
  ? ['这个学生的成绩怎么样？', '他还差多少学分？', '给他推荐下学期的课', '分析一下数据库原理这门课']
  : ['我的成绩怎么样？', '我还差多少学分？', '推荐下学期的课', '分析一下数据库原理这门课']

// 把消息列表滚动到底部：等 DOM 更新（nextTick）后定位到最底部，让新消息可见
function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}

// 发送当前输入框内容：去除首尾空格，空文本或请求中直接忽略；清空输入框后执行真正的发送逻辑
async function sendCurrent() {
  const text = input.value.trim()
  if (!text || loading.value) return
  input.value = ''
  await send(text)
}

// 管理员切换查询学生时清空历史会话，避免把上一个学生的上下文带给下一个学生
function handleStudentChange() {
  messages.value = []
}

// 发送消息：管理员未选学生时拦截；把用户消息加入列表后携带历史上下文（不含本条）调用 AI 接口，
// 将返回的答案追加为 assistant 消息；出错时由 http.ts 拦截器统一提示，结束后恢复 loading 并滚动到底部
async function send(text: string) {
  if (isAdmin.value && !selectedStudentNo.value) {
    ElMessage.warning('请先选择要查询的学生')
    return
  }
  messages.value.push({ role: 'user', content: text })
  loading.value = true
  scrollToBottom()
  try {
    const res = await aiChat(text, messages.value.slice(0, -1), selectedStudentNo.value || undefined)
    messages.value.push({ role: 'assistant', content: res.answer })
  } catch {
    // 错误提示已由 http.ts 拦截器统一处理
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

// 页面挂载时：若为管理员则加载学生列表供下拉选择（用于按学生提问），失败由拦截器提示
onMounted(async () => {
  if (isAdmin.value) {
    try {
      students.value = await listStudents()
    } catch {
      // 已由拦截器提示
    }
  }
})
</script>

<style scoped>
.ai-page {
  max-width: 860px;
  margin: 24px auto 0;
}
.chat-card {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 200px);
}
.chat-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.chat-title {
  font-size: 15px;
  font-weight: 600;
}
.chat-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.student-select {
  width: 220px;
}
.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 8px;
}
.empty {
  text-align: center;
  color: #909399;
  padding: 60px 0;
}
.empty-tip {
  margin-bottom: 16px;
  font-size: 14px;
}
.quick-wrap {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}
.msg {
  display: flex;
  margin-bottom: 14px;
}
.msg.user {
  justify-content: flex-end;
}
.msg.assistant {
  justify-content: flex-start;
}
.bubble {
  max-width: 72%;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.msg.user .bubble {
  background: #409eff;
  color: #fff;
  border-top-right-radius: 2px;
}
.msg.assistant .bubble {
  background: #f4f4f5;
  color: #303133;
  border-top-left-radius: 2px;
}
.bubble.typing {
  color: #909399;
}
.input-area {
  display: flex;
  gap: 10px;
  padding: 12px 4px 0;
  border-top: 1px solid #e4e7ed;
}
.send-btn {
  align-self: flex-end;
}
</style>
