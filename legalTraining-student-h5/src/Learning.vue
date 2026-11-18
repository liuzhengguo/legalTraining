<script setup>
import LegalAssistant from './LegalAssistant.vue'
import assistantAvatar from './assets/xiaolvfa-avatar.jpg'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { profileImageUrl, request } from './api'
import LinkedCases from './LinkedCases.vue'
import { navigation as sharedNavigation } from './navigation'

const route = useRoute()
const router = useRouter()
const data = ref(null)
const currentClasses = ref(null)
const currentClassLabel = computed(() => currentClasses.value === null
  ? '加载中…' : currentClasses.value.map(entry => entry.className).join('、') || '暂未分班')
const busy = ref(false)
const loading = ref(false)
const error = ref('')
const message = ref('')
const content = ref('')
const teamInfo = ref(null)
const editors = ref([])
const draftFocused = ref(false)
const autoSaveState = ref('')
const lastAutoSaved = ref('')
const draftDirty = ref(false)
let autoSaveTimer = null
let autoSaving = false
let pendingAutoSave = false
let suppressAutoSave = false

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
// 关联展厅内容在 App 内打开，保持移动端体验一致。
function galleryRoute(entry) {
  return entry.targetType === 'zone'
    ? '/gallery/zone/' + entry.targetId
    : '/gallery/item/' + entry.targetId
}
const achievementStatuses = ['待授权', '待审核', '审核通过', '审核驳回', '已撤回或拒绝', '已生成展项']
const statuses = ['未提交', '草稿', '已提交', '已批阅', '已退回', '逾期未交', '任务取消']
const lateStatuses = ['', '待审批', '已通过', '已驳回']
function taskSubmissionAction(task) {
  if (![1, 2].includes(Number(task.status))) return ''
  const state = Number(task.submitStatus)
  if (state === 4) return '查看重交'
  if (![0, 1, 5].includes(state)) return ''
  const deadline = task.deadline ? Date.parse(String(task.deadline).replace(' ', 'T')) : NaN
  if (state === 5 || deadline < clock.value) return '查看补交'
  return state === 1 ? '继续提交' : '去提交'
}
const navigation = sharedNavigation
const profileLinks = [
  ...navigation.filter(entry => entry.to !== '/profile' && entry.to !== '/home'),
  { to: '/notifications', title: '通知', icon: 'bell' },
  { to: '/profile/feedback', title: '意见反馈', icon: 'comment-o' },
  { to: '/profile/version', title: '版本更新', icon: 'upgrade' }
]
const release = __STUDENT_RELEASE__
const feedbackText = ref('')
const updateStatus = ref('')
const availableRelease = ref(null)
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
const profilePageTitles = { notifications: '通知', feedback: '意见反馈', version: '版本更新' }
const primaryPage = computed(() => navigation.some(entry => entry.routes[0] === route.name))
const assistantPage = computed(() => route.name === 'assistant')
const parentPage = computed(() => {
  if (assistantPage.value) {
    const from = route.query.from
    if (typeof from === 'string' && from.startsWith('/') && !from.startsWith('//')) {
      const destination = router.resolve(from)
      if (navigation.some(entry => entry.routes.includes(destination.name))) return destination.fullPath
    }
    return '/course'
  }
  if (route.name === 'task' && data.value?.task?.courseId) return '/course/' + data.value.task.courseId
  return activePage.value?.to || '/course'
})
const appBarTitle = computed(() => {
  if (route.name === 'courses') return '我的课程'
  if (route.name === 'course') return '课程详情'
  if (route.name === 'task') return '实训任务'
  if (route.name === 'avatar') return '更换头像'
  if (assistantPage.value) return '法律助手'
  return profilePageTitles[route.name] || activePage.value?.title || '法律实训'
})
const avatarInput = ref(null)
const avatarDraft = ref(null)
const avatarUnavailable = ref(false)
const savedAvatar = computed(() => profileImageUrl(data.value?.avatar))
const avatarSource = computed(() => avatarDraft.value?.url || (!avatarUnavailable.value ? savedAvatar.value : ''))
watch(savedAvatar, () => { avatarUnavailable.value = false })
onUnmounted(clearAvatarDraft)

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
  clearAvatarDraft()
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
      notifications: '/edu/learning/notifications',
      assistant: '/getInfo',
      profile: '/getInfo',
      avatar: '/getInfo'
    }
    const endpoint = endpoints[route.name]
    if (!endpoint) return
    const [result, classes] = await Promise.all([
      request(endpoint),
      ['courses', 'profile'].includes(route.name) ? request('/edu/learning/classes/current') : Promise.resolve(null)
    ])
    if (!active) return
    if (classes) currentClasses.value = classes.data
    if (route.name === 'score') {
      const result = await request('/edu/score/appeals/my')
      if (!active) return
      appeals.value = Object.fromEntries(result.data.map(appeal => [appeal.scoreId, appeal]))
    }
    data.value = endpoint === '/getInfo' ? result.user : result.data
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

