<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PanoramaView from './PanoramaView.vue'
import GalleryImage from './GalleryImage.vue'

const route = useRoute()
const router = useRouter()
const api = import.meta.env.VITE_GALLERY_API || '/gallery-api'
const mediaOrigin = import.meta.env.VITE_GALLERY_MEDIA || ''
const zones = ref([])
const cards = ref([])
const detail = ref(null)
const caseReferences = ref([])
const caseDetail = ref(null)
let caseRequest = 0
const activeLiteVideos = ref(new Set())
const loading = ref(false)
const error = ref('')
const copied = ref('')
const showQr = ref(false)
const fullscreen = ref(false)
const presentation = ref(false)
const dismissWeak = ref(false)
const fullscreenAvailable = document.fullscreenEnabled
const spots = ref([])
const panoramaFailed = ref(false)
const weakNetwork = ref(false)
const assetFailures = ref(0)
const textScale = ref(Number(localStorage.getItem('lt-gallery-scale')) || 1)
const night = ref(localStorage.getItem('lt-gallery-night') !== 'false')
const lite = computed(() => route.name === 'lite' || route.name === 'liteItem' || route.name === 'liteZone')
const offlineMessage = computed(() => route.query.reason === 'link' ? '该分享链接暂不可用。'
  : route.query.reason === 'adjusted' ? '该展项已调整。' : '该展项已下架。')
const musicSource = imageUrl(import.meta.env.VITE_GALLERY_BGM_URL)
const music = ref(null)
const musicPlaying = ref(false)
const musicVolume = ref(.35)
const musicError = ref(false)
const floorPlan = imageUrl(import.meta.env.VITE_GALLERY_PLAN_URL)
const floorPlanOpen = ref(false)
const floorPlanError = ref(false)
const floorPlanPositions = readFloorPlanPositions(import.meta.env.VITE_GALLERY_PLAN_MARKERS)
const zone = computed(() => zones.value.find(entry => String(entry.zoneId) === route.params.zoneId))
const planZones = computed(() => zones.value.flatMap(entry => {
  const position = floorPlanPositions.get(entry.zoneCode)
  return position ? [{ ...entry, position }] : []
}))
const panorama = computed(() => !lite.value && !presentation.value && !panoramaFailed.value
  ? imageUrl(zone.value?.panoramaUrl) : '')
const zonePosition = computed(() => zones.value.findIndex(entry => entry.zoneId === zone.value?.zoneId))
const previousZone = computed(() => zones.value[zonePosition.value - 1])
const nextZone = computed(() => zones.value[zonePosition.value + 1])
const visibleCards = computed(() => zone.value ? cards.value.filter(card => card.zoneId === zone.value.zoneId) : cards.value)
const modePath = computed(() => lite.value ? '/lite' : '/immersive')
const switchMode = computed(() => {
  const base = lite.value ? '/immersive' : '/lite'
  const path = route.params.itemId ? base + '/item/' + route.params.itemId
    : route.params.zoneId ? base + '/zone/' + route.params.zoneId : base
  return { path, query: route.query.channel ? { channel: route.query.channel } : {} }
})
const siblings = computed(() => cards.value.filter(card => card.zoneId === detail.value?.zoneId))
const itemIndex = computed(() => siblings.value.findIndex(card => card.itemId === detail.value?.itemId))
const current = detail

async function get(path, signal) {
  const response = await fetch(api + path, { signal, cache: 'no-store' })
  if (response.status === 410) {
    const reason = await response.json().catch(() => ({}))
    return { offline: true, zoneId: reason.zoneId, reasonType: reason.reasonType }
  }
  if (!response.ok) throw new Error(response.status === 404 ? '内容暂不可访问' : '展厅加载失败，请刷新重试')
  return (await response.json()).data
}

let temporarySession
function visitSession() {
  let stored
  try {
    stored = sessionStorage.getItem('lt-gallery-session')
  } catch {
    return temporarySession ||= newSession()
  }
  if (/^[a-f0-9]{32}$/.test(stored || '')) return stored
  temporarySession ||= newSession()
  try {
    sessionStorage.setItem('lt-gallery-session', temporarySession)
  } catch {
    // Retain the session in memory when storage is disabled.
  }
  return temporarySession
}

function newSession() {
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), byte => byte.toString(16).padStart(2, '0')).join('')
}

