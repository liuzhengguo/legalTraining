<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PanoramaView from './PanoramaView.vue'
import GalleryImage from './GalleryImage.vue'
import lobbyImage from './assets/lobby.webp'
import lobbyPanorama from './assets/lobby-panorama.png'
import courtPanorama from './assets/panoramas/court.png'
import achievementPanorama from './assets/panoramas/achievement.png'
import documentPanorama from './assets/panoramas/document.png'
import mediationPanorama from './assets/panoramas/mediation.png'
import compliancePanorama from './assets/panoramas/compliance.png'
import exhibitionArchitecture from './assets/exhibition-architecture.webp'
import courtHallScene from './assets/hall-court.webp'
import achievementHallScene from './assets/hall-achievement.webp'
import documentHallScene from './assets/hall-document.webp'
import mediationHallScene from './assets/hall-mediation.webp'
import complianceHallScene from './assets/hall-compliance.webp'

const route = useRoute()
const router = useRouter()
const api = import.meta.env.VITE_GALLERY_API || '/gallery-api'
const RESOURCE_TIMEOUT_MS = 5000
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
const shareCard = ref(null)
const shareCardOpen = ref(false)
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
const lobby = computed(() => route.name === 'immersive' || route.name === 'lite')
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
function themeForName(name = '') {
  if (name.includes('调解')) return 'mediation'
  if (name.includes('合规')) return 'compliance'
  if (name.includes('模拟法庭') || name.includes('法庭')) return 'court'
  if (name.includes('普法成果') || name.includes('成果')) return 'achievement'
  if (name.includes('法律文书') || name.includes('文书')) return 'document'
  return ''
}
const hallSceneImages = {
  court: courtHallScene,
  achievement: achievementHallScene,
  document: documentHallScene,
  mediation: mediationHallScene,
  compliance: complianceHallScene
}
// 各主题的全景 VR 兜底素材（展区未配置 panoramaUrl 时按主题选用）
const hallPanoramaImages = {
  court: courtPanorama,
  achievement: achievementPanorama,
  document: documentPanorama,
  mediation: mediationPanorama,
  compliance: compliancePanorama
}
const zoneTheme = computed(() => themeForName(zone.value?.zoneName))
function hallSceneFor(entry) {
  return hallSceneImages[themeForName(entry?.zoneName)] || lobbyImage
}
function zoneById(id) {
  return zones.value.find(entry => String(entry.zoneId) === String(id))
}
watch(zoneTheme, value => {
  if (value && !lite.value) document.body.dataset.hall = value
  else delete document.body.dataset.hall
})
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
const zoneCounts = computed(() => {
  const counts = new Map()
  for (const card of cards.value) counts.set(card.zoneId, (counts.get(card.zoneId) || 0) + 1)
  return counts
})
const lobbySelection = ref(-1)
const vrActive = ref(false)
const vrSceneId = ref('')
const vrError = ref(false)
const vrSpots = ref([])
const vrScenes = computed(() => {
  const central = imageUrl(import.meta.env.VITE_GALLERY_LOBBY_PANORAMA_URL) || lobbyPanorama
  return [
    { id: 'lobby', name: '中央大厅', source: central },
    ...zones.value.map(entry => {
      // 优先用后台配置的全景图；未配置时按展区主题选用兜底全景素材
      const source = imageUrl(entry.panoramaUrl)
        || hallPanoramaImages[themeForName(entry.zoneName)]
        || central
      return { id: String(entry.zoneId), name: entry.zoneName, source }
    })
  ]
})
const vrScene = computed(() => vrScenes.value.find(entry => entry.id === vrSceneId.value))
watch(vrSceneId, async (id, _, onCleanup) => {
  vrError.value = false
  vrSpots.value = []
  if (!id || id === 'lobby') return
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  try {
    const entries = await get('/zones/' + id + '/spots', controller.signal)
    if (!controller.signal.aborted && Array.isArray(entries)) vrSpots.value = entries
  } catch {
    // The scene can still be viewed if the hotspot request fails.
  }
})
watch(() => route.fullPath, () => closePanorama())
const sceneCarousel = ref(null)
const sceneMap = ref(null)
const planningDialog = ref(null)
const planningOpen = ref(false)
const planningSyncFailed = ref(false)
const planningPage = ref(0)
const planRooms = [
  { labelY: 17, pin: [17, 39], route: [[27, 55], [18, 56], [17, 39]] },
  { labelY: 17, pin: [38, 39], route: [[18, 56], [37, 50], [38, 39]] },
  { labelY: 15, pin: [61, 36], route: [[37, 50], [61, 44], [61, 36]] },
  { labelY: 18, pin: [84, 36], route: [[61, 44], [84, 42], [84, 36]] },
  { labelY: 80, pin: [69, 56], route: [[84, 42], [58, 46], [69, 56]] }
]
const planningPageCount = computed(() => Math.max(1, Math.ceil(zones.value.length / planRooms.length)))
const visiblePlanningRooms = computed(() => {
  const start = planningPage.value * planRooms.length
  return zones.value.slice(start, start + planRooms.length).map((entry, index) => ({
    entry, room: planRooms[index], number: String(start + index + 1).padStart(2, '0')
  }))
})
const planningLines = computed(() => {
  const points = [[45, 77], [44, 57], ...visiblePlanningRooms.value.flatMap(({ room }) => room.route)]
  return points.slice(1).map(([x, y], index) => {
    const [left, top] = points[index]
    const dx = x - left
    const dy = (y - top) * 2 / 3
    return { left: left + '%', top: top + '%', width: Math.hypot(dx, dy) + '%',
      transform: 'rotate(' + Math.atan2(dy, dx) + 'rad)' }
  })
})
const sceneIcons = {
  museum: 'M2 8 12 3l10 5H2Zm3 3v7m5-7v7m4-7v7m5-7v7M3 21h18',
  pin: 'M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0ZM12 8a2 2 0 1 0 0 4 2 2 0 0 0 0-4Z',
  grid: 'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z',
  fullscreen: 'M9 3H3v6m0-6 7 7m5-7h6v6m0-6-7 7M3 15v6h6m-6 0 7-7m11 1v6h-6m6 0-7-7',
  vr: 'M4 6h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-4l-4-3-4 3H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2Zm3 6h1m8 0h1',
  music: 'M3 9h4l5-4v14l-5-4H3zM16 9a4 4 0 0 1 0 6m3-9a8 8 0 0 1 0 12',
  share: 'M6 12l12-7M6 12l12 7M6 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6Zm12-7a3 3 0 1 0 0 6 3 3 0 0 0 0-6Zm0 14a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z',
  home: 'm2 11 10-8 10 8M5 10v10h5v-6h4v6h5V10',
  results: 'M3 21h18M6 17v-5m6 5V5m6 12V9'
}
const selectedLobbyZone = computed(() => lobbySelection.value >= 0 ? zones.value[lobbySelection.value] || null : null)
// 小地图与规划图共用同一底图与坐标系：展区用 planRooms 的 pin，中央大厅定位在入口处
const miniMapPinStyle = computed(() => {
  const index = lobbySelection.value
  const [x, y] = index < 0 ? [45, 80] : (planRooms[index % planRooms.length]?.pin || [45, 80])
  return { left: x + '%', top: y + '%' }
})
const lobbyZones = computed(() => {
  const current = Math.max(0, lobbySelection.value)
  const start = Math.max(0, Math.min(current - 1, zones.value.length - 3))
  return zones.value.slice(start, start + 3)
})
function stepLobby(direction) {
  if (!zones.value.length) return
  lobbySelection.value = (Math.max(0, lobbySelection.value) + direction + zones.value.length) % zones.value.length
}
function focusZoneSelection() {
  if (!zones.value.length) return
  // 中央大厅（未选中展区）时切到第一个展区，否则依次切到下一个并循环
  lobbySelection.value = lobbySelection.value < 0 ? 0 : (lobbySelection.value + 1) % zones.value.length
  nextTick(() => {
    const active = sceneCarousel.value?.querySelector('[aria-pressed="true"]')
    active?.focus()
    active?.scrollIntoView?.({ block: 'nearest', inline: 'center', behavior: 'smooth' })
  })
}
// 回到展厅首页（中央大厅）：切换展区只改了本地状态、路由仍是 /immersive，
// 需显式重置选中态，否则点在 /immersive 上的"展厅首页"链接不会触发任何变化
function goLobbyHome() {
  lobbySelection.value = -1
  if (typeof window !== 'undefined') window.scrollTo({ top: 0, behavior: 'smooth' })
}
const sceneSigns = computed(() => hallTheme.value ? [] : lobbyZones.value.map((entry, index) => ({ entry, index })))
// 首页场景：中央大厅（默认）或所选子展厅的独立场景
const hallTheme = computed(() => themeForName(selectedLobbyZone.value?.zoneName))
const sceneTitle = computed(() => selectedLobbyZone.value?.zoneName || '走进法治文化数字空间')
const sceneSubtitle = computed(() => selectedLobbyZone.value?.intro
  || (selectedLobbyZone.value ? '点击下方按钮进入展厅' : '数字普法教育 · 普法成果展示 · 法律文化博物馆'))
function thumbnailStyle(entry) {
  return { backgroundImage: 'url(' + planningCover(entry) + ')', backgroundSize: 'cover', backgroundPosition: 'center 54%' }
}

function planningCover(entry) {
  return hallSceneImages[themeForName(entry.zoneName)] || sceneCardCover(entry.cover) || lobbyImage
}

function openPlanning() {
  if (!planningDialog.value || planningDialog.value.open) return
  planningSyncFailed.value = false
  planningPage.value = Math.floor(Math.max(0, lobbySelection.value) / planRooms.length)
  planningDialog.value.showModal()
  planningOpen.value = true
}

