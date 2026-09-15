<template>
  <div class="login-page">
    <!-- 左侧品牌展示区 -->
    <div class="brand-panel">
      <div class="brand-bg" />
      <div class="brand-overlay" />

      <div class="brand-content">
        <div class="brand-logo animate-fade-up" style="animation-delay: 0.2s">
          <el-icon :size="28" color="#409eff"><School /></el-icon>
          <span>学生信息管理系统</span>
        </div>

        <div class="brand-title animate-fade-up" style="animation-delay: 0.35s">
          <h1>
            <span class="grad-text">智能教务</span><br />
            一站式管理平台
          </h1>
          <p>教学秘书 · 教师 · 学生 统一登录入口</p>
        </div>

        <div class="brand-steps animate-fade-up" style="animation-delay: 0.5s">
          <div v-for="(step, index) in steps" :key="index" class="step-item">
            <span class="step-index">{{ index + 1 }}</span>
            <span class="step-text">{{ step }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧表单区 -->
    <div class="form-panel">
      <div class="form-box">
        <div class="form-header">
          <h2>欢迎回来</h2>
          <p>请输入您的账号信息以继续</p>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入工号/学号"
              :prefix-icon="User"
              clearable
            />
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
              {{ loading ? '登录中...' : '登 录' }}
            </el-button>
          </el-form-item>
        </el-form>

        <div class="login-tips">
          <p>账号由教学秘书统一分配；如忘记密码，请联系教学秘书重置</p>
        </div>
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

const steps = ['输入工号 / 学号与密码', '完成身份校验', '进入对应角色的工作台']

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
  background: linear-gradient(135deg, #f2f7ff 0%, #eaf1fc 55%, #f6f9ff 100%);
  color: #1f2d3d;
}

/* ---------- 左侧品牌区（浅色科技风） ---------- */
.brand-panel {
  position: relative;
  width: 52%;
  min-width: 480px;
  overflow: hidden;
}
.brand-bg {
  position: absolute;
  inset: 0;
  /* 浅色科技渐变：蓝白光斑 + 细网格 + 浅蓝白底 */
  background:
    radial-gradient(1100px 560px at 82% 10%, rgba(64, 158, 255, 0.22), transparent 62%),
    radial-gradient(900px 520px at 8% 92%, rgba(124, 58, 237, 0.14), transparent 62%),
    repeating-linear-gradient(0deg, rgba(64, 158, 255, 0.05) 0 1px, transparent 1px 44px),
    repeating-linear-gradient(90deg, rgba(64, 158, 255, 0.05) 0 1px, transparent 1px 44px),
    linear-gradient(160deg, #e8f1ff 0%, #f2f7ff 45%, #ffffff 100%);
}
.brand-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(160deg, rgba(64, 158, 255, 0.05) 0%, rgba(124, 58, 237, 0.06) 100%);
}
.brand-content {
  position: relative;
  z-index: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 clamp(40px, 6vw, 88px);
  gap: 40px;
}
.brand-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
  letter-spacing: 0.5px;
}
.brand-title h1 {
  font-size: clamp(34px, 3.6vw, 52px);
  line-height: 1.18;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin-bottom: 16px;
  color: #16233a;
}
.brand-title p {
  font-size: 15px;
  color: #5a6b87;
}
.step-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  margin-bottom: 12px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(64, 158, 255, 0.18);
  box-shadow: 0 4px 14px rgba(64, 158, 255, 0.08);
}
.step-index {
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: var(--brand-grad-soft);
}
.step-text {
  font-size: 14px;
  color: #3a4a66;
}

/* ---------- 右侧表单区（浅色卡片） ---------- */
.form-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px clamp(24px, 5vw, 72px);
  overflow-y: auto;
}
.form-box {
  width: 100%;
  max-width: 420px;
}
.form-header {
  margin-bottom: 32px;
}
.form-header h2 {
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #16233a;
}
.form-header p {
  margin-top: 8px;
  font-size: 14px;
  color: #5a6b87;
}

/* 浅色表单控件（与系统 Element Plus 风格一致） */
.form-box :deep(.el-form-item) {
  margin-bottom: 22px;
}
.form-box :deep(.el-input__wrapper) {
  background: #fff;
  border: 1px solid #d9e2f0;
  border-radius: var(--radius-md);
  box-shadow: none;
  padding: 4px 14px;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.form-box :deep(.el-input__wrapper:hover) {
  border-color: #a8c4f5;
}
.form-box :deep(.el-input__wrapper.is-focus) {
  border-color: var(--brand-blue);
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.15);
}
.form-box :deep(.el-input__inner) {
  color: #1f2d3d;
  height: 44px;
}
.form-box :deep(.el-input__inner::placeholder) {
  color: #9aa5b8;
}
.form-box :deep(.el-input__prefix) {
  color: #7b8aa4;
}
.form-box :deep(.el-input__suffix) {
  color: #7b8aa4;
}

.login-btn {
  width: 100%;
  height: 48px;
  margin-top: 4px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 4px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--brand-grad);
  transition: opacity 0.2s, transform 0.15s;
}
.login-btn:hover {
  opacity: 0.9;
}
.login-btn:active {
  transform: scale(0.985);
}

.login-tips {
  margin-top: 8px;
  padding: 12px 14px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid #dce5f3;
  font-size: 12px;
  color: #5a6b87;
  line-height: 1.8;
}

/* 响应式：窄屏隐藏左侧 */
@media (max-width: 900px) {
  .brand-panel {
    display: none;
  }
}
</style>
