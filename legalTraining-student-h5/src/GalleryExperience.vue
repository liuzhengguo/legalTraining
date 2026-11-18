<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { loadGalleryData, loadSpots, hallSceneFor, panoramaFor, mediaUrl, itemCover, stripHtml } from './galleryContent'
import { navigation } from './navigation'
import heroImage from './assets/gallery-hero.webp'
import exhibitionPlan from './assets/exhibition-plan.webp'

const route = useRoute()
const router = useRouter()

const zones = ref([])
const items = ref([])
const loading = ref(true)
const spotsByZone = ref({})
const activeItem = ref(null)
const itemDialog = ref(false)
const planOpen = ref(false)
const vrOpen = ref(false)
const vrIndex = ref(0)
const vrOffset = ref(0)
const dragging = ref(false)
const dragStartX = ref(0)
const dragStartOffset = ref(0)

const mode = computed(() => route.name === 'galleryZone' ? 'zone'
  : route.name === 'galleryHotspots' ? 'hotspots'
    : route.name === 'galleryBoards' ? 'boards'
      : route.name === 'galleryItem' ? 'item' : 'guide')

const pageTitle = computed(() => ({
  guide: '展厅导览', zone: '沉浸展区', hotspots: '热点交互', boards: '图文展板', item: '展项详情'
})[mode.value])

const routeItem = computed(() => items.value.find(entry => String(entry.itemId) === String(route.params.itemId || '')))
const activeZoneId = computed(() => String(route.params.zoneId || (mode.value === 'item' ? routeItem.value?.zoneId : '') || zones.value[0]?.zoneId || ''))
const zone = computed(() => zones.value.find(entry => String(entry.zoneId) === activeZoneId.value) || zones.value[0])
const zoneIndex = computed(() => zones.value.findIndex(entry => String(entry.zoneId) === String(zone.value?.zoneId)))
const previousZone = computed(() => zoneIndex.value > 0 ? zones.value[zoneIndex.value - 1] : null)
const nextZone = computed(() => zoneIndex.value >= 0 && zoneIndex.value < zones.value.length - 1 ? zones.value[zoneIndex.value + 1] : null)
const zoneItems = computed(() => items.value.filter(entry => String(entry.zoneId) === String(zone.value?.zoneId)))
const currentSpots = computed(() => spotsByZone.value[String(zone.value?.zoneId)] || [])
const allSpots = computed(() => zones.value.flatMap(entry =>
  (spotsByZone.value[String(entry.zoneId)] || []).map(spot => ({ ...spot, zoneName: entry.zoneName }))))

const vrScenes = computed(() => [
  { id: 'lobby', name: '中央大厅', source: heroImage },
  ...zones.value.map(entry => ({ id: String(entry.zoneId), name: entry.zoneName, source: panoramaFor(entry) }))
])
const vrScene = computed(() => vrScenes.value[Math.min(vrIndex.value, vrScenes.value.length - 1)])

async function refresh() {
  loading.value = true
  try {
    const data = await loadGalleryData()
    zones.value = data.zones
    items.value = data.items
    const spotEntries = await Promise.all(data.zones.map(async entry => [entry.zoneId, await loadSpots(entry.zoneId)]))
    spotsByZone.value = Object.fromEntries(spotEntries)
    if (mode.value === 'item') {
      const item = items.value.find(entry => String(entry.itemId) === String(route.params.itemId || ''))
      if (item) openItem(item)
    }
  } finally {
    loading.value = false
  }
}
onMounted(refresh)

function goHome() {
  router.push('/home')
}
function openZone(entry) {
  router.push('/gallery/zone/' + entry.zoneId)
}
function stepZone(direction) {
  const target = direction > 0 ? nextZone.value : previousZone.value
  if (target) openZone(target)
}

function findItem(id) {
  return items.value.find(entry => String(entry.itemId) === String(id))
}
function openItem(item) {
  if (!item) return
  activeItem.value = item
  itemDialog.value = true
}
function openSpot(spot) {
  if (spot.targetType === 'zone') return openZone({ zoneId: spot.targetId })
  const item = findItem(spot.targetId)
  if (item) return openItem(item)
  showToast('该热点暂未开放')
}
function spotLabel(spot) {
  return spot.spotName || findItem(spot.targetId)?.title || '探索热点'
}
function itemMedia(item) {
  return Array.isArray(item?.media) ? item.media : []
}
function mediaSource(entry) {
  return mediaUrl(entry.url || entry.cover)
}

