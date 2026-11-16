<template>
  <div class="app-container">
    <el-button v-hasPermi="['ops:app:add']" type="primary" @click="openEditor()">注册应用</el-button>
    <el-button v-hasPermi="['ops:app:log']" @click="openLogs()">调用日志</el-button>
    <el-button v-if="$auth.hasRole('admin')" @click="openVersion">接口版本</el-button>
    <el-table :data="rows">
      <el-table-column prop="appName" label="应用名称" />
      <el-table-column prop="appCode" label="AppID" min-width="150" />
      <el-table-column prop="ownerUnit" label="归属单位" />
      <el-table-column prop="rateLimit" label="应用配额（次/分钟）" />
      <el-table-column label="状态" width="85">
        <template slot-scope="{row}">{{ statusNames[row.status] || '未知' }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="280">
        <template slot-scope="{row}">
          <el-button v-hasPermi="['ops:app:edit']" type="text" @click="openEditor(row)">配置</el-button>
          <el-button v-hasPermi="['ops:app:edit']" type="text" @click="openGrants(row)">接口和 IP</el-button>
          <el-button v-hasPermi="['ops:app:edit']" type="text" @click="changeStatus(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
          <el-button v-hasPermi="['ops:app:resetSecret']" type="text" @click="resetSecret(row)">重置密钥</el-button>
          <el-button v-hasPermi="['ops:app:log']" type="text" @click="openLogs(row)">调用日志</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="loadApps" />

    <el-dialog :title="form.appId ? '配置应用' : '注册应用'" :visible.sync="editing" append-to-body width="520px">
      <el-form label-width="120px">
        <el-form-item label="应用名称" required><el-input v-model.trim="form.appName" maxlength="100" /></el-form-item>
        <el-form-item label="归属单位"><el-input v-model.trim="form.ownerUnit" maxlength="100" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model.trim="form.contact" maxlength="100" /></el-form-item>
        <el-form-item label="应用配额"><el-input-number v-model="form.rateLimit" :min="0" :max="2147483647" /> 次/分钟，0 表示不限</el-form-item>
      </el-form>
      <el-button slot="footer" type="primary" :loading="saving" @click="saveApp">保存应用</el-button>
    </el-dialog>

    <el-dialog title="应用密钥" :visible.sync="secretOpen" append-to-body width="560px" @close="secret = {}">
      <p>请立即保存 AppSecret。关闭后可通过重置签发新密钥。</p>
      <el-form label-width="100px">
        <el-form-item label="AppID"><el-input :value="secret.appCode" readonly /></el-form-item>
        <el-form-item label="AppSecret"><el-input :value="secret.value" readonly /></el-form-item>
      </el-form>
    </el-dialog>

    <el-dialog title="接口与 IP 授权" :visible.sync="grantsOpen" append-to-body width="650px">
      <p>已启用的接口可供此应用调用。接口配额按分钟计，0 表示不限。</p>
      <el-table :data="grants">
        <el-table-column prop="label" label="接口" />
        <el-table-column label="允许访问" width="100">
          <template slot-scope="{row}"><el-switch v-model="row.allowed" /></template>
        </el-table-column>
        <el-table-column label="配额" width="180">
          <template slot-scope="{row}"><el-input-number v-model="row.quotaPerMin" size="small" :min="0" :max="2147483647" /></template>
        </el-table-column>
      </el-table>
      <p>留空表示不限制 IP。每行填写一个 IPv4 地址或 IPv4 CIDR 网段。</p>
      <el-input v-model="ipText" type="textarea" :rows="4" placeholder="例如 192.0.2.0/24" />
      <el-button slot="footer" type="primary" :loading="saving" @click="saveGrants">保存授权</el-button>
    </el-dialog>

    <el-dialog title="开放接口调用日志" :visible.sync="logsOpen" append-to-body width="900px">
      <el-button size="small" @click="exportLogs">导出日志</el-button>
      <el-table :data="logs">
        <el-table-column prop="callTime" label="时间" width="170" />
        <el-table-column prop="appCode" label="AppID" min-width="125" />
        <el-table-column prop="apiCode" label="接口" width="120" />
        <el-table-column prop="clientIp" label="来源 IP" min-width="120" />
        <el-table-column prop="httpStatus" label="状态码" width="75" />
        <el-table-column prop="errorMsg" label="异常原因" min-width="140" />
      </el-table>
      <pagination v-show="logTotal > 0" :total="logTotal" :page.sync="logQuery.pageNum" :limit.sync="logQuery.pageSize" @pagination="loadLogs" />
    </el-dialog>
    <el-dialog title="接口版本与下线通知" :visible.sync="versionOpen" append-to-body width="min(600px, 94vw)">
      <p>{{ version.version }} · {{ lifecycleNames[version.status] }}</p>
      <el-form label-width="120px">
        <el-form-item label="开始通知时间">
          <el-date-picker v-model="schedule.noticeFrom" type="datetime" value-format="yyyy-MM-dd'T'HH:mm:ss" />
        </el-form-item>
        <el-form-item label="下线时间">
          <el-date-picker v-model="schedule.sunsetAt" type="datetime" value-format="yyyy-MM-dd'T'HH:mm:ss" />
        </el-form-item>
        <el-form-item label="下线说明">
          <el-input v-model.trim="schedule.message" type="textarea" maxlength="160" show-word-limit />
        </el-form-item>
      </el-form>
      <p>时间采用北京时间。开放接口响应会通知调用方，下线后业务请求返回 410。</p>
      <span slot="footer">
        <el-button :loading="saving" @click="saveVersion(true)">撤销计划</el-button>
        <el-button type="primary" :loading="saving" @click="saveVersion(false)">保存计划</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'

const base = '/ops/app'
const apiNames = ['展区列表', '展项详情', '发布状态', '展项分享', '推荐推送', '审核回写', '设备鉴权', '案例检索', 'AI 咨询']

export default {
  name: 'OpsApp',
  data() {
    return {
      statusNames: ['待授权', '启用', '停用'],
      rows: [], total: 0, query: { pageNum: 1, pageSize: 10 },
      form: {}, editing: false, saving: false, secret: {}, secretOpen: false,
      grantsOpen: false, grantId: null, grants: [], ipText: '', ipRemarks: {},
      logsOpen: false, logs: [], logTotal: 0, logQuery: { pageNum: 1, pageSize: 10 },
      versionOpen: false, version: {}, schedule: {},
      lifecycleNames: { active: '运行中', deprecated: '下线通知中', retired: '已下线' }
    }
  },
  created() { this.loadApps() },
  methods: {
    async loadApps() {
      const result = await request({ url: base + '/list', params: this.query })
      this.rows = result.rows
      this.total = result.total
    },
    openEditor(row) {
      const { appId, appName, ownerUnit, contact, rateLimit } = row || { rateLimit: 600 }
      this.form = { appId, appName, ownerUnit, contact, rateLimit }
      this.editing = true
    },
    async saveApp() {
      if (!this.form.appName) return this.$modal.msgError('请输入应用名称')
      this.saving = true
      try {
        const result = await request({ url: base, method: this.form.appId ? 'put' : 'post', data: this.form })
        this.editing = false
        if (!this.form.appId) this.showSecret(result.data.appCode, result.data.secretPlain)
        await this.loadApps()
      } finally { this.saving = false }
    },
    showSecret(appCode, value) {
      this.secret = { appCode, value }
      this.secretOpen = true
    },
    async changeStatus(row) {
      const status = row.status === 1 ? 2 : 1
      try { await this.$modal.confirm('确认' + this.statusNames[status] + '应用「' + row.appName + '」？') } catch (_) { return }
      await request({ url: base + '/' + row.appId + '/status', method: 'put', params: { status } })
      await this.loadApps()
    },
    async resetSecret(row) {
      try { await this.$modal.confirm('确认重置「' + row.appName + '」的密钥？旧密钥会立即失效。') } catch (_) { return }
      const { data } = await request({ url: base + '/' + row.appId + '/reset-secret', method: 'put' })
      this.showSecret(row.appCode, data)
    },
    async openGrants(row) {
      const { data } = await request({ url: base + '/' + row.appId })
      const existing = new Map(data.apiList.map(api => [api.apiCode, api]))
      this.grantId = row.appId
      this.grants = apiNames.map((label, index) => {
        const apiCode = 'OP-02-0' + (index + 1)
        const grant = existing.get(apiCode)
        return { apiCode, label, allowed: !!grant && grant.enable === '1', quotaPerMin: grant ? grant.quotaPerMin : 60 }
      })
      for (const api of data.apiList) {
        if (!this.grants.some(row => row.apiCode === api.apiCode)) {
          this.grants.push({ ...api, label: api.apiCode, allowed: api.enable === '1' })
        }
      }
      this.ipText = data.ipList.map(ip => ip.ipAddr).join('\n')
      this.ipRemarks = Object.fromEntries(data.ipList.map(ip => [ip.ipAddr, ip.remark]))
      this.grantsOpen = true
    },
    async saveGrants() {
      const addresses = [...new Set(this.ipText.split(/\s+/).filter(Boolean))]
      const ips = addresses.map(ipAddr => ({ ipAddr, remark: this.ipRemarks[ipAddr] }))
      const apis = this.grants.filter(grant => grant.allowed).map(({ apiCode, quotaPerMin }) => ({ apiCode, quotaPerMin, enable: '1' }))
      this.saving = true
      try {
        await request({ url: base + '/' + this.grantId + '/authorization', method: 'put', data: { apis, ips } })
        this.grantsOpen = false
        this.$modal.msgSuccess('授权已保存')
      } finally { this.saving = false }
    },
    openLogs(row) {
      this.logQuery = { pageNum: 1, pageSize: 10, appId: row && row.appId }
      this.logsOpen = true
      this.loadLogs()
    },
    async loadLogs() {
      const { rows, total } = await request({ url: base + '/log', params: this.logQuery })
      this.logs = rows
      this.logTotal = total
    },
    exportLogs() { this.download(base + '/log/export', { appId: this.logQuery.appId }, '开放接口调用日志.xlsx') },
    async openVersion() {
      const { data } = await request({ url: base + '/version-policy' })
      this.version = data
      this.schedule = { ...data.schedule }
      this.versionOpen = true
    },
    async saveVersion(clear) {
      if (!clear && (!this.schedule.noticeFrom || !this.schedule.sunsetAt || !this.schedule.message)) {
        return this.$modal.msgError('请填写通知时间、下线时间及说明')
      }
      const question = clear ? '撤销接口下线计划？' : '保存此接口下线计划？'
      try { await this.$modal.confirm(question) } catch (_) { return }
      this.saving = true
      try {
        const { data } = await request({ url: base + '/version-policy', method: 'put', data: clear ? {} : this.schedule })
        this.version = data
        this.schedule = { ...data.schedule }
        this.$modal.msgSuccess(clear ? '计划已撤销' : '计划已保存')
      } finally { this.saving = false }
    }
  }
}
</script>
