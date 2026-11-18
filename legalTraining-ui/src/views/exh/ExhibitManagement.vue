<template>
  <div class="app-container">
    <el-form v-if="mode === 'item'" inline class="filter-bar" @submit.native.prevent="load">
      <el-form-item label="展区">
        <el-select v-model="filters.zoneId" clearable filterable placeholder="全部展区" style="width:180px" @change="load">
          <el-option v-for="zone in zones" :key="zone.zoneId" :label="zone.zoneName" :value="zone.zoneId" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filters.status" clearable placeholder="全部状态" style="width:150px" @change="load">
          <el-option v-for="(label, index) in itemStatuses" :key="index" :label="label" :value="index" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键词">
        <el-input v-model.trim="filters.keyword" clearable placeholder="标题或摘要" @keyup.enter.native="load" />
      </el-form-item>
      <el-button native-type="submit">查询</el-button>
    </el-form>
    <el-button v-if="mode !== 'publish'" type="primary" @click="edit()">新增{{ isZone ? '展区' : '展项' }}</el-button>
    <el-button @click="load">刷新</el-button>
    <ZoneRouteEditor v-if="isZone" ref="routeEditor" :zones="rows" @saved="load" />
    <el-table v-loading="loading" :data="rows" style="margin-top: 16px">
      <el-table-column v-if="isZone" prop="zoneCode" label="展区编号" />
      <el-table-column :prop="isZone ? 'zoneName' : 'title'" :label="isZone ? '展区名称' : '展项标题'" />
      <el-table-column v-if="!isZone" label="状态">
        <template slot-scope="{ row }">{{ ['草稿','待发布','已发布','已下架','发布失败'][row.status] }} · v{{ row.version }}</template>
      </el-table-column>
      <el-table-column label="开放级别">
        <template slot-scope="{ row }">{{ ['未开放','内部开放','对外开放'][isZone ? row.openLevel : row.viewLevel] }}</template>
      </el-table-column>
      <el-table-column label="操作" :width="isZone ? 170 : 330" align="center" fixed="right" class-name="nowrap-actions">
        <template slot-scope="{ row }">
          <el-button v-if="!isZone" type="text" @click="openPreview(row.itemId)">预览</el-button>
          <el-button v-if="mode === 'item'" v-hasPermi="['exh:item:list']" type="text" @click="$refs.caseReferences.show(row.itemId)">关联案例</el-button>
          <el-button v-if="mode !== 'publish'" type="text" @click="edit(row)">编辑</el-button>
          <el-button v-if="!isZone && row.sourceRec" type="text" @click="showSource(row)">来源成果</el-button>
          <el-button v-if="isZone && canSetLevel" type="text" @click="openLevel(row)">开放级别</el-button>
          <el-button v-if="isZone" type="text" @click="$refs.routeEditor.showSpots(row)">热点</el-button>
          <template v-if="mode === 'publish'">
            <el-button type="text" @click="openLevel(row)">发布</el-button>
            <el-button type="text" @click="showSchedule(row)">发布计划</el-button>
            <el-button v-if="row.status === 2" type="text" @click="offline(row)">下架</el-button>
            <el-button type="text" @click="history(row)">版本历史</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <CaseReferences ref="caseReferences" type="item" />
    <el-dialog :visible.sync="editing" :title="formId ? '编辑' : '新增'" width="620px">
      <el-form label-width="100px" @submit.native.prevent="save">
        <template v-if="isZone">
          <el-form-item label="展区编号"><el-input v-model.trim="form.zoneCode" maxlength="32" /></el-form-item>
          <el-form-item label="展区名称"><el-input v-model.trim="form.zoneName" maxlength="100" /></el-form-item>
          <el-form-item label="全景图">
            <el-input v-model.trim="form.panoramaUrl" placeholder="2:1 全景图 HTTPS 地址或平台素材路径" maxlength="500" />
            <input type="file" accept="image/png,image/jpeg,image/webp" :disabled="saving" @change="upload($event, 'panoramaUrl')" />
          </el-form-item>
          <el-form-item label="简介"><el-input v-model="form.intro" type="textarea" maxlength="2000" /></el-form-item>
          <el-form-item label="版式">
            <el-select v-model="form.layout">
              <el-option label="图墙" value="grid" />
              <el-option label="长页" value="flow" />
              <el-option label="时间轴" value="timeline" />
            </el-select>
          </el-form-item>
          <el-form-item label="动线顺序"><el-input-number v-model="form.routeNo" :min="0" /></el-form-item>
        </template>
        <template v-else>
          <el-form-item label="所属展区">
            <el-select v-model="form.zoneId" filterable>
              <el-option v-for="zone in zones" :key="zone.zoneId" :label="zone.zoneName" :value="zone.zoneId" />
            </el-select>
          </el-form-item>
          <el-form-item label="展项标题"><el-input v-model.trim="form.title" maxlength="200" /></el-form-item>
          <el-form-item label="摘要"><el-input v-model.trim="form.summary" type="textarea" maxlength="120" show-word-limit /></el-form-item>
          <el-form-item label="正文">
            <Editor v-model="form.content" :min-height="260" />
          </el-form-item>
          <el-form-item v-if="lockHeld && !lockMine" label="">
            <el-alert type="warning" :closable="false" show-icon :title="'此展项正由 ' + lockHolderName + ' 编辑，当前为只读'" />
          </el-form-item>
        </template>
        <el-form-item label="封面">
          <el-input v-model.trim="form.cover" placeholder="HTTPS 图片地址" maxlength="500" />
          <input type="file" accept="image/png,image/jpeg,image/webp" :disabled="saving" @change="upload" />
        </el-form-item>
        <el-form-item v-if="!isZone && formId" label="素材管理">
          <div class="media-block">
            <div class="media-toolbar">
              <el-select v-model="mediaForm.mediaType" style="width:100px">
                <el-option v-for="type in mediaTypes" :key="type.value" :label="type.label" :value="type.value" />
              </el-select>
              <el-input v-model.trim="mediaForm.url" placeholder="素材 URL，或选择本地文件上传" style="flex:1" />
              <el-button :disabled="!mediaForm.url || !lockMine" @click="addMedia">添加</el-button>
              <input type="file" :accept="mediaAccept" :disabled="!lockMine" @change="uploadMedia">
            </div>
            <div v-if="!mediaList.length" class="media-empty">暂无素材</div>
            <div v-for="(item, index) in mediaList" :key="item.mediaId" class="media-row">
              <span class="media-info">{{ mediaTypeLabel(item.mediaType) }} · {{ item.url }}</span>
              <el-button type="text" :disabled="index === 0 || !lockMine" @click="moveMedia(index, -1)">上移</el-button>
              <el-button type="text" :disabled="index === mediaList.length - 1 || !lockMine" @click="moveMedia(index, 1)">下移</el-button>
              <el-button type="text" :disabled="!lockMine" @click="replaceMedia(item)">替换</el-button>
              <el-button type="text" class="danger-text" :disabled="!lockMine" @click="removeMedia(item)">删除</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="">
          <span v-if="autoSavedAt" class="autosave-hint">草稿已自动保存 {{ autoSavedAt }}</span>
          <el-button v-if="!isZone && formId" :disabled="saving" @click="openPreview(formId)">四视角预览</el-button>
          <el-button type="primary" native-type="submit" :loading="saving" :disabled="lockHeld && !lockMine">保存</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
    <el-dialog :visible.sync="levelOpen" :title="isZone ? '设置展区开放级别' : '发布展项'" width="560px">
      <el-form label-width="100px" @submit.native.prevent="setLevel">
        <el-form-item label="开放级别">
          <el-select v-model="level" @change="!isZone && runPrecheck()">
            <el-option v-if="isZone" label="关闭" :value="0" />
            <el-option label="内部开放" :value="1" />
            <el-option label="对外开放" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isZone" label="计划时间">
          <el-date-picker v-model="planTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="留空则立即发布" />
        </el-form-item>
        <el-form-item v-if="!isZone" label="发布前校验">
          <span v-if="prechecking">正在校验…</span>
          <div v-else-if="!precheck">
            <span class="danger-text">发布前校验未完成</span>
            <el-button type="text" @click="runPrecheck">重新校验</el-button>
          </div>
          <div v-else class="precheck-block">
            <p :class="precheck.passed ? 'ok-text' : 'danger-text'">
              {{ precheck.passed ? '校验通过，可以发布' : '存在 ' + (precheck.problems || []).length + ' 项阻断问题' }}
            </p>
            <ul v-if="precheck.problems && precheck.problems.length">
              <li v-for="(problem, index) in precheck.problems" :key="index">{{ problem }}</li>
            </ul>
            <el-button v-if="!precheck.passed" type="text" @click="fixProblems">去修复素材与正文</el-button>
            <el-button type="text" @click="runPrecheck">重新校验</el-button>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="saving"
                     :disabled="!isZone && (prechecking || !precheck || !precheck.passed)">确认</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
    <el-dialog :visible.sync="scheduleOpen" title="发布计划" width="540px">
      <p v-if="!schedule">暂无发布计划</p>
      <template v-else>
        <p>{{ schedule.planTime }} · {{ ['待执行','已发布','失败','已取消'][schedule.status] }}</p>
        <p v-if="schedule.failReason">{{ schedule.failReason }}</p>
        <el-button v-if="schedule.status === 0" :loading="saving" @click="cancelSchedule">取消计划</el-button>
        <el-button v-if="schedule.status === 2" type="primary" :loading="saving" @click="reverify">复验并重试</el-button>
      </template>
    </el-dialog>
    <el-dialog :visible.sync="sourceOpen" title="来源成果" width="550px">
      <dl v-if="source">
        <dt>推荐编号</dt><dd>{{ source.recId }}</dd>
        <dt>实训任务</dt><dd>{{ source.taskTitle }}</dd>
        <dt>推荐理由</dt><dd>{{ source.reason }}</dd>
        <dt>授权</dt><dd>{{ source.signMode }} · {{ source.scopeDesc }}</dd>
        <dt>审核意见</dt><dd>{{ source.opinion || '无' }}</dd>
      </dl>
    </el-dialog>
    <el-dialog :visible.sync="historyOpen" title="发布版本历史" width="650px">
      <el-table :data="versions">
        <el-table-column prop="version" label="版本" />
        <el-table-column prop="operateTime" label="发布时间" />
        <el-table-column prop="sourceVersion" label="回退来源版本" />
        <el-table-column label="操作" width="130" fixed="right" align="center" class-name="nowrap-actions">
          <template slot-scope="{ row }"><el-button type="text" :disabled="saving" @click="revert(row)">回退并发布</el-button></template>
        </el-table-column>
      </el-table>
    </el-dialog>
    <el-dialog :visible.sync="previewOpen" title="展项预览" width="min(1100px, 96vw)" append-to-body>
      <el-tabs v-model="previewView" @tab-click="loadPreview">
        <el-tab-pane v-for="view in previewViews" :key="view.value" :label="view.label" :name="view.value" />
      </el-tabs>
      <div v-loading="previewLoading" class="preview-frame" :class="'preview-' + previewView">
        <p v-if="previewFailed" role="status">预览加载失败，请关闭后重试。</p>
        <template v-else-if="previewData.title">
          <h3>{{ previewData.title }}</h3>
          <div class="preview-layout">
            <div class="preview-stage">
              <p v-if="!previewMediaModes.length">暂无媒体资料</p>
              <img v-if="(previewView === 'lite' || previewSelectedMedia === 'image') && previewAsset(previewData.cover)"
                :src="previewAsset(previewData.cover)" alt="展项封面">
              <div v-for="item in previewMedia" :key="item.mediaId" class="preview-media">
                <img v-if="item.mediaType === 'image'" :src="previewAsset(item.url)" alt="展项图片">
                <video v-else-if="item.mediaType === 'video'" :src="previewAsset(item.url)" :poster="previewAsset(item.cover)"
                  controls playsinline preload="none"></video>
                <audio v-else-if="item.mediaType === 'audio'" :src="previewAsset(item.url)" controls preload="none"></audio>
                <a v-else :href="previewAsset(item.url)" target="_blank" rel="noopener noreferrer">打开关联资料</a>
              </div>
              <nav v-if="previewView !== 'lite' && previewMediaModes.length > 1" class="preview-modes" aria-label="展项媒体">
                <button v-for="type in previewMediaModes" :key="type.value" type="button"
                  :aria-pressed="previewSelectedMedia === type.value" @click="previewMediaMode = type.value">{{ type.label }}</button>
              </nav>
            </div>
            <section class="preview-intro">
              <h4>展项介绍</h4>
              <p v-if="previewData.authorName">{{ previewData.authorName }}</p>
              <p v-if="previewData.summary" class="preview-summary">{{ previewData.summary }}</p>
              <p class="preview-content">{{ previewData.content }}</p>
            </section>
          </div>
        </template>
      </div>
      <p v-if="previewData.warnings && previewData.warnings.length" class="preview-warnings">提示：{{ previewData.warnings.join('；') }}</p>
    </el-dialog>
  </div>
