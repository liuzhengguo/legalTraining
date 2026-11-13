<template>
  <div class="app-container">
    <el-button v-if="mode !== 'publish'" type="primary" @click="edit()">新增{{ isZone ? '展区' : '展项' }}</el-button>
    <el-button @click="load">刷新</el-button>
    <el-table v-loading="loading" :data="rows" style="margin-top: 16px">
      <el-table-column v-if="isZone" prop="zoneCode" label="展区编号" />
      <el-table-column :prop="isZone ? 'zoneName' : 'title'" :label="isZone ? '展区名称' : '展项标题'" />
      <el-table-column v-if="!isZone" label="状态">
        <template slot-scope="{ row }">{{ ['草稿','待发布','已发布','已下架','发布失败'][row.status] }} · v{{ row.version }}</template>
      </el-table-column>
      <el-table-column label="开放级别">
        <template slot-scope="{ row }">{{ ['未开放','内部开放','对外开放'][isZone ? row.openLevel : row.viewLevel] }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="220">
        <template slot-scope="{ row }">
          <el-button v-if="mode !== 'publish'" type="text" @click="edit(row)">编辑</el-button>
          <el-button v-if="!isZone && row.sourceRec" type="text" @click="showSource(row)">来源成果</el-button>
          <el-button v-if="isZone && canSetLevel" type="text" @click="openLevel(row)">开放级别</el-button>
          <template v-if="mode === 'publish'">
            <el-button type="text" @click="openLevel(row)">发布</el-button>
            <el-button type="text" @click="showSchedule(row)">发布计划</el-button>
            <el-button v-if="row.status === 2" type="text" @click="offline(row)">下架</el-button>
            <el-button type="text" @click="history(row)">版本历史</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog :visible.sync="editing" :title="formId ? '编辑' : '新增'" width="620px">
      <el-form label-width="100px" @submit.native.prevent="save">
        <template v-if="isZone">
          <el-form-item label="展区编号"><el-input v-model.trim="form.zoneCode" maxlength="32" /></el-form-item>
          <el-form-item label="展区名称"><el-input v-model.trim="form.zoneName" maxlength="100" /></el-form-item>
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
          <el-form-item label="正文"><el-input v-model="form.content" type="textarea" :rows="8" maxlength="50000" /></el-form-item>
        </template>
        <el-form-item label="封面">
          <el-input v-model.trim="form.cover" placeholder="HTTPS 图片地址" maxlength="500" />
          <input type="file" accept="image/png,image/jpeg,image/webp" :disabled="saving" @change="upload" />
        </el-form-item>
        <el-form-item><el-button type="primary" native-type="submit" :loading="saving">保存</el-button></el-form-item>
      </el-form>
    </el-dialog>
    <el-dialog :visible.sync="levelOpen" :title="isZone ? '设置展区开放级别' : '发布展项'" width="460px">
      <el-form label-width="100px" @submit.native.prevent="setLevel">
        <el-form-item label="开放级别">
          <el-select v-model="level">
            <el-option v-if="isZone" label="关闭" :value="0" />
            <el-option label="内部开放" :value="1" />
            <el-option label="对外开放" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isZone" label="计划时间">
          <el-date-picker v-model="planTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="留空则立即发布" />
        </el-form-item>
        <el-form-item><el-button type="primary" native-type="submit" :loading="saving">确认</el-button></el-form-item>
      </el-form>
    </el-dialog>
    <el-dialog :visible.sync="scheduleOpen" title="发布计划" width="540px">
      <p v-if="!schedule">暂无发布计划</p>
      <template v-else>
        <p>{{ schedule.planTime }} · {{ ['待执行','已发布','失败','已取消'][schedule.status] }}</p>
        <p v-if="schedule.failReason">{{ schedule.failReason }}</p>
        <el-button v-if="schedule.status === 0" :loading="saving" @click="cancelSchedule">取消计划</el-button>
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
        <el-table-column label="操作">
          <template slot-scope="{ row }"><el-button type="text" :disabled="saving" @click="revert(row)">回退并发布</el-button></template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>
<script>
import request from '@/utils/request'

export default {
  name: 'ExhibitManagement',
  props: { mode: { type: String, required: true } },
  data() {
    return { rows: [], zones: [], versions: [], loading: false, saving: false, editing: false, levelOpen: false,
      historyOpen: false, sourceOpen: false, source: null, scheduleOpen: false, schedule: null, planTime: null,
      selected: null, form: {}, formId: null, level: 0 }
  },
  computed: {
    isZone() { return this.mode === 'zone' },
    endpoint() { return '/exh/' + (this.isZone ? 'zone' : 'item') },
    canSetLevel() { return this.$store.getters.roles.some(role => role === 'admin' || role === 'registrar') }
  },
  created() { this.load() },
  methods: {
    async showSource(row) {
      const result = await request({ url: '/exh/item/' + row.itemId + '/source' })
      this.source = result.data
      this.sourceOpen = true
    },
    async load() {
      this.loading = true
      try {
        this.rows = (await request({ url: this.endpoint + '/list' })).data
        if (this.mode === 'item') this.zones = (await request({ url: '/exh/zone/list' })).data
      } finally { this.loading = false }
    },
    edit(row) {
      this.formId = row ? (this.isZone ? row.zoneId : row.itemId) : null
      this.form = row ? { ...row } : this.isZone ? { layout: 'grid', routeNo: 0 } : {}
      this.editing = true
    },
    async upload(event) {
      const file = event.target.files[0]
      event.target.value = ''
      if (!file || this.saving) return
      const data = new FormData()
      data.append('file', file)
      this.saving = true
      try {
        const result = await request({ url: '/common/upload', method: 'post', data, headers: { repeatSubmit: false } })
        this.$set(this.form, 'cover', result.fileName)
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
      const { zoneCode, zoneName, intro, cover, layout, routeNo, zoneId, title, summary, content, version } = this.form
      const data = this.isZone ? { zoneCode, zoneName, intro, cover, layout, routeNo } : { zoneId, title, cover, summary, content }
      return this.perform({ url: this.endpoint + (this.formId ? '/' + this.formId : ''),
        method: this.formId ? 'put' : 'post', params: this.formId && !this.isZone ? { version } : {}, data }, '已保存')
    },
    openLevel(row) {
      this.selected = row
      this.planTime = null
      this.level = this.isZone ? row.openLevel : row.viewLevel || 2
      this.levelOpen = true
    },
    setLevel() {
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
