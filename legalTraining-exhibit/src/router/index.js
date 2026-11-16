import { createRouter, createWebHistory } from 'vue-router'
import Gallery from '../Gallery.vue'
import Kiosk from '../Kiosk.vue'

const routes = [
  { path: '/', redirect: '/immersive' },
  { path: '/immersive', name: 'immersive', component: Gallery },
  { path: '/immersive/zone/:zoneId', name: 'zone', component: Gallery },
  { path: '/immersive/item/:itemId', name: 'item', component: Gallery },
  { path: '/lite', name: 'lite', component: Gallery },
  { path: '/lite/zone/:zoneId', name: 'liteZone', component: Gallery },
  { path: '/lite/item/:itemId', name: 'liteItem', component: Gallery },
  { path: '/kiosk', name: 'kiosk', component: Kiosk },
  {
    path: '/s/:code',
    name: 'share',
    component: Gallery,
    async beforeEnter(to) {
      const code = String(to.params.code)
      if (!/^[A-Za-z0-9]{1,16}$/.test(code)) return { name: 'offline', query: { reason: 'link' }, replace: true }
      try {
        const api = import.meta.env.VITE_GALLERY_API || '/gallery-api'
        const response = await fetch(api + '/share/' + code, { cache: 'no-store' })
        if (!response.ok) throw new Error('Invalid share link')
        const { data } = await response.json()
        if (!['item', 'zone'].includes(data?.targetType) || !Number.isSafeInteger(data.targetId)) {
          throw new Error('Invalid share target')
        }
        return { path: '/lite/' + data.targetType + '/' + data.targetId, query: { channel: data.channel }, replace: true }
      } catch {
        return { name: 'offline', query: { reason: 'link' }, replace: true }
      }
    }
  },
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
