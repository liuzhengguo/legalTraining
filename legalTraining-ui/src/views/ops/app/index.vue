<template>
  <div class="app-container">
    <el-tabs v-model="activeTab" type="card" class="ops-tabs">
      <!-- OP-03-01 / 02 / 03：应用注册、密钥、接口与 IP 授权、配额 -->
      <el-tab-pane label="应用授权" name="apps">
        <el-form :inline="true" :model="query" size="small">
          <el-form-item label="应用名称">
            <el-input v-model="query.appName" placeholder="请输入应用名称" clearable style="width: 180px" @keyup.enter.native="loadApps" />
          </el-form-item>
          <el-form-item label="AppID">
            <el-input v-model="query.appCode" placeholder="请输入 AppID" clearable style="width: 180px" @keyup.enter.native="loadApps" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
              <el-option label="待启用" :value="0" />
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="2" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="el-icon-search" @click="handleQuery">搜索</el-button>
            <el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button>
            <el-button v-hasPermi="['ops:app:add']" type="success" plain icon="el-icon-plus" @click="openEditor()">注册应用</el-button>
          </el-form-item>
        </el-form>

        <el-alert
          class="ops-alert"
          type="info"
          :closable="false"
          show-icon
          title="AppSecret 仅在注册或重置时显示一次，列表与详情均不返回密钥；调用方须按 AppID + 密钥签名，并遵守接口清单、IP 白名单与配额限制。"
        />

        <el-row :gutter="12" class="stat-row">
          <el-col :span="6">
            <div class="stat-card"><div class="stat-label">应用总数</div><div class="stat-value">{{ total }}</div></div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card"><div class="stat-label">启用中</div><div class="stat-value success">{{ enabledCount }}</div></div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card"><div class="stat-label">授权接口数（合计）</div><div class="stat-value">{{ apiCountTotal }}</div></div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-label">异常调用（401/403/429/5xx）</div>
              <div class="stat-value danger">{{ abnormalCount }}</div>
            </div>
          </el-col>
        </el-row>

        <el-table v-loading="loading" :data="rows" border size="small">
          <el-table-column prop="appName" label="应用名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="appCode" label="AppID" min-width="150" show-overflow-tooltip />
          <el-table-column prop="ownerUnit" label="归属单位" min-width="130" show-overflow-tooltip />
          <el-table-column label="IP 白名单（段）" min-width="180">
            <template slot-scope="{row}">
              <span :class="row.ipCount ? '' : 'text-muted'">{{ row.ipSummary || '不限制' }}</span>
              <el-tag v-if="row.ipCount" size="mini" class="ml5">{{ row.ipCount }} 条</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="访问接口（实时数）" width="120" align="center">
            <template slot-scope="{row}">
              <el-tag :type="row.apiCount ? 'success' : 'warning'" size="mini">{{ row.apiCount || 0 }} 个</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="配额（次/分钟）" prop="rateLimit" width="120" align="center" />
          <el-table-column label="密钥" width="100" align="center">
            <template slot-scope="{row}">
              <el-tag :type="row.secretReady ? 'success' : 'info'" size="mini" effect="plain">
                {{ row.secretReady ? '已签发' : '未签发' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template slot-scope="{row}">
              <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'info' : 'warning'" size="mini">
                {{ statusNames[row.status] || '未知' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="390" fixed="right" class-name="nowrap-actions">
            <template slot-scope="{row}">
              <el-button v-hasPermi="['ops:app:edit']" type="text" @click="openEditor(row)">配置</el-button>
              <el-button v-hasPermi="['ops:app:edit']" type="text" @click="openGrants(row)">接口和 IP</el-button>
              <el-button v-hasPermi="['ops:app:log']" type="text" @click="openLogs(row)">调用日志</el-button>
              <el-button v-hasPermi="['ops:app:edit']" type="text" @click="changeStatus(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
              <el-button v-hasPermi="['ops:app:resetSecret']" type="text" @click="resetSecret(row)">重置密钥</el-button>
              <el-button v-hasPermi="['ops:app:remove']" type="text" class="text-danger" @click="removeApp(row)">删除</el-button>
            </template>
          </el-table-column>
          <template slot="empty"><span>{{ loading ? '加载中…' : '没有符合条件的应用' }}</span></template>
        </el-table>
        <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="loadApps" />
      </el-tab-pane>

      <!-- OP-03-04：调用日志与异常告警 -->
      <el-tab-pane label="调用日志" name="logs">
        <el-form :inline="true" :model="logQuery" size="small">
          <el-form-item label="应用">
            <el-select v-model="logQuery.appId" placeholder="全部应用" clearable filterable style="width: 180px">
              <el-option v-for="app in appOptions" :key="app.appId" :label="app.appName" :value="app.appId" />
            </el-select>
          </el-form-item>
          <el-form-item label="接口">
            <el-select v-model="logQuery.apiCode" placeholder="全部接口" clearable style="width: 210px">
              <el-option v-for="api in apiCatalog" :key="api.code" :label="api.code + ' ' + api.name" :value="api.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态码">
            <el-select v-model="logQuery.httpStatus" placeholder="全部" clearable style="width: 110px">
              <el-option label="200 成功" :value="200" />
              <el-option label="401 未授权" :value="401" />
              <el-option label="403 无权限" :value="403" />
              <el-option label="429 超限" :value="429" />
              <el-option label="500 服务端" :value="500" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="logQuery.abnormalOnly" @change="handleLogQuery">仅看异常</el-checkbox>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="el-icon-search" @click="handleLogQuery">搜索</el-button>
            <el-button icon="el-icon-refresh" @click="resetLogQuery">重置</el-button>
            <el-button v-hasPermi="['ops:app:log']" icon="el-icon-download" @click="exportLogs">导出日志</el-button>
          </el-form-item>
        </el-form>
        <el-alert
          class="ops-alert"
          :type="abnormalCount ? 'warning' : 'success'"
          :closable="false"
          show-icon
          :title="abnormalCount ? `共发现 ${abnormalCount} 条异常调用：401 ${logSummary.unauthorized || 0} 条、403 ${logSummary.forbidden || 0} 条、429 ${logSummary.limited || 0} 条、5xx ${logSummary.serverError || 0} 条。请核对应用密钥、IP 白名单与配额。` : '暂无异常调用记录。'"
        />
        <el-table v-loading="logLoading" :data="logs" border size="small">
          <el-table-column prop="callTime" label="时间" width="165" />
          <el-table-column prop="appCode" label="AppID" min-width="140" show-overflow-tooltip />
          <el-table-column label="接口" width="110">
            <template slot-scope="{row}">{{ apiName(row.apiCode) }}</template>
          </el-table-column>
          <el-table-column prop="clientIp" label="来源 IP" min-width="125" />
          <el-table-column label="状态码" width="90" align="center">
            <template slot-scope="{row}">
              <el-tag :type="row.httpStatus === 200 ? 'success' : 'danger'" size="mini">{{ row.httpStatus }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="costMs" label="耗时(ms)" width="95" align="center" />
          <el-table-column prop="requestUri" label="请求路径" min-width="180" show-overflow-tooltip />
          <el-table-column prop="errorMsg" label="异常原因" min-width="180" show-overflow-tooltip />
          <template slot="empty"><span>{{ logLoading ? '加载中…' : '没有符合条件的调用日志' }}</span></template>
        </el-table>
        <pagination v-show="logTotal > 0" :total="logTotal" :page.sync="logQuery.pageNum" :limit.sync="logQuery.pageSize" @pagination="loadLogs" />
      </el-tab-pane>

      <!-- OP-03-05：接口版本管理与下线通知 -->
      <el-tab-pane label="接口版本" name="version">
        <div v-if="!$auth.hasRole('admin')" class="text-muted">仅管理员可维护接口版本与下线计划。</div>
        <el-alert
          v-else
          class="ops-alert"
          type="info"
          :closable="false"
          show-icon
          title="通知期内接口继续提供服务并在响应中带 Deprecation / Sunset 说明；到点后业务请求返回 410。时间采用北京时间。"
        />
        <el-form v-if="$auth.hasRole('admin')" label-width="130px" size="small" class="version-form">
          <el-form-item label="当前版本">
            <span>{{ version.version || 'v1' }}</span>
            <el-tag size="mini" class="ml10" :type="version.status === 'active' ? 'success' : version.status === 'deprecated' ? 'warning' : 'info'">
              {{ lifecycleNames[version.status] || '运行中' }}
            </el-tag>
          </el-form-item>
          <el-form-item label="开始通知时间">
            <el-date-picker v-model="schedule.noticeFrom" type="datetime" value-format="yyyy-MM-dd'T'HH:mm:ss" placeholder="选择时间" />
          </el-form-item>
          <el-form-item label="下线时间">
            <el-date-picker v-model="schedule.sunsetAt" type="datetime" value-format="yyyy-MM-dd'T'HH:mm:ss" placeholder="选择时间" />
          </el-form-item>
          <el-form-item label="下线说明">
            <el-input v-model.trim="schedule.message" type="textarea" :rows="3" maxlength="160" show-word-limit placeholder="请说明替代版本与迁移方式" />
          </el-form-item>
          <el-form-item>
            <el-button :loading="saving" @click="saveVersion(true)">撤销计划</el-button>
            <el-button v-hasPermi="['ops:app:edit']" type="primary" :loading="saving" @click="saveVersion(false)">保存计划</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <el-dialog :title="form.appId ? '配置应用' : '注册应用'" :visible.sync="editing" append-to-body width="540px">
      <el-form label-width="120px" size="small">
        <el-form-item label="应用名称" required><el-input v-model.trim="form.appName" maxlength="100" placeholder="请输入应用名称" /></el-form-item>
        <el-form-item label="归属单位"><el-input v-model.trim="form.ownerUnit" maxlength="100" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model.trim="form.contact" maxlength="100" /></el-form-item>
        <el-form-item label="应用配额">
          <el-input-number v-model="form.rateLimit" :min="0" :max="2147483647" /> 次/分钟，0 表示不限
        </el-form-item>
      </el-form>
      <p class="dialog-tip">注册成功后系统签发 AppID 与 AppSecret，请当场保存密钥明文。</p>
      <div slot="footer">
        <el-button @click="editing = false">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="saveApp">确 定</el-button>
      </div>
    </el-dialog>

    <el-dialog title="应用密钥（仅显示一次）" :visible.sync="secretOpen" append-to-body width="560px" @close="secret = {}">
      <el-alert class="ops-alert" type="warning" :closable="false" show-icon title="请立即复制保存 AppSecret，关闭后将无法再次查看；如需新密钥请点击重置。" />
      <el-form label-width="100px" size="small">
        <el-form-item label="AppID"><el-input :value="secret.appCode" readonly /></el-form-item>
        <el-form-item label="AppSecret"><el-input :value="secret.value" readonly /></el-form-item>
      </el-form>
    </el-dialog>

    <el-dialog title="接口与 IP 授权" :visible.sync="grantsOpen" append-to-body width="820px">
      <p class="dialog-tip">按需求 10.2 的开放接口清单授权；未勾选的接口调用将被 403 拒绝。配额 0 表示不限。</p>
      <el-table :data="grants" border size="small" max-height="340">
        <el-table-column prop="apiCode" label="接口编号" width="110" />
        <el-table-column label="接口名称" min-width="180">
          <template slot-scope="{row}">
            {{ row.name }}
            <el-tooltip :content="row.usage" placement="top"><i class="el-icon-question" /></el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="允许访问" width="100" align="center">
          <template slot-scope="{row}"><el-switch v-model="row.allowed" /></template>
        </el-table-column>
        <el-table-column label="接口配额（次/分钟）" width="180">
          <template slot-scope="{row}"><el-input-number v-model="row.quotaPerMin" size="small" :min="0" :max="2147483647" :disabled="!row.allowed" /></template>
        </el-table-column>
      </el-table>
      <div class="ip-section">
        <div class="ip-title">IP 白名单（段）</div>
        <p class="dialog-tip">每行一个 IPv4 地址或 CIDR 网段（如 192.0.2.0/24）；留空表示不限制来源 IP。</p>
        <el-input v-model="ipText" type="textarea" :rows="4" placeholder="例如 192.0.2.10 或 192.0.2.0/24" />
      </div>
      <div slot="footer">
        <el-button @click="grantsOpen = false">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="saveGrants">保存授权</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'

const base = '/ops/app'

// 需求 10.2 对外接口清单 OP-02-01 ~ OP-02-09
const API_CATALOG = [
  { code: 'OP-02-01', name: '展区列表查询', usage: '返回已开放展区，支持按可见级别过滤' },
  { code: 'OP-02-02', name: '展项详情查询', usage: '仅返回已发布展项；下架返回 410，未发布返回 404' },
  { code: 'OP-02-03', name: '发布状态查询', usage: '供终端做增量更新比对，返回版本号与更新时间' },
  { code: 'OP-02-04', name: '分享链接与二维码生成', usage: '链接长期有效并指向内容 ID，不生成临时链接' },
  { code: 'OP-02-05', name: '成果推荐推送', usage: '教学侧推送至展厅侧，以推荐记录 ID 幂等' },
  { code: 'OP-02-06', name: '审核状态回写', usage: '展厅侧回写审核结果与展项 ID，供教师端显示' },
  { code: 'OP-02-07', name: '终端设备鉴权', usage: '设备码 + 令牌换取访问凭证，可吊销' },
  { code: 'OP-02-08', name: '案例检索与详情', usage: '外部案例库，调用失败时降级为不显示案例区' },
  { code: 'OP-02-09', name: 'AI 咨询调起', usage: '携带展项主题与身份上下文，不传学生敏感信息' }
]

const IP_PATTERN = /^(\d{1,3}\.){3}\d{1,3}(\/\d{1,2})?$/

export default {
  name: 'OpsApp',
  data() {
    return {
      activeTab: 'apps',
      statusNames: { 0: '待启用', 1: '启用', 2: '停用' },
      lifecycleNames: { active: '运行中', deprecated: '下线通知中', retired: '已下线' },
      apiCatalog: API_CATALOG,
      loading: false,
      rows: [],
      allApps: [],
      total: 0,
      query: { pageNum: 1, pageSize: 10, appName: undefined, appCode: undefined, status: undefined },
      logSummary: {},
      form: {},
      editing: false,
      saving: false,
      secret: {},
      secretOpen: false,
      grantsOpen: false,
      grantId: null,
      grants: [],
      ipText: '',
      ipRemarks: {},
      logLoading: false,
      logs: [],
      logTotal: 0,
      logQuery: { pageNum: 1, pageSize: 10, appId: undefined, apiCode: undefined, httpStatus: undefined, abnormalOnly: false },
      versionOpen: false,
      version: {},
      schedule: {}
    }
  },
  computed: {
    appOptions() {
      return this.allApps
    },
    enabledCount() {
      return this.rows.filter(row => row.status === 1).length
    },
    apiCountTotal() {
      return this.rows.reduce((sum, row) => sum + (row.apiCount || 0), 0)
    },
    abnormalCount() {
      const s = this.logSummary
      return (Number(s.unauthorized) || 0) + (Number(s.forbidden) || 0) + (Number(s.limited) || 0) + (Number(s.serverError) || 0)
    }
  },
  created() {
    this.loadApps()
    this.loadAllApps()
    this.loadLogSummary()
    this.loadVersion()
  },
  methods: {
    async loadAllApps() {
      try {
        const { rows } = await request({ url: base + '/list', params: { pageNum: 1, pageSize: 1000 } })
        this.allApps = rows || []
      } catch (_) {
        this.allApps = []
      }
    },
    async loadApps() {
      this.loading = true
      try {
        const { rows, total } = await request({ url: base + '/list', params: this.query })
        this.rows = rows || []
        this.total = total || 0
      } finally {
        this.loading = false
      }
    },
    handleQuery() {
      this.query.pageNum = 1
      this.loadApps()
    },
    resetQuery() {
      this.query = { pageNum: 1, pageSize: 10, appName: undefined, appCode: undefined, status: undefined }
      this.loadApps()
    },
    async loadLogSummary() {
      try {
        const { data } = await request({ url: base + '/log/summary' })
        this.logSummary = data || {}
      } catch (_) {
        this.logSummary = {}
      }
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
        else this.$modal.msgSuccess('配置已保存')
        await this.loadApps()
        await this.loadAllApps()
      } finally {
        this.saving = false
      }
    },
    showSecret(appCode, value) {
      this.secret = { appCode, value }
      this.secretOpen = true
    },
    async changeStatus(row) {
      const status = row.status === 1 ? 2 : 1
      try { await this.$modal.confirm(`确认${this.statusNames[status]}应用「${row.appName}」？`) } catch (_) { return }
      await request({ url: `${base}/${row.appId}/status`, method: 'put', params: { status } })
      this.$modal.msgSuccess('状态已更新')
      await this.loadApps()
    },
    async resetSecret(row) {
      try { await this.$modal.confirm(`确认重置「${row.appName}」的密钥？旧密钥立即失效。`) } catch (_) { return }
      const { data } = await request({ url: `${base}/${row.appId}/reset-secret`, method: 'put' })
      this.showSecret(row.appCode, data)
      await this.loadApps()
    },
    async removeApp(row) {
      try { await this.$modal.confirm(`确认删除应用「${row.appName}」？其接口与 IP 授权将一并删除。`) } catch (_) { return }
      await request({ url: `${base}/${row.appId}`, method: 'delete' })
      this.$modal.msgSuccess('删除成功')
      await this.loadApps()
      await this.loadAllApps()
    },
    async openGrants(row) {
      const { data } = await request({ url: `${base}/${row.appId}` })
      const existing = new Map((data.apiList || []).map(api => [api.apiCode, api]))
      this.grantId = row.appId
      this.grants = API_CATALOG.map(api => {
        const grant = existing.get(api.code)
        return { ...api, allowed: !!grant && grant.enable === '1', quotaPerMin: grant ? grant.quotaPerMin : 60 }
      })
      for (const api of data.apiList || []) {
        if (!this.grants.some(item => item.apiCode === api.apiCode)) {
          this.grants.push({ apiCode: api.apiCode, name: api.apiCode, usage: '历史自定义接口', allowed: api.enable === '1', quotaPerMin: api.quotaPerMin })
        }
      }
      this.ipText = (data.ipList || []).map(ip => ip.ipAddr).join('\n')
      this.ipRemarks = Object.fromEntries((data.ipList || []).map(ip => [ip.ipAddr, ip.remark]))
      this.grantsOpen = true
    },
    async saveGrants() {
      const addresses = [...new Set(this.ipText.split(/\s+/).filter(Boolean))]
      const invalid = addresses.filter(ip => !IP_PATTERN.test(ip))
      if (invalid.length) return this.$modal.msgError('IP 格式不正确：' + invalid.join('、'))
      const ips = addresses.map(ipAddr => ({ ipAddr, remark: this.ipRemarks[ipAddr] }))
      const apis = this.grants.filter(grant => grant.allowed).map(({ apiCode, quotaPerMin }) => ({ apiCode, quotaPerMin, enable: '1' }))
      this.saving = true
      try {
        await request({ url: `${base}/${this.grantId}/authorization`, method: 'put', data: { apis, ips } })
        this.grantsOpen = false
        this.$modal.msgSuccess('接口与 IP 授权已保存')
        await this.loadApps()
      } finally {
        this.saving = false
      }
    },
    openLogs(row) {
      this.activeTab = 'logs'
      this.logQuery = { pageNum: 1, pageSize: 10, appId: row ? row.appId : undefined, apiCode: undefined, httpStatus: undefined, abnormalOnly: false }
      this.loadLogs()
    },
    handleLogQuery() {
      this.logQuery.pageNum = 1
      this.loadLogs()
    },
    resetLogQuery() {
      this.logQuery = { pageNum: 1, pageSize: 10, appId: undefined, apiCode: undefined, httpStatus: undefined, abnormalOnly: false }
      this.loadLogs()
    },
    async loadLogs() {
      this.logLoading = true
      try {
        const { rows, total } = await request({ url: base + '/log', params: this.logQuery })
        this.logs = rows || []
        this.logTotal = total || 0
      } finally {
        this.logLoading = false
      }
    },
    exportLogs() {
      this.download(base + '/log/export', { appId: this.logQuery.appId, apiCode: this.logQuery.apiCode, httpStatus: this.logQuery.httpStatus, abnormalOnly: this.logQuery.abnormalOnly }, '开放接口调用日志.xlsx')
    },
    apiName(code) {
      const api = API_CATALOG.find(item => item.code === code)
      return api ? api.name : code
    },
    async loadVersion() {
      if (!this.$auth.hasRole('admin')) return
      const { data } = await request({ url: base + '/version-policy' })
      this.version = data || {}
      this.schedule = { ...(data && data.schedule) }
    },
    async saveVersion(clear) {
      if (!clear && (!this.schedule.noticeFrom || !this.schedule.sunsetAt || !this.schedule.message)) {
        return this.$modal.msgError('请填写通知时间、下线时间及说明')
      }
      try { await this.$modal.confirm(clear ? '撤销接口下线计划？' : '保存此接口下线计划？') } catch (_) { return }
      this.saving = true
      try {
        const { data } = await request({ url: base + '/version-policy', method: 'put', data: clear ? {} : this.schedule })
        this.version = data
        this.schedule = { ...data.schedule }
        this.$modal.msgSuccess(clear ? '计划已撤销' : '计划已保存')
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped>
.ops-tabs >>> .el-tabs__header {
  margin-bottom: 12px;
}
.ops-alert {
  margin-bottom: 12px;
}
.stat-row {
  margin-bottom: 12px;
}
.stat-card {
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 4px;
}
.stat-label {
  color: #909399;
  font-size: 12px;
}
.stat-value {
  margin-top: 4px;
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}
.stat-value.success {
  color: #67c23a;
}
.stat-value.danger {
  color: #f56c6c;
}
.dialog-tip {
  margin: 8px 0;
  color: #909399;
  font-size: 12px;
  line-height: 20px;
}
.ip-section {
  margin-top: 16px;
}
.ip-title {
  font-weight: 600;
}
.ml5 {
  margin-left: 5px;
}
.ml10 {
  margin-left: 10px;
}
.text-muted {
  color: #909399;
}
.text-danger {
  color: #f56c6c;
}
.version-form {
  max-width: 680px;
}
</style>
