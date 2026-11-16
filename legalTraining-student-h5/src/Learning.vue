<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { request } from './api'
import LinkedCases from './LinkedCases.vue'

const route = useRoute()
const router = useRouter()
const data = ref(null)
const busy = ref(false)
const loading = ref(false)
const error = ref('')
const message = ref('')
const captcha = ref(null)
const login = ref({ username: '', password: '', code: '' })
const content = ref('')
const teamInfo = ref(null)
const editors = ref([])
const draftFocused = ref(false)

watch(() => [route.params.taskId, teamInfo.value?.team?.confirmed, draftFocused.value], ([taskId, confirmed, focused], _, cleanup) => {
  if (!taskId || !confirmed) {
    editors.value = []
    return
  }
  const base = '/edu/submission/team/' + taskId
  let active = true
  const refresh = async () => {
    try {
      const result = await request(base + (focused ? '/editing?editing=true' : '/editors'),
        focused ? { method: 'POST' } : {})
      if (active) editors.value = result.data || []
    } catch { /* Presence refresh is independent of the draft. */ }
  }
  refresh()
  const timer = setInterval(refresh, 15000)
  cleanup(() => {
    active = false
    clearInterval(timer)
    if (focused) request(base + '/editing?editing=false', { method: 'POST' }).catch(() => {})
  })
}, { flush: 'post' })
const teamName = ref('')
const invitation = ref('')
const contribution = ref('')
const files = ref([])
const lateReason = ref('')
const appeals = ref({})
const appealScoreId = ref(null)
const appealReason = ref('')
const appealStatuses = ['待处理', '维持成绩', '已更正']
const preview = ref(null)
const authorization = ref({ signMode: '完全匿名', scope: '仅展厅内' })
const galleryBase = (import.meta.env.VITE_GALLERY_BASE || (import.meta.env.DEV ? 'http://localhost:5174' : '')).replace(/\/$/, '')
function galleryUrl(entry) {
  return galleryBase + '/immersive/' + entry.targetType + '/' + entry.targetId
}
const achievementStatuses = ['待授权', '待审核', '审核通过', '审核驳回', '已撤回或拒绝', '已生成展项']
const statuses = ['未提交', '草稿', '已提交', '已批阅', '已退回', '逾期未交', '任务取消']
const lateStatuses = ['', '待审批', '已通过', '已驳回']
const navigation = [
  { to: '/course', title: '我的课程', routes: ['courses', 'course', 'task'] },
  { to: '/score', title: '我的成绩', routes: ['score'] },
  { to: '/achievements', title: '我的成果', routes: ['achievements'] },
  { to: '/notifications', title: '通知', routes: ['notifications'] }
]
const activePage = computed(() => navigation.find(entry => entry.routes.includes(route.name)))
const clock = ref(Date.now())
const deadlineTimer = setInterval(() => { clock.value = Date.now() }, 1000)
onUnmounted(() => clearInterval(deadlineTimer))
const countdown = computed(() => {
  const deadline = data.value?.task?.deadline
  if (!deadline || data.value.task.status !== 1) return ''
  const remaining = new Date(deadline.replace(' ', 'T')).getTime() - clock.value
  if (!Number.isFinite(remaining)) return ''
  if (remaining <= 0) return '已到截止时间'
  const minutes = Math.ceil(remaining / 60000)
  const days = Math.floor(minutes / 1440)
  const hours = Math.floor(minutes % 1440 / 60)
  return '距截止 ' + (days ? days + ' 天 ' : '') + (hours ? hours + ' 小时 ' : '') + minutes % 60 + ' 分钟'
})
const unreadCount = computed(() => route.name === 'notifications' && Array.isArray(data.value)
  ? data.value.filter(entry => entry.isRead === '0').length : 0)
const pageHints = {
  courses: '进入课程查看实训任务、提交要求和学习资料。',
  score: '查看已发布的实训成绩和复核进度。',
  achievements: '查看教师推荐、展示授权和审核进度。',
  notifications: '查看任务安排、批阅结果和成果动态。'
}

