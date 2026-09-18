// ===== 应用入口（main.ts） =====
// 职责：创建 Vue 应用实例，注册 Pinia / 路由 / Element Plus（中文语言包），
// 全局注册 Element Plus 图标组件并挂载到 #app。
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import '@/styles/theme.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'

// 创建根应用实例
const app = createApp(App)

// 遍历并全局注册所有 Element Plus 图标组件（模板中可直接用 <Bell /> 等）
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 依次安装状态管理（Pinia）、路由（Router）、UI 组件库（Element Plus，中文语言包）
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 挂载到 index.html 中的 #app 节点
app.mount('#app')
