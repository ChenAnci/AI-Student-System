<template>
  <div class="portal">
    <!-- ===== 首屏 Hero ===== -->
    <section class="hero">
      <nav class="nav" :class="{ 'nav-scrolled': scrolled }">
        <div class="nav-logo">
          <el-icon :size="24" color="#409eff"><School /></el-icon>
          <span>学生信息管理系统</span>
        </div>

        <ul class="nav-links">
          <li v-for="(item, index) in navItems" :key="item.label">
            <a
              :href="item.href"
              @click.prevent="scrollToSection(item.href)"
              :class="{ active: activeNav === index }"
            >{{ item.label }}</a>
          </li>
        </ul>

        <button type="button" class="nav-cta" @click="goLogin">立即登录</button>
      </nav>

      <div class="hero-bg" />
      <div
        ref="revealRef"
        class="hero-reveal"
      />
      <div class="hero-overlay" />

      <div class="hero-content">
        <p class="hero-badge animate-fade-up" style="animation-delay: 0.15s">智能教务 · 一站式平台</p>
        <h1 class="animate-fade-up" style="animation-delay: 0.3s">
          <span>让教学管理</span>
          <span class="grad-text">更简单高效</span>
        </h1>
        <p class="hero-sub animate-fade-up" style="animation-delay: 0.45s">
          覆盖课程管理、选课报名、成绩录入审核与统计分析的统一管理平台，
          为教学秘书、教师与学生提供一站式数字化服务。
        </p>
        <div class="hero-actions animate-fade-up" style="animation-delay: 0.6s">
          <button type="button" class="btn-primary" @click="goLogin">进入系统</button>
          <a href="#features" class="btn-ghost" @click.prevent="scrollToSection('#features')">了解功能</a>
        </div>
      </div>
    </section>

    <!-- ===== 功能特性 ===== -->
    <section id="features" class="section section-dark">
      <div class="container">
        <div class="section-head scroll-trigger" ref="featuresTitleRef">
          <h2>核心功能</h2>
          <p>为不同角色量身打造，覆盖教务管理全流程</p>
        </div>

        <div class="feature-grid">
          <div
            v-for="(feature, index) in features"
            :key="feature.title"
            class="feature-card scroll-trigger"
            :ref="(el) => setRef(featureRefs, index, el)"
          >
            <div class="feature-icon">
              <el-icon :size="26"><component :is="feature.icon" /></el-icon>
            </div>
            <h3>{{ feature.title }}</h3>
            <p>{{ feature.description }}</p>
            <span class="feature-role">{{ feature.role }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 使用流程 ===== -->
    <section id="how-it-works" class="section section-white">
      <div class="container">
        <div class="section-head scroll-trigger" ref="stepsTitleRef">
          <h2>使用流程</h2>
          <p>三步开启您的教务管理之旅</p>
        </div>

        <div class="step-line" />

        <div class="step-grid">
          <div
            v-for="(step, index) in steps"
            :key="step.title"
            class="step-card scroll-trigger"
            :ref="(el) => setRef(stepRefs, index, el)"
          >
            <div class="step-badge">
              <el-icon :size="22"><component :is="step.icon" /></el-icon>
              <span class="step-num">{{ index + 1 }}</span>
            </div>
            <h3>{{ step.title }}</h3>
            <p>{{ step.description }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 联系 / CTA ===== -->
    <section id="contact" class="section section-dark">
      <div class="container">
        <div class="cta-box scroll-trigger" ref="contactRef">
          <h2>准备好开始了吗？</h2>
          <p>使用统一账号登录，即刻体验智能教务管理</p>
          <button type="button" class="btn-primary" @click="goLogin">立即登录</button>
        </div>
      </div>
    </section>

    <!-- ===== 页脚 ===== -->
    <footer class="footer">
      <div class="footer-inner">
        <div class="footer-brand">
          <el-icon :size="20" color="#409eff"><School /></el-icon>
          <span>学生信息管理系统</span>
        </div>
        <p>© {{ year }} 学生信息管理系统 · 智能教务管理平台</p>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, type Component } from 'vue'
import { useRouter } from 'vue-router'
import {
  ShoppingCart,
  Document,
  TrendCharts,
  ChatDotRound,
  Key,
  MagicStick,
  Files
} from '@element-plus/icons-vue'

const router = useRouter()

const year = new Date().getFullYear()

const scrolled = ref(false)
const activeNav = ref(0)
const navItems = [
  { label: '核心功能', href: '#features' },
  { label: '使用流程', href: '#how-it-works' },
  { label: '联系我们', href: '#contact' }
]

interface FeatureItem {
  icon: Component
  title: string
  description: string
  role: string
}

const features: FeatureItem[] = [
  {
    icon: ShoppingCart,
    title: '在线选课中心',
    description: '学生在线浏览课程、自主选课，实时掌握选课状态与课表安排。',
    role: '学生'
  },
  {
    icon: Document,
    title: '成绩查询',
    description: '支持成绩在线查询与可视化统计，让每一门课程的成绩清晰可见。',
    role: '学生'
  },
  {
    icon: TrendCharts,
    title: '数据分析统计',
    description: '教务统计看板与可视化图表，辅助教学秘书掌握教学运行全局。',
    role: '教学秘书 · 教师'
  },
  {
    icon: ChatDotRound,
    title: 'AI 智能助手',
    description: '基于大模型的智能问答助手，为教学管理提供即时咨询与建议。',
    role: '全体用户'
  }
]

interface StepItem {
  icon: Component
  title: string
  description: string
}

const steps: StepItem[] = [
  {
    icon: Key,
    title: '分配账号',
    description: '教学秘书为教师与学生统一分配登录账号，安全可靠。'
  },
  {
    icon: MagicStick,
    title: '登录系统',
    description: '使用工号 / 学号与密码登录，进入对应角色的专属工作台。'
  },
  {
    icon: Files,
    title: '开展业务',
    description: '选课、录分、审核、统计……一站式完成日常教务工作。'
  }
]

const featureRefs = ref<Array<HTMLElement | null>>([])
const stepRefs = ref<Array<HTMLElement | null>>([])
const featuresTitleRef = ref<HTMLElement | null>(null)
const stepsTitleRef = ref<HTMLElement | null>(null)
const contactRef = ref<HTMLElement | null>(null)

function setRef(list: Array<HTMLElement | null>, index: number, el: unknown) {
  if (el instanceof HTMLElement) {
    list[index] = el
  }
}

function goLogin() {
  router.push('/login')
}

function scrollToSection(href: string) {
  const element = document.querySelector(href)
  if (element) {
    element.scrollIntoView({ behavior: 'smooth' })
  }
}

/* ---------- 鼠标聚光探照灯效果（CSS mask 变量，轻量） ---------- */
const revealRef = ref<HTMLElement | null>(null)
const mouseRef = ref({ x: -999, y: -999 })
const smoothRef = ref({ x: -999, y: -999 })
let rafId: number | null = null

function handleMouseMove(e: MouseEvent) {
  mouseRef.value.x = e.clientX
  mouseRef.value.y = e.clientY
}

function animate() {
  smoothRef.value.x += (mouseRef.value.x - smoothRef.value.x) * 0.12
  smoothRef.value.y += (mouseRef.value.y - smoothRef.value.y) * 0.12
  const reveal = revealRef.value
  if (reveal) {
    reveal.style.setProperty('--spot-x', `${smoothRef.value.x}px`)
    reveal.style.setProperty('--spot-y', `${smoothRef.value.y}px`)
  }
  rafId = requestAnimationFrame(animate)
}

/* ---------- 滚动监听 ---------- */
let observer: IntersectionObserver | null = null

function handleScroll() {
  scrolled.value = window.scrollY > 60
  const sectionIds = navItems.map((item) => item.href.replace('#', ''))
  const sections = sectionIds
    .map((id) => document.getElementById(id))
    .filter((el): el is HTMLElement => el !== null)

  let closestIdx = 0
  let closestDistance = Infinity
  sections.forEach((el, idx) => {
    const distance = Math.abs(el.getBoundingClientRect().top)
    if (distance < closestDistance) {
      closestDistance = distance
      closestIdx = idx
    }
  })
  activeNav.value = closestIdx
}

onMounted(() => {
  window.addEventListener('mousemove', handleMouseMove)
  rafId = requestAnimationFrame(animate)

  window.addEventListener('scroll', handleScroll, { passive: true })
  handleScroll()

  setTimeout(() => {
    const targets = [
      featuresTitleRef.value,
      stepsTitleRef.value,
      contactRef.value,
      ...featureRefs.value,
      ...stepRefs.value
    ].filter((el): el is HTMLElement => el !== null)

    observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('visible')
          }
        })
      },
      { threshold: 0.12, rootMargin: '-40px 0px -40px 0px' }
    )
    targets.forEach((el) => observer?.observe(el))
  }, 100)
})

