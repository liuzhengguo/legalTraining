<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const api = import.meta.env.VITE_GALLERY_API || '/gallery-api'
const mediaOrigin = import.meta.env.VITE_GALLERY_MEDIA || ''
const zones = ref([])
const cards = ref([])
const detail = ref(null)
const loading = ref(false)
const error = ref('')
const copied = ref('')
const index = ref(0)
const lite = computed(() => route.name === 'lite' || route.name === 'liteItem')
const kiosk = computed(() => route.name === 'kiosk')
const zone = computed(() => zones.value.find(entry => String(entry.zoneId) === route.params.zoneId))
const visibleCards = computed(() => zone.value ? cards.value.filter(card => card.zoneId === zone.value.zoneId) : cards.value)
const current = computed(() => kiosk.value ? cards.value[index.value] : detail.value)

async function get(path, signal) {
  const response = await fetch(api + path, { signal, cache: 'no-store' })
  if (response.status === 410) return null
  if (!response.ok) throw new Error(response.status === 404 ? '内容暂不可访问' : '展厅加载失败，请刷新重试')
  return (await response.json()).data
}

watch(() => route.fullPath, async (_, __, cleanup) => {
  const controller = new AbortController()
  let timer
  cleanup(() => { controller.abort(); clearInterval(timer) })
  detail.value = null
  zones.value = []
  cards.value = []
  index.value = 0
  error.value = ''
  copied.value = ''
  loading.value = route.name !== 'offline'
  try {
    if (route.name === 'offline') return
    if (route.params.itemId) {
      const item = await get('/items/' + encodeURIComponent(route.params.itemId), controller.signal)
      if (controller.signal.aborted) return
      if (!item) return router.replace('/offline')
      detail.value = item
    } else {
      const [nextZones, nextCards] = await Promise.all([get('/zones', controller.signal), get('/items', controller.signal)])
      if (controller.signal.aborted) return
      zones.value = nextZones
      cards.value = nextCards
      if (route.name === 'zone' && !zone.value) error.value = '展区暂不可访问'
      if (kiosk.value) {
        timer = setInterval(async () => {
          try {
            const items = await get('/items', controller.signal)
            if (controller.signal.aborted) return
            cards.value = items
            index.value = items.length ? (index.value + 1) % items.length : 0
            error.value = ''
          } catch {
            if (!controller.signal.aborted) {
              cards.value = []
              error.value = '展厅连接中断，正在重试'
            }
          }
        }, 12000)
      }
    }
  } catch (cause) {
    if (!controller.signal.aborted) error.value = cause.message
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}, { immediate: true })

function imageUrl(value) {
  if (typeof value !== 'string') return ''
  if (value.startsWith('/profile/upload/')) return mediaOrigin + value
  return /^https:\/\//i.test(value) ? value : ''
}

async function share() {
  const url = new URL('/lite/item/' + detail.value.itemId, location.origin)
  try {
    await navigator.clipboard.writeText(url.href)
    copied.value = '链接已复制'
  } catch {
    copied.value = url.href
  }
}
</script>

<template>
  <main class="zone-page">
    <header class="gallery-header">
      <RouterLink to="/immersive">法律实训数字展厅</RouterLink>
      <nav v-if="!kiosk">
        <RouterLink :to="detail ? (lite ? '/immersive/item/' : '/lite/item/') + detail.itemId : lite ? '/immersive' : '/lite'">{{ lite ? '沉浸模式' : '图文模式' }}</RouterLink>
      </nav>
    </header>
    <p v-if="loading" role="status">加载中…</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <section v-else-if="route.name === 'offline'" class="item-card item-card__body">
      <h1>内容已下架</h1>
      <nav class="zone-links" aria-label="下架后的浏览入口">
        <RouterLink to="/immersive">返回展厅首页</RouterLink>
        <RouterLink to="/lite">浏览其他展区和展项</RouterLink>
      </nav>
    </section>
    <article v-else-if="current" class="item-card exhibit-detail">
      <img v-if="imageUrl(current.cover)" :src="imageUrl(current.cover)" alt="" class="item-card__cover">
      <div class="item-card__body">
        <h1>{{ current.title }}</h1>
        <p v-if="current.authorName">{{ current.authorName }}</p>
        <p class="item-card__summary">{{ current.summary }}</p>
        <p v-if="!kiosk" class="exhibit-content">{{ current.content }}</p>
        <button v-if="detail" @click="share">复制分享链接</button>
        <p v-if="copied" role="status" class="copy-result">{{ copied }}</p>
      </div>
    </article>
    <template v-else>
      <section v-if="zone" class="zone-hero">
        <img v-if="imageUrl(zone.cover)" :src="imageUrl(zone.cover)" alt="" class="item-card__cover">
        <div class="zone-hero__mask"></div>
        <h1 class="zone-hero__title">{{ zone.zoneName }}</h1>
      </section>
      <p v-if="zone?.intro">{{ zone.intro }}</p>
      <nav v-if="!zone && !lite && !kiosk" class="zone-links" aria-label="展区导览">
        <RouterLink v-for="entry in zones" :key="entry.zoneId" :to="'/immersive/zone/' + entry.zoneId">{{ entry.zoneName }}</RouterLink>
      </nav>
      <p v-if="!visibleCards.length">暂无开放展项</p>
      <section v-else :class="'zone-layout--' + (zone?.layout || (lite ? 'flow' : 'grid'))">
        <RouterLink v-for="card in visibleCards" :key="card.itemId" :to="(lite ? '/lite/item/' : '/immersive/item/') + card.itemId" class="item-card">
          <img v-if="imageUrl(card.cover)" :src="imageUrl(card.cover)" alt="" class="item-card__cover" loading="lazy">
          <div class="item-card__body">
            <h2 class="item-card__title">{{ card.title }}</h2>
            <p class="item-card__summary">{{ card.summary }}</p>
          </div>
        </RouterLink>
      </section>
    </template>
  </main>
</template>

<style scoped>
.gallery-header, .zone-links { display: flex; flex-wrap: wrap; gap: 1rem; padding: 1.5rem 0; }
.gallery-header { justify-content: space-between; align-items: center; font-weight: 700; }
a { color: inherit; }
.exhibit-detail { max-width: 60rem; margin-inline: auto; }
.exhibit-content, .copy-result { white-space: pre-wrap; overflow-wrap: anywhere; user-select: text; }
button { font: inherit; padding: .45rem .8rem; cursor: pointer; }
.zone-hero img { height: 100%; }
@media (prefers-reduced-motion: reduce) {
  .item-card { transition: none; }
}
</style>