function draftEditable() {
  const submission = data.value.submission
  if (submission.submittable) return true
  const status = data.value.task.status
  return Boolean(teamInfo.value?.team?.confirmed && [0, 1].includes(submission.status) && ![0, 3, 4].includes(status))
}


watch(() => route.fullPath, async (_, __, onCleanup) => {
  let active = true
  onCleanup(() => { active = false })
  data.value = null
  teamInfo.value = null
  draftFocused.value = false
  teamName.value = ''
  invitation.value = ''
  contribution.value = ''
  closePreview()
  error.value = ''
  message.value = ''
  lateReason.value = ''
  appealScoreId.value = null
  appealReason.value = ''
  loading.value = true
  try {
    const endpoints = {
      courses: '/edu/learning/courses',
      course: '/edu/learning/courses/' + route.params.courseId,
      task: '/edu/learning/tasks/' + route.params.taskId,
      score: '/edu/score/my',
      achievements: '/edu/rec/my',
      notifications: '/edu/learning/notifications'
    }
    const result = route.name === 'login'
      ? await request('/captchaImage', { authenticated: false })
      : await request(endpoints[route.name])
    if (!active) return
    if (route.name === 'score') {
      const result = await request('/edu/score/appeals/my')
      if (!active) return
      appeals.value = Object.fromEntries(result.data.map(appeal => [appeal.scoreId, appeal]))
    }
    if (route.name === 'login') captcha.value = result
    else data.value = result.data
    if (route.name === 'task') {
      syncSubmission(result.data.submission)
      if (result.data.task.finishMode) {
        const team = await request('/edu/submission/team/' + result.data.task.taskId)
        if (active) updateTeam(team.data)
      }
    }
  } catch (cause) {
    if (active) error.value = cause.message
  } finally {
    if (active) loading.value = false
  }
}, { immediate: true })

function syncSubmission(submission) {
  data.value.submission = submission
  content.value = submission.content || ''
  files.value = submission.files || []
}

async function run(action) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    await action()
  } catch (cause) {
    error.value = cause.message
  } finally {
    busy.value = false
  }
}

function submitAppeal(scoreId) {
  return run(async () => {
    await request('/edu/score/' + scoreId + '/appeals', {
      method: 'POST', body: { reason: appealReason.value.trim() }
    })
    if (route.name !== 'score') return
    appeals.value = { ...appeals.value, [scoreId]: { status: 0 } }
    appealScoreId.value = null
    appealReason.value = ''
    message.value = '成绩异议已提交'
  })
}

function updateTeam(info) {
  teamInfo.value = info
  contribution.value = info.members.find(member => member.userId === info.selfId)?.contribution || ''
}

function teamAction(suffix, body, method = 'POST') {
  return run(async () => {
    const taskId = data.value.task.taskId
    const base = '/edu/submission/team/' + taskId
    const result = await request(base + suffix, { method, body })
    const info = result.data || (await request(base)).data
    if (route.params.taskId !== String(taskId)) return
    const submission = await request('/edu/submission/my/' + taskId)
    if (route.params.taskId !== String(taskId)) return
    updateTeam(info)
    syncSubmission(submission.data)
    message.value = suffix === '/contribution' ? '贡献说明已保存' : '组队信息已更新'
  })
}

function signIn() {
  return run(async () => {
    let result
    try {
      result = await request('/login', {
        method: 'POST', authenticated: false, body: { ...login.value, uuid: captcha.value?.uuid }
      })
    } catch (cause) {
      captcha.value = await request('/captchaImage', { authenticated: false })
      login.value.code = ''
      throw cause
    }
    localStorage.setItem('lt_token', result.token)
    const destination = route.query.redirect
    await router.replace(typeof destination === 'string' && /^\/(course|task|score|achievements|notifications)(\/|\?|$)/.test(destination) ? destination : '/course')
  })
}

function signOut() {
  return run(async () => {
    await request('/logout', { method: 'POST' })
    localStorage.removeItem('lt_token')
    await router.replace('/login')
  })
}