function openVr(index) {
  vrIndex.value = typeof index === 'number' ? index
    : Math.max(0, vrScenes.value.findIndex(entry => entry.id === String(zone.value?.zoneId)))
  vrOffset.value = 0
  vrOpen.value = true
}
function switchVr(index) {
  vrIndex.value = index
  vrOffset.value = 0
}
function onDragStart(event) {
  dragging.value = true
  dragStartX.value = event.clientX ?? (event.touches?.[0]?.clientX || 0)
  dragStartOffset.value = vrOffset.value
  event.currentTarget?.setPointerCapture?.(event.pointerId)
}
function onDragMove(event) {
  if (!dragging.value) return
  const x = event.clientX ?? (event.touches?.[0]?.clientX || 0)
  const delta = (x - dragStartX.value) / Math.max(1, window.innerWidth) * 200
  vrOffset.value = Math.max(-50, Math.min(0, dragStartOffset.value + delta))
}
function onDragEnd() {
  dragging.value = false
}
watch(vrOpen, open => {
  document.body.style.overflow = open ? 'hidden' : ''
})
watch(() => route.fullPath, () => {
  planOpen.value = false
  if (mode.value === 'item' && items.value.length) {
    const item = items.value.find(entry => String(entry.itemId) === String(route.params.itemId || ''))
    if (item) openItem(item)
  }
})
</script>

