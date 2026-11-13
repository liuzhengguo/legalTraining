import { createApp, h } from 'vue'
import { RouterView } from 'vue-router'
import { createPinia } from 'pinia'
import Vant from 'vant'
import 'vant/lib/index.css'
import router from './router'
import './styles/base.css'

// 工程 B：学生移动端（E3），单入口，底部 Tab 导航
const app = createApp({ render: () => h(RouterView) })
app.use(createPinia())
app.use(router)
app.use(Vant)
app.mount('#app')
