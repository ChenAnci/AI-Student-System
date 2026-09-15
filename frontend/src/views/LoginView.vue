<template>
  <div class="login-page">
    <div class="login-box">
      <div class="login-title">
        <el-icon :size="34" color="#409eff"><School /></el-icon>
        <h2>学生信息管理系统</h2>
        <p>教学秘书 · 教师 · 学生 统一登录入口</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入工号/学号" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-tips">
        <p>账号由教学秘书统一分配；如忘记密码，请联系教学秘书重置</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

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

async function handleLogin() {
  await formRef.value?.validate()
  loading.value = true
  try {
    const data = await login(form)
    userStore.setLogin(data)
    ElMessage.success(`欢迎回来，${data.realName}`)
    router.push(homeByRole(data.roleType))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1d3a8a 0%, #409eff 60%, #79bbff 100%);
}
.login-box {
  width: 400px;
  padding: 40px 36px 28px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
}
.login-title {
  text-align: center;
  margin-bottom: 26px;
}
.login-title h2 {
  margin: 8px 0 4px;
  font-size: 22px;
  color: #303133;
}
.login-title p {
  font-size: 13px;
  color: #909399;
}
.login-btn {
  width: 100%;
}
.login-tips {
  margin-top: 12px;
  padding: 10px 12px;
  background: #f4f6fb;
  border-radius: 6px;
  font-size: 12px;
  color: #909399;
  line-height: 1.8;
}
</style>