watch(() => route.fullPath, async (_, __, cleanup) => {
  const controller = new AbortController()
  let visit
  const finishVisit = () => {
    if (!visit) return
    const { began, ...fields } = visit
    visit = null
    const duration = Math.min(3600, Math.round((performance.now() - began) / 1000))
    const body = JSON.stringify({ ...fields, duration })
    fetch(api + '/visits', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body, keepalive: true
    }).catch(() => {})
  }
  const beginVisit = (targetType, targetId) => {
    const channel = ['qrcode', 'link', 'terminal', 'portal'].includes(route.query.channel) ? route.query.channel : 'direct'
    const session = visitSession()
    visit = { targetType, targetId, channel, mode: lite.value ? 'lite' : 'immersive', session, began: performance.now() }
    window.addEventListener('pagehide', finishVisit)
  }
  const weakTimer = setTimeout(() => {
    if (!lite.value && loading.value) weakNetwork.value = true
  }, 5000)
  cleanup(() => {
    controller.abort()
    finishVisit()
    window.removeEventListener('pagehide', finishVisit)
    clearTimeout(weakTimer)
  })
  spots.value = []
  panoramaFailed.value = false
  weakNetwork.value = false
  dismissWeak.value = false
  if (route.name !== 'zone') presentation.value = false
  assetFailures.value = 0
  detail.value = null
  caseReferences.value = []
  caseDetail.value = null
  caseRequest++
  activeLiteVideos.value.clear()
  zones.value = []
  cards.value = []
  error.value = ''
  copied.value = ''
  showQr.value = false
  loading.value = route.name !== 'offline'
  try {
    if (route.name === 'offline') return
    if (route.params.itemId) {
      const item = await get('/items/' + encodeURIComponent(route.params.itemId), controller.signal)
      if (controller.signal.aborted) return
      if (item.offline) {
        return router.replace({ name: 'offline', query: {
          zoneId: item.zoneId || undefined, reason: item.reasonType === 'adjusted' ? 'adjusted' : undefined
        } })
      }
      detail.value = item
      beginVisit('item', item.itemId)
      if (item.caseReferences?.length) {
        get('/items/' + item.itemId + '/cases', controller.signal).then(entries => {
          if (!controller.signal.aborted) caseReferences.value = Array.isArray(entries) ? entries : []
        }).catch(() => {})
      }
      get('/items', controller.signal).then(entries => {
        if (!controller.signal.aborted) cards.value = entries
      }).catch(() => {})
    } else {
      const [nextZones, nextCards] = await Promise.all([get('/zones', controller.signal), get('/items', controller.signal)])
      if (controller.signal.aborted) return
      zones.value = nextZones
      cards.value = nextCards
      if (zone.value) beginVisit('zone', zone.value.zoneId)
      if (route.name === 'zone' && !zone.value) error.value = '展区暂不可访问'
      if (zone.value && !lite.value) {
        get('/zones/' + zone.value.zoneId + '/spots', controller.signal).then(entries => {
          if (!controller.signal.aborted) spots.value = entries
        }).catch(() => {})
      }

    }
  } catch (cause) {
    if (!controller.signal.aborted) error.value = cause.message
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}, { immediate: true })

watch(textScale, value => localStorage.setItem('lt-gallery-scale', String(value)))
watch(night, value => localStorage.setItem('lt-gallery-night', String(value)))

function syncFullscreen() {
  fullscreen.value = Boolean(document.fullscreenElement)
}

async function toggleFullscreen() {
  try {
    if (fullscreen.value) await document.exitFullscreen()
    else await document.documentElement.requestFullscreen()
  } catch {
    copied.value = '当前浏览器无法切换全屏'
  }
}