onUnmounted(() => {
  window.removeEventListener('mousemove', handleMouseMove)
  window.removeEventListener('scroll', handleScroll)
  if (observer) observer.disconnect()
  if (rafId !== null) cancelAnimationFrame(rafId)
})
</script>

<style scoped>
.portal {
  background: #f5f8fc;
  color: #1f2d3d;
  overflow-x: hidden;
}

/* ===== 导航 ===== */
.nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22px clamp(20px, 5vw, 64px);
  transition: background 0.3s, padding 0.3s;
}
.nav-scrolled {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(14px);
  padding-top: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid #dce5f3;
  box-shadow: 0 6px 24px rgba(31, 45, 61, 0.06);
}
.nav-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 17px;
  font-weight: 600;
  color: #1f2d3d;
}
.nav-links {
  display: flex;
  gap: 40px;
  list-style: none;
}
.nav-links a {
  position: relative;
  font-size: 14px;
  color: #5a6b87;
  text-decoration: none;
  transition: color 0.2s;
}
.nav-links a::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -8px;
  height: 2px;
  border-radius: 2px;
  background: var(--brand-grad);
  opacity: 0;
  transition: opacity 0.2s;
}
.nav-links a:hover,
.nav-links a.active {
  color: #1f2d3d;
}
.nav-links a.active::after {
  opacity: 1;
}
.nav-cta {
  padding: 9px 22px;
  border-radius: 999px;
  border: none;
  background: var(--brand-grad);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s, transform 0.15s;
}
.nav-cta:hover {
  opacity: 0.9;
}
.nav-cta:active {
  transform: scale(0.97);
}

