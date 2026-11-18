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
          <template v-for="item in todoItems">
            <a v-if="isStudentTodo(item)" :key="item.key" class="todo-card" :class="'tone-' + item.tone"
               :href="studentUrl(item.path)" @click.prevent="openStudent(item.path)">
              <span class="portal-icon"><i :class="item.icon" aria-hidden="true" /></span>
              <span>{{ item.label }}</span>
              <strong>{{ portal.todo[item.key] }}</strong>
            </a>
            <router-link v-else :key="item.key" class="todo-card" :class="'tone-' + item.tone" :to="item.path">
              <span class="portal-icon"><i :class="item.icon" aria-hidden="true" /></span>
              <span>{{ item.label }}</span>
              <strong>{{ portal.todo[item.key] }}</strong>
            </router-link>
          </template>
        </div>
      </section>

      <section v-if="notices.length" class="portal-section" aria-labelledby="notice-title">
        <div class="section-title">
          <h2 id="notice-title">最新公告</h2>
          <router-link v-if="canManageNotice" to="/system/notice">全部公告 <i class="el-icon-arrow-right" aria-hidden="true" /></router-link>
        </div>
        <ul class="notice-list">
          <li v-for="notice in notices" :key="notice.noticeId">
            <span class="notice-tag" :class="notice.type === '2' ? 'notice-tag--public' : 'notice-tag--info'">
              {{ notice.type === '2' ? '公告' : '通知' }}
            </span>
            <span class="notice-title">{{ notice.title }}</span>
            <time>{{ notice.publishDate }}</time>
          </li>
        </ul>
      </section>

      <section class="portal-section" aria-labelledby="service-title">
        <div class="section-title"><h2 id="service-title">服务入口</h2></div>
        <div class="service-grid">
          <a v-for="link in serviceLinks" :key="link.key" class="service-card" :href="link.url"
             target="_blank" rel="noopener noreferrer">
            <span class="portal-icon"><i :class="link.icon" aria-hidden="true" /></span>
            <span class="service-copy"><strong>{{ link.label }}</strong><small>{{ link.hint }}</small></span>
            <i class="el-icon-arrow-right" aria-hidden="true" />
          </a>
          <button v-if="!links.help" class="service-card" type="button" @click="helpVisible = true">
            <span class="portal-icon"><i class="el-icon-question" aria-hidden="true" /></span>
            <span class="service-copy"><strong>使用帮助</strong><small>常见问题与操作指引</small></span>
            <i class="el-icon-arrow-right" aria-hidden="true" />
          </button>
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
      <el-dialog title="使用帮助" :visible.sync="helpVisible" width="520px">
        <ul class="help-list">
          <li>待批阅、待审核可直接进入对应处理页面；待提交、待授权会跳转到学生端对应栏目。</li>
          <li>课程资料请在教师工作室的“资源管理”投放，学生刷新课程后即可看到。</li>
          <li>展项素材与发布状态请在“展厅内容”查看，发布前建议先执行预检。</li>
          <li>外部 AI 与案例库入口由学校配置；若未显示，请联系管理员配置系统参数。</li>
        </ul>
      </el-dialog>
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
  { key: 'review', label: '待批阅', icon: 'el-icon-edit-outline', tone: 'blue', path: '/teacher/review', permissions: ['edu:review:list', 'edu:task:list'] },
  { key: 'audit', label: '待审核', icon: 'el-icon-document-checked', tone: 'teal', path: '/exhibit/audit', permissions: ['exh:audit:list'] },
  { key: 'submit', label: '待提交', icon: 'el-icon-upload2', tone: 'amber', path: '/course', permissions: ['student:task:list'] },
  { key: 'authorize', label: '待授权', icon: 'el-icon-lock', tone: 'violet', path: '/achievements', permissions: ['student:rec:list'] }
]

const studentTodoPaths = { submit: '/course', authorize: '/achievements' }

