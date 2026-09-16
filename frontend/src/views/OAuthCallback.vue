<template>
  <div class="oauth-page">
    <el-card class="oauth-card">
      <el-icon class="spin" :size="30" color="#409eff"><Loading /></el-icon>
      <p>{{ loading ? '正在完成登录...' : '登录成功，即将跳转' }}</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { exchangeGithub } from '@/api/oauth'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(true)

function homeByRole(role: string): string {
  if (role === 'ADMIN') return '/admin/accounts'
  if (role === 'TEACHER') return '/teacher/courses'
  return '/student/dashboard'
}

onMounted(async () => {
  const authCode = route.query.authCode as string | undefined
  const needBind = route.query.needBind as string | undefined
  const providerUid = route.query.providerUid as string | undefined

  if (authCode) {
    // 后端回调只携带一次性授权码（URL 不含 JWT），凭授权码换取完整登录态
    try {
      const data = await exchangeGithub({ authCode })
      userStore.setLogin(data)
      loading.value = false
      ElMessage.success(`欢迎回来，${data.realName}`)
      router.replace(homeByRole(data.roleType))
      return
    } catch {
      loading.value = false
      router.replace('/login')
      return
    }
  }
  if (needBind && providerUid) {
    loading.value = false
    router.replace({ path: '/oauth/bind', query: { providerUid } })
    return
  }
  loading.value = false
  ElMessage.error('登录失败，请重试')
  router.replace('/login')
})
</script>

<style scoped>
.oauth-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f2f7ff 0%, #eaf1fc 55%, #f6f9ff 100%);
}
.oauth-card {
  width: 320px;
  text-align: center;
  padding: 8px;
}
.spin {
  animation: rotate 1s linear infinite;
  margin-bottom: 12px;
}
@keyframes rotate {
  to {
    transform: rotate(360deg);
  }
}
</style>
