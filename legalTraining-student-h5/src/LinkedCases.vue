<script setup>
import { ref, watch } from 'vue'
import { request } from './api'

const props = defineProps({ endpoint: { type: String, required: true } })
const references = ref([])
const detail = ref(null)
const detailError = ref('')
let selection = 0

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
  <section v-if="references.length" aria-label="相关案例">
    <h3>相关案例</h3>
    <ul><li v-for="entry in references" :key="entry.id"><button @click="open(entry.id)">{{ entry.title }}</button></li></ul>
    <p v-if="detailError" role="status">{{ detailError }}</p>
    <article v-if="detail">
      <h4>{{ detail.title }}</h4>
      <p v-if="detail.source">{{ detail.source }}</p>
      <p v-if="detail.summary">{{ detail.summary }}</p>
      <p class="case-text">{{ detail.content }}</p>
    </article>
  </section>
</template>

<style scoped>
.case-text { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