function closePlanning() {
  planningOpen.value = false
  planningDialog.value?.close()
}

watch(() => route.fullPath, closePlanning)
watch(planningOpen, (open, _, onCleanup) => {
  if (!open) return
  const controller = new AbortController()
  async function refreshZones() {
    if (loading.value) return
    try {
      const entries = await get('/zones', controller.signal)
      if (controller.signal.aborted) return
      if (!Array.isArray(entries)) throw new Error('展区数据无效')
      const selectedId = selectedLobbyZone.value?.zoneId
      zones.value = entries
      lobbySelection.value = entries.findIndex(entry => entry.zoneId === selectedId)
      planningPage.value = Math.min(planningPage.value, planningPageCount.value - 1)
      planningSyncFailed.value = false
    } catch {
      if (!controller.signal.aborted) planningSyncFailed.value = true
    }
  }
  refreshZones()
  const timer = setInterval(refreshZones, 10000)
  onCleanup(() => {
    clearInterval(timer)
    controller.abort()
  })
})

function openPanorama() {
  const selectedId = String(selectedLobbyZone.value?.zoneId)
  vrSceneId.value = (vrScenes.value.find(entry => entry.id === selectedId) || vrScenes.value[0])?.id || ''
  vrActive.value = true
}

function closePanorama() {
  vrActive.value = false
  vrSceneId.value = ''
}

const sceneTools = [
  { icon: 'fullscreen', label: () => fullscreen.value ? '退出全屏' : '全屏浏览', action: () => fullscreenAvailable ? toggleFullscreen() : (copied.value = '当前浏览器无法切换全屏') },
  { icon: 'vr', label: () => 'VR 全景', action: openPanorama },
  { icon: 'music', label: () => musicPlaying.value ? '暂停音乐' : '背景音乐', action: () => musicSource ? toggleMusic() : (copied.value = '展厅尚未配置背景音乐') },
  { icon: 'share', label: () => '分享展厅', action: share },
  { icon: 'home', label: () => '返回首页', action: goLobbyHome }
]
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
const mediaLabels = { image: '图片展示', video: '视频播放', audio: '音频讲解', link: '关联链接' }
const mediaMode = ref('')
const mediaModes = computed(() => Object.keys(mediaLabels).filter(type =>
  (type === 'image' && imageUrl(detail.value?.cover)) ||
  detail.value?.media?.some(entry => entry.type === type && imageUrl(entry.url))))
const selectedMedia = computed(() => mediaModes.value.includes(mediaMode.value) ? mediaMode.value : mediaModes.value[0])
const displayedMedia = computed(() => {
  const entries = detail.value?.media || []
  return lite.value ? entries : entries.filter(entry => entry.type === selectedMedia.value)
})
watch([loading, () => route.hash], async ([pending, hash]) => {
  if (!pending && ['#gallery-zones', '#zone-exhibits'].includes(hash)) {
    await nextTick()
    document.getElementById(hash.slice(1))?.scrollIntoView()
  }
})

async function get(path, signal) {
  const controller = new AbortController()
  const relayAbort = () => controller.abort()
  if (signal) signal.addEventListener('abort', relayAbort)
  const timer = setTimeout(() => controller.abort(), RESOURCE_TIMEOUT_MS)
  try {
    const response = await fetch(api + path, { signal: controller.signal, cache: 'no-store' })
    if (response.status === 410) {
      const reason = await response.json().catch(() => ({}))
      return { offline: true, zoneId: reason.zoneId, reasonType: reason.reasonType }
    }
    if (!response.ok) throw new Error(response.status === 404 ? '内容暂不可访问' : '展厅加载失败，请刷新重试')
    return (await response.json()).data
  } catch (error) {
    if (controller.signal.aborted && !(signal && signal.aborted)) throw new Error('展厅资源加载超时')
    throw error
  } finally {
    clearTimeout(timer)
    if (signal) signal.removeEventListener('abort', relayAbort)
  }
}

function webglSupported() {
  try {
    const canvas = document.createElement('canvas')
    return !!(window.WebGLRenderingContext && (canvas.getContext('webgl') || canvas.getContext('experimental-webgl')))
  } catch {
    return false
  }
}

function degradeToLite(reason) {
  if (lite.value || route.name === 'offline') return
  const query = { ...route.query, degrade: reason || 'manual' }
  if (route.params.itemId) {
    return router.replace({ name: 'liteItem', params: { itemId: route.params.itemId }, query })
  }
  if (route.params.zoneId) {
    return router.replace({ name: 'liteZone', params: { zoneId: route.params.zoneId }, query })
  }
  return router.replace({ name: 'lite', query })
}

const degradeText = computed(() => ({
  timeout: '资源加载超时', webgl: '设备不支持 WebGL', panorama: '全景资源不可用',
  assets: '素材加载失败', manual: '手动切换'
}[route.query.degrade] || '弱网降级'))

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
    if (!lite.value && loading.value) {
      weakNetwork.value = true
      degradeToLite('timeout')
    }
  }, RESOURCE_TIMEOUT_MS)
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
  mediaMode.value = ''
  caseReferences.value = []
  caseDetail.value = null
  caseRequest++
  activeLiteVideos.value.clear()
  zones.value = []
  lobbySelection.value = -1
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
      if (!lite.value && !webglSupported()) {
        await degradeToLite('webgl')
        return
      }
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
  if (event.key === 'Escape' && vrActive.value) {
    event.preventDefault()
    closePanorama()
    return
  }
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
  degradeToLite('panorama')
}
function onAssetError() {
  assetFailures.value += 1
  weakNetwork.value = true
  if (assetFailures.value >= 2) degradeToLite('assets')
}

function imageUrl(value) {
  if (typeof value !== 'string') return ''
  if (value.startsWith('/profile/upload/')) return mediaOrigin + value
  return /^https:\/\//i.test(value) ? value : ''
}

