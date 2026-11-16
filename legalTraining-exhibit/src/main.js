import { createApp, h } from 'vue'
import { RouterView } from 'vue-router'
import { createPinia } from 'pinia'
import router from './router'
import './styles/base.css'

/**
 * 工程 C：展厅展示端（网页版，不使用三维引擎）
 * 一个包、三种运行模式（immersive 长页 / lite 简版 / kiosk 终端），内容与接口完全共用。
 * 展区/展项全部以 HTML + CSS + 图片/音视频渲染，靠版式与滚动动效做沉浸感。
 * 只调用 /openapi/v1/**，不调内部接口。
 */
const app = createApp({ render: () => h(RouterView) })
app.use(createPinia())
app.use(router)
app.mount('#app')

// kiosk 模式禁用浏览器手势返回与长按菜单（TM-02-01）
if (location.pathname.startsWith('/kiosk')) {
  document.addEventListener('contextmenu', (e) => e.preventDefault())
  history.pushState(null, '', location.href)
  window.addEventListener('popstate', () => history.pushState(null, '', location.href))
}
