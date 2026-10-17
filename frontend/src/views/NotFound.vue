<template>
  <div class="not-found">
    <!-- Animated Background Circles -->
    <div class="bg-animate">
      <div class="bg-circle circle-1"></div>
      <div class="bg-circle circle-2"></div>
      <div class="bg-circle circle-3"></div>
    </div>

    <div class="content glass-card">
      <div class="icon-wrapper">
        <el-icon :size="64" color="var(--accent)"><Warning /></el-icon>
      </div>
      <h1>404</h1>
      <p class="subtitle">抱歉，您访问的页面不存在</p>
      <p class="hint">请检查 URL 或返回首页</p>
      <el-button type="primary" size="large" @click="goHome" class="home-btn">
        <el-icon><HomeFilled /></el-icon>
        返回首页
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Warning, HomeFilled } from '@element-plus/icons-vue'

const router = useRouter()

const goHome = () => {
  router.push('/dashboard')
}
</script>

<style scoped>
.not-found {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--background);
  position: relative;
  overflow: hidden;
}

.bg-animate {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.bg-circle {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.15) 0%, transparent 70%);
  animation: float 20s infinite ease-in-out;
}

.circle-1 {
  width: 500px;
  height: 500px;
  top: -200px;
  right: -200px;
}

.circle-2 {
  width: 400px;
  height: 400px;
  bottom: -100px;
  left: -100px;
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

.content {
  text-align: center;
  padding: 64px 80px;
  position: relative;
  z-index: 1;
  animation: fadeInUp 0.6s ease;
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

.icon-wrapper {
  width: 100px;
  height: 100px;
  margin: 0 auto 24px;
  background: var(--accent-light);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: pulse-icon 2s infinite ease-in-out;
}

@keyframes pulse-icon {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.05); opacity: 0.9; }
}

h1 {
  font-size: 140px;
  font-weight: 700;
  color: var(--accent);
  margin: 0 0 16px 0;
  letter-spacing: var(--tracking-tight);
  line-height: 1;
}

.subtitle {
  font-size: 24px;
  font-weight: 600;
  color: var(--foreground);
  margin: 0 0 8px 0;
}

.hint {
  font-size: 14px;
  color: var(--muted-foreground);
  margin: 0 0 32px 0;
}

.home-btn {
  padding: 12px 32px;
  font-size: 16px;
  border-radius: var(--radius);
}

.home-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(99, 102, 241, 0.3);
}

@media (prefers-reduced-motion: reduce) {
  .bg-circle,
  .icon-wrapper {
    animation: none;
  }
  .content {
    animation: none;
  }
}
</style>
