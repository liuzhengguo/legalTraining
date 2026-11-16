import { createRouter, createWebHistory } from 'vue-router'
import Learning from '../Learning.vue'

const routes = [
  { path: '/', redirect: '/course' },
  { path: '/login', component: Learning, name: 'login' },
  { path: '/course', component: Learning, name: 'courses' },
  { path: '/course/:courseId', component: Learning, name: 'course' },
  { path: '/task/:taskId', component: Learning, name: 'task' },
  { path: '/score', component: Learning, name: 'score' },
  { path: '/achievements', component: Learning, name: 'achievements' },
  { path: '/notifications', component: Learning, name: 'notifications' },
  { path: '/:pathMatch(.*)*', redirect: '/course' }
]
const router = createRouter({ history: createWebHistory(), routes, scrollBehavior: () => ({ top: 0 }) })
router.beforeEach(to => {
  if (to.name !== 'login' && !localStorage.getItem('lt_token')) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
})
window.addEventListener('lt-session-expired', () => {
  router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
})
export default router
