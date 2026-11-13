import { createRouter, createWebHistory } from 'vue-router'
import Gallery from '../Gallery.vue'

const routes = [
  { path: '/', redirect: '/immersive' },
  { path: '/immersive', name: 'immersive', component: Gallery },
  { path: '/immersive/zone/:zoneId', name: 'zone', component: Gallery },
  { path: '/immersive/item/:itemId', name: 'item', component: Gallery },
  { path: '/lite', name: 'lite', component: Gallery },
  { path: '/lite/item/:itemId', name: 'liteItem', component: Gallery },
  { path: '/kiosk', name: 'kiosk', component: Gallery },
  { path: '/offline', name: 'offline', component: Gallery },
  { path: '/:pathMatch(.*)*', redirect: '/immersive' }
]
const router = createRouter({
  history: createWebHistory(), routes, scrollBehavior: (_, __, saved) => saved || { top: 0 }
})
router.afterEach(to => {
  const mode = to.path.startsWith('/lite') || to.name === 'offline' ? 'lite'
    : to.name === 'kiosk' ? 'kiosk' : 'immersive'
  document.documentElement.dataset.mode = mode
  document.body.classList.remove('mode-lite', 'mode-kiosk', 'mode-immersive')
  document.body.classList.add('mode-' + mode)
})
export default router
