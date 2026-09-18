<!-- ===== 根组件（App.vue） =====
  职责：应用最外层组件，仅渲染路由出口；全局基础样式在此定义。 -->
<template>
  <!-- 路由出口：根据当前路由渲染对应页面组件 -->
  <router-view />
</template>

<style>
/* 全局重置：清除默认外边距/内边距，统一盒模型 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}
/* 根元素撑满视口高度，并设置全局字体栈 */
html,
body,
#app {
  height: 100%;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Microsoft YaHei', Arial, sans-serif;
}
</style>
