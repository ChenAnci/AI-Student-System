// ===== 环境类型声明（env.d.ts） =====
// 职责：声明 Vite 客户端类型（import.meta.env 等），
// 并为 *.vue 模块提供默认类型，使 TS 能识别 .vue 文件导入。
/// <reference types="vite/client" />

// 声明 .vue 单文件组件模块：导入 .vue 时使用该默认组件类型（含泛型 props/emits/slots）
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