// 正文与附件变更触发草稿自动保存（ST-02-02）
watch(content, () => {
  if (suppressAutoSave) return
  draftDirty.value = true
  scheduleAutoSave()
})
watch(() => files.value.map(file => file.fileId).join(','), () => {
  if (suppressAutoSave) return
  draftDirty.value = true
  scheduleAutoSave()
})

function scheduleAutoSave() {
  clearTimeout(autoSaveTimer)
  autoSaveTimer = setTimeout(() => autoSaveDraft(), 3000)
}

async function autoSaveDraft(force = false) {
  if (route.name !== 'task' || !data.value || !draftDirty.value) return
  if (!force && (!draftEditable() || busy.value)) return
  if (autoSaving) { pendingAutoSave = true; return }
  const taskId = data.value.task.taskId
  const snapshot = content.value
  const filesSnapshot = files.value.map(file => ({ ...file }))
  autoSaving = true
  autoSaveState.value = 'saving'
  clearTimeout(autoSaveTimer)
  try {
    await request('/edu/submission/draft', {
      method: 'POST', body: { taskId, content: snapshot, files: filesSnapshot }
    })
    lastAutoSaved.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    autoSaveState.value = 'saved'
    draftDirty.value = false
  } catch {
    autoSaveState.value = 'error'
  } finally {
    autoSaving = false
    if (pendingAutoSave) {
      pendingAutoSave = false
      scheduleAutoSave()
    }
  }
}

function flushAutoSave() {
  clearTimeout(autoSaveTimer)
  if (draftDirty.value) return autoSaveDraft(true)
  return Promise.resolve()
}

function onVisibilityChange() {
  if (document.visibilityState === 'hidden') flushAutoSave()
}
function onOnline() {
  if (draftDirty.value || autoSaveState.value === 'error') scheduleAutoSave()
}
onMounted(() => {
  document.addEventListener('visibilitychange', onVisibilityChange)
  window.addEventListener('online', onOnline)
  window.addEventListener('beforeunload', onVisibilityChange)
})
onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
  window.removeEventListener('online', onOnline)
  window.removeEventListener('beforeunload', onVisibilityChange)
  clearTimeout(autoSaveTimer)
})

function syncSubmission(submission) {
  suppressAutoSave = true
  clearTimeout(autoSaveTimer)
  data.value.submission = submission
  content.value = submission.content || ''
  files.value = submission.files || []
  draftDirty.value = false
  nextTick(() => { suppressAutoSave = false })
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

function signOut() {
  return run(async () => {
    await request('/logout', { method: 'POST' })
    localStorage.removeItem('lt_token')
    await router.replace('/login')
  })
}

function clearAvatarDraft() {
  if (avatarDraft.value) URL.revokeObjectURL(avatarDraft.value.url)
  avatarDraft.value = null
}

function chooseAvatar(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || busy.value) return
  error.value = ''
  message.value = ''
  if (!/\.(?:jpe?g|png|gif|bmp)$/i.test(file.name)) {
    error.value = '请选择 JPG、PNG、GIF 或 BMP 图片。'
    return
  }
  if (!file.size || file.size > 10 * 1024 * 1024) {
    error.value = '请选择不超过 10 MB 的有效图片。'
    return
  }
  clearAvatarDraft()
  avatarDraft.value = { file, url: URL.createObjectURL(file) }
}

