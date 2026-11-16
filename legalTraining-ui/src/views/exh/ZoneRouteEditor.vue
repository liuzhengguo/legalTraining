<template>
  <div class="zone-route-controls">
    <el-button @click="showOrder">编排参观动线</el-button>

    <el-dialog title="参观动线" :visible.sync="orderOpen" width="560px" append-to-body>
      <p>拖动展区调整顺序，也可用上移、下移按钮。</p>
      <ol class="route-list">
        <li v-for="(row, index) in ordered" :key="row.zoneId" :draggable="!saving"
            @dragstart="dragIndex = index; $event.dataTransfer.setData('text/plain', String(row.zoneId))"
            @dragover.prevent @drop.prevent="dropAt(index)" @dragend="dragIndex = null">
          <span>{{ row.zoneName }}</span>
          <el-button type="text" :disabled="index === 0 || saving" @click="move(index, index - 1)">上移</el-button>
          <el-button type="text" :disabled="index === ordered.length - 1 || saving" @click="move(index, index + 1)">下移</el-button>
        </li>
      </ol>
      <span slot="footer"><el-button type="primary" :loading="saving" :disabled="!ordered.length" @click="saveOrder">保存动线</el-button></span>
    </el-dialog>

    <el-dialog title="展区导航热点" :visible.sync="spotsOpen" width="min(900px, 94vw)" append-to-body @closed="dragging = null">
      <div ref="preview" class="spot-preview" @pointermove="drag" @pointerup="dragging = null" @pointercancel="dragging = null">
        <img v-if="previewUrl" :src="previewUrl" alt="热点定位底图" draggable="false">
        <p v-else class="preview-empty">未设置全景图或封面，按展厅深色底预览</p>
        <button v-for="(spot, index) in spots" :key="spot.localKey" type="button" class="marker"
                :class="{ active: activeIndex === index }"
                :title="spot.spotName" :aria-label="'定位热点 ' + (index + 1)"
                :style="{ left: (spot.posX * 100) + '%', top: (spot.posY * 100) + '%' }"
                @pointerdown.prevent="startDrag(index, $event)">
          <span class="marker-dot" aria-hidden="true"></span>
          <span class="marker-icon" aria-hidden="true">{{ icons[spot.icon] || '➜' }}</span>
          <span class="marker-name">{{ spot.spotName || '未命名热点' }}</span>
        </button>
      </div>
      <p>拖动热点调整位置；样式与展厅端一致，点击热点或悬停下方表单行可互相定位。</p>
      <el-form v-for="(spot, index) in spots" :key="spot.localKey" inline class="spot-row"
               :class="{ active: activeIndex === index }"
               @mouseenter="activeIndex = index" @mouseleave="activeIndex = null">
        <el-form-item label="名称"><el-input v-model.trim="spot.spotName" maxlength="100" placeholder="热点名称" /></el-form-item>
        <el-form-item label="图标">
          <el-select v-model="spot.icon" class="small-select">
            <el-option label="箭头" value="arrow" /><el-option label="展项" value="item" /><el-option label="信息" value="info" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标类型">
          <el-select v-model="spot.targetType" class="small-select" @change="spot.targetId = null">
            <el-option label="展区" value="zone" /><el-option label="展项" value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标">
          <el-select v-model="spot.targetId" filterable placeholder="选择目标">
            <el-option v-for="target in targets(spot)" :key="target.id" :label="target.title" :value="target.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="横坐标"><el-input-number v-model="spot.posX" :min="0" :max="1" :step=".01" :precision="4" /></el-form-item>
        <el-form-item label="纵坐标"><el-input-number v-model="spot.posY" :min="0" :max="1" :step=".01" :precision="4" /></el-form-item>
        <el-button type="text" @click="removeSpot(index)">移除热点</el-button>
      </el-form>
      <el-button :disabled="spots.length >= 100" @click="addSpot">添加热点</el-button>
      <span slot="footer"><el-button type="primary" :loading="saving" @click="saveSpots">保存热点</el-button></span>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'ZoneRouteEditor',
  props: { zones: { type: Array, required: true } },
  data() {
    return { ordered: [], orderOpen: false, spotsOpen: false, dragIndex: null, dragging: null,
      items: [], spots: [], nextKey: 0, editZone: null, saving: false, activeIndex: null,
      icons: { arrow: '➜', item: '▣', info: 'ℹ' } }
  },
  computed: {
    previewUrl() {
      if (!this.editZone) return ''
      const source = this.editZone.panoramaUrl || this.editZone.cover
      if (!source) return ''
      if (source.startsWith('/profile/upload/')) return process.env.VUE_APP_BASE_API + source
      return /^https:\/\//i.test(source) ? source : ''
    }
  },
  methods: {
    showOrder() {
      this.ordered = this.zones.slice()
      this.orderOpen = true
    },
    move(from, to) {
      if (from === to || to < 0 || to >= this.ordered.length) return
      const [row] = this.ordered.splice(from, 1)
      this.ordered.splice(to, 0, row)
    },
    dropAt(index) {
      if (this.dragIndex !== null) this.move(this.dragIndex, index)
      this.dragIndex = null
    },
    async saveOrder() {
      this.saving = true
      try {
        const data = this.ordered.map((zone, index) => ({ zoneId: zone.zoneId, routeNo: index + 1 }))
        await request({ url: '/exh/zone/sort', method: 'put', data })
        this.$modal.msgSuccess('动线已保存')
        this.orderOpen = false
        this.$emit('saved')
      } finally { this.saving = false }
    },
    async showSpots(zone) {
      if (!zone) return
      const [spots, items] = await Promise.all([
        request({ url: '/exh/zone/' + zone.zoneId + '/spots' }),
        request({ url: '/exh/zone/spot-targets' })
      ])
      this.editZone = zone
      this.items = items.data
      this.spots = spots.data.map(spot => ({ ...spot, localKey: ++this.nextKey, icon: spot.icon || 'arrow' }))
      this.spotsOpen = true
    },
    targets(spot) {
      return spot.targetType === 'zone' ? this.zones.map(row => ({ id: row.zoneId, title: row.zoneName }))
        : this.items.map(row => ({ id: row.itemId, title: row.title }))
    },
    addSpot() {
      this.spots.push({ localKey: ++this.nextKey, spotName: '', icon: 'arrow', targetType: 'zone', targetId: null, posX: .5, posY: .5 })
    },
    removeSpot(index) {
      this.dragging = null
      this.spots.splice(index, 1)
    },
    startDrag(index, event) {
      this.dragging = index
      this.activeIndex = index
      event.currentTarget.setPointerCapture(event.pointerId)
    },
    drag(event) {
      if (this.dragging === null || !this.spots[this.dragging]) return
      const area = this.$refs.preview.getBoundingClientRect()
      const ratio = (cursor, origin, size) => Math.round(Math.max(0, Math.min(1, (cursor - origin) / size)) * 10000) / 10000
      const spot = this.spots[this.dragging]
      spot.posX = ratio(event.clientX, area.left, area.width)
      spot.posY = ratio(event.clientY, area.top, area.height)
    },
    async saveSpots() {
      if (this.spots.some(spot => !spot.spotName || !spot.targetId || spot.posX == null || spot.posY == null)) {
        return this.$modal.msgError('请填写每个热点的名称、目标和坐标')
      }
      this.saving = true
      try {
        const data = this.spots.map(({ spotName, icon, targetType, targetId, posX, posY }, sortNo) =>
          ({ spotName, icon, targetType, targetId, posX, posY, sortNo }))
        await request({ url: '/exh/zone/' + this.editZone.zoneId + '/spots', method: 'put', data })
        this.$modal.msgSuccess('热点已保存，展厅端即时生效')
        this.spotsOpen = false
      } finally { this.saving = false }
    }
  }
}
</script>

