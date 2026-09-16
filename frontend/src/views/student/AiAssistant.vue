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

const quickQuestions = isAdmin.value
  ? ['这个学生的成绩怎么样？', '他还差多少学分？', '给他推荐下学期的课', '分析一下数据库原理这门课']
  : ['我的成绩怎么样？', '我还差多少学分？', '推荐下学期的课', '分析一下数据库原理这门课']

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}

async function sendCurrent() {
  const text = input.value.trim()
  if (!text || loading.value) return
  input.value = ''
  await send(text)
}

function handleStudentChange() {
  messages.value = []
}

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
