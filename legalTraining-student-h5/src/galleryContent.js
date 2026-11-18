// App 内置展厅内容：接口优先，失败时提供本地素材兜底，避免跳转到 Web 展厅。
import { request } from './api'
import heroImage from './assets/gallery-hero.webp'
import courtImage from './assets/hall-court.webp'
import achievementImage from './assets/hall-achievement.webp'
import documentImage from './assets/hall-document.webp'
import mediationImage from './assets/hall-mediation.webp'
import complianceImage from './assets/hall-compliance.webp'
import courtPanorama from './assets/panoramas/court.png'
import achievementPanorama from './assets/panoramas/achievement.png'
import documentPanorama from './assets/panoramas/document.png'
import mediationPanorama from './assets/panoramas/mediation.png'
import compliancePanorama from './assets/panoramas/compliance.png'

const apiBase = (import.meta.env.VITE_API_BASE || '/dev-api').replace(/\/$/, '')

export function themeForName(name = '') {
  if (name.includes('调解')) return 'mediation'
  if (name.includes('合规')) return 'compliance'
  if (name.includes('模拟法庭') || name.includes('法庭')) return 'court'
  if (name.includes('普法成果') || name.includes('成果')) return 'achievement'
  if (name.includes('法律文书') || name.includes('文书')) return 'document'
  return ''
}

const hallImages = {
  court: courtImage,
  achievement: achievementImage,
  document: documentImage,
  mediation: mediationImage,
  compliance: complianceImage
}

const panoramaImages = {
  court: courtPanorama,
  achievement: achievementPanorama,
  document: documentPanorama,
  mediation: mediationPanorama,
  compliance: compliancePanorama
}

const themeByCode = {
  court: 'court', achievement: 'achievement', document: 'document',
  mediation: 'mediation', compliance: 'compliance'
}

function themeOf(entry) {
  return themeForName(entry?.zoneName) || themeByCode[entry?.zoneCode] || ''
}

export function hallSceneFor(zone) {
  return hallImages[themeOf(zone)] || heroImage
}

export function panoramaFor(zone) {
  return zone?.panoramaUrl ? mediaUrl(zone.panoramaUrl) : (panoramaImages[themeOf(zone)] || courtPanorama)
}

export function mediaUrl(url) {
  if (typeof url !== 'string' || !url) return ''
  if (url.startsWith('/profile/upload/')) return apiBase + url
  if (/^https?:\/\//i.test(url) || url.startsWith('data:')) return url
  return apiBase + (url.startsWith('/') ? url : '/' + url)
}

const fallbackZones = [
  { zoneId: 1, zoneCode: 'court', zoneName: '模拟法庭展区', intro: '走近审判现场，感受庭审流程与法治精神', cover: '' },
  { zoneId: 2, zoneCode: 'achievement', zoneName: '普法成果展示', intro: '学法知法，懂法守法，一站式了解普法建设成果', cover: '' },
  { zoneId: 3, zoneCode: 'document', zoneName: '法律文书展区', intro: '翻阅珍贵卷宗，感受法治历程', cover: '' },
  { zoneId: 4, zoneCode: 'mediation', zoneName: '人民调解展区', intro: '化解矛盾纠纷，体会调解温度', cover: '' },
  { zoneId: 5, zoneCode: 'compliance', zoneName: '合规教育展区', intro: '明晰行为边界，筑牢合规意识', cover: '' }
]

const fallbackItems = [
  { itemId: 101, zoneId: 1, title: '模拟法庭：校园借贷纠纷案', summary: '还原真实庭审程序，体验举证、质证与法庭辩论。', cover: '', content: '通过模拟法庭案例，学习民事诉讼的基本流程，理解证据规则与法律适用。' },
  { itemId: 102, zoneId: 2, title: '武汉理工大学普法成果', summary: '展示高校法治宣传教育成果与实践经验。', cover: '', content: '武汉理工大学持续推进法治校园建设，开展形式多样的普法宣传活动。' },
  { itemId: 103, zoneId: 3, title: '古代法律文书赏析', summary: '从珍贵卷宗中读懂法律制度的历史演进。', cover: '', content: '展区收录多份具有代表性的法律文书，呈现法治文化的发展脉络。' },
  { itemId: 104, zoneId: 4, title: '调解案例：邻里纠纷', summary: '以案说法，理解调解在基层治理中的作用。', cover: '', content: '调解员通过耐心沟通促使双方达成一致，实现案结事了人和。' },
  { itemId: 105, zoneId: 5, title: '大学生合规指南', summary: '学习、科研、就业中的合规要点提示。', cover: '', content: '围绕学术诚信、网络安全与劳动权益，梳理常见的合规风险与应对方式。' }
]

let cache = null

export async function loadGalleryData(force = false) {
  if (cache && !force) return cache
  const [zoneResult, itemResult] = await Promise.allSettled([
    request('/gallery/v1/zones', { authenticated: false }),
    request('/gallery/v1/items', { authenticated: false })
  ])
  const apiZones = zoneResult.status === 'fulfilled' && Array.isArray(zoneResult.value?.data)
    ? zoneResult.value.data : []
  const apiItems = itemResult.status === 'fulfilled' && Array.isArray(itemResult.value?.data)
    ? itemResult.value.data : []
  cache = {
    zones: apiZones.length ? apiZones : fallbackZones,
    items: apiItems.length ? apiItems : fallbackItems,
    live: apiZones.length > 0
  }
  return cache
}

export async function loadSpots(zoneId) {
  try {
    const result = await request('/gallery/v1/zones/' + zoneId + '/spots', { authenticated: false })
    if (Array.isArray(result?.data) && result.data.length) return result.data
  } catch { /* 使用本地热点兜底 */ }
  const fallback = fallbackItems.find(item => String(item.zoneId) === String(zoneId))
  return fallback ? [{
    spotId: 'spot-' + fallback.itemId,
    spotName: fallback.title,
    posX: 50,
    posY: 52,
    targetType: 'item',
    targetId: fallback.itemId
  }] : []
}

export function itemCover(item, index = 0) {
  const fallbackZonesById = { 1: 'court', 2: 'achievement', 3: 'document', 4: 'mediation', 5: 'compliance' }
  const sample = fallbackItems[index % fallbackItems.length]
  const theme = themeOf(item) || fallbackZonesById[item?.zoneId] || fallbackZonesById[sample?.zoneId]
  return mediaUrl(item?.cover) || hallImages[theme] || hallSceneFor({ zoneName: '' })
}

export function stripHtml(text = '') {
  return String(text).replace(/<[^>]*>/g, ' ').replace(/&nbsp;/g, ' ').replace(/\s+/g, ' ').trim()
}
