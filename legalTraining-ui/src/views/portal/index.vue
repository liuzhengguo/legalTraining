<template>
  <main v-loading="loading" class="portal">
    <div class="portal-inner">
      <header class="portal-hero">
        <div class="portal-hero-content">
          <p class="portal-term"><i class="el-icon-date" aria-hidden="true" /> {{ termName }}</p>
          <h1>{{ accountName ? '你好，' + accountName : '管理工作台' }}</h1>
          <p v-if="portal && portal.account.deptName" class="portal-dept">{{ portal.account.deptName }}</p>
        </div>
        <router-link v-if="portal && canViewTodo" class="hero-action" to="/portal/todo">
          我的待办 <strong>{{ portal.todo.total }}</strong><i class="el-icon-arrow-right" aria-hidden="true" />
        </router-link>
      </header>

      <section v-if="portal && canViewTodo && todoItems.length" class="portal-section" aria-labelledby="todo-title">
        <div class="section-title">
          <h2 id="todo-title">待办概览</h2>
          <router-link to="/portal/todo">查看全部 <i class="el-icon-arrow-right" aria-hidden="true" /></router-link>
        </div>
        <div class="todo-grid">
          <router-link v-for="item in todoItems" :key="item.key" class="todo-card" :class="'tone-' + item.tone" to="/portal/todo">
            <span class="portal-icon"><i :class="item.icon" aria-hidden="true" /></span>
            <span>{{ item.label }}</span>
            <strong>{{ portal.todo[item.key] }}</strong>
          </router-link>
        </div>
      </section>

      <section v-if="cards.length" class="portal-section" aria-labelledby="modules-title">
        <div class="section-title"><h2 id="modules-title">业务入口</h2></div>
        <div class="module-grid">
          <article v-for="card in cards" :key="card.path" class="module-card" :class="'tone-' + card.tone">
            <div class="module-heading">
              <span class="portal-icon"><i :class="card.icon" aria-hidden="true" /></span>
              <h3>{{ card.title }}</h3>
            </div>
            <nav :aria-label="card.title">
              <router-link v-for="entry in card.entries" :key="entry.path" :to="entry.path">
                {{ entry.title }} <i class="el-icon-arrow-right" aria-hidden="true" />
              </router-link>
            </nav>
          </article>
        </div>
      </section>
      <el-alert v-if="loadError" class="portal-error" title="暂时无法加载待办和学期信息" type="warning" :closable="false" show-icon />
    </div>
  </main>
</template>

<script>
import request from '@/utils/request'
import auth from '@/plugins/auth'

const modules = [
  { path: '/admin', icon: 'el-icon-setting', tone: 'blue', preferred: ['/admin/org/term', '/admin/org/class', '/admin/org/student'] },
  { path: '/teacher', icon: 'el-icon-reading', tone: 'teal', preferred: ['/teacher/course', '/teacher/task', '/teacher/review'] },
  { path: '/exhibit', icon: 'el-icon-picture-outline', tone: 'amber', preferred: ['/exhibit/zone', '/exhibit/item', '/exhibit/publish'] }
]

const todoItems = [
  { key: 'review', label: '待批阅', icon: 'el-icon-edit-outline', tone: 'blue', permissions: ['edu:review:list', 'edu:task:list'] },
  { key: 'audit', label: '待审核', icon: 'el-icon-document-checked', tone: 'teal', permissions: ['exh:audit:list'] },
  { key: 'submit', label: '待提交', icon: 'el-icon-upload2', tone: 'amber', permissions: ['edu:submission:my'] },
  { key: 'authorize', label: '待授权', icon: 'el-icon-lock', tone: 'violet', permissions: ['edu:submission:my'] }
]

function menuPath(path, parent = '') {
  return (path.startsWith('/') ? path : parent + '/' + path).replace(/\/{2,}/g, '/').replace(/\/$/, '') || '/'
}

function leafRoutes(route, parent) {
  if (route.hidden || route.meta?.link || /^https?:/i.test(route.path)) return []
  const path = menuPath(route.path, parent)
  if (route.children?.length) return route.children.flatMap(child => leafRoutes(child, path))
  return route.meta?.title ? [{ path, title: route.meta.title }] : []
}

