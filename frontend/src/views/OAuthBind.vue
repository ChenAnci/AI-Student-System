<template>
  <div class="bind-page">
    <el-card class="bind-card">
      <h2>绑定账号</h2>
      <p class="tip">首次使用 GitHub 登录，请绑定您的系统账号（工号/学号）</p>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleBind">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入工号/学号" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="primary" class="bind-btn" :loading="loading" @click="handleBind">绑定并登录</el-button>
      </el-form>
      <el-button text type="primary" @click="goLogin">返回账号密码登录</el-button>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { bindGithub } from '@/api/oauth'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const providerUid = String(route.query.providerUid || '')

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入工号/学号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function homeByRole(role: string): string {
  if (role === 'ADMIN') return '/admin/accounts'
  if (role === 'TEACHER') return '/teacher/courses'
  return '/student/dashboard'
}

async function handleBind() {
  if (!providerUid) {
    ElMessage.error('绑定凭证缺失，请重新通过 GitHub 登录')
    return
  }
  await formRef.value?.validate()
  loading.value = true
  try {
    const data = await bindGithub({ ...form, providerUid })
    userStore.setLogin(data)
    ElMessage.success(`绑定成功，欢迎 ${data.realName}`)
    router.replace(homeByRole(data.roleType))
  } finally {
    loading.value = false
  }
}

function goLogin() {
  router.replace('/login')
}
</script>

<style scoped>
.bind-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f2f7ff 0%, #eaf1fc 55%, #f6f9ff 100%);
}
.bind-card {
  width: 400px;
  padding: 8px;
  text-align: center;
}
.bind-card h2 {
  margin-bottom: 6px;
}
.tip {
  color: #5a6b87;
  font-size: 13px;
  margin-bottom: 24px;
}
.bind-btn {
  width: 100%;
  margin-bottom: 8px;
}
</style>
