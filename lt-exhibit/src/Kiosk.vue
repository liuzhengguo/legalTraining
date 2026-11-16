<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

const api = import.meta.env.VITE_GALLERY_API || '/gallery-api'
const mediaOrigin = import.meta.env.VITE_GALLERY_MEDIA || ''
const prefix = 'lt-device:' + api + ':'
const credentialsKey = prefix + 'credentials'
const manifestKey = prefix + 'manifest'
const cacheName = prefix + 'covers'
let cacheBroken = false
const read = key => {
  try {
    const value = JSON.parse(localStorage.getItem(key))
    if (key === manifestKey && value !== null && (!Array.isArray(value.items) || !value.version)) {
      cacheBroken = true
      return null
    }
    return value
  } catch {
    if (key === manifestKey) cacheBroken = true
    return null
  }
}
const credentials = ref(read(credentialsKey))
const manifest = ref(read(manifestKey))
const configuration = ref({})
const items = computed(() => manifest.value?.items || [])
const selected = ref(0)
const browsing = ref(false)
const current = computed(() => items.value[selected.value])
const cover = ref('')
const movie = ref('')
const video = ref(null)
const videoSource = computed(() => configuration.value.mode === 'lite' ? ''
  : imageSource(current.value?.media?.find(entry => entry.type === 'video')?.url))
const offline = ref(false)
const busy = ref(false)
const error = ref('')
const form = ref({ deviceCode: '', activateCode: '' })
let heartbeat, rotation, idle, controller
let active = true

function imageSource(value) {
  if (typeof value !== 'string') return ''
  if (value.startsWith('/profile/upload/')) return new URL(mediaOrigin + value, location.origin).href
  return /^https:\/\//i.test(value) ? value : ''
}

function choose(index) {
  selected.value = items.value.length ? (index + items.value.length) % items.value.length : 0
}

function advance() {
  if (browsing.value || items.value.length === 0) return
  choose(selected.value + 1)
  if (items.value.length === 1 && video.value) {
    video.value.currentTime = 0
    video.value.play().catch(() => {})
  }
  play()
}

function play(allowVideoFallback = false) {
  clearTimeout(rotation)
  if (browsing.value || items.value.length < 2 || (videoSource.value && !allowVideoFallback)) return
  rotation = setTimeout(advance, Math.max(1, current.value?.staySeconds || 15) * 1000)
}

function interact() {
  browsing.value = true
  clearTimeout(rotation)
  clearTimeout(idle)
  idle = setTimeout(() => {
    browsing.value = false
    choose(0)
    play()
  }, (configuration.value.timeoutSeconds || 180) * 1000)
}

async function storeImages(rows, signal) {
  if (!window.caches) return
  try {
    const cache = await caches.open(cacheName)
    const URLs = new Set(rows.flatMap(row => [
      imageSource(row.cover), ...(row.media || []).filter(entry => entry.type === 'video' || entry.type === 'image')
        .map(entry => imageSource(entry.url))
    ]).filter(Boolean))
    await Promise.all([...URLs].map(async url => {
      if (signal.aborted) return
      if (await cache.match(url)) return
      try {
        const response = await fetch(url, { signal })
        if (response.ok) await cache.put(url, response)
      } catch { /* A failed asset does not block the playlist. */ }
    }))
    if (signal.aborted) return
    for (const request of await cache.keys()) {
      if (!URLs.has(request.url)) await cache.delete(request)
    }
  } catch { /* Browser storage can be unavailable in private sessions. */ }
}

function watchAsset(sourceOf, destination) {
  watch(sourceOf, async (source, _, cleanup) => {
    let url, cancelled = false
    cleanup(() => {
      cancelled = true
      if (url) URL.revokeObjectURL(url)
    })
    destination.value = source
    if (!source || !window.caches) return
    try {
      const saved = await (await caches.open(cacheName)).match(source)
      if (saved && !cancelled) {
        const blob = await saved.blob()
        if (!cancelled) destination.value = url = URL.createObjectURL(blob)
      }
    } catch { /* The remote cover is still available when online. */ }
  }, { immediate: true })
}
watchAsset(() => imageSource(current.value?.cover), cover)
watchAsset(() => offline.value ? videoSource.value : '', movie)
watch([configuration, video], () => {
  if (video.value) video.value.volume = (configuration.value.volume ?? 60) / 100
})
watch(videoSource, () => play())

async function request(path, body, headers = {}, signal = AbortSignal.timeout(7000)) {
  const response = await fetch(api + '/device/' + path, {
    method: 'POST', headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body), cache: 'no-store', signal
  })
  if (response.status === 401) return null
  const result = await response.json()
  if (!response.ok || result.code !== 200) throw new Error(result.msg || '终端连接失败')
  return result.data
}