export default {
  name: 'BusinessPortal',
  data() { return { portal: null, loading: false, loadError: false } },
  computed: {
    accountName() {
      return this.portal?.account?.nickName || this.$store.getters.nickName
    },
    termName() {
      return this.portal?.term?.termName || '当前学期'
    },
    canViewTodo() { return auth.hasPermi('portal:todo:list') },
    todoItems() {
      return todoItems.filter(item => this.portal?.todo?.[item.key] > 0 || auth.hasPermiOr(item.permissions))
    },
    cards() {
      const routes = this.$store.getters.defaultRoutes
      return modules.flatMap(module => {
        const root = routes.find(route => !route.hidden && menuPath(route.path) === module.path)
        if (!root) return []
        const entries = leafRoutes(root).filter(entry => {
          const match = this.$router.match(entry.path)
          return match.path === entry.path && match.matched.length > 0
        })
        const preferred = module.preferred.map(path => entries.find(entry => entry.path === path)).filter(Boolean)
        const shortcuts = preferred.concat(entries.filter(entry => !preferred.includes(entry))).slice(0, 3)
        return shortcuts.length ? [{ ...module, title: root.meta.title, entries: shortcuts }] : []
      })
    }
  },
  async created() {
    this.loading = true
    try {
      this.portal = (await request({ url: '/portal/index' })).data
    } catch {
      this.loadError = true
    } finally {
      this.loading = false
    }
  }
}
</script>

<style lang="scss" scoped>
.portal {
  min-height: calc(100vh - 84px);
  padding: clamp(18px, 3.1vw, 44px);
  background: #fcf1e3;
  color: #461d16;
}
.portal-inner { max-width: 1320px; margin: 0 auto; }
.portal-hero {
  display: flex;
  position: relative;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 22px;
  min-height: 216px;
  padding: clamp(24px, 4vw, 50px);
  border-radius: 16px;
  color: #fff;
  background: linear-gradient(90deg, #511913 6%, #4b1612f2 37%, #651e1446 100%),
    #511913 var(--lt-exhibition-hall) right 51% / auto 160% no-repeat;
  box-shadow: 0 8px 26px #551b141a;
}
.portal-hero-content { min-width: 0; }
.portal-term { display: flex; align-items: center; gap: 8px; margin: 0 0 14px; color: #f4d5aa; font-size: 13px; }
.portal-hero h1 { margin: 0; font-size: clamp(25px, 3vw, 44px); font-weight: 750; letter-spacing: -.025em; overflow-wrap: anywhere; }
.portal-dept { margin: 12px 0 0; color: #f4d9b9; }
.hero-action { display: inline-flex; align-items: center; gap: 12px; padding: 11px 14px; border: 1px solid #ffffff58; border-radius: 8px; background: #5c1d16e6; font-size: 14px; }
.hero-action:hover { background: #842d22; }
.hero-action strong { color: #f4d5aa; font-size: 19px; }
.portal-section { margin-top: 28px; }
.section-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 13px; }
.section-title h2 { margin: 0; font-size: 19px; font-weight: 750; }
.section-title a { color: #963b2e; font-size: 13px; }
.todo-grid, .module-grid { display: grid; gap: 14px; }
.todo-grid { grid-template-columns: repeat(auto-fit, minmax(min(100%, 168px), 1fr)); }
.module-grid { grid-template-columns: repeat(auto-fit, minmax(min(100%, 245px), 1fr)); }
.todo-card, .module-card { border: 1px solid #e8d2bc; border-radius: 12px; background: #fff9ef; box-shadow: 0 3px 12px #551c140b; }
.todo-card { display: grid; grid-template-columns: auto 1fr; align-items: center; gap: 8px 11px; padding: 18px; color: #76574a; font-size: 14px; }
.todo-card:hover { border-color: var(--tone); }
.todo-card strong { grid-column: 2; font-size: 28px; color: #461d16; font-variant-numeric: tabular-nums; line-height: 1; }
.tone-blue { --tone: #8b3024; --tint: #f8e5d8; }
.tone-teal { --tone: #9a4f2d; --tint: #f8e8d9; }
.tone-amber { --tone: #8a5526; --tint: #fbf0e3; }
.tone-violet { --tone: #7d3d32; --tint: #f4e2d7; }
.portal-icon { display: grid; place-items: center; width: 38px; height: 38px; border-radius: 9px; background: var(--tint); color: var(--tone); font-size: 19px; flex: none; }
.todo-card .portal-icon { grid-row: span 2; }
.module-card { padding: 20px; }
.module-heading { display: flex; align-items: center; gap: 11px; margin-bottom: 13px; }
.module-heading h3 { margin: 0; font-size: 17px; font-weight: 700; }
.module-card nav a { display: flex; align-items: center; justify-content: space-between; gap: 8px; min-height: 40px; border-top: 1px solid #efdfcf; padding: 6px 2px; color: #724c3f; font-size: 14px; }
.module-card nav a:hover { color: var(--tone); background: var(--tint); }
.portal a:focus-visible { outline: 2px solid #963b2e; outline-offset: 3px; }
.portal-error { margin-top: 22px; }
@media (max-width: 540px) {
  .portal-hero { min-height: 175px; background-position: right center; }
  .todo-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .todo-card { padding: 11px; }
  .todo-card .portal-icon { width: 31px; height: 31px; }
}
</style>