<style scoped>
.zone-route-controls { display: inline-block; margin-left: 12px; }
.route-list li { padding: .35rem 0; cursor: grab; }
.route-list span { display: inline-block; min-width: 12rem; }
/* 预览底色与展厅端 base.css 的 --ex-bg 一致；热点配色取 --ex-accent/--ex-panel，与前端渲染同款 */
.spot-preview { position: relative; min-height: 10rem; background: #141824; }
.spot-preview img { display: block; width: 100%; }
.preview-empty { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  margin: 0; color: #9aa4b2; font-size: 13px; pointer-events: none; }
/* 热点标记 = 网页版的蓝点 + 沉浸式的名称胶囊，两种前端形态合一 */
.marker { position: absolute; transform: translate(-50%, -50%); touch-action: none; cursor: grab;
  display: flex; align-items: center; gap: .3rem; white-space: nowrap;
  padding: .25rem .6rem .25rem .45rem; border-radius: .4rem;
  border: 1px solid rgba(64, 158, 255, .55); background: rgba(26, 31, 46, .92);
  color: #eef1f6; font-size: 13px; line-height: 1.5; transition: box-shadow .15s, border-color .15s; }
.marker-dot { flex: none; width: 10px; height: 10px; border-radius: 50%;
  background: #409EFF; box-shadow: 0 0 0 4px rgba(64, 158, 255, .24); }
.marker.active, .marker:hover { border-color: #409EFF; box-shadow: 0 0 0 3px rgba(64, 158, 255, .3); }
.spot-row { padding: .35rem .5rem; margin: .4rem -.5rem 0; border-radius: 6px; transition: background .15s; }
.spot-row.active { background: #ecf5ff; }
.small-select { width: 6rem; }
</style>
