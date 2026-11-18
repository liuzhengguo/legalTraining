import { createRouter, createWebHistory } from 'vue-router'
import Learning from '../Learning.vue'
import GalleryHome from '../GalleryHome.vue'
import GalleryExperience from '../GalleryExperience.vue'
import StudentLogin from '../StudentLogin.vue'
import { consumeSsoTicket } from '../sso'

const routes = [
  { path: '/', redirect: '/home' },
  { path: '/home', component: GalleryHome, name: 'home' },
  { path: '/gallery', component: GalleryExperience, name: 'galleryGuide' },
  { path: '/gallery/zone/:zoneId', component: GalleryExperience, name: 'galleryZone' },
  { path: '/gallery/item/:itemId', component: GalleryExperience, name: 'galleryItem' },
  { path: '/gallery/hotspots', component: GalleryExperience, name: 'galleryHotspots' },
  { path: '/gallery/boards', component: GalleryExperience, name: 'galleryBoards' },
  { path: '/login', component: StudentLogin, name: 'login' },
  { path: '/course', component: Learning, name: 'courses' },
  { path: '/course/:courseId', component: Learning, name: 'course' },
  { path: '/task/:taskId', component: Learning, name: 'task' },
  { path: '/score', component: Learning, name: 'score' },
  { path: '/achievements', component: Learning, name: 'achievements' },
  { path: '/assistant', component: Learning, name: 'assistant' },
  { path: '/notifications', component: Learning, name: 'notifications' },
  { path: '/profile', component: Learning, name: 'profile' },
  { path: '/profile/avatar', component: Learning, name: 'avatar' },
  { path: '/profile/feedback', component: Learning, name: 'feedback' },
  { path: '/profile/version', component: Learning, name: 'version' },
  { path: '/:pathMatch(.*)*', redirect: '/course' }
]
const router = createRouter({ history: createWebHistory(), routes, scrollBehavior: () => ({ top: 0 }) })
router.beforeEach(async to => {
  const query = { ...to.query }
  if (typeof query.sso === 'string') {
    delete query.sso
    const clean = { path: to.path, query, hash: to.hash, replace: true }
    const signedIn = await consumeSsoTicket()
    if (!signedIn) {
      const redirect = to.path + (Object.keys(query).length ? '?' + new URLSearchParams(query).toString() : '')
      return { name: 'login', query: { redirect, reason: 'sso' }, replace: true }
    }
    return clean
  }
  if (to.name !== 'login' && !localStorage.getItem('lt_token')) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
})
window.addEventListener('lt-session-expired', () => {
  router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
})
export default router
