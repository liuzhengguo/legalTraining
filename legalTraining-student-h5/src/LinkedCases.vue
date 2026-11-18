<script setup>
import { ref, computed, watch } from 'vue'
import { request } from './api'

const props = defineProps({ endpoint: { type: String, required: true } })
const references = ref([])
const detail = ref(null)
const detailError = ref('')
let selection = 0

// 把标题里的【实训】等前缀拆成徽章，正文作为标题
const caseList = computed(() => references.value.map(entry => {
  const match = /^【([^】]+)】\s*(.*)$/.exec(entry.title || '')
  return match
    ? { ...entry, tag: match[1], name: match[2] }
    : { ...entry, tag: '', name: entry.title || '' }
}))

watch(() => props.endpoint, async (endpoint, _, cleanup) => {
  let active = true
  selection++
  references.value = []
  detail.value = null
  detailError.value = ''
  cleanup(() => { active = false; selection++ })
  try {
    const result = await request(endpoint)
    if (active) references.value = result.data
  } catch { /* Cases are optional when the provider is unavailable. */ }
}, { immediate: true })

async function open(id) {
  const current = ++selection
  detail.value = null
  detailError.value = ''
  try {
    const { data } = await request(props.endpoint + '/' + encodeURIComponent(id))
    if (current !== selection) return
    if (data.degrade) detailError.value = '这条案例暂不可查看'
    else detail.value = data.case
  } catch {
    if (current === selection) detailError.value = '案例详情加载失败，请重试'
  }
}
</script>

<template>
  <section v-if="references.length" class="linked-cases" aria-label="相关案例">
    <h3>相关案例</h3>
    <ul class="case-list">
      <li v-for="entry in caseList" :key="entry.id">
        <button type="button" class="case-trigger" @click="open(entry.id)">
          <span class="case-icon" aria-hidden="true">📄</span>
          <span class="case-text">
            <span v-if="entry.tag" class="case-tag">{{ entry.tag }}</span>
            <span class="case-title">{{ entry.name }}</span>
          </span>
          <i class="case-arrow" aria-hidden="true" />
        </button>
      </li>
    </ul>
    <p v-if="detailError" class="case-error" role="status">{{ detailError }}</p>
    <article v-if="detail" class="case-detail">
      <h4>{{ detail.title }}</h4>
      <p v-if="detail.source" class="case-source">{{ detail.source }}</p>
      <p v-if="detail.summary" class="case-summary">{{ detail.summary }}</p>
      <p class="case-content">{{ detail.content }}</p>
    </article>
  </section>
</template>

<style scoped>
.linked-cases { margin-top: 1.15rem; }
.linked-cases h3 { margin: 0 0 .7rem; font-size: 1.05rem; font-weight: 700; color: var(--lt-primary); }
.case-list { list-style: none; padding: 0; margin: 0; display: grid; gap: .6rem; }
.case-trigger {
  display: flex; align-items: center; gap: .7rem; width: 100%;
  min-height: 3.4rem; padding: .7rem .85rem; text-align: left;
  border: 1px solid var(--lt-border); border-radius: var(--lt-radius);
  background: var(--lt-field); color: var(--lt-text); cursor: pointer;
  transition: border-color .15s, background .15s, transform .15s;
}
.case-trigger:hover { border-color: var(--lt-primary); background: var(--lt-tint); transform: translateY(-1px); }
.case-trigger:active { transform: translateY(0); }
.case-icon {
  display: grid; place-items: center; flex: none; width: 2.1rem; height: 2.1rem;
  border-radius: .55rem; background: var(--lt-tint); font-size: 1.1rem;
}
.case-trigger:hover .case-icon { background: var(--lt-primary); }
.case-text { display: flex; flex-wrap: wrap; align-items: center; gap: .3rem .5rem; flex: 1; min-width: 0; }
.case-tag {
  flex: none; padding: .1rem .45rem; border-radius: .35rem;
  background: var(--lt-primary); color: var(--lt-on-dark); font-size: .72rem; line-height: 1.6;
}
.case-title { font-size: .9rem; line-height: 1.4; overflow-wrap: anywhere; }
.case-arrow { flex: none; width: .5rem; height: .5rem; margin-right: .15rem; border-top: 2px solid var(--lt-primary); border-right: 2px solid var(--lt-primary); transform: rotate(45deg); }
.case-error { margin: .65rem 0 0; color: var(--lt-danger); font-size: .84rem; }
.case-detail {
  margin-top: .75rem; padding: .9rem 1rem;
  border: 1px solid var(--lt-border); border-radius: var(--lt-radius);
  background: var(--lt-card);
}
.case-detail h4 { margin: 0 0 .5rem; font-size: 1rem; color: var(--lt-primary); }
.case-source { margin: 0 0 .4rem; font-size: .82rem; color: var(--lt-sub); }
.case-summary { margin: 0 0 .6rem; font-size: .88rem; color: var(--lt-text); }
.case-content { margin: 0; font-size: .88rem; line-height: 1.75; color: var(--lt-text); white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
