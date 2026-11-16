<script setup>
import { ref, watch } from 'vue'
import { request } from './api'

const props = defineProps({ endpoint: { type: String, required: true } })
const references = ref([])
const detail = ref(null)
let selection = 0

watch(() => props.endpoint, async (endpoint, _, cleanup) => {
  let active = true
  selection++
  references.value = []
  detail.value = null
  cleanup(() => { active = false; selection++ })
  try {
    const result = await request(endpoint)
    if (active) references.value = result.data
  } catch { /* Cases are optional when the provider is unavailable. */ }
}, { immediate: true })

async function open(id) {
  const current = ++selection
  detail.value = null
  try {
    const { data } = await request(props.endpoint + '/' + encodeURIComponent(id))
    if (current !== selection) return
    if (data.degrade) references.value = []
    else detail.value = data.case
  } catch {
    if (current === selection) references.value = []
  }
}
</script>

<template>
  <section v-if="references.length" aria-label="相关案例">
    <h3>相关案例</h3>
    <ul><li v-for="entry in references" :key="entry.id"><button @click="open(entry.id)">{{ entry.title }}</button></li></ul>
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
