import { createRouter, createWebHistory } from 'vue-router'
import Learning from '../Learning.vue'
import StudentLogin from '../StudentLogin.vue'

const routes = [
  { path: '/', redirect: '/course' },
  { path: '/login', component: StudentLogin, name: 'login' },
  { path: '/course', component: Learning, name: 'courses' },
  { path: '/course/:courseId', component: Learning, name: 'course' },
  { path: '/task/:taskId', component: Learning, name: 'task' },
  { path: '/score', component: Learning, name: 'score' },
  { path: '/achievements', component: Learning, name: 'achievements' },
  { path: '/notifications', component: Learning, name: 'notifications' },
  { path: '/profile', component: Learning, name: 'profile' },
  { path: '/profile/avatar', component: Learning, name: 'avatar' },
  { path: '/profile/feedback', component: Learning, name: 'feedback' },
  { path: '/profile/version', component: Learning, name: 'version' },
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
