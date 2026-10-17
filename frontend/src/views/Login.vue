<template>
  <div class="login-container">
    <!-- Animated Background -->
    <div class="bg-animate">
      <div class="bg-circle circle-1"></div>
      <div class="bg-circle circle-2"></div>
      <div class="bg-circle circle-3"></div>
      <div class="bg-grid"></div>
    </div>

    <!-- Left Side - Branding -->
    <div class="login-left">
      <div class="brand-content">
        <div class="logo-wrapper">
          <svg class="logo-icon" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="50" cy="50" r="45" stroke="white" stroke-width="3" fill="none" opacity="0.3"/>
            <circle cx="50" cy="50" r="35" stroke="white" stroke-width="2" fill="none" opacity="0.5"/>
            <path d="M50 20 L50 80 M30 40 L50 20 L70 40 M30 60 L50 80 L70 60" stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
            <circle cx="50" cy="50" r="8" fill="white" opacity="0.8"/>
          </svg>
        </div>
        <h1 class="brand-title">RAG医学知识库</h1>
        <p class="brand-subtitle">智能文档检索 · 精准问答服务 · 知识管理平台</p>

        <div class="feature-list">
          <div class="feature-item">
            <el-icon class="feature-icon"><Reading /></el-icon>
            <span>海量文档智能管理</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon"><Search /></el-icon>
            <span>精准语义检索</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon"><ChatDotRound /></el-icon>
            <span>智能问答交互</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Right Side - Login Form -->
    <div class="login-right">
      <div class="login-box">
        <div class="login-header">
          <h2>欢迎回来</h2>
          <p>请登录您的账号以继续使用系统</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          class="login-form"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="username" class="form-item-animated">
            <label class="form-label">
              <el-icon class="label-icon"><User /></el-icon>
              用户名
            </label>
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              size="large"
              class="form-input-modern"
            >
              <template #prefix>
                <el-icon class="input-icon"><User /></el-icon>
              </template>
            </el-input>
          </el-form-item>

<el-form-item prop="password" class="form-item-animated">
            <label class="form-label">
              <el-icon class="label-icon"><Lock /></el-icon>
              密码
            </label>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              size="large"
              show-password
              class="form-input-modern"
              @keyup.enter="handleLogin"
            >
              <template #prefix>
                <el-icon class="input-icon"><Lock /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <div class="form-options">
            <el-checkbox v-model="rememberMe" class="checkbox-modern">
              <span class="checkbox-label">记住我</span>
            </el-checkbox>
            <a href="#" class="forgot-link">忘记密码？</a>
          </div>

          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-btn-modern"
            @click="handleLogin"
          >
            <span v-if="!loading" class="btn-text">登 录</span>
            <span v-else class="btn-text">登录中...</span>
          </el-button>
        </el-form>

        <div class="login-footer">
          <span class="footer-text">还没有账号？</span>
          <router-link to="/register" class="register-link">
            <span class="link-text">立即注册</span>
            <span class="link-arrow">→</span>
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Reading, Search, ChatDotRound } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { auditService } from '@/services/auditLog'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const rememberMe = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      const success = await userStore.login(form.username, form.password, true)
      if (success) {
        auditService.logLogin({
          username: form.username,
          success: true,
          reason: '登录成功'
        })
        ElMessage.success('登录成功')
        const redirect = (route.query.redirect as string) || '/dashboard'
        router.push(redirect)
      }
    } catch (error: any) {
      console.error('Login failed:', error)
      auditService.logLogin({
        username: form.username,
        success: false,
        reason: error?.message || '未知错误'
      })
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  position: relative;
  overflow: hidden;
}

/* Animated Background */
.bg-animate {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, var(--dark-bg) 0%, var(--dark-bg-2) 50%, var(--dark-bg) 100%);
  z-index: 0;
}

.bg-circle {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.3) 0%, transparent 70%);
  animation: float 20s infinite ease-in-out;
}

.circle-1 {
  width: 600px;
  height: 600px;
  top: -200px;
  left: -200px;
  animation-delay: 0s;
}

.circle-2 {
  width: 400px;
  height: 400px;
  bottom: -100px;
  right: -100px;
  animation-delay: -5s;
}

.circle-3 {
  width: 300px;
  height: 300px;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation-delay: -10s;
}

@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  25% { transform: translate(20px, -20px) scale(1.05); }
  50% { transform: translate(-10px, 10px) scale(0.95); }
  75% { transform: translate(10px, 20px) scale(1.02); }
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
  background-size: 50px 50px;
}

/* Left Side */
.login-left {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  position: relative;
  z-index: 1;
}