function save(action) {
  if (action === 'submit' && !window.confirm('确认正式提交作业及当前附件？')) return
  return run(async () => {
    const taskId = data.value.task.taskId
    await request('/edu/submission/' + action, { method: 'POST', body: { taskId, content: content.value, files: files.value } })
    if (route.params.taskId === String(taskId)) {
      syncSubmission((await request('/edu/submission/my/' + taskId)).data)
      message.value = action === 'draft' ? '草稿已保存' : '作业已提交'
    }
  })
}

function downloadReceipt() {
  return run(async () => {
    const submission = data.value.submission
    const receipt = await request('/edu/submission/my/' + route.params.taskId + '/receipt', { binary: true })
    const href = URL.createObjectURL(receipt)
    const link = document.createElement('a')
    link.href = href
    link.download = 'receipt-' + submission.submissionId + '.json'
    link.click()
    setTimeout(() => URL.revokeObjectURL(href), 1000)
  })
}

function update(action, query = '') {
  return run(async () => {
    const taskId = data.value.task.taskId
    const target = action === 'late-apply' ? 'task/' + taskId : data.value.submission.submissionId
    await request('/edu/submission/' + target + '/' + action + query, { method: 'POST' })
    if (route.params.taskId === String(taskId)) {
      syncSubmission((await request('/edu/submission/my/' + taskId)).data)
      message.value = action === 'withdraw' ? '作业已撤回' : '补交申请已提交'
    }
  })
}

function upload(event) {
  const selected = event.target.files[0]
  event.target.value = ''
  if (!selected) return
  const taskId = data.value.task.taskId
  return run(async () => {
    const form = new FormData()
    form.append('file', selected)
    const result = await request('/common/upload', { method: 'POST', body: form })
    if (route.params.taskId !== String(taskId)) return
    files.value.push({
      fileId: result.fileName, fileName: selected.name, fileSize: selected.size,
      fileExt: selected.name.split('.').pop().toLowerCase(), mimeType: selected.type
    })
  })
}

function closePreview() {
  if (preview.value) URL.revokeObjectURL(preview.value.url)
  preview.value = null
}

function decideAchievement(id, action, body) {
  return run(async () => {
    await request('/edu/rec/' + id + '/' + action, { method: 'POST', body })
    if (route.name === 'achievements') data.value = (await request('/edu/rec/my')).data
    message.value = '展示授权状态已更新'
  })
}

function markRead(notification) {
  return run(async () => {
    await request('/edu/learning/notifications/' + notification.id + '/read', { method: 'PUT' })
    notification.isRead = '1'
  })
}

function openResource(resource, download = false) {
  const courseId = route.params.courseId
  return run(async () => {
    const blob = await request('/edu/learning/courses/' + courseId + '/resources/' + resource.resourceId + '?download=' + download, { binary: true })
    if (route.params.courseId !== courseId) return
    const url = URL.createObjectURL(blob)
    if (download) {
      const link = document.createElement('a')
      link.href = url
      link.download = resource.fileName || resource.title
      link.click()
      setTimeout(() => URL.revokeObjectURL(url), 30000)
    } else {
      closePreview()
      preview.value = { url, title: resource.title, type: blob.type }
    }
  })
}
</script>

