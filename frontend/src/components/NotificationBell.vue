<template>
  <el-popover placement="bottom-end" :width="340" trigger="click" @show="load">
    <template #reference>
      <el-badge :value="store.unread" :hidden="store.unread === 0" :max="99" class="bell">
        <el-button link circle>
          <el-icon :size="18"><Bell /></el-icon>
        </el-button>
      </el-badge>
    </template>
    <div class="notif-panel">
      <div class="notif-head">
        <span>通知</span>
        <el-button link type="primary" size="small" @click="goAll">查看全部</el-button>
      </div>
      <div v-if="items.length === 0" class="notif-empty">暂无通知</div>
      <div
        v-for="n in items"
        :key="n.receiverId"
        class="notif-item"
        :class="{ unread: !n.read }"
        @click="open(n)"
      >
        <div class="notif-title">{{ n.title }}</div>
        <div class="notif-time">{{ (n.createdAt || '').slice(5, 16) }}</div>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'
import { useNotificationStore } from '@/stores/notification'
import { inbox, markRead } from '@/api/notification'
import type { NotificationItem } from '@/types'

const router = useRouter()
const store = useNotificationStore()
const items = ref<NotificationItem[]>([])

async function load() {
  try {
    const page = await inbox(1, 5)
    items.value = page.records
  } catch {
    // 已由拦截器提示
  }
}

function goAll() {
  router.push('/notifications')
}

async function open(n: NotificationItem) {
  if (n.receiverId && !n.read) {
    try {
      await markRead(n.receiverId)
      store.unread = Math.max(0, store.unread - 1)
      n.read = true
    } catch {
      // no-op
    }
  }
  router.push('/notifications')
}

onMounted(() => {
  load()
})
</script>

<style scoped>
.bell {
  display: inline-flex;
  vertical-align: middle;
}
.notif-panel {
  max-height: 360px;
  overflow-y: auto;
}
.notif-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}
.notif-empty {
  text-align: center;
  color: #909399;
  padding: 24px 0;
}
.notif-item {
  padding: 10px 4px;
  border-bottom: 1px solid #f2f3f5;
  cursor: pointer;
}
.notif-item:hover {
  background: #f5f7fa;
}
.notif-item.unread .notif-title {
  font-weight: 600;
}
.notif-title {
  font-size: 14px;
  color: #303133;
}
.notif-time {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
</style>
