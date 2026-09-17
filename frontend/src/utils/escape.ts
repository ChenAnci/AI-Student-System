/**
 * 轻量 HTML 转义：用于 ECharts tooltip 等以 HTML 渲染的字符串插值，
 * 防止来自数据库的名称（课程名/院系名等）被当作 HTML 注入执行（存储型 XSS，S-8）。
 */
export function escapeHtml(value: unknown): string {
  return String(value ?? '').replace(/[&<>"']/g, (ch) => {
    const map: Record<string, string> = {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    }
    return map[ch]
  })
}