<template>
  <div class="learning" :class="{ 'learning--login': route.name === 'login' }">
    <header class="workspace-nav">
      <RouterLink class="brand" to="/course">
        <span class="brand-mark" aria-hidden="true">法</span>
        <span>法律实训<small>学生端</small></span>
      </RouterLink>
      <nav v-if="activePage" class="primary-nav" aria-label="学生端导航">
        <RouterLink v-for="entry in navigation" :key="entry.to" :to="entry.to"
          :class="{ selected: entry === activePage }" :aria-current="entry === activePage ? 'location' : undefined">
          {{ entry.title }}
        </RouterLink>
      </nav>
      <button v-if="activePage" class="logout secondary" type="button" :disabled="busy" @click="signOut">退出登录</button>
    </header>
    <main class="page workspace-main" :aria-busy="loading || busy">
    <header v-if="pageHints[route.name]" class="page-banner">
      <div><h1>{{ activePage.title }}</h1><p>{{ pageHints[route.name] }}</p></div>
      <span v-if="unreadCount" class="banner-count">{{ unreadCount }} 条未读</span>
    </header>
    <p v-if="error" class="card error" role="alert">{{ error }}</p>
    <p v-if="message" class="card" role="status">{{ message }}</p>
    <p v-if="loading" class="card" role="status">加载中…</p>
    <form v-if="route.name === 'login'" class="card fields" @submit.prevent="signIn">
      <h1>登录学生端</h1>
      <label>登录账号 <input v-model.trim="login.username" autocomplete="username" required></label>
      <label>密码 <input v-model="login.password" type="password" autocomplete="current-password" required></label>
      <label v-if="captcha?.captchaEnabled">验证码
        <img :src="'data:image/png;base64,' + captcha.img" alt="登录验证码">
        <input v-model.trim="login.code" autocomplete="off" required>
      </label>
      <button :disabled="busy || loading || !captcha">登录</button>
    </form>
    <section v-else-if="data && route.name === 'courses'" aria-label="课程列表">
      <p v-if="!data.length" class="card empty">暂无课程，课程发布并分配到你的班级后会显示在这里。</p>
      <div v-else class="course-grid">
        <RouterLink v-for="course in data" :key="course.courseId" :to="'/course/' + course.courseId" class="card course-card">
          <h2>{{ course.courseName }}</h2>
          <p v-if="course.intro">{{ course.intro }}</p>
          <span class="course-cta">进入课程 <span aria-hidden="true">→</span></span>
        </RouterLink>
      </div>
    </section>
    <section v-else-if="data && route.name === 'achievements'">
      <p v-if="!data.length" class="card empty">暂无推荐成果</p>
      <article v-for="item in data" :key="item.recId" class="card fields">
        <h3>{{ item.title }}</h3>
        <p>{{ item.zoneName }} · {{ achievementStatuses[item.status] }}</p>
        <p>{{ item.reason }}</p>
        <p v-if="item.opinion">审核意见 {{ item.opinion }}</p>
        <p v-if="item.exhibitStatus === 2">展项已发布</p>
        <p v-if="item.exhibitStatus === 3">展项已下架</p>
        <template v-if="item.status === 0">
          <label>署名方式
            <select v-model="authorization.signMode">
              <option>实名</option><option>部分匿名</option><option>完全匿名</option>
            </select>
          </label>
          <label>授权范围
            <select v-model="authorization.scope"><option>仅展厅内</option><option>含对外分享</option></select>
          </label>
          <nav>
            <button :disabled="busy" @click="decideAchievement(item.recId, 'authorize', { ...authorization, accept: true })">同意展示</button>
            <button :disabled="busy" @click="decideAchievement(item.recId, 'authorize', { accept: false })">拒绝授权</button>
          </nav>
        </template>
        <template v-else-if="[1, 2, 3, 5].includes(item.status)">
          <p>{{ item.signMode }} · {{ item.scopeDesc }}</p>
          <button :disabled="busy" @click="decideAchievement(item.recId, 'revoke')">撤回授权</button>
        </template>
      </article>
    </section>
    <section v-else-if="data && route.name === 'notifications'">
      <p v-if="!data.length" class="card empty">暂无通知</p>
      <article v-for="notification in data" :key="notification.id" class="card notification"
        :class="{ unread: notification.isRead === '0' }">
        <div class="notification-title">
          <h2>{{ notification.title }}</h2>
          <span v-if="notification.isRead === '0'" class="unread-label">未读</span>
        </div>
        <p>{{ notification.content }}</p>
        <p class="muted">{{ notification.sendTime }}</p>
        <nav v-if="notification.isRead === '0' || ['NT-01', 'NT-02', 'NT-03', 'NT-06', 'NT-07', 'SCORE-APPEAL'].includes(notification.eventCode) || notification.bizType === 'rec'" aria-label="通知操作">
          <RouterLink v-if="notification.bizType === 'rec'" to="/achievements">查看成果</RouterLink>
          <RouterLink v-if="notification.bizType === 'task' && ['NT-01', 'NT-02'].includes(notification.eventCode)" :to="'/task/' + notification.bizId">查看任务</RouterLink>
          <RouterLink v-if="notification.eventCode === 'NT-03'" :to="'/task/' + notification.bizId">查看提交和回执</RouterLink>
          <RouterLink v-if="['NT-06', 'NT-07', 'SCORE-APPEAL'].includes(notification.eventCode)" to="/score">查看成绩</RouterLink>
          <button v-if="notification.isRead === '0'" class="secondary" :disabled="busy" @click="markRead(notification)">标为已读</button>
        </nav>
      </article>
    </section>
    <section v-else-if="data && route.name === 'course'" class="card">
      <h2>{{ data.courseName }}</h2>
      <LinkedCases :endpoint="'/edu/learning/courses/' + route.params.courseId + '/cases'" />
      <section v-if="data.exhibits?.length">
        <h3>延伸学习</h3>
        <ul>
          <li v-for="entry in data.exhibits" :key="entry.targetType + ':' + entry.targetId">
            <a :href="galleryUrl(entry)" target="_blank" rel="noopener">{{ entry.title }}</a>
          </li>
        </ul>
      </section>
      <p class="multiline">{{ data.intro }}</p>
      <h3>任务</h3>
      <p v-if="!data.tasks.length">暂无任务</p>
      <ul class="record-list"><li v-for="task in data.tasks" :key="task.taskId">
        <RouterLink :to="'/task/' + task.taskId">{{ task.title }}</RouterLink>
        <span class="muted">截止 {{ task.deadline }} · {{ statuses[task.submitStatus] }}</span>
      </li></ul>
      <h3>课程资源</h3>
      <p v-if="!data.resources.length">暂无资源</p>
      <ul><li v-for="resource in data.resources" :key="resource.resourceId">
        {{ resource.title }}
        <a v-if="resource.resType === 'link' && /^https?:\/\//i.test(resource.externalUrl)" :href="resource.externalUrl" target="_blank" rel="noopener noreferrer">打开资源</a>
        <template v-else-if="resource.resType !== 'link'">
          <button v-if="resource.previewable" :disabled="busy" @click="openResource(resource)">预览</button>
          <button v-if="resource.allowDownload === '1'" :disabled="busy" @click="openResource(resource, true)">下载</button>
        </template>
      </li></ul>
      <section v-if="preview">
        <h3>{{ preview.title }}</h3>
        <button @click="closePreview">关闭预览</button>
        <img v-if="preview.type.startsWith('image/')" :src="preview.url" :alt="preview.title">
        <video v-else-if="preview.type.startsWith('video/')" :src="preview.url" controls></video>
        <audio v-else-if="preview.type.startsWith('audio/')" :src="preview.url" controls></audio>
        <iframe v-else :src="preview.url" :title="preview.title" sandbox></iframe>
      </section>
    </section>
    <section v-else-if="data && route.name === 'task'" class="card fields">
      <RouterLink :to="'/course/' + data.task.courseId">返回课程</RouterLink>
      <h2>{{ data.task.title }}</h2>
      <p class="multiline">{{ data.task.requirement }}</p>
      <LinkedCases :endpoint="'/edu/learning/tasks/' + route.params.taskId + '/cases'" />
      <p>截止时间 {{ data.task.deadline }} · {{ statuses[data.submission.status] }}</p>
      <p v-if="countdown" class="muted">{{ countdown }}</p>
      <section aria-label="评分规则">
        <h3>评分规则</h3>
        <p>{{ ['百分制', '等级制', '多维度加权'][data.task.scoreMode] }} · 满分 {{ data.task.totalScore }}</p>
        <ul v-if="data.task.scoreItems?.length">
          <li v-for="item in data.task.scoreItems" :key="item.itemId">{{ item.itemName }} · 满分 {{ item.maxScore }}</li>
        </ul>
      </section>
      <section v-if="data.exhibits?.length" aria-label="关联展厅内容">
        <h3>关联展厅内容</h3>
        <ul><li v-for="entry in data.exhibits" :key="entry.targetType + ':' + entry.targetId">
          <a :href="galleryUrl(entry)" target="_blank" rel="noopener">{{ entry.title }}</a>
        </li></ul>
      </section>
      <div v-if="data.submission.submitTime">
        <p>提交编号 {{ data.submission.submissionId }} · {{ data.submission.submitTime }} · {{ statuses[data.submission.status] }}</p>
        <button type="button" :disabled="busy" @click="downloadReceipt">下载提交回执</button>
      </div>
      <p v-if="data.submission.returnReason">退回原因 {{ data.submission.returnReason }}</p>
      <p v-if="data.submission.resubmitDeadline">重交截止 {{ data.submission.resubmitDeadline }}</p>
      <section v-if="teamInfo" aria-label="任务分组">
        <h3>任务分组</h3>
        <p v-if="!teamInfo.team && data.task.finishMode === 1">等待教师安排分组</p>
        <template v-if="data.task.finishMode === 2">
          <p>组队人数 {{ teamInfo.memberMin }}–{{ teamInfo.memberMax }} 人</p>
          <form v-if="!teamInfo.team" @submit.prevent="teamAction('', { text: teamName })">
            <label>队伍名称 <input v-model.trim="teamName" maxlength="64" required></label>
            <button :disabled="busy">创建队伍</button>
          </form>
          <div v-for="invite in teamInfo.invites" :key="invite.groupId">
            {{ invite.leaderName }} 邀请你加入 {{ invite.groupName }}
            <button :disabled="busy || !!teamInfo.team" @click="teamAction('/invite/' + invite.groupId + '?accept=true')">接受</button>
            <button :disabled="busy" @click="teamAction('/invite/' + invite.groupId + '?accept=false')">拒绝</button>
          </div>
          <div v-if="teamInfo.team && !teamInfo.team.confirmed && teamInfo.team.leaderId === teamInfo.selfId">
            <form @submit.prevent="teamAction('/invite', { text: invitation })">
              <label>成员学号 <input v-model.trim="invitation" maxlength="64" required></label>
              <button :disabled="busy">发送邀请</button>
            </form>
            <button :disabled="busy" @click="teamAction('/confirm')">确认队伍</button>
          </div>
        </template>
        <div v-if="teamInfo.team">
          <p>{{ teamInfo.team.confirmed ? '队伍已确认' : '队伍组建中' }}</p>
          <ul><li v-for="member in teamInfo.members" :key="member.userId">
            {{ member.name }}{{ member.userId === teamInfo.team.leaderId ? '（组长）' : '' }}
            {{ member.confirmStatus === 0 ? '待接受' : '' }}
            <p v-if="member.contribution">{{ member.contribution }}</p>
          </li></ul>
          <form v-if="teamInfo.team.confirmed && ![2, 3].includes(data.submission.status)"
                @submit.prevent="teamAction('/contribution', { text: contribution }, 'PUT')">
            <label>我的贡献说明 <textarea v-model="contribution" maxlength="500" /></label>
            <button :disabled="busy">保存贡献说明</button>
          </form>
        </div>
      </section>
      <div v-if="data.feedback">
        <h3>教师评语</h3>
        <p class="multiline">{{ data.feedback.reviewComment }}</p>
        <ul v-if="data.feedback.items?.length">
          <li v-for="item in data.feedback.items" :key="item.itemId">{{ item.itemName }} · {{ item.score }} / {{ item.maxScore }}</li>
        </ul>
      </div>
      <label>作业说明
        <span v-if="editors.some(editor => editor.userId !== teamInfo?.selfId)" role="status">
          正在编辑：{{ editors.filter(editor => editor.userId !== teamInfo?.selfId).map(editor => editor.name).join('、') }}
        </span>
        <textarea v-model="content" rows="5" :readonly="!draftEditable()"
                  @focus="draftFocused = draftEditable()" @blur="draftFocused = false"></textarea>
      </label>
      <ul><li v-for="(file, index) in files" :key="file.fileId">
        {{ file.fileName }}
        <button v-if="draftEditable()" :disabled="busy" @click="files.splice(index, 1)">移除</button>
      </li></ul>
      <label v-if="draftEditable()">添加附件 <input type="file" :disabled="busy" @change="upload"></label>
      <nav>
        <button v-if="draftEditable() && data.submission.status !== 4" :disabled="busy" @click="save('draft')">保存草稿</button>
        <button v-if="data.submission.submittable" :disabled="busy" @click="save('submit')">{{ data.submission.status === 4 ? '重交作业' : '提交作业' }}</button>
        <button v-if="data.submission.withdrawable && data.submission.status === 2" :disabled="busy" @click="update('withdraw')">撤回</button>
      </nav>
      <p v-if="data.submission.lateApplyStatus">补交申请 {{ lateStatuses[data.submission.lateApplyStatus] }}</p>
      <form v-if="[0, 1, 5].includes(data.submission.status) && [1, 2].includes(data.task.status) && Date.now() > new Date(data.task.deadline.replace(' ', 'T')).getTime() && data.task.deadlinePolicy === 2 && ![1, 2].includes(data.submission.lateApplyStatus)"
        class="fields" @submit.prevent="update('late-apply', '?reason=' + encodeURIComponent(lateReason.trim()))">
        <label>补交理由 <input v-model="lateReason" required maxlength="500"></label>
        <button :disabled="busy || !lateReason.trim()">申请补交</button>
      </form>
    </section>
    <section v-else-if="data && route.name === 'score'" class="card">
      <p v-if="!data.length" class="empty">暂无已发布成绩</p>
      <ul class="record-list"><li v-for="score in data" :key="score.scoreId">
        <RouterLink :to="'/task/' + score.taskId">{{ score.taskTitle }}</RouterLink>
        <strong class="score-value">{{ score.levelCode || score.finalScore }}</strong>
        <div v-if="appeals[score.scoreId]">
          异议状态：{{ appealStatuses[appeals[score.scoreId].status] }}
          <p v-if="appeals[score.scoreId].reply" class="multiline">{{ appeals[score.scoreId].reply }}</p>
        </div>
        <form v-else-if="appealScoreId === score.scoreId" @submit.prevent="submitAppeal(score.scoreId)">
          <label>异议说明 <textarea v-model="appealReason" maxlength="500" required /></label>
          <button :disabled="busy || !appealReason.trim()">提交异议</button>
          <button :disabled="busy" type="button" @click="appealScoreId = null">取消</button>
        </form>
        <button v-else :disabled="busy" @click="appealScoreId = score.scoreId; appealReason = ''">申请成绩复核</button>
      </li></ul>
    </section>
    </main>
  </div>