.brand-content {
  text-align: center;
  color: #fff;
  animation: fadeInUp 0.8s ease;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.logo-wrapper {
  width: 120px;
  height: 120px;
  margin: 0 auto 32px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  animation: pulse 3s infinite ease-in-out;
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(255, 255, 255, 0.2); }
  50% { box-shadow: 0 0 30px 10px rgba(255, 255, 255, 0.1); }
}

.logo-icon {
  width: 64px;
  height: 64px;
}

.brand-title {
  font-size: 42px;
  font-weight: 700;
  margin-bottom: 16px;
  color: var(--dark-fg);
  letter-spacing: var(--tracking-tight);
}

.brand-subtitle {
  font-size: 18px;
  color: rgba(255, 255, 255, 0.8);
  margin-bottom: 48px;
  letter-spacing: 1px;
}

.feature-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  text-align: left;
  max-width: 280px;
  margin: 0 auto;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  transition: all 0.3s ease;
}

.feature-item:hover {
  background: rgba(255, 255, 255, 0.15);
  transform: translateX(10px);
}

.feature-icon {
  font-size: 20px;
}

/* Right Side */
.login-right {
  width: 520px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  background: var(--card);
  backdrop-filter: blur(20px);
  position: relative;
  z-index: 1;
  border-left: 1px solid var(--border);
}

.login-box {
  width: 100%;
  max-width: 400px;
  animation: fadeInUp 0.8s ease 0.2s both;
}

.login-header {
  margin-bottom: 40px;
  text-align: center;
}

.login-header h2 {
  font-size: 28px;
  font-weight: 600;
  color: var(--foreground);
  margin-bottom: 8px;
}

.login-header p {
  font-size: 14px;
  color: var(--muted-foreground);
}

.login-form {
  margin-bottom: 24px;
}

/* Form Items */
.form-item-animated {
  margin-bottom: 24px;
  animation: fadeInUp 0.5s ease both;
}

.form-item-animated:nth-child(1) { animation-delay: 0.3s; }
.form-item-animated:nth-child(2) { animation-delay: 0.4s; }

.form-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--foreground);
  margin-bottom: 8px;
}

.label-icon {
  font-size: 16px;
  color: var(--muted-foreground);
}

.form-input-modern {
  --el-input-border-radius: var(--radius);
  --el-input-height: 48px;
}

.form-input-modern :deep(.el-input__wrapper) {
  border-radius: var(--radius);
  padding: 0 16px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border);
  transition: all var(--transition);
}

.form-input-modern :deep(.el-input__wrapper:hover) {
  border-color: var(--accent-light);
}

.form-input-modern :deep(.el-input__wrapper:focus) {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.input-icon {
  color: var(--muted-foreground);
  font-size: 18px;
}

/* Form Options */
.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.checkbox-modern :deep(.el-checkbox__label) {
  color: var(--foreground);
}

.forgot-link {
  font-size: 14px;
  color: var(--accent);
  text-decoration: none;
  transition: color var(--transition);
}

.forgot-link:hover {
  color: var(--accent-dark);
}

/* Login Button */
.login-btn-modern {
  width: 100%;
  height: 52px;
  font-size: 16px;
  font-weight: 600;
  border: none;
  border-radius: var(--radius);
  background: linear-gradient(135deg, var(--accent) 0%, var(--accent-dark) 100%);
  color: var(--accent-foreground);
  cursor: pointer;
  transition: all var(--transition);
  position: relative;
  overflow: hidden;
}

.login-btn-modern::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transition: left 0.5s ease;
}

.login-btn-modern:hover::before {
  left: 100%;
}

.login-btn-modern:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(99, 102, 241, 0.4);
}

.login-btn-modern:active {
  transform: translateY(0);
}

/* Login Footer */
.login-footer {
  text-align: center;
  font-size: 14px;
  color: var(--muted-foreground);
  margin-top: 32px;
}

.footer-text {
  color: var(--muted-foreground);
}

.register-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--accent);
  text-decoration: none;
  font-weight: 500;
  transition: all var(--transition);
  margin-left: 4px;
}

.register-link:hover {
  color: var(--accent-dark);
}

.link-text {
  position: relative;
}

.link-text::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 0;
  width: 0;
  height: 2px;
  background: var(--accent-dark);
  transition: width var(--transition);
}

.register-link:hover .link-text::after {
  width: 100%;
}

.link-arrow {
  font-size: 16px;
  transition: transform var(--transition);
}

.register-link:hover .link-arrow {
  transform: translateX(4px);
}

/* Responsive */
@media (max-width: 768px) {
  .login-left {
    display: none;
  }

  .login-right {
    width: 100%;
    padding: 24px;
  }

  .brand-title {
    font-size: 32px;
  }
}
</style>