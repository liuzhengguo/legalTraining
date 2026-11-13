<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { request } from './api'

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
const files = ref([])
const lateReason = ref('')
const preview = ref(null)
const authorization = ref({ signMode: '完全匿名', scope: '仅展厅内' })
const galleryBase = (import.meta.env.VITE_GALLERY_BASE || (import.meta.env.DEV ? 'http://localhost:5174' : '')).replace(/\/$/, '')
function galleryUrl(entry) {
  return galleryBase + '/immersive/' + entry.targetType + '/' + entry.targetId
}
const achievementStatuses = ['待授权', '待审核', '审核通过', '审核驳回', '已撤回或拒绝', '已生成展项']
const statuses = ['未提交', '草稿', '已提交', '已批阅', '已退回', '逾期未交', '任务取消']
const lateStatuses = ['', '待审批', '已通过', '已驳回']

watch(() => route.fullPath, async (_, __, onCleanup) => {
  let active = true
  onCleanup(() => { active = false })
  data.value = null
  closePreview()
  error.value = ''
  message.value = ''
  lateReason.value = ''
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
    if (route.name === 'login') captcha.value = result
    else data.value = result.data
    if (route.name === 'task') syncSubmission(result.data.submission)
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
  return run(async () => {
    const taskId = data.value.task.taskId
    await request('/edu/submission/' + action, { method: 'POST', body: { taskId, content: content.value, files: files.value } })
    if (route.params.taskId === String(taskId)) {
      syncSubmission((await request('/edu/submission/my/' + taskId)).data)
      message.value = action === 'draft' ? '草稿已保存' : '作业已提交'
    }
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
  <main class="page learning" :aria-busy="loading || busy">
    <header class="card">
      <h1>法律实训 · 学生端</h1>
      <nav v-if="route.name !== 'login'">
        <RouterLink to="/course">我的课程</RouterLink>
        <RouterLink to="/score">我的成绩</RouterLink>
        <RouterLink to="/achievements">我的成果</RouterLink>
        <RouterLink to="/notifications">通知</RouterLink>
        <button type="button" :disabled="busy" @click="signOut">退出登录</button>
      </nav>
    </header>
    <p v-if="error" class="card error" role="alert">{{ error }}</p>
    <p v-if="message" class="card" role="status">{{ message }}</p>
    <p v-if="loading" class="card" role="status">加载中…</p>
    <form v-if="route.name === 'login'" class="card fields" @submit.prevent="signIn">
      <label>学号 <input v-model.trim="login.username" autocomplete="username" required></label>
      <label>密码 <input v-model="login.password" type="password" autocomplete="current-password" required></label>
      <label v-if="captcha?.captchaEnabled">验证码
        <img :src="'data:image/png;base64,' + captcha.img" alt="登录验证码">
        <input v-model.trim="login.code" autocomplete="off" required>
      </label>
      <button :disabled="busy || loading || !captcha">登录</button>
    </form>
    <section v-else-if="data && route.name === 'courses'" class="card">
      <h2>我的课程</h2>
      <p v-if="!data.length">暂无课程</p>
      <ul><li v-for="course in data" :key="course.courseId">
        <RouterLink :to="'/course/' + course.courseId">{{ course.courseName }}</RouterLink>
      </li></ul>
    </section>
    <section v-else-if="data && route.name === 'achievements'">
      <h2 class="card">我的成果</h2>
      <p v-if="!data.length" class="card">暂无推荐成果</p>
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
      <h2 class="card">通知</h2>
      <p v-if="!data.length" class="card">暂无通知</p>
      <article v-for="notification in data" :key="notification.id" class="card">
        <h3>{{ notification.title }}</h3>
        <p>{{ notification.content }}</p>
        <p>{{ notification.sendTime }}</p>
        <RouterLink v-if="notification.bizType === 'rec'" to="/achievements">查看成果</RouterLink>
        <button v-if="notification.isRead === '0'" :disabled="busy" @click="markRead(notification)">标为已读</button>
      </article>
    </section>
    <section v-else-if="data && route.name === 'course'" class="card">
      <h2>{{ data.courseName }}</h2>
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
      <ul><li v-for="task in data.tasks" :key="task.taskId">
        <RouterLink :to="'/task/' + task.taskId">{{ task.title }}</RouterLink>
        <span> · {{ statuses[task.submitStatus] }}</span>
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
      <p>截止时间 {{ data.task.deadline }} · {{ statuses[data.submission.status] }}</p>
      <p v-if="data.submission.returnReason">退回原因 {{ data.submission.returnReason }}</p>
      <p v-if="data.submission.resubmitDeadline">重交截止 {{ data.submission.resubmitDeadline }}</p>
      <div v-if="data.feedback?.reviewComment">
        <h3>教师评语</h3>
        <p class="multiline">{{ data.feedback.reviewComment }}</p>
      </div>
      <label>作业说明
        <textarea v-model="content" rows="5" :readonly="!data.submission.submittable"></textarea>
      </label>
      <ul><li v-for="(file, index) in files" :key="file.fileId">
        {{ file.fileName }}
        <button v-if="data.submission.submittable" :disabled="busy" @click="files.splice(index, 1)">移除</button>
      </li></ul>
      <label v-if="data.submission.submittable">添加附件 <input type="file" :disabled="busy" @change="upload"></label>
      <nav>
        <button v-if="data.submission.submittable && data.submission.status !== 4" :disabled="busy" @click="save('draft')">保存草稿</button>
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
      <h2>我的成绩</h2>
      <p v-if="!data.length">暂无已发布成绩</p>
      <ul><li v-for="score in data" :key="score.scoreId">
        <RouterLink :to="'/task/' + score.taskId">{{ score.taskTitle }}</RouterLink>
        <span> · {{ score.levelCode || score.finalScore }}</span>
      </li></ul>
    </section>
  </main>
</template>

<style scoped>
.learning { max-width: 46rem; margin: auto; }
nav { display: flex; flex-wrap: wrap; align-items: center; gap: 1rem; }
.fields, label { display: grid; gap: .65rem; }
input, textarea, button { font: inherit; padding: .5rem; }
button { cursor: pointer; }
button:disabled { cursor: default; }
textarea { width: 100%; }
img { max-width: 100%; }
video, audio, iframe { width: 100%; }
iframe { min-height: 24rem; }
li { margin-block: .5rem; }
.multiline { white-space: pre-wrap; overflow-wrap: anywhere; }
.error { color: var(--lt-danger); }
</style>