function avatarImageFailed() {
  if (avatarDraft.value) {
    clearAvatarDraft()
    error.value = '无法读取这张图片，请重新选择。'
  } else {
    avatarUnavailable.value = true
  }
}

function saveAvatar() {
  if (!avatarDraft.value) return
  const draft = avatarDraft.value
  const form = new FormData()
  form.append('avatarfile', draft.file)
  return run(async () => {
    const image = new Image()
    image.src = draft.url
    await image.decode().catch(() => { throw new Error('无法读取这张图片，请重新选择。') })
    if (avatarDraft.value !== draft) return
    const result = await request('/system/user/profile/avatar', { method: 'POST', body: form })
    if (!profileImageUrl(result.imgUrl)) throw new Error('头像地址无效，请重新进入个人页查看。')
    if (route.name !== 'avatar' || avatarDraft.value !== draft) return
    await router.replace('/profile')
    showSuccessToast('头像已更新')
  })
}

function submitFeedback() {
  const content = feedbackText.value.trim()
  if (!content) return
  return run(async () => {
    await request('/edu/learning/feedback', { method: 'POST', body: { content } })
    feedbackText.value = ''
    if (route.name === 'feedback') message.value = '反馈已提交。'
  })
}

function checkUpdate() {
  return run(async () => {
    updateStatus.value = ''
    availableRelease.value = null
    const response = await fetch(import.meta.env.BASE_URL + 'student-version.json', { cache: 'no-store' })
    if (!response.ok) throw new Error('暂时无法检查更新，请稍后再试。')
    const latest = await response.json().catch(() => null)
    if (typeof latest?.version !== 'string' || typeof latest.build !== 'string') {
      throw new Error('暂时无法读取版本信息，请稍后再试。')
    }
    if (latest.version === release.version && latest.build === release.build) {
      updateStatus.value = '当前已是最新版本。'
    } else {
      availableRelease.value = latest
      updateStatus.value = '有新版本可用。'
    }
  })
}

function applyUpdate() {
  if (!availableRelease.value) return
  const url = new URL(window.location.href)
  url.searchParams.set('build', availableRelease.value.build)
  window.location.replace(url.href)
}