<template>
  <div class="experience">
    <!-- 顶部：沉浸式场景 -->
    <section class="scene-hero" :style="{ backgroundImage: 'url(' + (mode === 'guide' || mode === 'zone' ? hallSceneFor(zone) : heroImage) + ')' }">
      <div class="scene-mask"></div>
      <div class="scene-top">
        <button type="button" class="round-btn" aria-label="返回展厅首页" @click="goHome"><van-icon name="arrow-left" /></button>
        <span class="scene-top-title">{{ pageTitle }}</span>
        <button type="button" class="round-btn" aria-label="打开规划图" @click="planOpen = true"><van-icon name="map-o" /></button>
      </div>
      <div class="scene-copy">
        <p class="scene-eyebrow">云上法治 · 数字展厅</p>
        <h1>{{ mode === 'zone' ? (zone?.zoneName || '沉浸展区') : '云墙数字展厅' }}</h1>
        <p class="scene-intro">{{ mode === 'zone' ? (zone?.intro || '沉浸式参观本展区的展项内容') : '足不出户，沉浸式参观法治文化展厅' }}</p>
      </div>
      <button v-for="(spot, index) in (mode === 'zone' ? currentSpots : [])" :key="spot.spotId || index"
              type="button" class="scene-spot" :style="{ left: (spot.posX || 50) + '%', top: (spot.posY || 50) + '%' }"
              :aria-label="spotLabel(spot)" @click="openSpot(spot)">
        <van-icon name="location-o" /><span>{{ spotLabel(spot) }}</span>
      </button>
    </section>

    <div class="content-body">
      <div class="scene-actions">
        <button type="button" class="action-primary" @click="openZone(zone || { zoneId: 1 })">进入展区</button>
        <button type="button" @click="openVr()"><van-icon name="eye-o" /> VR 全景</button>
        <button type="button" @click="planOpen = true"><van-icon name="map-o" /> 规划图</button>
      </div>

      <!-- 展区切换 -->
      <div class="zone-strip" aria-label="切换展区">
        <button v-for="entry in zones" :key="entry.zoneId" type="button"
                :class="{ active: String(entry.zoneId) === String(zone?.zoneId) }"
                @click="openZone(entry)">{{ entry.zoneName }}</button>
      </div>

      <!-- 展厅导览 / 展区列表 -->
      <template v-if="mode === 'guide' || mode === 'zone'">
        <div class="section-head">
          <h2>{{ mode === 'zone' ? zone?.zoneName + ' · 展项' : '展区导览' }}</h2>
          <button v-if="mode === 'zone'" type="button" class="text-btn" @click="goHome">返回首页</button>
        </div>
        <ul v-if="mode === 'zone' ? zoneItems.length : zones.length" class="card-list">
          <li v-for="(entry, index) in (mode === 'zone' ? zoneItems : zones)" :key="entry.itemId || entry.zoneId">
            <button type="button" class="content-card" @click="mode === 'zone' ? openItem(entry) : openZone(entry)">
              <img :src="itemCover(entry, index)" :alt="entry.title || entry.zoneName" loading="lazy">
              <span class="card-info">
                <strong>{{ entry.title || entry.zoneName }}</strong>
                <small>{{ stripHtml(entry.summary || entry.intro || '点击进入参观') }}</small>
                <em>{{ mode === 'zone' ? '查看详情' : (items.filter(item => String(item.zoneId) === String(entry.zoneId)).length + ' 件展项 · 进入展区') }}</em>
              </span>
              <van-icon name="arrow" />
            </button>
          </li>
        </ul>
        <p v-else class="empty">正在加载展厅内容…</p>
        <div v-if="mode === 'zone'" class="zone-step">
          <button type="button" :disabled="!previousZone" @click="stepZone(-1)">‹ 上一展区</button>
          <button type="button" :disabled="!nextZone" @click="stepZone(1)">下一展区 ›</button>
        </div>
      </template>

      <!-- 热点交互 -->
      <template v-else-if="mode === 'hotspots'">
        <div class="section-head"><h2>可交互热点</h2><span class="count">{{ allSpots.length }} 个</span></div>
        <ul v-if="allSpots.length" class="card-list">
          <li v-for="(spot, index) in allSpots" :key="spot.spotId || index">
            <button type="button" class="content-card" @click="openSpot(spot)">
              <span class="spot-icon"><van-icon name="location-o" /></span>
              <span class="card-info">
                <strong>{{ spotLabel(spot) }}</strong>
                <small>{{ spot.zoneName }} · 点击查看对应内容</small>
                <em>{{ spot.targetType === 'zone' ? '展区入口' : '展项详情' }}</em>
              </span>
              <van-icon name="arrow" />
            </button>
          </li>
        </ul>
        <p v-else class="empty">{{ loading ? '正在加载热点…' : '当前展区暂未配置热点' }}</p>
      </template>

      <!-- 图文展板 -->
      <template v-else>
        <div class="section-head">
          <h2>{{ mode === 'item' ? '更多展项' : '图文展板' }}</h2>
          <button v-if="mode === 'item' && zone" type="button" class="text-btn" @click="openZone(zone)">返回所属展区</button>
          <span v-else class="count">{{ items.length }} 件</span>
        </div>
        <div v-if="items.length" class="board-grid">
          <button v-for="(entry, index) in items" :key="entry.itemId || index" type="button" class="board-card" @click="openItem(entry)">
            <img :src="itemCover(entry, index)" :alt="entry.title" loading="lazy">
            <span class="board-title">{{ entry.title }}</span>
            <span class="board-summary">{{ stripHtml(entry.summary || '') }}</span>
          </button>
        </div>
        <p v-else class="empty">{{ loading ? '正在加载展板…' : '暂无展板内容' }}</p>
      </template>
    </div>

    <!-- 底部导航，与学生端其他页面保持一致 -->
    <nav class="primary-nav" aria-label="主导航">
      <RouterLink v-for="entry in navigation" :key="entry.to" :to="entry.to">
        <van-icon :name="entry.icon" aria-hidden="true" /><span>{{ entry.title }}</span>
      </RouterLink>
    </nav>

    <!-- 展项详情 -->
    <van-popup v-model:show="itemDialog" position="bottom" :style="{ height: '78%', borderRadius: '1.2rem 1.2rem 0 0' }">
      <div v-if="activeItem" class="detail-panel">
        <div class="detail-head">
          <strong>展项详情</strong>
          <button type="button" aria-label="关闭详情" @click="itemDialog = false"><van-icon name="cross" /></button>
        </div>
        <div class="detail-body">
          <img v-if="itemCover(activeItem)" class="detail-cover" :src="itemCover(activeItem)" :alt="activeItem.title">
          <h2>{{ activeItem.title }}</h2>
          <p v-if="activeItem.summary" class="detail-summary">{{ stripHtml(activeItem.summary) }}</p>
          <p v-if="activeItem.content" class="detail-content">{{ stripHtml(activeItem.content) }}</p>
          <template v-for="(media, index) in itemMedia(activeItem)" :key="index">
            <img v-if="media.type === 'image' && mediaSource(media)" class="detail-media" :src="mediaSource(media)" :alt="activeItem.title" loading="lazy">
            <video v-else-if="media.type === 'video' && mediaSource(media)" class="detail-media" :src="mediaSource(media)" controls playsinline></video>
            <audio v-else-if="media.type === 'audio' && mediaSource(media)" class="detail-audio" :src="mediaSource(media)" controls></audio>
            <a v-else-if="media.type === 'link' && mediaSource(media)" class="detail-link" :href="mediaSource(media)" target="_blank" rel="noopener">打开关联链接</a>
          </template>
        </div>
      </div>
    </van-popup>

    <!-- 规划图 -->
    <van-popup v-model:show="planOpen" position="center" :style="{ width: '88%', maxWidth: '26rem', borderRadius: '1rem' }">
      <div class="plan-panel">
        <div class="detail-head"><strong>展馆规划图</strong><button type="button" aria-label="关闭规划图" @click="planOpen = false"><van-icon name="cross" /></button></div>
        <img :src="exhibitionPlan" alt="展馆规划图">
        <div class="plan-zones">
          <button v-for="entry in zones" :key="entry.zoneId" type="button" @click="planOpen = false; openZone(entry)">{{ entry.zoneName }}</button>
        </div>
      </div>
    </van-popup>

    <!-- App 内全景浏览（左右拖动切换视角） -->
    <div v-if="vrOpen" class="vr-overlay">
      <div class="vr-head">
        <button type="button" class="round-btn" aria-label="关闭全景" @click="vrOpen = false"><van-icon name="arrow-left" /></button>
        <strong>{{ vrScene?.name || '全景浏览' }}</strong>
        <span class="vr-count">{{ vrIndex + 1 }}/{{ vrScenes.length }}</span>
      </div>
      <div class="vr-stage" @pointerdown="onDragStart" @pointermove="onDragMove" @pointerup="onDragEnd" @pointercancel="onDragEnd">
        <img v-if="vrScene" :src="vrScene.source" :alt="vrScene.name" draggable="false"
             :style="{ transform: 'translateX(' + vrOffset + '%)' }">
      </div>
      <div class="vr-scenes">
        <button v-for="(scene, index) in vrScenes" :key="scene.id" type="button"
                :class="{ active: index === vrIndex }" @click="switchVr(index)">{{ scene.name }}</button>
      </div>
      <p class="vr-tip">左右滑动浏览当前展馆全景</p>
    </div>
  </div>
