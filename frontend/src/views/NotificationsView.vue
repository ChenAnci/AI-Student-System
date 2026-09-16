<template>
  <div class="notifications-page">
    <el-card shadow="never">
      <template #header>
        <div class="page-head">
          <span>{{ isStaff ? '发送记录' : '我的通知' }}</span>
          <div>
            <el-button v-if="!isStaff" type="primary" link @click="handleReadAll">全部已读</el-button>
            <el-button v-if="isStaff" type="primary" @click="sendVisible = true">发送通知</el-button>
          </div>
        </div>
      </template>

      <el-empty v-if="list.length === 0" description="暂无通知" />
      <el-table v-else :data="list" stripe>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.type)" size="small">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column v-if="isStaff" prop="senderName" label="发送人" width="110" />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ row.createdAt?.replace('T', ' ').slice(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" :title="detail?.title || '通知详情'" width="520px">
      <div class="detail-meta">
        <el-tag :type="detail ? typeTag(detail.type) : 'info'" size="small">
          {{ detail ? typeLabel(detail.type) : '' }}
        </el-tag>
        <span class="detail-time">{{ detail?.createdAt?.replace('T', ' ').slice(0, 16) }}</span>
      </div>
      <p class="detail-content">{{ detail?.content }}</p>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <SendNotificationDialog v-model="sendVisible" @sent="load" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { inbox, readAll, sent } from '@/api/notification'
import type { NotificationItem } from '@/types'
import SendNotificationDialog from '@/components/SendNotificationDialog.vue'

const userStore = useUserStore()
// 老师/教秘看"发送记录"（发件箱），学生看"我的通知"（收件箱），据此分流接口、按钮与表格列
const isStaff = computed(() => userStore.role() === 'TEACHER' || userStore.role() === 'ADMIN')

// 分页状态：列表数据 / 当前页 / 每页条数 / 总数（el-pagination 组件依赖）
const list = ref<NotificationItem[]>([])
const page = ref(1)
const size = 10
const total = ref(0)
const detailVisible = ref(false)
const detail = ref<NotificationItem | null>(null)
const sendVisible = ref(false)

// 按角色加载数据：老师/教秘调 sent（发件箱），学生调 inbox（收件箱）
async function load() {
  try {
    const p = isStaff.value ? await sent(page.value, size) : await inbox(page.value, size)
    list.value = p.records
    total.value = p.total
  } catch {
    // 已由拦截器提示
  }
}

// 打开详情弹窗：直接展示行数据，无需额外请求
function openDetail(row: NotificationItem) {
  detail.value = row
  detailVisible.value = true
}

// 全部已读：调用接口后刷新列表（read 状态随之更新，角标由 store 在刷新后重新同步）
async function handleReadAll() {
  try {
    await readAll()
    ElMessage.success('已全部标为已读')
    load()
  } catch {
    // no-op
  }
}

// 通知类型 → 中文标签 / 标签颜色（与后端 NotificationType 枚举对应），未知类型兜底为"通知/info"
function typeLabel(type: string) {
  const map: Record<string, string> = {
    MANUAL: '通知',
    GRADE_PUBLISH: '成绩',
    COURSE_CHANGE: '调课',
    ENROLL: '选课'
  }
  return map[type] || '通知'
}

function typeTag(type: string) {
  const map: Record<string, string> = {
    MANUAL: 'info',
    GRADE_PUBLISH: 'success',
    COURSE_CHANGE: 'warning',
    ENROLL: 'primary'
  }
  return map[type] || 'info'
}

onMounted(load)
</script>

<style scoped>
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pager {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}
.detail-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.detail-time {
  font-size: 12px;
  color: #909399;
}
.detail-content {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #303133;
}
</style>