</template>
<script>
import CaseReferences from '@/components/CaseReferences.vue'
import Editor from '@/components/Editor'
import request from '@/utils/request'
import ZoneRouteEditor from './ZoneRouteEditor.vue'

export default {
  name: 'ExhibitManagement',
  components: { CaseReferences, Editor, ZoneRouteEditor },
  props: { mode: { type: String, required: true } },
  data() {
    return { rows: [], zones: [], versions: [], loading: false, saving: false, editing: false, levelOpen: false,
      historyOpen: false, sourceOpen: false, source: null, scheduleOpen: false, schedule: null, planTime: null,
      selected: null, form: {}, formId: null, level: 0,
      filters: { zoneId: null, status: null, keyword: '' },
      itemStatuses: ['草稿', '待发布', '已发布', '已下架', '发布失败'],
      mediaTypes: [{ label: '图片', value: 'image' }, { label: '视频', value: 'video' },
        { label: '音频', value: 'audio' }, { label: '外链', value: 'link' }],
      mediaForm: { mediaType: 'image', url: '' }, mediaList: [],
      lockHeld: false, lockMine: false, lockHolderName: '', lockTimer: null, autoSaveTimer: null, autoSavedAt: '',
      precheck: null, prechecking: false, precheckVersion: 0,
      previewOpen: false, previewLoading: false, previewView: 'pc', previewData: {},
      previewItemId: null, previewRequest: 0, previewFailed: false, previewMediaMode: '',
      previewViews: [{ label: 'PC', value: 'pc' }, { label: '移动端', value: 'mobile' },
        { label: '大屏终端', value: 'screen' }, { label: '图文简版', value: 'lite' }] }
  },
  computed: {
    isZone() { return this.mode === 'zone' },
    endpoint() { return '/exh/' + (this.isZone ? 'zone' : 'item') },
    canSetLevel() { return this.$store.getters.roles.some(role => role === 'admin' || role === 'registrar') },
    previewMediaModes() {
      return this.mediaTypes.filter(type =>
        (type.value === 'image' && this.previewAsset(this.previewData.cover)) ||
        (this.previewData.media || []).some(item => item.mediaType === type.value && this.previewAsset(item.url)))
    },
    previewSelectedMedia() {
      return this.previewMediaModes.some(type => type.value === this.previewMediaMode)
        ? this.previewMediaMode : this.previewMediaModes[0]?.value
    },
    previewMedia() {
      return (this.previewData.media || []).filter(item => this.previewAsset(item.url) &&
        (this.previewView === 'lite' ? item.mediaType !== 'audio' : item.mediaType === this.previewSelectedMedia))
    },
    mediaAccept() {
      return { image: 'image/*', video: 'video/*', audio: 'audio/*', link: '' }[this.mediaForm.mediaType] || ''
    }
  },
  created() { this.load() },
  watch: {
    'form.title': 'scheduleAutosave',
    'form.summary': 'scheduleAutosave',
    'form.content': 'scheduleAutosave',
    'form.cover': 'scheduleAutosave',
    editing(open) {
      if (open) return
      this.stopLockRenew()
      clearTimeout(this.autoSaveTimer)
      if (this.formId && !this.isZone && this.lockMine) {
        request({ url: '/exh/item/' + this.formId + '/lock', method: 'put', data: { action: 'release' } }).catch(() => {})
      }
      this.resetLock()
    }
  },
  beforeDestroy() {
    this.stopLockRenew()
    clearTimeout(this.autoSaveTimer)
  },
  methods: {
    async showSource(row) {
      const result = await request({ url: '/exh/item/' + row.itemId + '/source' })
      this.source = result.data
      this.sourceOpen = true
    },
    async load() {
      this.loading = true
      try {
        const params = this.mode === 'item' ? { ...this.filters } : {}
        this.rows = (await request({ url: this.endpoint + '/list', params })).data
        if (this.mode === 'item' && !this.zones.length) {
          this.zones = (await request({ url: '/exh/zone/list' })).data
        }
      } finally { this.loading = false }
    },
    async edit(row) {
      this.resetLock()
      this.mediaList = []
      this.autoSavedAt = ''
      this.formId = row ? (this.isZone ? row.zoneId : row.itemId) : null
      this.form = row ? { ...row } : this.isZone ? { layout: 'grid', routeNo: 0 } : { itemType: 0 }
      this.editing = true
      if (!row || this.isZone) return
      const detail = (await request({ url: '/exh/item/' + this.formId })).data
      this.form = { ...this.form, ...detail }
      this.mediaList = detail.media || []
      const holder = detail.lock || {}
      const myId = this.$store.getters.userId || this.$store.state.user.userId
      this.lockHeld = !!holder.held
      this.lockMine = !this.lockHeld || String(holder.lockUser) === String(myId)
      this.lockHolderName = holder.holder || ''
      if (!this.lockHeld || this.lockMine) {
        await request({ url: '/exh/item/' + this.formId + '/lock', method: 'put', data: { action: 'acquire' } })
        this.lockHeld = true
        this.lockMine = true
        this.startLockRenew()
      }
    },
    // ---------- 编辑锁与草稿自动保存（EM-02-04） ----------
    resetLock() {
      this.stopLockRenew()
      this.lockHeld = false
      this.lockMine = false
      this.lockHolderName = ''
    },
    stopLockRenew() {
      if (this.lockTimer) {
        clearInterval(this.lockTimer)
        this.lockTimer = null
      }
    },
    startLockRenew() {
      this.stopLockRenew()
      this.lockTimer = setInterval(() => {
        request({ url: '/exh/item/' + this.formId + '/lock', method: 'put', data: { action: 'renew' } })
          .catch(() => {
            this.lockMine = false
            this.$modal.msgWarning('编辑锁已失效，请关闭后重新打开')
          })
      }, 180000)
    },
    scheduleAutosave() {
      if (!this.formId || this.isZone || !this.lockMine) return
      clearTimeout(this.autoSaveTimer)
      this.autoSaveTimer = setTimeout(() => this.autosave(), 2500)
    },
    async autosave() {
      if (!this.formId || this.isZone || !this.lockMine || this.saving) return
      try {
        const { title, summary, content, cover, zoneId, itemType } = this.form
        const result = (await request({ url: '/exh/item/' + this.formId + '/autosave', method: 'post',
          data: { title, summary, content, cover, zoneId, itemType: String(itemType || 0) } })).data
        this.autoSavedAt = result.savedAt
      } catch (error) {
        this.$modal.msgError('草稿自动保存失败，请手动保存')
      }
    },
    // ---------- 素材管理（EM-02-03） ----------
    mediaTypeLabel(type) {
      return { image: '图片', video: '视频', audio: '音频', link: '外链' }[type] || type
    },
    async addMedia() {
      if (!this.mediaForm.url || !this.lockMine) return
      this.mediaList = (await request({ url: '/exh/item/' + this.formId + '/media', method: 'post',
        data: { mediaType: this.mediaForm.mediaType, url: this.mediaForm.url } })).data
      this.mediaForm.url = ''
      this.$modal.msgSuccess('素材已添加')
    },
    async uploadMedia(event) {
      const file = event.target.files[0]
      event.target.value = ''
      if (!file) return
      const data = new FormData()
      data.append('file', file)
      const result = await request({ url: '/common/upload', method: 'post', data, headers: { repeatSubmit: false } })
      this.mediaForm.url = result.fileName
      this.mediaForm.mediaType = file.type.startsWith('video/') ? 'video'
        : file.type.startsWith('audio/') ? 'audio' : 'image'
      await this.addMedia()
    },
    async moveMedia(index, delta) {
      const target = index + delta
      if (target < 0 || target >= this.mediaList.length) return
      const ids = this.mediaList.map(item => item.mediaId)
      const [id] = ids.splice(index, 1)
      ids.splice(target, 0, id)
      this.mediaList = (await request({ url: '/exh/item/' + this.formId + '/media/sort', method: 'put', data: ids })).data
    },
    async removeMedia(item) {
      try { await this.$modal.confirm('确认删除该素材？') } catch { return }
      this.mediaList = (await request({ url: '/exh/item/' + this.formId + '/media/' + item.mediaId, method: 'delete' })).data
    },
    async replaceMedia(item) {
      let url
      try {
        url = (await this.$prompt('请输入替换后的素材 URL', '替换素材',
          { inputValue: item.url, inputPattern: /\S/, inputErrorMessage: '请填写素材 URL' })).value
      } catch { return }
      const index = this.mediaList.findIndex(entry => entry.mediaId === item.mediaId)
      await request({ url: '/exh/item/' + this.formId + '/media/' + item.mediaId, method: 'delete' })
      const created = (await request({ url: '/exh/item/' + this.formId + '/media', method: 'post',
        data: { mediaType: item.mediaType, url: url.trim() } })).data
      const ids = created.map(entry => entry.mediaId)
      const newId = ids.find(id => !this.mediaList.some(entry => entry.mediaId === id))
      if (newId != null && index >= 0) {
        ids.splice(ids.indexOf(newId), 1)
        ids.splice(index, 0, newId)
        this.mediaList = (await request({ url: '/exh/item/' + this.formId + '/media/sort', method: 'put', data: ids })).data
      } else {
        this.mediaList = created
      }
      this.$modal.msgSuccess('素材已替换')
    },
    // ---------- 四视角预览（EM-02-05） ----------
    previewAsset(value) {
      if (typeof value !== 'string') return ''
      if (value.startsWith('/profile/upload/')) return (process.env.VUE_APP_BASE_API || '') + value
      return /^https:\/\//i.test(value) ? value : ''
    },
    async openPreview(itemId) {
      this.previewItemId = itemId
      this.previewView = 'pc'
      this.previewData = {}
      this.previewMediaMode = ''
      this.previewOpen = true
      await this.loadPreview()
    },
    async loadPreview() {
      const version = ++this.previewRequest
      this.previewLoading = true
      this.previewFailed = false
      try {
        const { data } = await request({ url: '/exh/item/' + this.previewItemId + '/preview',
          params: { view: this.previewView } })
        if (version === this.previewRequest) this.previewData = data
      } catch {
        if (version === this.previewRequest) this.previewFailed = true
      } finally {
        if (version === this.previewRequest) this.previewLoading = false
      }
    },
    async upload(event, field = 'cover') {
      const file = event.target.files[0]
      event.target.value = ''
      if (!file || this.saving) return
      const data = new FormData()
      data.append('file', file)
      this.saving = true
      const form = this.form
      try {
        if (field === 'panoramaUrl') {
          const source = URL.createObjectURL(file)
          try {
            const image = new Image()
            image.src = source
            await image.decode()
            if (Math.abs(image.naturalWidth / image.naturalHeight - 2) > 0.02) {
              this.$modal.msgError('请选择宽高比为 2:1 的等距柱状全景图')
              return
            }
          } catch {
            this.$modal.msgError('无法读取这张全景图，请检查图片文件')
            return
          } finally {
            URL.revokeObjectURL(source)
          }
        }
        const result = await request({ url: '/common/upload', method: 'post', data, headers: { repeatSubmit: false } })
        this.$set(form, field, result.fileName)
      } finally { this.saving = false }
    },
    async perform(options, message) {
      if (this.saving) return
      this.saving = true
      try {
        await request(options)
        this.editing = this.levelOpen = this.historyOpen = this.scheduleOpen = false
        this.$modal.msgSuccess(message)
        await this.load()
      } finally { this.saving = false }
    },
    save() {
      const { zoneCode, zoneName, intro, cover, panoramaUrl, layout, routeNo, zoneId, title, summary, content, version } = this.form
      const data = this.isZone ? { zoneCode, zoneName, intro, cover, panoramaUrl, layout, routeNo } : { zoneId, title, cover, summary, content }
      return this.perform({ url: this.endpoint + (this.formId ? '/' + this.formId : ''),
        method: this.formId ? 'put' : 'post', params: this.formId && !this.isZone ? { version } : {}, data }, '已保存')
    },
    async openLevel(row) {
      this.selected = row
      this.planTime = null
      this.level = this.isZone ? row.openLevel : row.viewLevel || 2
      this.levelOpen = true
      if (!this.isZone) await this.runPrecheck()
    },
    async runPrecheck() {
      if (this.isZone || !this.selected) return
      const version = ++this.precheckVersion
      this.precheck = null
      this.prechecking = true
      try {
        const { data } = await request({ url: '/exh/item/' + this.selected.itemId + '/validate',
          method: 'post', headers: { repeatSubmit: false }, params: { level: this.level } })
        if (version === this.precheckVersion) this.precheck = data
      } catch {
        // The request interceptor reports the error. Keep confirmation disabled until a retry succeeds.
      } finally {
        if (version === this.precheckVersion) this.prechecking = false
      }
    },
    fixProblems() {
      const row = this.selected
      this.levelOpen = false
      this.$nextTick(() => this.edit(row))
    },
    async reverify() {
      if (this.saving) return
      this.saving = true
      try {
        await request({ url: '/exh/item/' + this.selected.itemId + '/schedule/reverify', method: 'post' })
        this.scheduleOpen = false
        this.$modal.msgSuccess('复验通过并已重新发布')
        await this.load()
      } finally { this.saving = false }
    },
    setLevel() {
      if (!this.isZone && (this.prechecking || !this.precheck || !this.precheck.passed)) {
        return this.$modal.msgWarning('请先通过发布前校验')
      }
      const id = this.isZone ? this.selected.zoneId : this.selected.itemId
      const action = this.isZone ? 'level' : this.planTime ? 'schedule' : 'publish'
      return this.perform({ url: this.endpoint + '/' + id + '/' + action,
        method: this.isZone ? 'put' : 'post', params: { level: this.level, planTime: this.planTime } },
      { level: '开放级别已更新', schedule: '发布计划已保存', publish: '展项已发布' }[action])
    },
    async showSchedule(row) {
      this.selected = row
      const result = await request({ url: this.endpoint + '/' + row.itemId + '/schedule' })
      this.schedule = result.data[0] || null
      this.scheduleOpen = true
    },
    cancelSchedule() {
      return this.perform({ url: this.endpoint + '/' + this.selected.itemId + '/schedule', method: 'delete' }, '发布计划已取消')
    },
    async offline(row) {
      let reason
      try {
        reason = (await this.$prompt('请输入下架原因', '展项下架', { inputPattern: /\S/, inputErrorMessage: '请填写下架原因' })).value
      } catch { return }
      return this.perform({ url: this.endpoint + '/' + row.itemId + '/offline', method: 'post',
        params: { reason: reason.trim() } }, '展项已下架')
    },
    async history(row) {
      this.selected = row
      this.versions = (await request({ url: this.endpoint + '/' + row.itemId + '/versions' })).data
      this.historyOpen = true
    },
    async revert(row) {
      try { await this.$modal.confirm('确认将此版本重新发布？') } catch { return }
      return this.perform({ url: this.endpoint + '/' + this.selected.itemId + '/revert', method: 'post',
        params: { version: row.version } }, '已回退并发布新版本')
    }
  }
}
</script>
<style scoped>
.filter-bar { margin-bottom: 4px; }
.media-block { border: 1px solid #ebeef5; border-radius: 4px; padding: 8px; width: 100%; }
.media-toolbar { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; }
.media-toolbar input[type=file] { max-width: 180px; }
.media-row { display: flex; align-items: center; gap: 6px; padding: 4px 0; border-top: 1px solid #f0f2f5; }
.media-info { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #606266; font-size: 12px; }
.media-empty { color: #909399; font-size: 13px; }
.danger-text { color: #f56c6c; }
.autosave-hint { color: #67c23a; font-size: 12px; margin-right: 12px; }
.preview-frame {
  min-height: 240px; margin: 0 auto; padding: clamp(12px, 2vw, 24px);
  border-radius: 12px; color: #ffe3a4; background: #3d140f;
}
.preview-frame h3 {
  margin: 0 0 18px; text-align: center;
  font: 700 clamp(20px, 3vw, 34px)/1.35 "Songti SC", SimSun, serif;
}
.preview-layout { display: grid; grid-template-columns: minmax(0, 1.65fr) minmax(0, 1fr); gap: 14px; }
.preview-stage { min-width: 0; padding: 9px; border: 1px solid #925449; border-radius: 10px; background: #602319; }
.preview-stage img, .preview-stage video { display: block; width: 100%; border-radius: 6px; }
.preview-stage audio { width: 100%; }
.preview-stage > img + .preview-media, .preview-media + .preview-media { margin-top: 10px; }
.preview-intro { min-width: 0; padding: 16px; border-radius: 10px; color: #4c1d16; background: #f7ecdc; }
.preview-intro h4 { margin: 0 0 10px; font-size: 16px; }
.preview-intro p { margin: 0 0 10px; line-height: 1.65; }
.preview-intro .preview-summary { padding: 9px 10px; border-radius: 6px; background: #f0dfc9; }
.preview-content { white-space: pre-wrap; overflow-wrap: anywhere; }
.preview-modes { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
.preview-modes button {
  padding: 5px 9px; border: 1px solid #bd8a67; border-radius: 6px; color: inherit; background: transparent; cursor: pointer;
}
.preview-modes button[aria-pressed="true"] { color: #3d140f; background: #ffe3a4; }
.preview-mobile { max-width: 390px; }
.preview-screen { background: #2b0f0b; }
.preview-lite { max-width: 720px; }
.preview-mobile .preview-layout, .preview-lite .preview-layout { grid-template-columns: minmax(0, 1fr); }
.preview-lite h3 { text-align: left; }
@media (max-width: 650px) {
  .preview-layout { grid-template-columns: minmax(0, 1fr); }
}
.preview-warnings { color: #e6a23c; font-size: 13px; }
.precheck-block { border: 1px solid #ebeef5; border-radius: 4px; padding: 8px 12px; width: 100%; }
.precheck-block p { margin: 0 0 6px; }
.precheck-block ul { margin: 4px 0 8px 18px; }
.ok-text { color: #67c23a; }
</style>