function presentationKeys(event) {
  if (!presentation.value || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return
  if (event.key === 'Escape') {
    presentation.value = false
    return
  }
  if (event.key === ' ' && event.target.closest('button, a, input, select, textarea')) return
  const target = event.key === 'ArrowLeft' ? previousZone.value
    : ['ArrowRight', ' '].includes(event.key) ? nextZone.value : null
  if (target) {
    event.preventDefault()
    router.push('/immersive/zone/' + target.zoneId)
  }
}

onMounted(() => {
  document.addEventListener('fullscreenchange', syncFullscreen)
  window.addEventListener('keydown', presentationKeys)
})
onUnmounted(() => {
  document.removeEventListener('fullscreenchange', syncFullscreen)
  window.removeEventListener('keydown', presentationKeys)
})

watch(lite, enabled => {
  if (enabled) music.value?.pause()
})

async function toggleMusic() {
  const player = music.value
  if (!player) return
  if (!player.paused) {
    player.pause()
    return
  }
  musicError.value = false
  try {
    await player.play()
  } catch {
    musicError.value = true
  }
}

function onPanoramaError() {
  panoramaFailed.value = true
  weakNetwork.value = true
}
function onAssetError() {
  assetFailures.value += 1
  if (assetFailures.value >= 2) weakNetwork.value = true
}

function imageUrl(value) {
  if (typeof value !== 'string') return ''
  if (value.startsWith('/profile/upload/')) return mediaOrigin + value
  return /^https:\/\//i.test(value) ? value : ''
}

function readFloorPlanPositions(value) {
  const positions = new Map()
  if (!value) return positions
  try {
    for (const [code, coordinates] of Object.entries(JSON.parse(value))) {
      if (Array.isArray(coordinates) && coordinates.length === 2
        && coordinates.every(point => Number.isFinite(point) && point >= 0 && point <= 1)) {
        positions.set(code, coordinates)
      }
    }
  } catch {
    // The zone directory still works when plan configuration is unavailable.
  }
  return positions
}

async function share() {
  const url = new URL('/lite/item/' + detail.value.itemId + '?channel=link', location.origin)
  try {
    await navigator.clipboard.writeText(url.href)
    copied.value = '链接已复制'
  } catch {
    copied.value = url.href
  }
}

async function openCase(id) {
  const current = ++caseRequest
  caseDetail.value = null
  try {
    const data = await get('/items/' + detail.value.itemId + '/cases/' + encodeURIComponent(id))
    if (current !== caseRequest) return
    if (!data || data.degrade) caseReferences.value = []
    else caseDetail.value = data.case
  } catch {
    if (current === caseRequest) caseReferences.value = []
  }
}
</script>

<template>
  <main class="zone-page" :class="{ 'lite-day': lite && !night, 'lite-item': lite && detail }">
    <audio v-if="musicSource && !lite" ref="music" :src="musicSource" :volume="musicVolume" loop preload="none"
      @play="musicPlaying = true" @pause="musicPlaying = false"
      @error="musicPlaying = false; musicError = true" />
    <header v-if="!presentation" class="gallery-header">
      <RouterLink to="/immersive">法律实训数字展厅</RouterLink>
      <nav>
        <RouterLink v-if="!lite || !detail" :to="switchMode">{{ lite ? '进入沉浸式展厅' : '图文模式' }}</RouterLink>
        <template v-if="lite">
          <label>字号
            <select v-model.number="textScale"><option :value="1">标准</option><option :value="1.2">大</option><option :value="1.4">特大</option></select>
          </label>
          <button @click="night = !night">{{ night ? '日间模式' : '夜间模式' }}</button>
        </template>
        <button v-else-if="fullscreenAvailable" @click="toggleFullscreen">{{ fullscreen ? '退出全屏' : '全屏浏览' }}</button>
        <template v-if="musicSource && !lite">
          <button type="button" :aria-pressed="musicPlaying" @click="toggleMusic">{{ musicPlaying ? '关闭背景音乐' : '播放背景音乐' }}</button>
          <label>背景音乐音量 <input v-model.number="musicVolume" type="range" min="0" max="1" step=".05"></label>
          <span v-if="musicError" role="status">背景音乐暂不可用</span>
        </template>
        <button v-if="zone && !lite" @click="presentation = true">讲解模式</button>
        <button v-if="floorPlan && planZones.length && !lite" type="button" @click="floorPlanOpen = !floorPlanOpen"
          :aria-expanded="floorPlanOpen" aria-controls="gallery-floor-plan">展馆平面图</button>
        <details v-if="!lite">
          <summary>浏览帮助</summary>
          <p>从展区目录选择展区，点击封面上的热点或展项卡片可继续浏览。</p>
        </details>
      </nav>
    </header>
    <section v-if="floorPlanOpen && !lite && !presentation && planZones.length" id="gallery-floor-plan" aria-label="展馆平面图">
      <p v-if="floorPlanError" role="status">平面图暂不可用，请使用展区目录。</p>
      <div v-else class="floor-plan">
        <img :src="floorPlan" alt="展馆平面图" @error="floorPlanError = true">
        <RouterLink v-for="entry in planZones" :key="entry.zoneId" :to="'/immersive/zone/' + entry.zoneId"
          :aria-current="entry.zoneId === (zone?.zoneId ?? detail?.zoneId) ? 'location' : undefined"
          :style="{ left: entry.position[0] * 100 + '%', top: entry.position[1] * 100 + '%' }">
          {{ entry.zoneName }}
        </RouterLink>
      </div>
    </section>
    <nav v-if="presentation" class="zone-links" aria-label="讲解控制">
      <RouterLink v-if="previousZone" :to="'/immersive/zone/' + previousZone.zoneId">上一展区</RouterLink>
      <RouterLink v-if="nextZone" :to="'/immersive/zone/' + nextZone.zoneId">下一展区</RouterLink>
      <RouterLink to="/immersive">返回首页</RouterLink>
      <button @click="presentation = false">退出讲解</button>
    </nav>
    <p v-if="copied && !detail" role="status">{{ copied }}</p>
    <p v-if="weakNetwork && !dismissWeak && !lite && !presentation" role="status">
      展厅资源加载较慢，<RouterLink :to="switchMode">切换图文简版</RouterLink>
      <button @click="dismissWeak = true">继续等待</button>
    </p>
    <p v-if="loading" role="status">加载中…</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <section v-else-if="route.name === 'offline'" class="item-card item-card__body">
      <h1>内容已下线</h1>
      <p>{{ offlineMessage }}</p>
      <nav class="zone-links" aria-label="下架后的浏览入口">
        <RouterLink to="/immersive">返回展厅首页</RouterLink>
        <RouterLink v-if="/^\d+$/.test(route.query.zoneId || '')" :to="'/lite/zone/' + route.query.zoneId">浏览同展区其他展项</RouterLink>
        <RouterLink v-else to="/lite">浏览其他展区和展项</RouterLink>
      </nav>
    </section>
    <article v-else-if="current" class="item-card exhibit-detail">
      <GalleryImage v-if="imageUrl(current.cover)" :source="imageUrl(current.cover)" :lazy="lite"
        class="item-card__cover" @unavailable="onAssetError" />
      <div class="item-card__body">
        <h1>{{ current.title }}</h1>
        <p v-if="current.authorName">{{ current.authorName }}</p>
        <p class="item-card__summary">{{ current.summary }}</p>
        <p class="exhibit-content" :style="{ fontSize: lite ? textScale + 'rem' : undefined }">{{ current.content }}</p>
        <div v-if="current.media?.length" class="media">
          <template v-for="entry in current.media" :key="entry.mediaId">
            <GalleryImage v-if="entry.type === 'image' && imageUrl(entry.url)"
              :source="(lite && imageUrl(entry.cover)) || imageUrl(entry.url)" lazy
              alt="展项图片" @unavailable="onAssetError" />
            <template v-else-if="entry.type === 'video' && imageUrl(entry.url)">
              <div v-if="lite && !activeLiteVideos.has(entry.mediaId)">
                <GalleryImage v-if="imageUrl(entry.cover)" :source="imageUrl(entry.cover)" lazy
                  alt="视频封面" @unavailable="onAssetError" />
                <button type="button" @click="activeLiteVideos.add(entry.mediaId)">播放视频</button>
              </div>
              <video v-else :src="imageUrl(entry.url)" :poster="imageUrl(entry.cover)" :autoplay="lite"
                     controls playsinline preload="none" @error="onAssetError" />
            </template>
            <audio v-else-if="!lite && entry.type === 'audio' && imageUrl(entry.url)" :src="imageUrl(entry.url)"
                   controls preload="none" @error="onAssetError" />
            <a v-else-if="entry.type === 'link' && imageUrl(entry.url)" :href="imageUrl(entry.url)"
               target="_blank" rel="noopener noreferrer">打开关联资料</a>
          </template>
        </div>
        <nav v-if="detail" class="zone-links" aria-label="展项导航">
          <RouterLink :to="modePath + '/zone/' + detail.zoneId">返回展区目录</RouterLink>
          <RouterLink v-if="itemIndex > 0" :to="modePath + '/item/' + siblings[itemIndex - 1].itemId">上一展项</RouterLink>
          <RouterLink v-if="itemIndex >= 0 && siblings[itemIndex + 1]" :to="modePath + '/item/' + siblings[itemIndex + 1].itemId">下一展项</RouterLink>
        </nav>
        <section v-if="caseReferences.length" aria-label="相关案例">
          <h2>相关案例</h2>
          <ul><li v-for="entry in caseReferences" :key="entry.id"><button @click="openCase(entry.id)">{{ entry.title }}</button></li></ul>
          <article v-if="caseDetail">
            <h3>{{ caseDetail.title }}</h3>
            <p v-if="caseDetail.source">{{ caseDetail.source }}</p>
            <p v-if="caseDetail.summary">{{ caseDetail.summary }}</p>
            <p class="case-text">{{ caseDetail.content }}</p>
          </article>
        </section>
        <button v-if="detail" @click="share">复制分享链接</button>
        <button v-if="detail" @click="showQr = !showQr" :aria-expanded="showQr">{{ showQr ? '收起二维码' : '分享二维码' }}</button>
        <p v-if="showQr && detail"><img :src="api + '/items/' + detail.itemId + '/qr.png'" width="256" height="256" alt="展项分享二维码"></p>
        <p v-if="copied" role="status" class="copy-result">{{ copied }}</p>
      </div>
    </article>
    <template v-else>
      <section v-if="!zone && !lite && zones.length" class="item-card item-card__body">
        <h1>中央大厅</h1>
        <RouterLink :to="'/immersive/zone/' + zones[0].zoneId">开始漫游</RouterLink>
      </section>
      <template v-if="panorama">
        <h1>{{ zone.zoneName }}</h1>
        <PanoramaView :key="panorama" :source="panorama" :spots="spots" @error="onPanoramaError" />
      </template>
      <section v-else-if="zone" class="zone-hero">
        <GalleryImage v-if="imageUrl(zone.cover)" :source="imageUrl(zone.cover)" :lazy="lite"
          class="item-card__cover" @unavailable="onAssetError" />
        <div class="zone-hero__mask"></div>
        <h1 class="zone-hero__title">{{ zone.zoneName }}</h1>
        <RouterLink v-for="spot in presentation ? [] : spots" :key="spot.spotId" class="zone-spot"
          :aria-label="spot.spotName" :title="spot.spotName"
          :style="{ left: spot.posX * 100 + '%', top: spot.posY * 100 + '%' }"
          :to="'/immersive/' + spot.targetType + '/' + spot.targetId" />
      </section>
      <p v-if="zone?.intro">{{ zone.intro }}</p>
      <nav v-if="!presentation" class="zone-links" aria-label="展区导览">
        <RouterLink v-if="zone && previousZone" :to="modePath + '/zone/' + previousZone.zoneId">上一展区</RouterLink>
        <RouterLink v-if="zone && nextZone" :to="modePath + '/zone/' + nextZone.zoneId">下一展区</RouterLink>
        <RouterLink v-for="entry in zones" :key="entry.zoneId" :to="modePath + '/zone/' + entry.zoneId"
          :aria-current="entry.zoneId === zone?.zoneId ? 'location' : undefined">{{ entry.zoneName }}</RouterLink>
      </nav>
      <p v-if="!visibleCards.length">暂无开放展项</p>
      <section v-else :class="'zone-layout--' + (zone?.layout || (lite ? 'flow' : 'grid'))">
        <component :is="presentation ? 'article' : 'RouterLink'" v-for="card in visibleCards" :key="card.itemId" :to="presentation ? undefined : modePath + '/item/' + card.itemId" class="item-card">
          <GalleryImage v-if="imageUrl(card.cover)" :source="imageUrl(card.cover)" lazy
            class="item-card__cover" @unavailable="onAssetError" />
          <div class="item-card__body">
            <h2 class="item-card__title">{{ card.title }}</h2>
            <p class="item-card__summary">{{ card.summary }}</p>
          </div>
        </component>
      </section>
    </template>
    <div v-if="lite && detail" class="lite-mode-dock">
      <RouterLink :to="switchMode">进入沉浸式展厅</RouterLink>
    </div>
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
.media { display: grid; gap: 1rem; }
.media img, .media video { max-width: 100%; }
.gallery-header nav { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; }
.zone-spot:focus-visible { outline: 3px solid var(--ex-text); }
.case-text { white-space: pre-wrap; overflow-wrap: anywhere; }
.lite-item { padding-bottom: 6rem; }
.lite-mode-dock { position: fixed; z-index: 20; inset: auto 0 0; display: flex; justify-content: center;
  padding: .65rem 1rem calc(.65rem + env(safe-area-inset-bottom)); background: var(--ex-panel);
  box-shadow: 0 -1px 0 currentColor; }
.lite-mode-dock a { padding: .45rem 1rem; }
.floor-plan { position: relative; margin: 0 auto 1rem; max-width: 52rem; }
.floor-plan img { display: block; width: 100%; }
.floor-plan a { position: absolute; transform: translate(-50%, -50%); padding: .35rem .55rem;
  color: var(--ex-text); background: var(--ex-panel); border: 1px solid currentColor; border-radius: .35rem; }
.floor-plan a[aria-current] { outline: 3px solid currentColor; font-weight: 700; }
.lite-day { --ex-panel: #fff; --ex-text: #172437; --ex-sub: #425169; background: #edf4fa; color: var(--ex-text); }
@media (prefers-reduced-motion: reduce) {
  .item-card { transition: none; }
}
</style>