</template>

<style scoped>
.experience {
  width: 100%;
  max-width: var(--lt-app-width);
  min-height: 100dvh;
  margin-inline: auto;
  padding-bottom: calc(4.2rem + var(--lt-safe-bottom));
  background: var(--lt-field);
  box-shadow: 0 0 48px #21050466;
}
.scene-hero {
  position: relative;
  height: clamp(14rem, 58vw, 21rem);
  background-position: center;
  background-size: cover;
  color: var(--lt-on-dark);
  overflow: hidden;
}
.scene-mask { position: absolute; inset: 0; background: linear-gradient(180deg, #3d100e9c 0%, #3d100e40 42%, #3d100ee0 100%); }
.scene-top { position: relative; z-index: 2; display: flex; align-items: center; justify-content: space-between; padding: calc(.8rem + var(--lt-safe-top)) 1rem 0; }
.scene-top-title { font-size: 1rem; font-weight: 700; letter-spacing: .08em; }
.round-btn { display: grid; place-items: center; width: 2.3rem; height: 2.3rem; border: 1px solid #ffffff59; border-radius: 50%; background: #3d100e80; color: var(--lt-on-dark); font-size: 1.15rem; }
.scene-copy { position: absolute; z-index: 1; left: 1.1rem; right: 1.1rem; bottom: 1.3rem; }
.scene-eyebrow { margin: 0 0 .35rem; font-size: .78rem; letter-spacing: .18em; color: var(--lt-on-dark-muted); }
.scene-copy h1 { margin: 0; font: 700 1.75rem/1.25 "Songti SC", SimSun, serif; text-shadow: 0 2px 10px #1a0505aa; }
.scene-intro { margin: .45rem 0 0; font-size: .84rem; color: var(--lt-on-dark-muted); }
.scene-spot { position: absolute; z-index: 3; display: grid; justify-items: center; gap: .1rem; transform: translate(-50%, -50%); border: 0; background: transparent; color: var(--lt-on-dark); font-size: .72rem; }
.scene-spot .van-icon { display: grid; place-items: center; width: 2.2rem; height: 2.2rem; border: 1px solid #fff8; border-radius: 50%; background: #842d22d9; box-shadow: 0 0 0 .35rem #842d2233; font-size: 1.2rem; }
.scene-spot span { max-width: 7rem; padding: .12rem .4rem; border-radius: 1rem; background: #3d100ecc; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.content-body { padding: .9rem 1rem 1.5rem; }
.scene-actions { display: grid; grid-template-columns: 1.2fr 1fr 1fr; gap: .5rem; }
.scene-actions button { display: flex; align-items: center; justify-content: center; gap: .3rem; min-height: 2.6rem; border: 1px solid var(--lt-border); border-radius: .8rem; background: var(--lt-card); color: var(--lt-primary); font-size: .86rem; font-weight: 650; }
.scene-actions button.action-primary { border-color: var(--lt-primary); background: var(--lt-primary); color: var(--lt-on-dark); }
.zone-strip { display: flex; gap: .45rem; margin: 1rem 0 1.2rem; padding-bottom: .2rem; overflow-x: auto; scrollbar-width: none; }
.zone-strip::-webkit-scrollbar { display: none; }
.zone-strip button { flex: none; padding: .45rem .8rem; border: 1px solid var(--lt-border); border-radius: 2rem; background: var(--lt-card); color: var(--lt-sub); font-size: .8rem; }
.zone-strip button.active { border-color: var(--lt-primary); background: var(--lt-primary); color: var(--lt-on-dark); }
.section-head { display: flex; align-items: center; justify-content: space-between; margin: 0 .1rem .75rem; }
.section-head h2 { margin: 0; font-size: 1.1rem; color: var(--lt-text); }
.count, .text-btn { color: var(--lt-sub); font-size: .8rem; }
.text-btn { border: 0; background: transparent; }
.card-list { display: grid; gap: .7rem; margin: 0; padding: 0; list-style: none; }
.content-card { display: flex; align-items: center; gap: .8rem; width: 100%; padding: .6rem; border: 1px solid var(--lt-border); border-radius: var(--lt-card-radius); background: var(--lt-card); text-align: left; color: var(--lt-text); }
.content-card img { flex: none; width: 5.1rem; height: 4.2rem; border-radius: .7rem; object-fit: cover; background: var(--lt-tint); }
.content-card .van-icon { flex: none; color: var(--lt-primary); }
.card-info { display: grid; gap: .25rem; flex: 1; min-width: 0; }
.card-info strong { font-size: .95rem; }
.card-info small, .card-info em { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: .76rem; font-style: normal; color: var(--lt-sub); }
.card-info em { color: var(--lt-primary); }
.spot-icon { display: grid; place-items: center; flex: none; width: 3.2rem; height: 3.2rem; border-radius: 50%; background: var(--lt-tint); color: var(--lt-primary); font-size: 1.5rem; }
.empty { padding: 2rem 0; color: var(--lt-sub); text-align: center; font-size: .85rem; }
.zone-step { display: flex; justify-content: space-between; margin-top: 1rem; }
.zone-step button { padding: .5rem .9rem; border: 1px solid var(--lt-border); border-radius: .8rem; background: var(--lt-card); color: var(--lt-primary); font-size: .8rem; }
.zone-step button:disabled { opacity: .45; }
.board-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .7rem; }
.board-card { display: grid; gap: .35rem; padding: .5rem; border: 1px solid var(--lt-border); border-radius: var(--lt-card-radius); background: var(--lt-card); text-align: left; }
.board-card img { width: 100%; height: 6rem; border-radius: .7rem; object-fit: cover; background: var(--lt-tint); }
.board-title { color: var(--lt-text); font-size: .88rem; font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.board-summary { color: var(--lt-sub); font-size: .74rem; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }

.primary-nav {
  position: fixed; z-index: 5; inset: auto 0 0; width: min(100%, var(--lt-app-width));
  display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: .2rem; margin-inline: auto;
  padding: .4rem .3rem calc(.4rem + var(--lt-safe-bottom));
  background: var(--lt-nav); border-top: 1px solid #a15e45; box-shadow: 0 -4px 18px #551c1414;
}
.primary-nav a { display: grid; justify-items: center; align-content: center; gap: .16rem; min-height: 3.35rem; padding: .3rem .1rem; border-radius: var(--lt-radius); color: var(--lt-on-dark-muted); text-align: center; text-decoration: none; font-size: .76rem; font-weight: 650; }
.primary-nav .van-icon { font-size: 1.35rem; }

.detail-panel { height: 100%; display: flex; flex-direction: column; background: var(--lt-card); }
.detail-head { display: flex; align-items: center; justify-content: space-between; padding: .9rem 1rem; border-bottom: 1px solid var(--lt-border); color: var(--lt-text); }
.detail-head button { border: 0; background: transparent; color: var(--lt-sub); font-size: 1.25rem; }
.detail-body { overflow-y: auto; padding: 1rem; }
.detail-cover, .detail-media { display: block; width: 100%; border-radius: .8rem; margin-bottom: .9rem; }
.detail-media { max-height: 18rem; object-fit: cover; }
.detail-audio, .detail-link { width: 100%; margin-bottom: .9rem; }
.detail-body h2 { margin: 0 0 .6rem; font-size: 1.15rem; }
.detail-summary { margin: 0 0 .8rem; color: var(--lt-primary); font-size: .88rem; }
.detail-content { margin: 0; color: var(--lt-text); font-size: .9rem; line-height: 1.8; white-space: pre-line; }
.plan-panel { padding-bottom: 1rem; background: var(--lt-card); }
.plan-panel img { display: block; width: 100%; margin: .8rem 0; border-radius: .7rem; }
.plan-zones { display: flex; flex-wrap: wrap; gap: .4rem; padding: 0 .8rem; }
.plan-zones button { padding: .35rem .65rem; border: 1px solid var(--lt-border); border-radius: 1rem; background: var(--lt-tint); color: var(--lt-primary); font-size: .76rem; }

.vr-overlay { position: fixed; z-index: 20; inset: 0; display: flex; flex-direction: column; background: #210a08; color: var(--lt-on-dark); }
.vr-head { display: flex; align-items: center; justify-content: space-between; padding: calc(.7rem + var(--lt-safe-top)) 1rem .7rem; }
.vr-count { color: var(--lt-on-dark-muted); font-size: .8rem; }
.vr-stage { flex: 1; overflow: hidden; touch-action: pan-y; cursor: grab; }
.vr-stage img { display: block; width: 200%; height: 100%; max-width: none; object-fit: cover; user-select: none; will-change: transform; }
.vr-scenes { display: flex; gap: .4rem; padding: .8rem; overflow-x: auto; }
.vr-scenes button { flex: none; padding: .4rem .7rem; border: 1px solid #ffffff4d; border-radius: 2rem; background: transparent; color: var(--lt-on-dark-muted); font-size: .78rem; }
.vr-scenes button.active { border-color: var(--lt-primary); background: var(--lt-primary); color: var(--lt-on-dark); }
.vr-tip { margin: 0 0 calc(.7rem + var(--lt-safe-bottom)); color: var(--lt-on-dark-muted); text-align: center; font-size: .75rem; }
</style>