/* ===== Hero（浅色科技风） ===== */
.hero {
  position: relative;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
}
.hero-bg,
.hero-reveal {
  position: absolute;
  inset: 0;
  /* 浅色科技渐变：蓝白光斑 + 细网格 + 浅蓝白底 */
  background:
    radial-gradient(1200px 620px at 78% 12%, rgba(64, 158, 255, 0.2), transparent 62%),
    radial-gradient(900px 520px at 12% 90%, rgba(124, 58, 237, 0.12), transparent 62%),
    repeating-linear-gradient(0deg, rgba(64, 158, 255, 0.045) 0 1px, transparent 1px 44px),
    repeating-linear-gradient(90deg, rgba(64, 158, 255, 0.045) 0 1px, transparent 1px 44px),
    linear-gradient(170deg, #eaf2ff 0%, #f2f7ff 55%, #ffffff 100%);
}
.hero-reveal {
  z-index: 1;
  pointer-events: none;
  --spot-x: -999px;
  --spot-y: -999px;
  /* 聚光区域显示更亮的蓝紫光斑 */
  background:
    radial-gradient(circle 300px at 50% 50%, rgba(96, 165, 250, 0.5), transparent 68%),
    radial-gradient(1200px 620px at 78% 12%, rgba(64, 158, 255, 0.3), transparent 62%),
    linear-gradient(170deg, #eaf2ff 0%, #f6faff 60%, #ffffff 100%);
  -webkit-mask-image: radial-gradient(circle 280px at var(--spot-x) var(--spot-y), #fff 0%, #fff 42%, transparent 72%);
  mask-image: radial-gradient(circle 280px at var(--spot-x) var(--spot-y), #fff 0%, #fff 42%, transparent 72%);
}
.hero-overlay {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.1) 0%, rgba(255, 255, 255, 0.06) 45%, #f5f8fc 100%);
}
.hero-content {
  position: relative;
  z-index: 4;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 0 24px;
}
.hero-badge {
  padding: 7px 18px;
  margin-bottom: 28px;
  border-radius: 999px;
  font-size: 13px;
  letter-spacing: 2px;
  color: #2f6bd8;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(64, 158, 255, 0.3);
  backdrop-filter: blur(8px);
}
.hero-content h1 {
  font-size: clamp(40px, 6vw, 76px);
  font-weight: 800;
  line-height: 1.12;
  letter-spacing: -0.03em;
  margin-bottom: 22px;
  color: #16233a;
}
.hero-sub {
  max-width: 640px;
  font-size: 16px;
  line-height: 1.9;
  color: #5a6b87;
  margin-bottom: 40px;
}
.hero-actions {
  display: flex;
  gap: 16px;
}
.btn-primary {
  padding: 14px 42px;
  border: none;
  border-radius: 999px;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  background: var(--brand-grad);
  cursor: pointer;
  box-shadow: 0 8px 28px rgba(64, 158, 255, 0.32);
  transition: opacity 0.2s, transform 0.15s;
}
.btn-primary:hover {
  opacity: 0.9;
}
.btn-primary:active {
  transform: scale(0.97);
}
.btn-ghost {
  padding: 14px 42px;
  border-radius: 999px;
  font-size: 15px;
  font-weight: 500;
  color: #2f6bd8;
  text-decoration: none;
  border: 1px solid rgba(64, 158, 255, 0.45);
  background: rgba(255, 255, 255, 0.6);
  transition: border-color 0.2s, background 0.2s;
}
.btn-ghost:hover {
  border-color: var(--brand-blue);
  background: rgba(255, 255, 255, 0.9);
}

/* ===== 区块通用（浅色交替） ===== */
.section {
  padding: clamp(72px, 10vw, 128px) 0;
}
.section-dark {
  background: #f0f4fa;
}
.section-white {
  background: #ffffff;
}
.container {
  max-width: 1120px;
  margin: 0 auto;
  padding: 0 24px;
}
.section-head {
  text-align: center;
  margin-bottom: clamp(40px, 6vw, 64px);
}
.section-head h2 {
  font-size: clamp(30px, 4vw, 46px);
  font-weight: 800;
  letter-spacing: -0.02em;
  margin-bottom: 14px;
  background: var(--brand-grad);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.section-head p {
  font-size: 15px;
  color: #5a6b87;
}

/* ===== 功能卡片 ===== */
.feature-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 24px;
}
.feature-card {
  position: relative;
  padding: 32px 28px;
  border-radius: var(--radius-lg);
  background: #fff;
  border: 1px solid #dce5f3;
  box-shadow: 0 4px 16px rgba(31, 45, 61, 0.05);
  overflow: hidden;
  transition: transform 0.4s, border-color 0.4s, box-shadow 0.4s;
}
.feature-card:hover {
  transform: translateY(-6px);
  border-color: rgba(64, 158, 255, 0.4);
  box-shadow: 0 14px 34px rgba(64, 158, 255, 0.12);
}
.feature-card::before {
  content: '';
  position: absolute;
  top: -60px;
  right: -60px;
  width: 140px;
  height: 140px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--brand-blue) 0%, transparent 70%);
  opacity: 0;
  transition: opacity 0.6s;
  pointer-events: none;
}
.feature-card:hover::before {
  opacity: 0.14;
}
.feature-icon {
  width: 54px;
  height: 54px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  margin-bottom: 22px;
  color: #fff;
  background: var(--brand-grad-soft);
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.3);
}
.feature-card h3 {
  font-size: 19px;
  font-weight: 600;
  margin-bottom: 10px;
  color: #1f2d3d;
}
.feature-card p {
  font-size: 14px;
  line-height: 1.8;
  color: #5a6b87;
  margin-bottom: 18px;
}
.feature-role {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  color: #2f6bd8;
  background: rgba(64, 158, 255, 0.1);
  border: 1px solid rgba(64, 158, 255, 0.28);
}

