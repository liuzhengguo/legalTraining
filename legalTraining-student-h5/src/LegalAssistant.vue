<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { profileImageUrl, request } from './api'
import assistantAvatar from './assets/xiaolvfa-avatar.jpg'

const props = defineProps({ student: Object })
const assistantName = '小律法'
const studentName = computed(() => props.student?.nickName || props.student?.userName || '我')
const studentAvatar = computed(() => profileImageUrl(props.student?.avatar))
const avatarFailed = ref(false)
watch(studentAvatar, () => { avatarFailed.value = false })

const question = ref('')
const records = ref([])
const sessions = ref([])
const sessionId = ref(null)
const showHistory = ref(false)
const loading = ref(false)
const sending = ref(false)
const error = ref('')
const transcript = ref(null)

function revealLatest() {
  nextTick(() => {
    const panel = transcript.value
    if (panel) panel.scrollTop = panel.scrollHeight
  })
}

async function refreshSessions() {
  sessions.value = (await request('/edu/assistant/sessions')).data
}

async function openHistory() {
  showHistory.value = !showHistory.value
  if (!showHistory.value) return
  if (transcript.value) transcript.value.scrollTop = 0
  try {
    await refreshSessions()
  } catch (cause) {
    error.value = cause.message
  }
}

function newSession() {
  sessionId.value = null
  records.value = []
  question.value = ''
  error.value = ''
  showHistory.value = false
}

async function load(id) {
  loading.value = true
  error.value = ''
  try {
    const { data } = await request('/edu/assistant/history?sessionId=' + id)
    sessionId.value = id
    records.value = data
    question.value = ''
    showHistory.value = false
    revealLatest()
  } catch (cause) {
    error.value = cause.message
  } finally {
    loading.value = false
  }
}

async function send() {
  const text = question.value.trim()
  if (!text || sending.value || loading.value) return
  sending.value = true
  error.value = ''
  try {
    const { data } = await request('/edu/assistant/question', { method: 'POST', body: { question: text, sessionId: sessionId.value } })
    sessionId.value = data.sessionId
    records.value.push(data)
    question.value = ''
  } catch (cause) {
    error.value = cause.message
  } finally {
    sending.value = false
    revealLatest()
  }
}