</template>

<style scoped>
.learning {
  display: grid;
  grid-template-columns: 15rem minmax(0, 1fr);
  min-height: 100dvh;
  background: #f2f6fc;
}
.workspace-nav {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  height: 100dvh;
  padding: 1.6rem 1rem;
  background: #102347;
  color: #f2f6ff;
}
.brand { display: flex; align-items: center; gap: .75rem; padding: .35rem; color: inherit; font-size: 1.15rem; font-weight: 750; text-decoration: none; }
.brand-mark { display: grid; place-items: center; width: 2.7rem; height: 2.7rem; border-radius: .75rem; background: #d7e8ff; color: #12386e; }
.brand small { display: block; color: #b7c9e9; font-size: .75rem; font-weight: 500; }
nav { display: flex; flex-wrap: wrap; align-items: center; gap: .75rem; }
.primary-nav { flex-direction: column; align-items: stretch; margin-top: 2.4rem; }
.primary-nav a { padding: .75rem 1rem; border-radius: .65rem; color: #c8d8f3; text-decoration: none; font-weight: 650; }
.primary-nav a:hover, .primary-nav .selected { color: white; background: #27497c; }
.workspace-main { width: 100%; max-width: 78rem; margin-inline: auto; padding: clamp(1rem, 4vw, 3rem); }
.workspace-main .card { margin: 0 0 1rem; padding: clamp(1rem, 2.7vw, 1.65rem); border: 1px solid #dce6f4; box-shadow: 0 4px 16px #1d3c6610; }
.page-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
  margin-bottom: 1.35rem;
  padding: clamp(1.25rem, 4vw, 2.6rem);
  color: white;
  background: linear-gradient(112deg, #132d59, #245594);
  border-radius: 1rem;
}
h1, h2, h3 { line-height: 1.3; margin: 0; }
h1 { font-size: clamp(1.65rem, 4vw, 2.45rem); }
h2 { font-size: 1.2rem; }
.page-banner p { color: #e1ecff; margin: .5rem 0 0; }
.banner-count { padding: .35rem .8rem; border-radius: 2rem; background: #ffffff20; }
.course-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 20rem), 1fr)); gap: 1rem; }
.course-grid .course-card { display: flex; flex-direction: column; min-height: 11rem; margin: 0; text-decoration: none; color: inherit; }
.course-grid .course-card:hover { border-color: #6a98d5; }
.course-card p { color: var(--lt-sub); }
.course-cta { color: #1756a6; margin-top: auto; padding-top: .7rem; font-weight: 700; }
.notification.unread { border-inline-start: .25rem solid #3774c4; }
.notification-title { display: flex; flex-wrap: wrap; align-items: center; gap: .7rem; }
.unread-label { background: #e7f0ff; color: #174b8b; padding: .15rem .55rem; border-radius: 2rem; font-size: .8rem; }
.muted, .empty { color: var(--lt-sub); }
.empty { text-align: center; padding-block: 1rem; }
.record-list { list-style: none; padding: 0; margin: .7rem 0 0; }
.record-list li { display: flex; flex-wrap: wrap; align-items: center; gap: .8rem; padding: .85rem; border-radius: .65rem; background: #f2f6fc; }
.record-list form { flex-basis: 100%; }
.record-list .muted, .score-value { margin-inline-start: auto; }
.score-value { font-size: 1.35rem; color: #174b8b; }
.workspace-main a { color: #1756a6; text-underline-offset: .2em; }
.fields, label { display: grid; gap: .65rem; }
input, textarea, select, button { font: inherit; padding: .55rem .75rem; border-radius: .55rem; }
input, textarea, select { min-width: 0; border: 1px solid #b8c8dd; background: white; color: inherit; }
button { min-height: 2.5rem; border: 1px solid transparent; background: #1956a2; color: white; cursor: pointer; }
button:hover:enabled { filter: brightness(1.12); }
button:disabled { cursor: default; opacity: .58; }
.secondary { background: #eaf2ff; color: #174b8b; border-color: #d2e2fa; }
.logout { margin-top: auto; }
:is(a, button, input, textarea, select):focus-visible { outline: 3px solid #6a9de0; outline-offset: 3px; }
textarea { width: 100%; }
img { max-width: 100%; }
video, audio, iframe { width: 100%; }
iframe { min-height: 24rem; }
li { margin-block: .5rem; }
.multiline { white-space: pre-wrap; overflow-wrap: anywhere; }
.error { color: var(--lt-danger); }
.learning--login { display: block; background: #eaf1fc; }
.learning--login .workspace-nav { position: static; min-height: auto; height: auto; align-items: center; }
.learning--login .workspace-main { max-width: 32rem; }
.learning--login label img { display: block; }
@media (max-width: 700px) {
  .learning { display: block; }
  .workspace-nav { position: static; min-height: auto; height: auto; flex-direction: row; align-items: center; justify-content: space-between; padding: .7rem 1rem; }
  .logout { margin-top: 0; }
  .primary-nav {
    position: fixed; z-index: 5; inset: auto 0 0;
    display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: .25rem;
    margin: 0; padding: .4rem .35rem calc(.4rem + var(--lt-safe-bottom));
    background: #102347; text-align: center;
  }
  .primary-nav a { padding: .6rem .15rem; font-size: .85rem; }
  .workspace-main { padding-bottom: calc(4.5rem + var(--lt-safe-bottom)); }
  .record-list .muted { margin-inline-start: 0; }
}
</style>