function sceneCardCover(cover) {
  const source = imageUrl(cover)
  // The demo diagrams remain available in the exhibit itself.
  return /\/lt-demo\/(court|documents)\.png(?:[?#]|$)/i.test(source) ? '' : source
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

function shareTarget() {
  if (detail.value) return { targetType: 'item', targetId: detail.value.itemId }
  if (route.params.zoneId) return { targetType: 'zone', targetId: Number(route.params.zoneId) }
  const selected = lobbyZones.value && lobbyZones.value[lobbySelection.value]
  if (selected && selected.zoneId) return { targetType: 'zone', targetId: selected.zoneId }
  return null
}

function trackShare(target, shareType) {
  if (!target) return
  const channel = ['qrcode', 'link', 'terminal', 'portal'].includes(route.query.channel) ? route.query.channel : 'link'
  const body = JSON.stringify({ ...target, channel, shareType, session: visitSession() })
  fetch(api + '/shares', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body, keepalive: true
  }).catch(() => {})
}

function shareUrlFor(target) {
  // 大厅首页（未选展区/展项）时分享整个展厅入口；否则分享具体展项或展区
  const path = !target ? '/lite?channel=link'
    : target.targetType === 'item' ? '/lite/item/' + target.targetId + '?channel=link'
    : '/lite/zone/' + target.targetId + '?channel=link'
  return new URL(path, location.origin).href
}

async function share() {
  const target = shareTarget()
  if (target) trackShare(target, 'link')
  const url = shareUrlFor(target)
  try {
    await navigator.clipboard.writeText(url)
    copied.value = '链接已复制'
  } catch {
    // 非安全上下文或剪贴板不可用时，展示链接供手动复制
    copied.value = url
  }
}

async function openShareCard() {
  const target = shareTarget()
  if (!target) return
  try {
    shareCard.value = await get('/share-card?targetType=' + target.targetType + '&targetId=' + target.targetId)
    shareCardOpen.value = true
    trackShare(target, 'card')
  } catch {
    copied.value = '分享信息暂不可用'
  }
}

function systemShare() {
  const target = shareTarget()
  const url = shareUrlFor(target)
  if (navigator.share && shareCard.value) {
    navigator.share({ title: shareCard.value.title, text: shareCard.value.summary || '', url }).catch(() => {})
    if (target) trackShare(target, 'system')
  } else {
    share()
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
  <main class="zone-page" :class="{ 'lite-day': lite && !night, 'lite-item': lite && detail, 'scene-home': lobby && !lite, ['theme-' + zoneTheme]: zoneTheme }">
    <audio v-if="musicSource && !lite" ref="music" :src="musicSource" :volume="musicVolume" loop preload="none"
      @play="musicPlaying = true" @pause="musicPlaying = false"
      @error="musicPlaying = false; musicError = true" />
    <header v-if="!presentation" class="gallery-header">
      <RouterLink class="gallery-brand" to="/immersive">
        <span aria-hidden="true">法</span>
        <div>云端数字展厅<small>法治文化数字展览平台</small></div>
      </RouterLink>
      <nav v-if="!lite" class="gallery-nav" aria-label="展厅导航">
        <RouterLink to="/immersive" :aria-current="lobby ? 'page' : undefined" @click="goLobbyHome">展厅首页</RouterLink>
        <RouterLink :to="{ path: '/immersive', hash: '#gallery-zones' }">展馆导航</RouterLink>
      </nav>
      <nav v-if="lite || (fullscreenAvailable && !lobby) || musicSource || zone || (floorPlan && planZones.length)"
        class="gallery-tools" aria-label="展厅工具">
        <RouterLink v-if="lite && !detail" :to="switchMode">进入沉浸式展厅</RouterLink>
        <template v-if="lite">
          <label>字号
            <select v-model.number="textScale"><option :value="1">标准</option><option :value="1.2">大</option><option :value="1.4">特大</option></select>
          </label>
          <button @click="night = !night">{{ night ? '日间模式' : '夜间模式' }}</button>
        </template>
        <button v-else-if="fullscreenAvailable && !lobby" @click="toggleFullscreen">{{ fullscreen ? '退出全屏' : '全屏浏览' }}</button>
        <template v-if="musicSource && !lite">
          <button type="button" :aria-pressed="musicPlaying" @click="toggleMusic">{{ musicPlaying ? '关闭背景音乐' : '播放背景音乐' }}</button>
          <label>背景音乐音量 <input v-model.number="musicVolume" type="range" min="0" max="1" step=".05"></label>
          <span v-if="musicError" role="status">背景音乐暂不可用</span>
        </template>
        <button v-if="zone && !lite" @click="presentation = true">讲解模式</button>
        <button v-if="floorPlan && planZones.length && !lite" type="button" @click="floorPlanOpen = !floorPlanOpen"
          :aria-expanded="floorPlanOpen" aria-controls="gallery-floor-plan">展馆平面图</button>
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
    <p v-if="copied && !detail && !(lobby && !lite)" role="status">{{ copied }}</p>
    <p v-if="weakNetwork && !dismissWeak && !lite && !presentation" role="status">
      展厅资源加载较慢{{ route.query.degrade === 'webgl' ? '（设备不支持 WebGL）' : '' }}，<RouterLink :to="switchMode">切换图文简版</RouterLink>
      <button @click="dismissWeak = true">继续等待</button>
      <button @click="degradeToLite('manual')">一键进入图文简版</button>
    </p>
    <section v-if="lobby && !lite" class="lobby-scene">
      <div class="scene-backdrop" aria-hidden="true">
        <span class="scene-backdrop-layer" :class="{ 'is-active': !hallTheme }"
          :style="{ '--layer-image': 'url(' + lobbyImage + ')' }"></span>
        <span v-for="entry in zones" :key="'bg-hall-' + entry.zoneId" class="scene-backdrop-layer"
          :class="{ 'is-active': selectedLobbyZone?.zoneId === entry.zoneId && !!hallTheme }"
          :style="{ '--layer-image': 'url(' + hallSceneFor(entry) + ')' }"></span>
      </div>
      <div class="scene-title">
        <h1>{{ sceneTitle }}</h1>
        <p>{{ sceneSubtitle }}</p>
      </div>
      <div v-if="sceneSigns.length" class="scene-projection">
        <nav class="scene-hotspots" aria-label="大厅展区入口">
          <RouterLink v-for="sign in sceneSigns" :key="sign.entry.zoneId" :to="modePath + '/zone/' + sign.entry.zoneId"
            :class="'scene-sign--' + (sceneSigns.length === 2 && sign.index === 1 ? 2 : sign.index)">
            <span class="scene-sign-content">
              <span class="scene-sign-icon" aria-hidden="true"><i></i></span>
              <span class="scene-sign-copy">
                <strong>{{ sign.entry.zoneName }}</strong>
                <small v-if="sign.entry.intro">{{ sign.entry.intro }}</small>
              </span>
              <span class="scene-sign-arrow" aria-hidden="true">›</span>
            </span>
          </RouterLink>
        </nav>
      </div>
      <button v-else type="button" class="scene-back-exit" @click="goLobbyHome">
        <span aria-hidden="true">‹</span> 返回中央大厅
      </button>
      <aside class="scene-guide" aria-label="大厅导览">
        <div class="scene-guide-menu">
          <button type="button" aria-haspopup="dialog" @click="openPlanning">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="sceneIcons.museum" /></svg>展馆总览
          </button>
          <div>
            <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="sceneIcons.pin" /></svg>
            <span><small>当前位置</small>{{ selectedLobbyZone?.zoneName || '中央大厅' }}</span>
          </div>
          <button v-if="zones.length" type="button" @click="focusZoneSelection">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="sceneIcons.grid" /></svg>展区切换
          </button>
        </div>
        <details id="scene-map" ref="sceneMap" class="scene-map">
          <summary aria-label="展开或收起展馆空间规划">
            <span class="scene-map-canvas">
              <img :src="exhibitionArchitecture" alt="展馆空间规划图">
              <span class="scene-map-pin" :style="miniMapPinStyle" aria-hidden="true"></span>
            </span>
            <span class="scene-map-caption">{{ selectedLobbyZone?.zoneName || '中央大厅' }} <b aria-hidden="true">⌃</b></span>
          </summary>
          <button type="button" class="scene-map-open" aria-haspopup="dialog" @click="openPlanning">查看完整展馆规划 ↗</button>
        </details>
      </aside>
      <dialog ref="planningDialog" class="planning-dialog" aria-labelledby="planning-title" @close="planningOpen = false">
        <header class="planning-header">
          <div>
            <h2 id="planning-title">现代法治文化数字展厅空间规划</h2>
            <p>展区布局与参观动线</p>
          </div>
          <button type="button" autofocus @click="closePlanning">关闭</button>
        </header>
        <p v-if="planningSyncFailed" role="status">暂时无法同步展区，正在重试。</p>
        <div class="planning-map">
          <img :src="exhibitionArchitecture" class="planning-architecture" alt="展馆轴测建筑示意图">
          <template v-if="visiblePlanningRooms.length">
            <span v-for="(line, index) in planningLines" :key="index" class="planning-line" :style="line" aria-hidden="true"></span>
            <span class="planning-entrance">↑ 入口</span>
            <RouterLink v-for="{ entry, room, number } in visiblePlanningRooms" :key="entry.zoneId"
              :to="modePath + '/zone/' + entry.zoneId" :aria-label="entry.zoneName"
              :aria-current="entry.zoneId === selectedLobbyZone?.zoneId ? 'location' : undefined"
              class="planning-pin" :class="{ 'planning-pin-below': room.labelY > room.pin[1] }"
              :style="{ left: room.pin[0] + '%', top: room.pin[1] + '%', '--stem': Math.abs(room.pin[1] - room.labelY) * 2 / 3 + 'cqw' }"
              @click="closePlanning">
              <span class="planning-label"><span class="planning-number">{{ number }}</span><span>{{ entry.zoneName }}</span></span>
            </RouterLink>
          </template>
        </div>
        <nav v-if="visiblePlanningRooms.length" class="planning-legend" aria-label="参观动线">
          <RouterLink v-for="{ entry, number } in visiblePlanningRooms" :key="entry.zoneId"
            :to="modePath + '/zone/' + entry.zoneId" @click="closePlanning">
            <span class="planning-number">{{ number }}</span>{{ entry.zoneName }}
          </RouterLink>
          <span class="planning-legend-route"><b aria-hidden="true">➜</b> 参观动线</span>
        </nav>
        <p v-else class="planning-empty">{{ loading ? '正在加载展区…' : '暂无开放展区' }}</p>
        <div v-if="planningPageCount > 1" class="planning-pages">
          <button type="button" :disabled="planningPage === 0" @click="planningPage--">上一页</button>
          <span>{{ planningPage + 1 }} / {{ planningPageCount }}</span>
          <button type="button" :disabled="planningPage === planningPageCount - 1" @click="planningPage++">下一页</button>
        </div>
      </dialog>
      <aside class="scene-tools" aria-label="场景工具">
        <button v-for="tool in sceneTools" :key="tool.icon" type="button" @click="tool.action">
          <span aria-hidden="true"><svg viewBox="0 0 24 24"><path :d="sceneIcons[tool.icon]" /></svg></span>
          {{ tool.label() }}
        </button>
      </aside>
      <transition name="vr-stage">
        <section v-if="vrActive" class="vr-stage" aria-labelledby="vr-title">
          <header>
            <h2 id="vr-title">VR 全景</h2>
            <label v-if="vrScenes.length" class="vr-select">
              场景
              <select v-model="vrSceneId">
                <option v-for="scene in vrScenes" :key="scene.id" :value="scene.id">{{ scene.name }}</option>
              </select>
            </label>
            <button type="button" aria-label="退出全景" @click="closePanorama">×</button>
          </header>
          <div class="vr-stage__body">
            <PanoramaView v-if="vrScene && !vrError" :key="vrScene.id" :source="vrScene.source"
              :spots="vrSpots" @error="vrError = true" />
            <p v-else class="vr-message" role="status">
              {{ vrError ? '此场景的全景图加载失败，请重试或切换场景。' : '展厅尚未发布 360° 全景场景。' }}
              <button v-if="vrError" type="button" @click="vrError = false">重试</button>
            </p>
          </div>
        </section>
      </transition>
      <p v-if="copied || musicError" class="scene-status" role="status">
        {{ copied || '背景音乐暂不可用' }}
        <button type="button" aria-label="关闭提示" @click="copied = ''; musicError = false">×</button>
      </p>
      <div v-if="zones.length" class="scene-dock">
        <RouterLink class="scene-entry" :to="modePath + '/zone/' + (selectedLobbyZone || zones[0]).zoneId">
          {{ selectedLobbyZone ? '进入' + selectedLobbyZone.zoneName : '进入沉浸式展厅' }} <span aria-hidden="true">›</span>
        </RouterLink>
        <RouterLink class="scene-roam" :to="modePath + '/zone/' + zones[0].zoneId">
          <span aria-hidden="true">▶</span>开始漫游
        </RouterLink>
        <div ref="sceneCarousel" class="scene-carousel" role="group" aria-label="展区切换">
          <button type="button" class="scene-step" :disabled="zones.length < 2" @click="stepLobby(-1)">
            <span aria-hidden="true">‹</span>上一展区
          </button>
          <button v-for="entry in lobbyZones" :key="entry.zoneId" type="button" class="scene-thumbnail"
            :aria-pressed="entry.zoneId === selectedLobbyZone?.zoneId" @click="lobbySelection = zones.indexOf(entry)">
            <span class="scene-thumbnail-photo" :style="thumbnailStyle(entry)" aria-hidden="true"></span>
            <span class="scene-thumbnail-label">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="index === 1 ? sceneIcons.results : sceneIcons.museum" /></svg>
              <span>{{ entry.zoneName }}</span>
            </span>
          </button>
          <button type="button" class="scene-step" :disabled="zones.length < 2" @click="stepLobby(1)">
            <span aria-hidden="true">›</span>下一展区
          </button>
        </div>
      </div>
    </section>
    <section v-else-if="lobby" class="gallery-lobby gallery-lobby--lite">
      <h1>图文导览</h1>
      <p v-if="zones.length">{{ zones.length }} 个开放展区 · {{ cards.length }} 件展项</p>
    </section>
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
    <article v-else-if="current" class="exhibit-detail" :class="{ 'exhibit-detail--lite': lite }">
      <header class="exhibit-heading">
        <RouterLink class="detail-back" :to="modePath + '/zone/' + current.zoneId">← 返回展区</RouterLink>
        <h1>{{ current.title }}</h1>
      </header>
      <div class="exhibit-stage">
        <p v-if="!mediaModes.length" class="exhibit-media-empty">暂无媒体资料</p>
        <GalleryImage v-if="(lite || selectedMedia === 'image') && imageUrl(current.cover)" :source="imageUrl(current.cover)" :lazy="lite"
          class="item-card__cover" @unavailable="onAssetError" />
        <div v-if="displayedMedia.length" class="media">
          <template v-for="entry in displayedMedia" :key="entry.mediaId">
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
        <nav v-if="!lite && mediaModes.length > 1" class="media-modes" aria-label="展项媒体">
          <button v-for="type in mediaModes" :key="type" type="button"
            :aria-pressed="selectedMedia === type" @click="mediaMode = type">{{ mediaLabels[type] }}</button>
        </nav>
      </div>
      <div class="item-card__body exhibit-description">
        <h2>展项介绍</h2>
        <p v-if="current.authorName">{{ current.authorName }}</p>
        <p class="item-card__summary">{{ current.summary }}</p>
        <p class="exhibit-content" :style="{ fontSize: lite ? textScale + 'rem' : undefined }">{{ current.content }}</p>
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
        <button v-if="detail" @click="openShareCard">分享卡片</button>
        <button v-if="detail" @click="showQr = !showQr" :aria-expanded="showQr">{{ showQr ? '收起二维码' : '分享二维码' }}</button>
        <p v-if="showQr && detail"><img :src="api + '/items/' + detail.itemId + '/qr.png'" width="256" height="256" alt="展项分享二维码"></p>
        <p v-if="copied" role="status" class="copy-result">{{ copied }}</p>
        <div v-if="shareCardOpen" class="share-modal" role="dialog" aria-label="分享卡片">
          <div class="share-modal__panel">
            <button type="button" class="share-modal__close" aria-label="关闭" @click="shareCardOpen = false">×</button>
            <h3 v-if="shareCard">{{ shareCard.title }}</h3>
            <GalleryImage v-if="shareCard && imageUrl(shareCard.cover)" :source="imageUrl(shareCard.cover)" class="share-modal__cover" />
            <p v-if="shareCard && shareCard.summary">{{ shareCard.summary }}</p>
            <p v-if="shareCard" class="muted">来源：{{ shareCard.source }}</p>
            <img v-if="shareCard" :src="api + shareCard.qrUrl" width="180" height="180" alt="分享二维码" class="share-modal__qr">
            <div class="share-modal__actions">
              <button type="button" @click="share">复制链接</button>
              <button type="button" @click="systemShare">系统分享</button>
            </div>
          </div>
        </div>
      </div>
      <nav v-if="siblings.length > 1" class="exhibit-rail zone-links" aria-label="展项导航">
        <RouterLink v-if="itemIndex > 0" :to="modePath + '/item/' + siblings[itemIndex - 1].itemId">‹ 上一展项</RouterLink>
        <div class="exhibit-thumbnails">
          <RouterLink v-for="entry in siblings" :key="entry.itemId" :to="modePath + '/item/' + entry.itemId"
            :aria-current="entry.itemId === current.itemId ? 'page' : undefined" :aria-label="entry.title">
            <GalleryImage v-if="imageUrl(entry.cover)" :source="imageUrl(entry.cover)" class="item-card__cover" lazy />
            <span v-else>{{ entry.title }}</span>
          </RouterLink>
        </div>
        <RouterLink v-if="itemIndex >= 0 && siblings[itemIndex + 1]" :to="modePath + '/item/' + siblings[itemIndex + 1].itemId">下一展项 ›</RouterLink>
      </nav>
    </article>
    <template v-else>
      <template v-if="panorama">
        <h1>{{ zone.zoneName }}</h1>
        <PanoramaView :key="panorama" :source="panorama" :spots="spots" @error="onPanoramaError" />
      </template>
      <section v-else-if="zone && !lite" class="zone-banner">
        <div class="zone-banner__copy">
          <RouterLink :to="{ path: '/immersive', hash: '#gallery-zones' }">← 展区导览</RouterLink>
          <h1>{{ zone.zoneName }}</h1>
          <p v-if="zone.intro">{{ zone.intro }}</p>
          <a v-if="visibleCards.length" class="zone-banner__action" href="#zone-exhibits">
            浏览展项 <span>· {{ visibleCards.length }} 件</span> ↓
          </a>
        </div>
        <div class="zone-banner__scene">
          <GalleryImage :source="sceneCardCover(zone.cover) || hallSceneFor(zone)" class="item-card__cover" @unavailable="onAssetError" />
          <RouterLink v-for="spot in presentation ? [] : spots" :key="spot.spotId" class="zone-spot"
            :aria-label="spot.spotName" :title="spot.spotName"
            :style="{ left: spot.posX * 100 + '%', top: spot.posY * 100 + '%' }"
            :to="'/immersive/' + spot.targetType + '/' + spot.targetId" />
        </div>
      </section>
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
      <p v-if="zone?.intro && (lite || panorama)" class="zone-intro">{{ zone.intro }}</p>
      <section v-if="lobby && zones.length" class="gallery-section" aria-labelledby="gallery-zones">
        <h2 id="gallery-zones">展区导览</h2>
        <nav class="zone-layout--grid" aria-labelledby="gallery-zones">
          <RouterLink v-for="entry in zones" :key="entry.zoneId" :to="modePath + '/zone/' + entry.zoneId"
            class="item-card zone-card">
            <GalleryImage v-if="!lite" :source="sceneCardCover(entry.cover) || hallSceneFor(entry)" lazy
              class="item-card__cover" @unavailable="onAssetError" />
            <div class="item-card__body">
              <span class="item-meta">{{ zoneCounts.get(entry.zoneId) || 0 }} 件展项</span>
              <h3 class="item-card__title">{{ entry.zoneName }}</h3>
              <p v-if="entry.intro" class="item-card__summary">{{ entry.intro }}</p>
              <span class="zone-card__action">进入展区 <span aria-hidden="true">→</span></span>
            </div>
          </RouterLink>
        </nav>
      </section>
      <nav v-else-if="!lobby && zones.length && !presentation" class="zone-links" aria-label="切换展区">
        <span v-if="!lite" class="zone-nav-label">切换展区</span>
        <RouterLink v-if="lite && previousZone" :to="modePath + '/zone/' + previousZone.zoneId">上一展区</RouterLink>
        <RouterLink v-if="lite && nextZone" :to="modePath + '/zone/' + nextZone.zoneId">下一展区</RouterLink>
        <RouterLink v-for="entry in zones" :key="entry.zoneId" :to="modePath + '/zone/' + entry.zoneId"
          :aria-current="entry.zoneId === zone?.zoneId ? 'location' : undefined">{{ entry.zoneName }}</RouterLink>
      </nav>
      <section v-if="!visibleCards.length" class="gallery-empty" aria-labelledby="gallery-empty">
        <h2 id="gallery-empty">{{ lobby && !zones.length ? '暂无公开展区' : '暂无开放展项' }}</h2>
        <p>{{ lobby && !zones.length ? '展区开放并发布展项后，即可在这里浏览。' : '展项发布后将在这里展示。' }}</p>
      </section>
      <h2 v-if="lobby && visibleCards.length" class="gallery-section">开放展项</h2>
      <h2 v-else-if="zone && !lite && visibleCards.length" id="zone-exhibits" class="zone-exhibits-title" tabindex="-1">展区展项</h2>
      <section v-if="visibleCards.length" :class="['zone-layout--' + (zone?.layout || (lite ? 'flow' : 'grid')), { 'zone-exhibits': zone && !lite }]">
        <component :is="presentation ? 'article' : 'RouterLink'" v-for="card in visibleCards" :key="card.itemId" :to="presentation ? undefined : modePath + '/item/' + card.itemId" class="item-card">
          <GalleryImage v-if="!lite && (lobby || sceneCardCover(card.cover) || hallSceneImages[themeForName(zoneById(card.zoneId)?.zoneName)])"
            :source="sceneCardCover(card.cover) || hallSceneFor(zoneById(card.zoneId))" lazy
            class="item-card__cover" @unavailable="onAssetError" />
          <GalleryImage v-else-if="lite && imageUrl(card.cover)" :source="imageUrl(card.cover)" lazy
            class="item-card__cover" @unavailable="onAssetError" />
          <div class="item-card__body">
            <component :is="zone && !lite ? 'h3' : 'h2'" class="item-card__title">{{ card.title }}</component>
            <p class="item-card__summary">{{ card.summary }}</p>
            <span v-if="zone && !lite && !presentation" class="zone-card__action">查看展项 →</span>
          </div>
        </component>
      </section>
    </template>
    <div v-if="lite && detail" class="lite-mode-dock">
      <p v-if="route.query.degrade" class="degrade-note">已切换至同内容图文简版（{{ degradeText }}）</p>
      <RouterLink :to="switchMode">进入沉浸式展厅</RouterLink>
    </div>
  </main>
</template>

<style scoped>
.degrade-note { margin: 0 0 .5rem; color: var(--lt-muted, #666); font-size: .82rem; }
.share-modal { position: fixed; inset: 0; z-index: 40; display: grid; place-items: center; padding: 1rem; background: #0008; }
.share-modal__panel { position: relative; width: min(360px, 92vw); max-height: 86vh; overflow-y: auto; padding: 1.25rem; border-radius: 14px; background: var(--lt-field, #fff); color: var(--lt-ink, #222); text-align: center; }
.share-modal__close { position: absolute; top: .5rem; right: .75rem; border: 0; background: none; font-size: 1.5rem; line-height: 1; cursor: pointer; }
.share-modal__cover { width: 100%; max-height: 160px; object-fit: cover; border-radius: 8px; }
.share-modal__qr { margin: .75rem auto; image-rendering: pixelated; }
.share-modal__actions { display: flex; gap: .75rem; justify-content: center; flex-wrap: wrap; }
:global(html[data-mode="immersive"]), :global(body.mode-immersive), :global(body.mode-immersive #app) {
  background: #3d100e;
}
:global(body[data-hall="court"].mode-immersive) { background: #24100a; }
:global(body[data-hall="achievement"].mode-immersive) { background: #571109; }
:global(body[data-hall="document"].mode-immersive) { background: #2c1f12; }
:global(body[data-hall="mediation"].mode-immersive) { background: #2d2119; }
:global(body[data-hall="compliance"].mode-immersive) { background: #161c25; }
.zone-page {
  --ex-bg: #3d100e;
  --ex-panel: #611d18;
  --ex-text: #fff6e7;
  --ex-sub: #f1d0b8;
  --ex-accent: #ffe3aa;
  --ex-accent-2: #a33027;
  --gallery-rule: color-mix(in srgb, var(--ex-sub) 23%, transparent);
  width: min(100%, 1480px);
  min-height: 100vh;
  padding-inline: clamp(1rem, 3.8vw, 3rem);
  color: var(--ex-text);
}
.gallery-header, .zone-links { display: flex; flex-wrap: wrap; gap: .75rem; padding: 1.1rem 0; }
.gallery-header {
  position: relative; z-index: 2; justify-content: space-between; align-items: center; font-weight: 700;
  border-bottom: 1px solid #dfad7880;
}
.scene-home { width: 100%; max-width: none; padding-inline: 0; }
.scene-home .gallery-header {
  padding-inline: clamp(1rem, 3vw, 2.5rem);
  background: linear-gradient(100deg, #661411, #7d211b 60%, #53110f);
}
@media (min-width: 900px) {
  .scene-home .gallery-header { display: grid; grid-template-columns: 1fr auto 1fr; }
  .scene-home .gallery-tools { justify-self: end; }
}
.scene-home > :not(.gallery-header, .lobby-scene, audio) { margin-inline: clamp(1rem, 4vw, 4rem); }
.gallery-nav a[aria-current] { color: var(--ex-accent); border-bottom-color: var(--ex-accent); }
.gallery-brand small { display: block; color: var(--ex-sub); font-weight: 500; font-size: .72rem; }
a { color: inherit; }
.gallery-brand { display: flex; align-items: center; gap: .75rem; font-size: clamp(1rem, 2.6vw, 1.2rem); text-decoration: none; }
.gallery-brand span {
  display: grid; place-items: center; width: 2.45rem; height: 2.45rem; border: 1px solid var(--gallery-rule);
  border-radius: .65rem; color: var(--ex-accent); font: 700 1.3rem "Songti SC", SimSun, serif;
}
.gallery-header nav > :is(a, button), .zone-links > :is(a, button), .exhibit-detail button {
  display: inline-flex;
  align-items: center;
  gap: .6rem;
  min-height: 2.65rem;
  padding: .45rem .9rem;
  border: 1px solid var(--gallery-rule);
  border-radius: .62rem;
  background: color-mix(in srgb, var(--ex-panel) 82%, transparent);
  color: var(--ex-text);
  text-decoration: none;
  font-size: .9rem;
  cursor: pointer;
}
.gallery-header nav > :is(a, button):hover, .zone-links > :is(a, button):hover,
.exhibit-detail button:hover { border-color: var(--ex-accent); }
.zone-links > [aria-current] { border-color: var(--ex-accent); color: var(--ex-accent); }
.zone-page :is(a, button, summary):focus-visible { outline: 2px solid var(--ex-accent); outline-offset: 3px; }
.lobby-scene {
  position: relative; display: grid; grid-template-rows: auto 1fr auto;
  min-height: calc(100svh - 4.65rem); padding: clamp(1.45rem, 5vh, 3.25rem) 5rem 1.4rem;
  overflow: hidden;
  background: var(--ex-panel);
}
.scene-backdrop { position: absolute; inset: 0; z-index: 0; }
.scene-backdrop-layer {
  position: absolute; inset: 0; opacity: 0; transition: opacity .65s ease;
  background: linear-gradient(180deg, #380b0a27 0%, transparent 40%, #390c09ab 100%),
    var(--ex-panel) var(--layer-image) center 54% / cover no-repeat;
}
.scene-backdrop-layer.is-active { opacity: 1; }
.lobby-scene > :where(:not(.scene-backdrop)) { position: relative; z-index: 1; }
.scene-back-exit {
  position: absolute; left: clamp(1rem, 4vw, 3rem); top: 42%; z-index: 1;
  display: inline-flex; align-items: center; gap: .4rem;
  padding: .55rem 1.1rem; border: 1px solid #dfaa7688; border-radius: 999px;
  background: linear-gradient(145deg, #732820e8, #48100de8); color: var(--ex-text);
  font: inherit; font-weight: 700; cursor: pointer; box-shadow: 0 .35rem 1.2rem #3d0e0b68;
}
.scene-back-exit:hover, .scene-back-exit:focus-visible { border-color: var(--ex-accent); }
.scene-title { justify-self: center; text-align: center; padding: .75rem 1rem; color: var(--ex-accent); text-shadow: 0 2px 8px #481411; }
.scene-title h1 { margin: 0; font: 700 clamp(1.8rem, 4.2vw, 3.5rem)/1.15 "Songti SC", SimSun, serif; letter-spacing: .07em; }
.scene-title p { margin: .5rem 0 0; color: var(--ex-text); font-size: clamp(.8rem, 1.5vw, 1rem); }
.scene-projection { position: absolute; inset: 0; container-type: size; pointer-events: none; }
/* Match the 3:2 lobby image's cover scaling and 50% 54% background position. */
.scene-hotspots {
  position: absolute; left: 50%; top: 54%; transform: translate(-50%, -54%);
  width: max(100cqw, 150cqh); height: max(100cqh, 66.6667cqw);
}
.scene-tools, .scene-guide-menu, .scene-map, .scene-carousel {
  border: 1px solid #dfaa7660; border-radius: .8rem; background: linear-gradient(145deg, #732820e8, #48100de8);
  box-shadow: 0 .35rem 1.2rem #3d0e0b68;
}
.scene-hotspots a {
  position: absolute; pointer-events: auto; isolation: isolate;
  width: var(--sign-width); height: var(--sign-height); clip-path: var(--sign-outline);
  background: #eab77e; color: var(--ex-text); text-decoration: none;
  font-size: clamp(.68rem, 1.03cqw, 1.7rem);
}
.scene-hotspots a::before {
  content: ''; position: absolute; z-index: -1; inset: 1.5px; clip-path: var(--sign-outline);
  background: linear-gradient(155deg, #a3291c, #640d09);
}
.scene-hotspots a:hover::before, .scene-hotspots a:focus-visible::before { background: #9c2418; }
.scene-sign-content {
  position: absolute; inset: 0; display: grid; grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center; gap: clamp(.35rem, .6cqw, .9rem);
  padding-inline: 5%; transform: translateY(3%) rotate(var(--sign-angle));
}
/* Corners calibrated against the original 1536 × 1024 lobby artwork. */
.scene-sign--0 {
  left: 6.38%; top: 26.07%; --sign-width: 17.71%; --sign-height: 11.91%; --sign-angle: 11.6deg;
  --sign-outline: polygon(0 0, 100% 52.46%, 100% 100%, 0 60.66%);
}
.scene-sign--1 {
  left: 28.52%; top: 33.69%; --sign-width: 14.45%; --sign-height: 7.03%; --sign-angle: 5.7deg;
  --sign-outline: polygon(0 0, 100% 34.72%, 100% 100%, 0 73.61%);
}
.scene-sign--2 {
  left: 75.13%; top: 28.71%; --sign-width: 19.53%; --sign-height: 10.16%; --sign-angle: -8.9deg;
  --sign-outline: polygon(0 51.92%, 100% 0, 100% 61.54%, 0 100%);
}
.scene-sign-copy { min-width: 0; }
.scene-sign-copy > * { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.scene-sign-copy strong { line-height: 1.22; }
.scene-sign--1 .scene-sign-copy strong { font-size: .82em; }
.scene-sign-copy small { margin-top: .2em; color: var(--ex-sub); font-size: .55em; }
.scene-sign-icon, .scene-sign-arrow {
  display: grid; place-items: center; width: 1.8em; aspect-ratio: 1; border: 1px solid #f4c98e9c;
  border-radius: 50%; background: #500c0960; color: var(--ex-accent);
}
.scene-sign-arrow { width: 1.25em; line-height: 1; }
.scene-sign-icon i { display: block; width: .91em; height: .78em; background: currentColor; }
.scene-sign--0 i { clip-path: polygon(0 28%, 50% 0, 100% 28%, 83% 40%, 83% 75%, 50% 100%, 17% 75%, 17% 40%); }
.scene-sign--1 i { clip-path: polygon(0 92%, 10% 92%, 10% 47%, 29% 47%, 29% 92%, 40% 92%, 40% 9%, 59% 9%, 59% 92%, 70% 92%, 70% 30%, 89% 30%, 89% 92%, 100% 92%, 100% 100%, 0 100%); }
.scene-sign--2 i { clip-path: polygon(0 6%, 24% 4%, 48% 22%, 48% 100%, 24% 81%, 0 83%, 0 6%, 100% 6%, 76% 4%, 52% 22%, 52% 100%, 76% 81%, 100% 83%, 100% 6%); }
.scene-tools { position: absolute; right: .8%; top: 57%; transform: translateY(-50%); display: grid; padding: .35rem; }
.scene-tools button, .scene-guide-menu button, .scene-step {
  border: 0; background: transparent; color: inherit; cursor: pointer;
}
.scene-tools button { display: grid; justify-items: center; gap: .25rem; min-width: 3.45rem; padding: .4rem .1rem; font-size: .72rem; }
.scene-tools button > span, .scene-guide-menu svg, .scene-step > span, .scene-roam > span {
  display: grid; place-items: center; border: 1px solid #f6bd826c; border-radius: 50%; background: #8c2b2160;
}
.scene-tools button > span { width: 2.5rem; height: 2.5rem; }
.scene-tools svg, .scene-guide-menu svg, .scene-thumbnail svg {
  width: 1.35rem; height: 1.35rem; fill: none; stroke: currentColor; stroke-width: 1.65; stroke-linejoin: round; stroke-linecap: round;
}
.scene-guide { position: absolute; bottom: 7.4%; left: 1.7%; width: clamp(12rem, 21vw, 27rem); z-index: 1; }
.scene-guide-menu { width: max-content; min-width: 62%; margin-bottom: .45rem; overflow: hidden; }
.scene-guide-menu > * {
  display: flex; align-items: center; gap: .55rem; width: 100%; padding: .47rem .75rem; color: inherit; text-align: left;
}
.scene-guide-menu > * + * { border-top: 1px solid #efab7133; }
.scene-guide-menu svg { box-sizing: content-box; padding: .32rem; }
.scene-guide-menu small { display: block; color: var(--ex-sub); font-size: .7rem; }
.scene-tools button:hover, .scene-guide-menu button:hover, .scene-step:not(:disabled):hover { background: #a84a354f; }
.scene-map { overflow: hidden; }
.scene-map summary { display: block; cursor: pointer; }
.scene-map summary::-webkit-details-marker { display: none; }
.scene-map-canvas { position: relative; display: block; }
.scene-map img { display: block; width: 100%; }
.scene-map-pin { position: absolute; width: 2.2rem; height: 2.2rem; transform: translate(-50%, -50%); pointer-events: none; }
.scene-map-pin::before, .scene-map-pin::after { content: ''; position: absolute; inset: 0; border-radius: 50%; }
.scene-map-pin::before { border: 4px solid #ffd08a; background: radial-gradient(circle, #fff6e0 0 30%, #ff9d4d 55%, transparent 72%); box-shadow: 0 0 1.5rem .55rem #ffae5b99; }
.scene-map-pin::after { border: 2px solid #ffbf6e; animation: scene-map-pulse 1.8s ease-out infinite; }
@keyframes scene-map-pulse { 0% { transform: scale(.6); opacity: .9; } 100% { transform: scale(1.9); opacity: 0; } }
.scene-map-caption { display: block; padding: .36rem .65rem; text-align: center; border-top: 1px solid #e7ad7655; }
.scene-map summary b { float: right; }
.scene-map[open] summary b { transform: rotate(180deg); }
.scene-map-open { display: block; width: 100%; padding: .45rem .6rem; text-align: center; font-size: .78rem; }
.planning-dialog {
  width: min(73rem, calc(100% - 2rem), calc(135vh - 13rem)); max-height: 92vh;
  padding: clamp(1rem, 2.5vw, 2rem);
  box-sizing: border-box; overflow: auto; border: 1px solid #c5ae93; border-radius: .5rem;
  color: #45241a; background: radial-gradient(ellipse at 50% 43%, #f8f0e5, #e8dccd);
}
.planning-dialog::backdrop { background: #170a07c9; }
.planning-header { position: relative; text-align: center; padding-inline: 3rem; }
.planning-header h2 { margin: 0; color: #851c1c; font-size: clamp(1.2rem, 3.4vw, 2.5rem); }
.planning-header p { margin: .25rem 0 0; color: #64473c; letter-spacing: .2em; font-size: .83rem; }
.planning-header button {
  position: absolute; top: 0; right: 0; padding: .45rem .75rem; border: 1px solid #b99779;
  border-radius: .35rem; color: #71251a; background: #fff6e9; cursor: pointer;
}
.planning-map {
  position: relative; isolation: isolate; container-type: inline-size;
  margin: -.1rem 0 0; aspect-ratio: 3 / 2;
}
.planning-architecture {
  position: absolute; inset: 0; display: block; width: 100%; height: 100%; mix-blend-mode: multiply;
}
.planning-line {
  position: absolute; height: 2px; transform-origin: left center;
  background: #ba2b24; box-shadow: 0 0 2px #fff4db; pointer-events: none;
}
.planning-line::after {
  content: ''; position: absolute; top: 50%; left: 64%; width: .32rem; height: .32rem;
  border: solid #ba2b24; border-width: 2px 2px 0 0; transform: translateY(-50%) rotate(45deg);
}
.planning-entrance {
  position: absolute; left: 45%; top: 80%; transform: translateX(-50%);
  color: #a51f1d; font-weight: 700; font-size: clamp(.65rem, 1.4vw, 1rem);
}
.planning-pin {
  position: absolute; z-index: 2; width: .62rem; height: .62rem;
  transform: translate(-50%, -50%); background: #8c1f20;
  border: 1px solid #fff7e4; border-radius: 50%; box-shadow: 0 0 0 2px #96252154;
}
.planning-pin::before {
  content: ''; position: absolute; left: 50%; bottom: 50%; height: var(--stem);
  border-left: 1px solid #a0322d; pointer-events: none;
}
.planning-pin-below::before { top: 50%; bottom: auto; }
.planning-label {
  position: absolute; left: 50%; bottom: calc(50% + var(--stem)); transform: translateX(-50%);
  display: flex; align-items: center; gap: .4rem; width: max-content; max-width: 19cqw;
  padding: .24rem .65rem .24rem .32rem; border-radius: 1rem;
  color: #fff7e8; background: #8b2020; font-size: clamp(.69rem, 1.3vw, .95rem);
  line-height: 1.25; overflow-wrap: anywhere; box-shadow: 0 2px 5px #59302436;
}
.planning-pin-below .planning-label { top: calc(50% + var(--stem)); bottom: auto; }
.planning-pin:is(:hover, :focus-visible, [aria-current]) { z-index: 3; }
.planning-pin:is(:hover, :focus-visible, [aria-current]) .planning-label {
  background: #561313; outline: 2px solid #f0bd70; outline-offset: 2px;
}
.planning-number {
  display: inline-grid; place-items: center; flex: 0 0 1.32rem; width: 1.32rem; height: 1.32rem;
  border-radius: 50%; color: #8b2020; background: #fff1df; font-size: .7rem; font-weight: 700;
}
.planning-legend {
  display: flex; flex-wrap: wrap; gap: .5rem .8rem; width: fit-content; margin: -2rem 0 0 auto;
  position: relative; padding: .62rem .8rem; border-radius: .4rem; border: 1px solid #d8c2a5;
  color: #58372c; background: #fff8eb; font-size: .78rem;
}
.planning-legend > * { display: flex; align-items: center; gap: .3rem; color: inherit; }
.planning-legend a:hover { text-decoration: underline; }
.planning-legend .planning-number { color: #fff7e8; background: #8b2020; }
.planning-legend-route b { color: #b52a22; }
.planning-pages { display: flex; align-items: center; justify-content: center; gap: .7rem; margin-top: .8rem; }
.planning-pages button { color: #71251a; border-color: #bc915e; }
.planning-empty { text-align: center; }
@media (max-width: 680px) {
  .planning-dialog { width: calc(100% - 1rem); }
  .planning-header { padding-inline: 0; padding-top: 2rem; }
  .planning-label { padding: .14rem; }
  .planning-label > span + span { display: none; }
  .planning-legend { margin: 0; width: 100%; }
  .planning-line { height: 1px; }
  .planning-line::after { display: none; }
}
.scene-status {
  position: absolute; z-index: 2; top: .6rem; right: .8rem; max-width: calc(100% - 1.6rem); margin: 0;
  padding: .55rem .75rem; border-radius: .45rem; background: #561613; overflow-wrap: anywhere;
}
.scene-status button { margin-left: .5rem; background: transparent; border: 0; color: inherit; cursor: pointer; }
.vr-stage {
  position: fixed; inset: 0; z-index: 40; display: flex; flex-direction: column; gap: .6rem;
  padding: .85rem; background: var(--ex-bg); color: var(--ex-text);
}
.vr-stage-enter-active, .vr-stage-leave-active { transition: opacity .25s ease; }
.vr-stage-enter-from, .vr-stage-leave-to { opacity: 0; }
.vr-stage header { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
.vr-stage h2 { margin: 0; }
.vr-stage button, .vr-select select {
  border: 1px solid #dfaa7699; border-radius: .4rem; padding: .35rem .65rem; background: var(--ex-panel); color: inherit;
}
.vr-select { display: flex; align-items: center; gap: .6rem; }
.vr-select select { min-width: 0; }
.vr-stage__body { flex: 1; min-height: 0; display: flex; }
.vr-stage__body :deep(.panorama) { flex: 1; height: auto; min-height: 0; }
.vr-message { flex: 1; display: grid; place-content: center; padding: 2rem .5rem; text-align: center; }
.scene-dock {
  grid-row: 3; justify-self: center; display: grid; justify-items: center; gap: .55rem;
  width: clamp(30rem, 52vw, 65rem); max-width: 100%; margin-bottom: 6.5vh; z-index: 1;
}
.scene-entry {
  display: inline-flex; align-items: center; justify-content: center; gap: 1.4rem; min-width: clamp(15rem, 21vw, 25rem);
  padding: .68rem 1.15rem; border: 1px solid #fff0d2; border-radius: 999px; background: linear-gradient(#fff3dc, #edbc81);
  color: #681a13; text-decoration: none; font-weight: 750; font-size: clamp(1.05rem, 1.55vw, 1.55rem); box-shadow: 0 .4rem 1.5rem #62220ea0;
}
.scene-entry span { display: grid; place-items: center; border-radius: 50%; width: 1.6em; aspect-ratio: 1; background: #a12b20; color: white; }
.scene-roam { display: inline-flex; align-items: center; gap: .65rem; color: inherit; text-decoration: none; text-shadow: 0 1px 5px #310704; }
.scene-roam > span { width: 2rem; height: 2rem; font-size: .8rem; }
.scene-carousel { display: flex; align-items: center; justify-content: center; gap: clamp(.4rem, 1.3vw, 1.3rem); width: 100%; padding: .6rem; }
.scene-carousel button:disabled { opacity: .5; }
.scene-step { display: grid; justify-items: center; gap: .35rem; flex: 0 0 auto; padding: .25rem; border-radius: .4rem; font-size: .72rem; white-space: nowrap; }
.scene-step > span { width: 2.2rem; height: 2.2rem; font-size: 1.65rem; line-height: 1; }
.scene-thumbnail {
  flex: 1 1 0; min-width: 0; padding: 0; overflow: hidden; text-align: center; cursor: pointer;
  color: var(--ex-text); background: #551813; border: 1px solid #ce8d62; border-radius: .48rem;
}
.scene-thumbnail-photo { display: block; height: clamp(2.8rem, 4vw, 5.25rem); background-repeat: no-repeat; }
.scene-thumbnail-label { display: flex; align-items: center; justify-content: center; gap: .3rem; padding: .28rem .25rem; font-size: .78rem; }
.scene-thumbnail-label span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.scene-thumbnail svg { flex: 0 0 auto; width: .95rem; height: .95rem; }
.scene-thumbnail[aria-pressed="true"] { border-color: #fff1c7; box-shadow: 0 0 .65rem #ffcc91, inset 0 0 0 1px #fff1c7; }
.gallery-lobby--lite { min-height: auto; padding: clamp(1.4rem, 4vw, 2.75rem); background: var(--ex-panel); box-shadow: none; }
.gallery-lobby h1, .zone-hero__title, .zone-banner h1, .exhibit-detail h1 {
  margin: 0;
  font-family: "Songti SC", "Noto Serif CJK SC", SimSun, serif;
  font-weight: 700;
  line-height: 1.18;
}
.gallery-lobby h1 { font-size: clamp(3.2rem, 7vw, 6rem); letter-spacing: .055em; }
.gallery-lobby--lite h1 { font-size: clamp(2rem, 4vw, 3.25rem); }
.gallery-lobby p { margin: 1rem 0 0; color: var(--ex-sub); font-size: 1rem; }
.gallery-section, .gallery-empty { margin-top: clamp(1.6rem, 4vw, 3rem); }
.gallery-section h2, h2.gallery-section { margin-bottom: 1rem; font-size: clamp(1.35rem, 3vw, 2rem); letter-spacing: .045em; }
.gallery-section .zone-layout--grid { margin-top: 1rem; grid-template-columns: repeat(auto-fit, minmax(min(100%, 19rem), 1fr)); }
.zone-page .zone-layout--grid { grid-template-columns: repeat(auto-fit, minmax(min(100%, 17rem), 1fr)); }
.zone-page .item-card { border: 1px solid var(--gallery-rule); }
.zone-page .item-card__body { padding: clamp(1rem, 2.6vw, 1.5rem); }
.zone-card { display: flex; flex-direction: column; }
.zone-card .item-card__body { flex: 1; display: flex; flex-direction: column; align-items: start; }
.zone-card .item-card__cover { aspect-ratio: 2; }
.zone-card__action, .item-meta { color: var(--ex-accent); font-size: .85rem; }
.zone-card__action { margin-top: auto; padding-top: .85rem; font-weight: 700; }
.item-meta { margin-bottom: .25rem; }
.zone-card .item-card__title { font-size: 1.24rem; }
.zone-intro { max-width: 52rem; margin: 1.3rem 0 .25rem; color: var(--ex-sub); }
.zone-banner {
  display: grid; grid-template-columns: minmax(0, 4fr) minmax(0, 5fr);
  overflow: hidden; margin-top: 1rem; border: 1px solid var(--gallery-rule); border-radius: var(--ex-radius);
  background: linear-gradient(145deg, #67241b, #42110e);
}
.zone-banner__copy { display: grid; justify-items: start; align-content: center; gap: .9rem; padding: clamp(1.25rem, 4vw, 3.5rem); }
.zone-banner__copy a { color: var(--ex-accent); font-size: .9rem; }
.zone-banner h1 { color: var(--ex-accent); font-size: clamp(1.85rem, 4vw, 3.15rem); overflow-wrap: anywhere; }
.zone-banner p { max-width: 35ch; margin: 0; line-height: 1.7; color: var(--ex-sub); }
.zone-banner .zone-banner__action { padding: .5rem .85rem; border: 1px solid var(--gallery-rule); border-radius: 999px; }
.zone-banner__scene { position: relative; min-height: 16rem; }
.zone-banner__scene > .item-card__cover { position: absolute; inset: 0; height: 100%; }
.zone-nav-label { align-self: center; color: var(--ex-sub); font-size: .85rem; }
.zone-exhibits-title { margin: 1.1rem 0 0; font-size: clamp(1.35rem, 3vw, 1.85rem); }
.zone-exhibits .item-card { background: linear-gradient(145deg, #66241b, #521812); }
.zone-exhibits .item-card__title { color: var(--ex-accent); font-size: 1.12rem; }
.zone-exhibits .zone-card__action { display: inline-block; }
.zone-hero { border: 1px solid var(--gallery-rule); }
.zone-hero__title { font-size: clamp(1.85rem, 4vw, 3.15rem); max-width: calc(100% - 3rem); overflow-wrap: anywhere; }

/* ---------- 子展厅主题一：模拟法庭 · 庄严木金 ---------- */
.theme-court {
  --ex-panel: #3a1a10; --ex-text: #fff3dd; --ex-sub: #e3c79e;
  --ex-accent: #eec98c; --ex-accent-2: #c89b5a;
  --gallery-rule: color-mix(in srgb, #c89b5a 32%, transparent);
}
.theme-court .zone-banner {
  position: relative; grid-template-columns: minmax(0, 3fr) minmax(0, 6fr);
  border: 1px solid #c89b5a66; background: linear-gradient(115deg, #33150c, #1c0b06 60%, #2b1209);
  box-shadow: inset 0 0 0 1px #c89b5a2e, inset 0 -3rem 4rem #00000073;
}
.theme-court .zone-banner::before {
  content: '⚖'; position: absolute; left: clamp(.8rem, 2.5vw, 2rem); bottom: -1.4rem;
  font-size: clamp(6rem, 12vw, 11rem); line-height: 1; color: #eec98c14; pointer-events: none;
}
.theme-court .zone-banner__copy { position: relative; z-index: 1; border-right: 1px solid #c89b5a3d; }
.theme-court .zone-banner h1 { letter-spacing: .14em; }
.theme-court .zone-banner__action { border-color: #c89b5a80; }
.theme-court .zone-exhibits-title { letter-spacing: .12em; }
.theme-court .zone-exhibits .item-card {
  background: linear-gradient(165deg, #3a1a10, #22100a);
  border-color: #c89b5a4d; border-top: 3px solid #c89b5a;
}
.theme-court .zone-exhibits .item-card__title { letter-spacing: .06em; }
.theme-court .zone-exhibits .item-card__cover { border-bottom: 1px solid #c89b5a40; }

/* ---------- 子展厅主题二：普法成果展示 · 庆典展廊 ---------- */
.theme-achievement {
  --ex-panel: #8d2a1a; --ex-text: #fff2da; --ex-sub: #f6cf9d;
  --ex-accent: #ffd44d; --ex-accent-2: #ff9d3c;
  --gallery-rule: color-mix(in srgb, #ffd44d 30%, transparent);
}
.theme-achievement .zone-banner {
  position: relative; grid-template-columns: 1fr;
  background: linear-gradient(180deg, #a03415, #6d1c0e 45%, #4d130b);
}
.theme-achievement .zone-banner__scene { order: -1; min-height: clamp(13rem, 30vw, 22rem); }
.theme-achievement .zone-banner__copy { justify-items: center; text-align: center; padding-top: 1.6rem; }
.theme-achievement .zone-banner h1 {
  padding-bottom: .55rem;
  background: linear-gradient(90deg, transparent, #ffd44d, transparent) bottom / 42% 3px no-repeat;
}
.theme-achievement .zone-banner p { max-width: 52ch; }
.theme-achievement .zone-exhibits .item-card {
  position: relative; overflow: hidden; border-color: #ffd44d45;
  background: radial-gradient(120% 90% at 50% 0%, #b34a1e 0%, #7c2314 45%, #58160c 100%);
}
.theme-achievement .zone-exhibits .item-card::before {
  content: '★'; position: absolute; top: .55rem; right: .7rem; color: #ffd44db0; font-size: .95rem;
}
.theme-achievement .zone-exhibits .item-card__cover { aspect-ratio: 2.2; }

/* ---------- 子展厅主题三：法律文书 · 宣纸卷宗 ---------- */
.theme-document {
  --ex-panel: #40301d; --ex-text: #fff2d8; --ex-sub: #e6c9a0;
  --ex-accent: #e0a860; --ex-accent-2: #a63f2e;
  --gallery-rule: color-mix(in srgb, #e0a860 30%, transparent);
}
.theme-document .zone-banner {
  position: relative; grid-template-columns: minmax(0, 5fr) minmax(0, 4fr);
  border: 1px solid #8a6b3d80; background: linear-gradient(150deg, #f6ecd7, #ecdcba 70%, #e3cf9f);
}
.theme-document .zone-banner__copy { position: relative; }
.theme-document .zone-banner__copy a { color: #a63f2e; }
.theme-document .zone-banner h1 { color: #47301a; }
.theme-document .zone-banner p { color: #6b4f2e; }
.theme-document .zone-banner__action { border-color: #8a6b3d99; color: #47301a; }
.theme-document .zone-banner__copy::after {
  content: '卷宗'; position: absolute; top: 1.1rem; right: 1.1rem;
  display: grid; place-items: center; width: 2.6rem; aspect-ratio: 1;
  border: 2px solid #a63f2e; border-radius: .3rem; color: #a63f2e;
  font-family: "Songti SC", "Noto Serif CJK SC", SimSun, serif; font-weight: 700;
  writing-mode: vertical-rl; font-size: .95rem; letter-spacing: .12em;
  opacity: .85; transform: rotate(6deg);
}
.theme-document .zone-exhibits .item-card {
  position: relative; background: #f7edd8; border-color: #8a6b3d66;
  box-shadow: inset 4px 0 0 #a63f2e;
}
.theme-document .zone-exhibits .item-card__title { color: #47301a; }
.theme-document .zone-exhibits .item-card__summary { color: #6b4f2e; }
.theme-document .zone-exhibits .zone-card__action { color: #a63f2e; }
.theme-document .zone-exhibits .item-card__cover { border-bottom: 1px dashed #8a6b3d80; }
.theme-mediation {
  --ex-panel: #39291f; --ex-text: #fcf0db; --ex-sub: #e6c9a7;
  --ex-accent: #c9ddbf; --ex-accent-2: #9cb595;
  --gallery-rule: #9cb59555;
}
.theme-compliance {
  --ex-panel: #202a38; --ex-text: #fff3dc; --ex-sub: #ddcbb0;
  --ex-accent: #f1ca8b; --ex-accent-2: #c59155;
  --gallery-rule: #c5915566;
}
.theme-mediation .zone-banner, .theme-compliance .zone-banner {
  background: var(--ex-panel);
  border: 1px solid var(--gallery-rule);
}
.gallery-empty {
  padding: 1.25rem clamp(1rem, 4vw, 2rem);
  background: var(--ex-panel);
  border-radius: var(--ex-radius);
}
.gallery-empty h2 { margin: 0; font-size: 1.15rem; }
.gallery-empty p { margin: .4rem 0 0; color: var(--ex-sub); }
.exhibit-detail { display: grid; grid-template-columns: minmax(0, 1.65fr) minmax(0, 1fr); gap: 1.15rem; margin: 1rem auto 0; }
.exhibit-heading { grid-column: 1 / -1; }
.exhibit-heading h1 { text-align: center; color: var(--ex-accent); }
.exhibit-stage { min-width: 0; padding: .7rem; border: 1px solid var(--gallery-rule); border-radius: var(--ex-radius); background: var(--ex-panel); }
.exhibit-media-empty { margin: 0; padding: 2.5rem 1rem; color: var(--ex-sub); text-align: center; }
.exhibit-description { min-width: 0; --ex-panel: #efe0cd; --ex-text: #4c1d16; --ex-sub: #6c3827; --ex-accent: #932e21; background: #f7ecdc; color: var(--ex-text); border-radius: var(--ex-radius); }
.exhibit-description h2 { margin: 0 0 .65rem; font-size: 1.2rem; }
.exhibit-description .item-card__summary { padding: .75rem; background: #f0dfc9; border-radius: .55rem; }
.exhibit-detail--lite { max-width: 58rem; grid-template-columns: 1fr; }
.exhibit-detail--lite .exhibit-heading h1 { text-align: left; }
.exhibit-detail h1 { margin: .65rem 0; font-size: clamp(1.8rem, 4vw, 3rem); }
.exhibit-detail .item-card__cover { max-height: 29rem; border-radius: .55rem; }
.detail-back { font-size: .88rem; color: var(--ex-accent); text-decoration: none; }
.exhibit-detail .item-card__summary { font-size: 1rem; }
.exhibit-detail button + button { margin-inline-start: .5rem; }
.exhibit-content, .copy-result { white-space: pre-wrap; overflow-wrap: anywhere; user-select: text; }
button { font: inherit; padding: .45rem .8rem; cursor: pointer; }
.zone-hero img { height: 100%; }
.media { display: grid; gap: 1rem; }
.exhibit-stage > .item-card__cover + .media { margin-top: .85rem; }
.media img, .media video, .media audio { max-width: 100%; }
.media-modes { display: flex; flex-wrap: wrap; gap: .4rem; margin-top: .8rem; }
.media-modes button[aria-pressed="true"] { background: var(--ex-accent); color: var(--ex-bg); }
.exhibit-rail { grid-column: 1 / -1; align-items: center; padding: .65rem; background: var(--ex-panel); border-radius: var(--ex-radius); }
.exhibit-thumbnails { flex: 1; min-width: 0; display: flex; gap: .5rem; overflow-x: auto; padding: .2rem; }
.exhibit-thumbnails a { flex: 0 0 5.5rem; border: 1px solid var(--gallery-rule); border-radius: .55rem; overflow: hidden; text-decoration: none; }
.exhibit-thumbnails a[aria-current] { border-color: var(--ex-accent); box-shadow: 0 0 .4rem #ffc98f70; }
.exhibit-thumbnails span { display: block; padding: .2rem; font-size: .72rem; }
.exhibit-thumbnails .item-card__cover { aspect-ratio: 1.65; }
.gallery-header nav { display: flex; align-items: center; gap: .55rem; flex-wrap: wrap; }
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
.lite-day { --ex-bg: #edf4fa; --ex-panel: #fff; --ex-text: #172437; --ex-sub: #425169; --ex-accent: #175c70; background: var(--ex-bg); }
@media (max-width: 600px) {
  .gallery-header { padding-block: .9rem; }
  .lobby-scene { grid-template-columns: minmax(0, 1fr); min-height: 31rem; padding: .9rem .6rem 1rem; }
  .scene-title { padding-inline: .25rem; }
  .scene-title h1 { font-size: clamp(1.5rem, 6.5vw, 2.2rem); }
  .scene-title p { font-size: .77rem; }
  .scene-tools { position: static; grid-row: 5; grid-template-columns: repeat(5, minmax(0, 1fr)); transform: none; margin-top: .6rem; }
  .scene-tools button { min-width: 0; font-size: .65rem; }
  .scene-tools button > span { width: 2rem; height: 2rem; }
  .scene-guide { position: static; grid-row: 3; margin-top: .6rem; width: auto; }
  .scene-guide-menu { display: flex; width: 100%; }
  .scene-guide-menu > * { justify-content: center; padding-inline: .2rem; font-size: .72rem; }
  .scene-guide-menu > * + * { border-top: 0; border-left: 1px solid #efab7133; }
  .scene-guide-menu svg { width: .85rem; height: .85rem; padding: .2rem; }
  .scene-map { width: 100%; }
  .scene-map img, .scene-map-pin { display: none; }
  .scene-map[open] .scene-map-canvas { max-width: 12rem; margin-inline: auto; }
  .scene-map[open] img, .scene-map[open] .scene-map-pin { display: block; }
  .scene-dock { grid-row: 4; width: 100%; margin-bottom: 0; }
  .scene-carousel { gap: .3rem; }
  .scene-step { font-size: .62rem; }
  .scene-step > span { width: 1.55rem; height: 1.55rem; }
  .scene-thumbnail-photo { height: 2.5rem; }
  .scene-thumbnail-label svg { display: none; }
  .exhibit-detail { grid-template-columns: 1fr; }
  .scene-home .gallery-section, .scene-home .gallery-section + .zone-layout--grid {
    --ex-panel: #f7ecdc; --ex-text: #4c1d16; --ex-sub: #705141; --ex-accent: #932e21;
  }
  .gallery-lobby--lite { min-height: auto; background: var(--ex-panel); }
  .gallery-lobby h1 { font-size: 3rem; }
  .gallery-lobby--lite h1 { font-size: 2rem; }
  .zone-hero__title { left: 1rem; bottom: 1rem; }
  .zone-banner { grid-template-columns: minmax(0, 1fr); }
  .zone-banner__scene { min-height: 0; aspect-ratio: 2; }
}
@media (max-width: 720px), (max-aspect-ratio: 1/1) {
  .scene-projection { position: relative; inset: auto; container-type: normal; align-self: start; margin-top: 2.5rem; }
  .scene-hotspots { position: static; transform: none; width: auto; height: auto; display: flex; justify-content: center; flex-wrap: wrap; gap: .45rem; }
  .scene-hotspots a { position: static; width: auto; height: auto; max-width: 100%; clip-path: none; border-radius: .45rem; font-size: .78rem; }
  .scene-hotspots a::before { clip-path: none; border-radius: .35rem; }
  .scene-sign-content { position: relative; padding: .35rem .45rem; transform: none; }
  .scene-sign-copy small { display: none; }
}
@media (prefers-reduced-motion: reduce) {
  .item-card { transition: none; }
}
</style>