function save(action) {
  if (action === 'submit' && !window.confirm('确认正式提交作业及当前附件？')) return
  return run(async () => {
    const taskId = data.value.task.taskId
    if (action === 'submit') await flushAutoSave()
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
    const result = await request('/common/upload/secure', { method: 'POST', body: form })
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

function openResource(resource, download = false, endpoint = null) {
  const courseId = route.params.courseId
  const url = endpoint || ('/edu/learning/courses/' + courseId + '/resources/' + resource.resourceId)
  return run(async () => {
    const blob = await request(url + '?download=' + download, { binary: true })
    if (route.params.courseId !== courseId) return
    const objectUrl = URL.createObjectURL(blob)
    if (download) {
      const link = document.createElement('a')
      link.href = objectUrl
      link.download = resource.fileName || resource.title
      link.click()
      setTimeout(() => URL.revokeObjectURL(objectUrl), 30000)
    } else {
      closePreview()
      preview.value = { url: objectUrl, title: resource.title, type: blob.type }
    }
  })
}

function openTaskResource(resource, download = false) {
  return openResource(resource, download,
    '/edu/learning/tasks/' + route.params.taskId + '/resources/' + resource.resourceId)
}

function downloadTemplate() {
  return run(async () => {
    const blob = await request('/edu/learning/tasks/' + route.params.taskId + '/template', { binary: true })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '实训任务模板'
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 30000)
  })
}
</script>

<template>
  <div class="learning" :class="{ 'with-tabs': primaryPage, 'with-assistant-trigger': !assistantPage, 'assistant-page': assistantPage }">
    <header class="app-bar">
      <RouterLink v-if="!primaryPage" :to="parentPage" :replace="assistantPage" class="app-bar-back" aria-label="返回上级">
        <van-icon name="arrow-left" aria-hidden="true" />
      </RouterLink>
      <h1>{{ appBarTitle }}</h1>
    </header>
    <main class="page workspace-main" :aria-busy="loading || busy">
    <header v-if="route.name === 'courses'" class="page-banner page-banner--courses">
      <div>
        <h2>法律实训</h2>
        <p>查看课程任务与学习资料</p>
        <p class="current-class">当前班级：{{ currentClassLabel }}</p>
      </div>
    </header>
    <p v-if="unreadCount" class="unread-summary">{{ unreadCount }} 条未读通知</p>
    <p v-if="error" class="card error" role="alert">{{ error }}</p>
    <p v-if="message" class="card" role="status">{{ message }}</p>
    <p v-if="loading" class="card" role="status">加载中…</p>
    <LegalAssistant v-if="route.name === 'assistant'" :student="data" />
    <section v-else-if="route.name === 'profile'" aria-label="个人资料">
      <header class="page-banner">
        <RouterLink class="profile-avatar" to="/profile/avatar" aria-label="更换头像">
          <img v-if="avatarSource" :src="avatarSource" alt="" @error="avatarImageFailed">
          <van-icon v-else name="manager" aria-hidden="true" />
          <span class="avatar-edit"><van-icon name="photograph" aria-hidden="true" /></span>
        </RouterLink>
        <div>
          <h2 v-if="data" class="profile-name">{{ data.userName || data.nickName }}</h2>
          <p v-if="data?.nickName && data.userName && data.nickName !== data.userName">{{ data.nickName }}</p>
          <p class="current-class">当前班级：{{ currentClassLabel }}</p>
        </div>
      </header>
      <div class="card profile-card">
        <RouterLink v-for="entry in profileLinks" :key="entry.to" class="profile-link" :to="entry.to">
          <van-icon :name="entry.icon" aria-hidden="true" />
          <span>{{ entry.title }}</span>
          <small v-if="entry.to === '/profile/version'" class="muted">v{{ release.version }}</small>
          <van-icon name="arrow" aria-hidden="true" />
        </RouterLink>
        <button class="logout secondary" type="button" :disabled="busy" @click="signOut">退出登录</button>
      </div>
    </section>
    <section v-else-if="route.name === 'avatar'" class="avatar-page" aria-label="头像预览与上传">
      <div class="card avatar-panel">
        <div class="avatar-preview">
          <img v-if="avatarSource" :src="avatarSource" :alt="avatarDraft ? '新头像预览' : '当前头像'" @error="avatarImageFailed">
          <van-icon v-else name="manager" role="img" aria-label="默认头像" />
        </div>
        <p class="muted">{{ avatarDraft ? '头像预览' : '当前头像' }}</p>
      </div>
      <input ref="avatarInput" hidden type="file" accept=".jpg,.jpeg,.png,.gif,.bmp" @change="chooseAvatar">
      <div class="avatar-actions">
        <button class="secondary" type="button" :disabled="busy" @click="avatarInput.click()">
          {{ avatarDraft ? '重新选择' : '从相册选择' }}
        </button>
        <button v-if="avatarDraft" type="button" :disabled="busy" @click="saveAvatar">{{ busy ? '上传中…' : '保存头像' }}</button>
      </div>
    </section>
    <form v-else-if="route.name === 'feedback'" class="card fields" @submit.prevent="submitFeedback">
      <label>反馈内容
        <textarea v-model="feedbackText" rows="5" maxlength="1000" required :disabled="busy"
          placeholder="描述你遇到的问题或希望改进的地方" />
      </label>
      <small class="muted">{{ feedbackText.length }} / 1000</small>
      <button :disabled="busy || !feedbackText.trim()">{{ busy ? '提交中…' : '提交反馈' }}</button>
    </form>
    <section v-else-if="route.name === 'version'" class="card fields" aria-label="学生端版本">
      <p class="account-row"><span>当前版本</span><strong>v{{ release.version }}</strong></p>
      <p v-if="updateStatus" role="status">{{ updateStatus }}</p>
      <button v-if="availableRelease" type="button" @click="applyUpdate">刷新到新版本</button>
      <button v-else type="button" :disabled="busy" @click="checkUpdate">{{ busy ? '检查中…' : '检查更新' }}</button>
    </section>
    <section v-else-if="data && route.name === 'courses'" aria-label="课程列表">
      <div class="course-list-heading"><h2>课程列表</h2><span>{{ data.length }} 门</span></div>
      <p v-if="!data.length" class="card empty">暂无课程，课程发布并分配到你的班级后会显示在这里。</p>
      <div v-else class="course-grid">
        <RouterLink v-for="course in data" :key="course.courseId" :to="'/course/' + course.courseId" class="card course-card">
          <span class="course-icon"><van-icon name="records" aria-hidden="true" /></span>
          <div class="course-copy">
            <h3>{{ course.courseName }}</h3>
            <p v-if="course.intro">{{ course.intro }}</p>
          </div>
          <van-icon name="arrow" class="course-arrow" aria-hidden="true" />
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
            <RouterLink :to="galleryRoute(entry)">{{ entry.title }}</RouterLink>
          </li>
        </ul>
      </section>
      <p class="multiline">{{ data.intro }}</p>
      <h3>任务</h3>
      <p v-if="!data.tasks.length">暂无任务</p>
      <ul class="record-list course-task-list"><li v-for="task in data.tasks" :key="task.taskId">
        <span class="task-icon" aria-hidden="true">📝</span>
        <div class="course-task-details">
          <RouterLink :to="'/task/' + task.taskId">{{ task.title }}</RouterLink>
          <span class="muted">截止 {{ task.deadline }} · {{ statuses[task.submitStatus] }}</span>
        </div>
        <RouterLink v-if="taskSubmissionAction(task)" class="task-submit-entry" :to="'/task/' + task.taskId">
          {{ taskSubmissionAction(task) }}
        </RouterLink>
      </li></ul>
      <h3>课程资源</h3>
      <p v-if="!data.resources.length">暂无资源</p>
      <ul v-else class="resource-list"><li v-for="resource in data.resources" :key="resource.resourceId">
        <span class="resource-icon" aria-hidden="true">📎</span>
        <span class="resource-info">
          <span class="resource-title">{{ resource.title }}</span>
          <span v-if="resource.resType === 'link'" class="resource-type">外部链接</span>
        </span>
        <span class="resource-actions">
          <a v-if="resource.resType === 'link' && /^https?:\/\//i.test(resource.externalUrl)" class="resource-action"
             :href="resource.externalUrl" target="_blank" rel="noopener noreferrer">打开</a>
          <template v-else-if="resource.resType !== 'link'">
            <button v-if="resource.previewable" class="resource-action" :disabled="busy" @click="openResource(resource)">预览</button>
            <button v-if="resource.allowDownload === '1'" class="resource-action" :disabled="busy" @click="openResource(resource, true)">下载</button>
          </template>
        </span>
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
      <h2>{{ data.task.title }}</h2>
      <p class="multiline">{{ data.task.requirement }}</p>
      <p v-if="data.templateUrl"><button type="button" :disabled="busy" @click="downloadTemplate">下载附件模板</button></p>
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
      <section v-if="data.resources?.length" aria-label="任务资料">
        <h3>任务资料</h3>
        <ul><li v-for="resource in data.resources" :key="resource.resourceId">
          {{ resource.title }}
          <a v-if="resource.resType === 'link' && /^https?:\/\//i.test(resource.externalUrl)" :href="resource.externalUrl" target="_blank" rel="noopener noreferrer">打开资料</a>
          <template v-else-if="resource.resType !== 'link'">
            <button v-if="resource.previewable" :disabled="busy" @click="openTaskResource(resource)">预览</button>
            <button v-if="resource.allowDownload === '1'" :disabled="busy" @click="openTaskResource(resource, true)">下载</button>
          </template>
        </li></ul>
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
        <span v-if="autoSaveState" class="autosave-state" role="status">
          {{ autoSaveState === 'saving' ? '草稿保存中…' : autoSaveState === 'saved' ? ('草稿已自动保存 ' + lastAutoSaved) : '草稿保存失败，联网后将自动重试' }}
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
    <nav v-if="primaryPage" class="primary-nav" aria-label="学生端导航">
      <RouterLink v-for="entry in navigation" :key="entry.to" :to="entry.to"
        :class="{ selected: entry === activePage }" :aria-current="entry === activePage ? 'location' : undefined">
        <van-icon :name="entry.icon" aria-hidden="true" />
        <span>{{ entry.title }}</span>
      </RouterLink>
    </nav>
    <RouterLink v-if="!assistantPage" class="assistant-trigger"
      :to="{ name: 'assistant', query: { from: route.fullPath } }" aria-label="打开法律助手小律法">
      <img :src="assistantAvatar" alt="" width="48" height="48">
    </RouterLink>
  </div>
</template>

<style scoped>
.autosave-state { display: block; font-size: .78rem; color: #1989fa; }
.learning {
  --student-nav-height: 0rem;
  width: 100%;
  max-width: var(--lt-app-width);
  min-height: 100vh;
  min-height: 100dvh;
  margin-inline: auto;
  background: var(--lt-field);
  box-shadow: 0 0 48px #21050466;
}
.with-tabs { --student-nav-height: 4.2rem; }
nav { display: flex; flex-wrap: wrap; align-items: center; gap: .75rem; }
.app-bar {
  position: sticky;
  top: 0;
  z-index: 4;
  display: grid;
  grid-template-columns: 3rem minmax(0, 1fr) 3rem;
  align-items: center;
  min-height: calc(3.4rem + var(--lt-safe-top));
  padding-top: var(--lt-safe-top);
  background: var(--lt-nav);
  color: var(--lt-on-dark);
  border-bottom: 1px solid #a15e45;
}
.app-bar h1 { grid-column: 2; margin: 0; text-align: center; font-size: 1.13rem; letter-spacing: .04em; }
.current-class { overflow-wrap: anywhere; }
.app-bar-back {
  grid-column: 1; grid-row: 1; display: grid; place-items: center;
  min-height: 3rem; color: inherit; text-decoration: none; font-size: 1.25rem;
}
.primary-nav {
  position: fixed;
  z-index: 5;
  inset: auto 0 0;
  width: min(100%, var(--lt-app-width));
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: .25rem;
  margin-inline: auto;
  padding: .4rem .35rem calc(.4rem + var(--lt-safe-bottom));
  background: var(--lt-nav);
  border-top: 1px solid #a15e45;
  box-shadow: 0 -4px 18px #551c1414;
}
.primary-nav a {
  display: grid;
  justify-items: center;
  align-content: center;
  gap: .18rem;
  min-height: 3.35rem;
  padding: .3rem .1rem;
  border-radius: var(--lt-radius);
  color: var(--lt-on-dark-muted);
  text-align: center;
  text-decoration: none;
  font-size: .8rem;
  font-weight: 650;
}
.primary-nav a:hover, .primary-nav .selected { background: var(--lt-active); color: var(--lt-on-dark); }
.primary-nav .selected { box-shadow: inset 0 0 0 1px #c080507d; }
.primary-nav .van-icon { font-size: 1.45rem; }
.assistant-trigger {
  position: fixed;
  z-index: 5;
  right: max(calc(1rem + env(safe-area-inset-right, 0px)), calc((100% - var(--lt-app-width)) / 2 + 1rem));
  bottom: calc(var(--student-nav-height) + var(--lt-safe-bottom) + .85rem);
  display: grid;
  place-items: center;
  width: 3.45rem;
  height: 3.45rem;
  border: 2px solid var(--lt-badge);
  border-radius: 50%;
  background: var(--lt-nav);
  box-shadow: 0 .2rem .85rem #48151055;
}
.assistant-trigger img { width: 100%; height: 100%; object-fit: cover; border-radius: 50%; }
.workspace-main { padding: 1rem 1rem calc(1.25rem + var(--lt-safe-bottom)); overflow-wrap: anywhere; }
.assistant-page { display: flex; flex-direction: column; height: 100dvh; min-height: 0; }
.assistant-page .app-bar { flex: none; }
.assistant-page .workspace-main { flex: 1; min-height: 0; display: flex; flex-direction: column; padding: 0; }
.with-assistant-trigger .workspace-main { padding-bottom: calc(var(--student-nav-height) + var(--lt-safe-bottom) + 4.75rem); }
.workspace-main .card { margin: 0 0 1rem; padding: 1rem; border: 1px solid var(--lt-border); box-shadow: 0 4px 16px #551c1414; }
.page-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
  margin-bottom: 1.35rem;
  padding: 1.25rem;
  color: var(--lt-on-dark);
  background-color: var(--lt-nav);
  background-image: linear-gradient(112deg, var(--lt-nav), var(--lt-active));
  border-radius: var(--lt-card-radius);
}
.page-banner--courses {
  min-height: clamp(7.5rem, 33vw, 9.5rem);
  background-image: linear-gradient(100deg, #3d100ef5 6%, #4f1510df 53%, #5e1a1450),
    url('./assets/exhibition-hall.webp');
  background-position: center, center 55%;
  background-size: cover;
  background-repeat: no-repeat;
}
h1, h2, h3 { line-height: 1.3; margin: 0; }
h1 { font-size: 1.85rem; }
h2 { font-size: 1.2rem; }
.page-banner p { color: var(--lt-on-dark-muted); margin: .5rem 0 0; }
.page-banner--courses h2 { font: 700 1.6rem/1.2 "Songti SC", SimSun, serif; }
.unread-summary { color: var(--lt-sub); margin: 0 0 .75rem; }
.course-list-heading { display: flex; justify-content: space-between; align-items: center; margin: 0 .15rem .65rem; }
.course-list-heading span { color: var(--lt-sub); font-size: .84rem; }
.course-grid { border: 1px solid var(--lt-border); border-radius: var(--lt-card-radius); background: var(--lt-card); }
.course-grid .course-card {
  display: grid; grid-template-columns: 2.7rem minmax(0, 1fr) 1rem; align-items: center; gap: .7rem;
  min-height: 5.25rem; margin: 0; padding: .8rem; border: 0; box-shadow: none;
  border-radius: var(--lt-card-radius); background: transparent; text-decoration: none; color: inherit;
}
.course-grid .course-card + .course-card { border-top: 1px solid var(--lt-border); border-radius: 0 0 var(--lt-card-radius) var(--lt-card-radius); }
.course-grid .course-card:hover { background: var(--lt-tint); }
.course-icon {
  display: grid; place-items: center; width: 2.7rem; aspect-ratio: 1; border-radius: .78rem;
  background: var(--lt-tint); color: var(--lt-primary); font-size: 1.3rem;
}
.course-copy h3 { font-size: .98rem; }
.course-copy p { margin: .28rem 0 0; color: var(--lt-sub); font-size: .84rem; }
.course-arrow { color: var(--lt-primary); }
.notification.unread { border-inline-start: .25rem solid var(--lt-active); }
.notification-title { display: flex; flex-wrap: wrap; align-items: center; gap: .7rem; }
.unread-label { background: var(--lt-tint); color: var(--lt-primary); padding: .15rem .55rem; border-radius: 2rem; font-size: .8rem; }
.muted, .empty { color: var(--lt-sub); }
.empty { text-align: center; padding-block: 1rem; }
.record-list { list-style: none; padding: 0; margin: .7rem 0 0; }
.record-list li { display: flex; flex-wrap: wrap; align-items: center; gap: .8rem; padding: .85rem; border-radius: var(--lt-radius); background: var(--lt-field); }
.record-list form { flex-basis: 100%; }
.record-list .muted { flex-basis: 100%; }
.course-task-list li { display: flex; align-items: center; gap: .7rem; }
.task-icon { display: grid; place-items: center; flex: none; width: 2.1rem; height: 2.1rem; border-radius: .55rem; background: var(--lt-tint); font-size: 1.05rem; }
.course-task-details { display: grid; flex: 1; min-width: 0; gap: .35rem; }
.course-task-details .muted { flex-basis: auto; font-size: .8rem; }
.workspace-main .task-submit-entry {
  display: grid; place-items: center; min-height: 44px; padding: .4rem .65rem;
  color: var(--lt-on-dark); background: var(--lt-primary); border-radius: var(--lt-radius);
  text-decoration: none; font-size: .84rem; font-weight: 650; white-space: nowrap;
}
.resource-list { list-style: none; padding: 0; margin: .7rem 0 0; display: grid; gap: .6rem; }
.resource-list li {
  display: flex; align-items: center; gap: .7rem; padding: .7rem .85rem;
  border: 1px solid var(--lt-border); border-radius: var(--lt-radius); background: var(--lt-field);
}
.resource-icon {
  display: grid; place-items: center; flex: none; width: 2.1rem; height: 2.1rem;
  border-radius: .55rem; background: var(--lt-tint); font-size: 1.05rem;
}
.resource-info { display: grid; gap: .2rem; flex: 1; min-width: 0; }
.resource-title { font-size: .9rem; line-height: 1.4; color: var(--lt-text); overflow-wrap: anywhere; }
.resource-type { font-size: .74rem; color: var(--lt-sub); }
.resource-actions { display: flex; align-items: center; gap: .4rem; flex: none; }
.resource-action {
  display: inline-grid; place-items: center; min-height: 2rem; padding: .3rem .7rem;
  border: 1px solid var(--lt-tint); border-radius: .5rem;
  background: var(--lt-tint); color: var(--lt-primary);
  font-size: .8rem; font-weight: 650; text-decoration: none; white-space: nowrap; cursor: pointer;
}
.resource-action:hover:enabled { background: var(--lt-primary); color: var(--lt-on-dark); border-color: var(--lt-primary); }
.score-value { margin-inline-start: auto; }
.score-value { font-size: 1.35rem; color: var(--lt-primary); }
.workspace-main a { color: var(--lt-primary); text-underline-offset: .2em; }
.fields, label { display: grid; gap: .65rem; }
input, textarea, select, button { font: inherit; padding: .55rem .75rem; border-radius: var(--lt-radius); }
input, textarea, select { min-width: 0; max-width: 100%; border: 1px solid var(--lt-border); background: var(--lt-field); color: inherit; font-size: 16px; }
button { min-height: 2.75rem; border: 1px solid transparent; background: var(--lt-primary); color: var(--lt-on-dark); cursor: pointer; }
button:not(.secondary):hover:enabled { background: var(--lt-primary-hover); }
button:disabled { cursor: default; opacity: .58; }
.secondary { background: var(--lt-tint); color: var(--lt-primary); border-color: var(--lt-border); }
.profile-avatar {
  position: relative; display: grid; place-items: center; flex: none; width: 4rem; height: 4rem; padding: 0;
  border: 2px solid var(--lt-on-dark-muted); border-radius: 50%; background: var(--lt-badge); color: var(--lt-primary); font-size: 2rem;
}
.profile-avatar img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; }
.profile-avatar:focus-visible { outline-color: var(--lt-on-dark); }
.avatar-edit { position: absolute; right: -.2rem; bottom: -.15rem; display: grid; place-items: center; width: 1.45rem; height: 1.45rem; border-radius: 50%; background: var(--lt-primary); color: var(--lt-on-dark); font-size: .8rem; border: 1px solid var(--lt-on-dark); }
.avatar-panel { display: grid; justify-items: center; gap: .8rem; }
.workspace-main .avatar-panel { padding: 2rem 1rem 1.5rem; }
.avatar-panel p { margin: 0; }
.avatar-preview { display: grid; place-items: center; width: min(100%, 13rem); aspect-ratio: 1; overflow: hidden; border-radius: 50%; background: var(--lt-badge); color: var(--lt-primary); font-size: 5rem; }
.avatar-preview img { width: 100%; height: 100%; object-fit: cover; }
.avatar-actions { display: flex; gap: .75rem; }
.avatar-actions button { flex: 1; }
.profile-name { font-size: 1.6rem; }
.page-banner div:has(.profile-name) { flex: 1; min-width: 0; }
.account-row { display: flex; justify-content: space-between; gap: .75rem; margin: 0 0 1rem; }
.profile-link { display: flex; align-items: center; gap: .75rem; min-height: 3rem; padding: .65rem .5rem; border-bottom: 1px solid var(--lt-border); text-decoration: none; }
.profile-link span { flex: 1; }
.profile-link:hover { background: var(--lt-field); }
.logout { width: 100%; margin-top: 1rem; }
:is(a, button, input, textarea, select):focus-visible { outline: 3px solid var(--lt-focus); outline-offset: 3px; }
textarea { width: 100%; }
img { max-width: 100%; }
video, audio, iframe { width: 100%; }
iframe { min-height: 24rem; }
li { margin-block: .5rem; }
.multiline { white-space: pre-wrap; overflow-wrap: anywhere; }
.error { color: var(--lt-danger); }
</style>