onMounted(async () => {
  loading.value = true
  try {
    await refreshSessions()
    if (sessions.value.length) await load(sessions.value[0].sessionId)
  } catch (cause) {
    error.value = cause.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="assistant" aria-label="法律助手问答">
    <div class="session-actions">
      <button type="button" :disabled="loading || sending" @click="newSession"><van-icon name="plus" /> 新建会话</button>
      <button type="button" :aria-expanded="showHistory" :disabled="loading || sending" @click="openHistory">
        <van-icon name="clock-o" /> 历史会话
      </button>
    </div>
    <div ref="transcript" class="transcript">
    <section v-if="showHistory" class="sessions" aria-label="历史会话">
      <p v-if="!sessions.length" class="empty">暂无历史会话</p>
      <button v-for="entry in sessions" :key="entry.sessionId" type="button" :disabled="loading || sending"
        :aria-current="entry.sessionId === sessionId ? 'true' : undefined" @click="load(entry.sessionId)">
        <span>{{ entry.title }}</span>
        <small>{{ entry.updateTime }}</small>
      </button>
    </section>
    <div class="assistant-identity">
      <img :src="assistantAvatar" alt="" width="40" height="40">
      <strong>{{ assistantName }}</strong>
    </div>
    <p class="notice">AI 回答供实训学习参考，提问和回答会留存，请勿填写个人敏感信息。</p>
    <p v-if="loading" role="status">正在加载问答记录…</p>
    <p v-else-if="!records.length && !error" class="empty">可以询问法律概念、案例分析或文书写作。</p>
    <div class="conversation" aria-live="polite">
      <article v-for="item in records" :key="item.recordId" class="exchange">
        <div class="chat-row chat-row--student">
          <span class="chat-avatar">
            <img v-if="studentAvatar && !avatarFailed" :src="studentAvatar" alt="" @error="avatarFailed = true">
            <van-icon v-else name="manager" aria-hidden="true" />
          </span>
          <div class="chat-copy">
            <span class="chat-name">{{ studentName }}</span>
            <p class="question">{{ item.question }}</p>
          </div>
        </div>
        <div class="chat-row">
          <img class="chat-avatar" :src="assistantAvatar" alt="">
          <div class="chat-copy">
            <span class="chat-name">{{ assistantName }}</span>
            <p v-if="item.answer" class="answer">{{ item.answer }}</p>
            <p v-else class="answer message">{{ item.errorMessage || '这条问题尚未收到回答。' }}</p>
          </div>
        </div>
      </article>
      <div v-if="sending" class="chat-row" role="status">
        <img class="chat-avatar" :src="assistantAvatar" alt="">
        <div class="chat-copy">
          <span class="chat-name">{{ assistantName }}</span>
          <p class="answer">正在回答…</p>
        </div>
      </div>
    </div>
    </div>
    <form class="composer" @submit.prevent="send">
      <label for="legal-question">你的问题</label>
      <textarea id="legal-question" v-model="question" rows="2" maxlength="2000" placeholder="例如，合同审查通常要看哪些条款？" :disabled="sending" required />
      <button type="submit" :disabled="loading || sending || !question.trim()">{{ sending ? '发送中…' : '发送' }}</button>
      <p v-if="error" class="message composer-error" role="alert">{{ error }}</p>
    </form>
  </section>
</template>

<style scoped>
.assistant { display: flex; flex: 1; min-width: 0; min-height: 0; flex-direction: column; }
.transcript { flex: 1; min-height: 0; overflow-y: auto; overscroll-behavior-y: contain; padding: 0 1rem .75rem; }
.notice { border-radius: .75rem; padding: .65rem .8rem; margin: 0 0 .8rem; background: var(--lt-tint); color: var(--lt-text); font-size: .82rem; }
.empty { color: var(--lt-sub); }
.session-actions {
  display: grid; grid-template-columns: 1fr 1fr; gap: .55rem;
  padding: .65rem 1rem .45rem; background: var(--lt-field);
}
.session-actions button:last-child, .sessions button { color: var(--lt-primary); background: var(--lt-card); border: 1px solid var(--lt-border); }
.sessions { display: grid; gap: .35rem; margin-bottom: .75rem; }
.sessions button { display: grid; justify-items: start; padding: .5rem .7rem; text-align: left; overflow-wrap: anywhere; }
.sessions button[aria-current] { border-color: var(--lt-primary); background: var(--lt-tint); }
.sessions small { color: var(--lt-sub); }
.assistant-identity {
  display: flex; align-items: center; gap: .65rem; margin: .35rem 0 .65rem;
  color: var(--lt-primary); font-size: 1.04rem;
}
.assistant-identity img { border-radius: 50%; object-fit: cover; }
.exchange { display: grid; gap: .7rem; margin-bottom: .9rem; }
.chat-row { display: flex; align-items: flex-start; gap: .45rem; }
.chat-row--student { flex-direction: row-reverse; }
.chat-avatar {
  display: grid; place-items: center; width: 2.15rem; height: 2.15rem; flex: none;
  border-radius: 50%; overflow: hidden; object-fit: cover; color: var(--lt-primary); background: var(--lt-badge);
}
.chat-avatar img { width: 100%; height: 100%; object-fit: cover; }
.chat-copy { display: grid; min-width: 0; max-width: calc(100% - 2.6rem); justify-items: start; gap: .2rem; }
.chat-row--student .chat-copy { justify-items: end; }
.chat-name { color: var(--lt-sub); font-size: .76rem; overflow-wrap: anywhere; }
.question, .answer { margin: 0; padding: .72rem .85rem; border-radius: .8rem; white-space: pre-wrap; overflow-wrap: anywhere; }
.question { background: var(--lt-nav); color: var(--lt-on-dark); }
.answer { background: var(--lt-card); border: 1px solid var(--lt-border); }
.message { color: var(--lt-danger); }
.composer {
  flex: none; display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: end;
  gap: .35rem .55rem; padding: .65rem 1rem calc(.75rem + var(--lt-safe-bottom));
  border-top: 1px solid var(--lt-border); background: var(--lt-card);
}
.composer label, .composer-error { grid-column: 1 / -1; font-size: .8rem; }
.composer-error { margin: 0; }
.composer button { min-width: 3.65rem; padding-inline: .6rem; }
textarea { width: 100%; resize: none; padding: .5rem .65rem; border: 1px solid var(--lt-border); border-radius: .7rem; background: var(--lt-field); color: inherit; font: inherit; font-size: 16px; }
button { border: 0; border-radius: .7rem; min-height: 44px; color: var(--lt-on-dark); background: var(--lt-primary); font: inherit; cursor: pointer; }
button:disabled { opacity: .55; cursor: default; }
</style>