async function synchronize() {
  if (controller || !credentials.value || !active) return
  clearTimeout(heartbeat)
  controller = new AbortController()
  const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(7000)])
  try {
    const previous = manifest.value
    const result = await request('heartbeat', {
      cacheVersion: previous?.version || null,
      cacheSize: previous ? new Blob([JSON.stringify(previous)]).size : 0
    }, { 'X-Device-Code': credentials.value.deviceCode, 'X-Device-Token': credentials.value.token }, signal)
    if (!active) return
    if (!result) {
      credentials.value = null
      manifest.value = null
      clearTimeout(rotation)
      clearTimeout(idle)
      localStorage.removeItem(credentialsKey)
      localStorage.removeItem(manifestKey)
      if (window.caches) await caches.delete(cacheName)
      error.value = '终端授权已失效，请重新激活'
      return
    }
    configuration.value = { ...result.config, mode: result.mode }
    offline.value = false
    if (cacheBroken) {
      cacheBroken = false
      fetch(api + '/device/events', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Device-Code': credentials.value.deviceCode,
          'X-Device-Token': credentials.value.token },
        body: JSON.stringify({ type: 'cache_broken', detail: '已检测到清单损坏并重新同步' }),
        keepalive: true
      }).catch(() => { cacheBroken = true })
    }
    if (result.changed) {
      const currentId = current.value?.itemId
      manifest.value = { version: result.version, items: result.items }
      localStorage.setItem(manifestKey, JSON.stringify(manifest.value))
      selected.value = Math.max(0, items.value.findIndex(item => item.itemId === currentId))
      play()
    }
    await storeImages(items.value, signal)
  } catch {
    if (active) offline.value = true
  } finally {
    controller = null
    if (active && credentials.value) heartbeat = setTimeout(synchronize, 30000)
  }
}

async function activate() {
  if (busy.value) return
  error.value = ''
  busy.value = true
  try {
    const data = await request('activate', form.value)
    if (!data?.token) throw new Error('设备激活失败')
    manifest.value = null
    localStorage.removeItem(manifestKey)
    credentials.value = { deviceCode: form.value.deviceCode, token: data.token }
    localStorage.setItem(credentialsKey, JSON.stringify(credentials.value))
    form.value.activateCode = ''
    await synchronize()
  } catch (cause) {
    error.value = cause.message || '无法激活终端，请检查网络'
  } finally { busy.value = false }
}

onMounted(() => {
  window.addEventListener('online', synchronize)
  if (credentials.value) {
    play()
    synchronize()
  }
})
onUnmounted(() => {
  active = false
  controller?.abort()
  for (const timer of [heartbeat, rotation, idle]) clearTimeout(timer)
  window.removeEventListener('online', synchronize)
})
</script>

<template>
  <main class="zone-page kiosk" @contextmenu.prevent :style="{ filter: 'brightness(' + (configuration.brightness ?? 100) + '%)' }">
    <section v-if="!credentials" class="item-card item-card__body">
      <h1>设备待激活</h1>
      <form @submit.prevent="activate">
        <label>设备码 <input v-model.trim="form.deviceCode" maxlength="64" required autocomplete="off"></label>
        <label>激活码 <input v-model.trim="form.activateCode" maxlength="16" required autocomplete="off"></label>
        <button :disabled="busy">激活</button>
      </form>
      <p v-if="error" role="alert">{{ error }}</p>
    </section>
    <template v-else>
      <p v-if="offline" role="status">离线模式</p>
      <p v-if="!current" role="status">{{ offline ? '暂无可播放的离线展项' : '等待分配展项' }}</p>
      <article v-else class="item-card" @pointerdown="interact">
        <video v-if="videoSource" ref="video" :key="selected + ':' + current.itemId" :src="movie || videoSource"
               :poster="cover" autoplay muted playsinline controls @ended="advance" @error="play(true)" @stalled="play(true)" @playing="play()" />
        <img v-else-if="cover" :src="cover" alt="" class="item-card__cover">
        <div class="item-card__body">
          <h1>{{ current.title }}</h1>
          <p>{{ current.summary }}</p>
          <p v-if="browsing || configuration.mode === 'lite'" class="content">{{ current.content }}</p>
          <button v-if="!browsing" @click="interact">浏览展项</button>
          <nav v-else aria-label="终端展项浏览">
            <button @click="interact(); choose(selected - 1)">上一展项</button>
            <button @click="interact(); choose(selected + 1)">下一展项</button>
            <button @click="browsing = false; choose(0); play()">返回轮播</button>
          </nav>
        </div>
      </article>
    </template>
  </main>
</template>

<style scoped>
.kiosk { min-height: 100vh; padding-block: 1rem; overscroll-behavior: contain; }
.kiosk form, .kiosk nav { display: flex; flex-wrap: wrap; gap: 1rem; }
.kiosk img, .kiosk video { display: block; max-height: 64vh; object-fit: contain; }
.content { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