const serviceLinks = [
  { key: 'help', label: '使用帮助', hint: '平台操作与常见问题', icon: 'el-icon-question' },
  { key: 'ai', label: 'AI 法律咨询', hint: '外部智能法律助手', icon: 'el-icon-chat-dot-round' },
  { key: 'case', label: '外部案例库', hint: '扩展案例检索', icon: 'el-icon-collection' }
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

function externalUrl(value) {
  if (typeof value !== 'string' || !value.trim()) return ''
  try {
    const url = new URL(value.trim(), window.location.origin)
    return ['http:', 'https:'].includes(url.protocol) ? url.href : ''
  } catch {
    return ''
  }
}

export default {
  name: 'BusinessPortal',
  data() { return { portal: null, loading: false, loadError: false, helpVisible: false, ssoBusy: false } },
  computed: {
    accountName() {
      return this.portal?.account?.nickName || this.$store.getters.nickName
    },
    termName() {
      return this.portal?.term?.termName || '当前学期'
    },
    notices() {
      return Array.isArray(this.portal?.notices) ? this.portal.notices : []
    },
    links() {
      return this.portal?.links || {}
    },
    serviceLinks() {
      return serviceLinks.filter(item => externalUrl(this.links[item.key])).map(item => ({
        ...item, url: externalUrl(this.links[item.key])
      }))
    },
    canViewTodo() { return auth.hasPermi('portal:todo:list') },
    canManageNotice() { return auth.hasPermi('system:notice:list') },
    todoItems() {
      const labels = this.portal?.todoLabels || []
      return todoItems.filter(item => this.portal?.todo?.[item.key] > 0 || auth.hasPermiOr(item.permissions))
        .map(item => {
          const label = labels.find(entry => entry.key === item.key)
          return {
            ...item,
            label: label?.name || item.label,
            path: studentTodoPaths[item.key] || label?.path || item.path
          }
        })
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
  methods: {
    isStudentTodo(item) { return !!studentTodoPaths[item.key] },
    studentBase() {
      return (process.env.VUE_APP_STUDENT_URL
        || (process.env.NODE_ENV === 'development' ? 'http://localhost:5173' : '')).replace(/\/$/, '')
    },
    studentUrl(path) {
      const base = this.studentBase()
      return base ? base + (path.startsWith('/') ? path : '/' + path) : ''
    },
    async openStudent(path) {
      if (this.ssoBusy) return
      if (!this.studentBase()) {
        this.$message.warning('尚未配置学生端地址')
        return
      }
      this.ssoBusy = true
      try {
        const result = await request({ url: '/portal/sso/student-ticket' })
        const ticket = result.ticket || result.data?.ticket
        if (!ticket) throw new Error('ticket missing')
        const target = this.studentUrl(path)
        window.location.href = target + (target.includes('?') ? '&' : '?') + 'sso=' + encodeURIComponent(ticket)
      } catch {
        this.$message.error('无法进入学生端，请确认使用学生账号登录')
      } finally {
        this.ssoBusy = false
      }
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
  background: #f4f6fa;
  color: #303133;
}
.portal-inner { max-width: 1320px; margin: 0 auto; }
.portal-hero {
  display: flex;
  position: relative;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 22px;
  min-height: 196px;
  padding: clamp(26px, 4vw, 46px);
  border-radius: 16px;
  color: #fff;
  overflow: hidden;
  background: linear-gradient(120deg, #7d2a20 0%, #a23c2b 55%, #c2593d 100%);
  box-shadow: 0 10px 30px rgba(141, 48, 37, .18);
}
.portal-hero::before {
  content: '';
  position: absolute;
  top: -140px; right: -70px;
  width: 400px; height: 400px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255,255,255,.18), rgba(255,255,255,0) 68%);
  pointer-events: none;
}
.portal-hero::after {
  content: '';
  position: absolute;
  bottom: -170px; right: 130px;
  width: 320px; height: 320px;
  border: 1px solid rgba(255,255,255,.14);
  border-radius: 50%;
  pointer-events: none;
}
.portal-hero-content, .hero-action { position: relative; z-index: 1; }
.portal-hero-content { min-width: 0; }
.portal-term { display: flex; align-items: center; gap: 8px; margin: 0 0 14px; color: rgba(255,255,255,.78); font-size: 13px; }
.portal-hero h1 { margin: 0; font-size: clamp(25px, 3vw, 42px); font-weight: 700; letter-spacing: -.02em; overflow-wrap: anywhere; }
.portal-dept { margin: 12px 0 0; color: rgba(255,255,255,.82); }
.hero-action { display: inline-flex; align-items: center; gap: 12px; padding: 11px 16px; border: 1px solid rgba(255,255,255,.4); border-radius: 9px; background: rgba(255,255,255,.14); font-size: 14px; }
.hero-action:hover { background: rgba(255,255,255,.26); }
.hero-action strong { color: #ffe1b8; font-size: 19px; }
.portal-section { margin-top: 26px; }
.section-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 13px; }
.section-title h2 { margin: 0; font-size: 17px; font-weight: 650; color: #2b2f36; }
.section-title a { color: #8d3025; font-size: 13px; }
.todo-grid, .module-grid { display: grid; gap: 14px; }
.todo-grid { grid-template-columns: repeat(auto-fit, minmax(min(100%, 168px), 1fr)); }
.module-grid { grid-template-columns: repeat(auto-fit, minmax(min(100%, 245px), 1fr)); }
.todo-card, .module-card, .notice-list, .service-card {
  border: 1px solid #ebeef5;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 2px 12px rgba(17, 24, 39, .04);
}
.todo-card, .module-card { transition: box-shadow .2s, transform .2s, border-color .2s; }
.todo-card:hover, .module-card:hover { transform: translateY(-2px); box-shadow: 0 8px 22px rgba(17, 24, 39, .09); border-color: var(--tone); }
.todo-card { display: grid; grid-template-columns: auto 1fr; align-items: center; gap: 8px 11px; padding: 18px; color: #606266; font-size: 14px; }
.todo-card strong { grid-column: 2; font-size: 28px; color: #2b2f36; font-variant-numeric: tabular-nums; line-height: 1; }
.tone-blue { --tone: #3b82f6; --tint: #eaf2ff; }
.tone-teal { --tone: #10a37f; --tint: #e6f7f1; }
.tone-amber { --tone: #e8912d; --tint: #fff3e2; }
.tone-violet { --tone: #8b6fd6; --tint: #f1ecfb; }
.portal-icon { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 10px; background: var(--tint); color: var(--tone); font-size: 19px; flex: none; }
.todo-card .portal-icon { grid-row: span 2; }
.module-card { padding: 20px; }
.module-heading { display: flex; align-items: center; gap: 11px; margin-bottom: 13px; }
.module-heading h3 { margin: 0; font-size: 16px; font-weight: 650; color: #2b2f36; }
.module-card nav a { display: flex; align-items: center; justify-content: space-between; gap: 8px; min-height: 40px; border-top: 1px solid #f0f2f5; padding: 6px 2px; color: #606266; font-size: 14px; transition: color .15s, background .15s; }
.module-card nav a:hover { color: var(--tone); background: var(--tint); }
.portal a:focus-visible, .portal button:focus-visible { outline: 2px solid #8d3025; outline-offset: 3px; }
.portal-error { margin-top: 22px; }
.notice-list { margin: 0; padding: 0; list-style: none; overflow: hidden; }
.notice-list li { display: flex; align-items: center; gap: 12px; min-height: 46px; padding: 9px 16px; border-top: 1px solid #f0f2f5; font-size: 14px; }
.notice-list li:first-child { border-top: 0; }
.notice-list li:hover { background: #fafbfd; }
.notice-tag { flex: none; padding: 2px 9px; border-radius: 10px; font-size: 12px; }
.notice-tag--public { background: #fdeceb; color: #c0392b; }
.notice-tag--info { background: #eaf2ff; color: #3b82f6; }
.notice-title { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #303133; }
.notice-list time { flex: none; color: #909399; font-size: 12px; font-variant-numeric: tabular-nums; }
.service-grid { display: grid; gap: 14px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 220px), 1fr)); }
.service-card { display: flex; align-items: center; gap: 12px; width: 100%; min-height: 76px; padding: 14px 16px; color: #606266; font: inherit; text-align: left; cursor: pointer; transition: border-color .15s, background .15s; }
.service-card:hover { border-color: #8d3025; background: #fff8f6; }
.service-copy { display: grid; flex: 1; min-width: 0; gap: 3px; }
.service-copy strong { color: #2b2f36; font-size: 15px; }
.service-copy small { color: #909399; font-size: 12px; }
.help-list { margin: 0; padding-left: 20px; color: #606266; line-height: 1.9; }
@media (max-width: 540px) {
  .portal-hero { min-height: 168px; }
  .todo-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .todo-card { padding: 11px; }
  .todo-card .portal-icon { width: 34px; height: 34px; }
}
</style>