/* ===== 使用流程 ===== */
.step-line {
  display: none;
}
.step-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 24px;
}
.step-card {
  text-align: center;
  padding: 36px 24px;
  border-radius: var(--radius-lg);
  background: #fff;
  border: 1px solid #dce5f3;
  box-shadow: 0 4px 16px rgba(31, 45, 61, 0.05);
}
.step-badge {
  position: relative;
  width: 64px;
  height: 64px;
  margin: 0 auto 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  background: var(--brand-grad);
  box-shadow: 0 10px 28px rgba(64, 158, 255, 0.32);
}
.step-num {
  position: absolute;
  right: -6px;
  bottom: -6px;
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 700;
  color: #2f6bd8;
  background: #fff;
  border: 2px solid rgba(64, 158, 255, 0.45);
}
.step-card h3 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 10px;
  color: #1f2d3d;
}
.step-card p {
  font-size: 14px;
  line-height: 1.8;
  color: #5a6b87;
}

/* ===== CTA ===== */
.cta-box {
  max-width: 720px;
  margin: 0 auto;
  text-align: center;
  padding: clamp(44px, 6vw, 72px) 32px;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, #ffffff, #eef5ff);
  border: 1px solid rgba(64, 158, 255, 0.35);
  box-shadow: 0 10px 34px rgba(64, 158, 255, 0.1);
}
.cta-box h2 {
  font-size: clamp(26px, 3.5vw, 40px);
  font-weight: 800;
  margin-bottom: 14px;
  color: #16233a;
}
.cta-box p {
  font-size: 15px;
  color: #5a6b87;
  margin-bottom: 30px;
}

/* ===== 页脚 ===== */
.footer {
  border-top: 1px solid #dce5f3;
  background: #ffffff;
}
.footer-inner {
  max-width: 1120px;
  margin: 0 auto;
  padding: 32px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.footer-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;
}
.footer p {
  font-size: 13px;
  color: #8a97ad;
}

/* 响应式 */
@media (max-width: 720px) {
  .nav-links {
    display: none;
  }
  .hero-actions {
    flex-direction: column;
  }
  .btn-primary,
  .btn-ghost {
    width: 100%;
    text-align: center;
  }
}
</style>
