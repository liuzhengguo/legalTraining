<template>
  <div class="app-container">
    <el-form inline @submit.native.prevent="create">
      <el-form-item label="分享对象">
        <el-select v-model="selected" filterable placeholder="选择公开展区或展项">
          <el-option v-for="row in options" :key="targetKey(row)" :value="targetKey(row)"
                     :label="(row.targetType === 'zone' ? '展区 · ' : '展项 · ') + row.targetName" />
        </el-select>
      </el-form-item>
      <el-form-item label="来源">
        <el-select v-model="channel">
          <el-option label="二维码" value="qrcode" />
          <el-option label="网页链接" value="link" />
          <el-option label="终端" value="terminal" />
          <el-option label="门户" value="portal" />
        </el-select>
      </el-form-item>
      <el-button type="primary" native-type="submit" :loading="busy" :disabled="!selected">生成分享</el-button>
    </el-form>
    <el-table :data="links" v-loading="loading">
      <el-table-column prop="targetName" label="展区或展项" />
      <el-table-column prop="channel" label="来源" />
      <el-table-column prop="shortCode" label="短码" />
      <el-table-column prop="createTime" label="创建时间" />
      <el-table-column label="操作">
        <template slot-scope="{ row }">
          <el-button type="text" :disabled="busy" @click="createFor(row)">查看链接和二维码</el-button>
        </template>
      </el-table-column>
    </el-table>
    <section v-if="$auth.hasPermi('exh:stat:query')">
      <h3>访问统计</h3>
      <el-form inline @submit.native.prevent="loadStats">
        <el-form-item label="日期">
          <el-date-picker v-model="dates" type="daterange" value-format="yyyy-MM-dd"
                          start-placeholder="开始日期" end-placeholder="结束日期" />
        </el-form-item>
        <el-button native-type="submit" :loading="statsLoading">查询</el-button>
      </el-form>
      <template v-if="stats">
        <p>{{ stats.dateFrom }} 至 {{ stats.dateTo }} · 浏览 {{ stats.totals.pv }} 次 · 访客 {{ stats.totals.uv }}</p>
        <el-table :data="stats.trend" empty-text="该时段没有访问记录">
          <el-table-column prop="statDate" label="日期" />
          <el-table-column prop="pv" label="浏览量" />
          <el-table-column prop="uv" label="访客数" />
        </el-table>
        <h4>来源渠道</h4>
        <el-table :data="stats.byChannel">
          <el-table-column prop="channel" label="来源" />
          <el-table-column prop="pv" label="浏览量" />
          <el-table-column prop="uv" label="访客数" />
        </el-table>
        <h4>展区和展项</h4>
        <el-table :data="stats.byTarget">
          <el-table-column label="对象">
            <template slot-scope="{ row }">{{ targetName(row) }}</template>
          </el-table-column>
          <el-table-column prop="pv" label="浏览量" />
          <el-table-column prop="uv" label="访客数" />
        </el-table>
      </template>
    </section>
    <el-dialog title="分享链接和二维码" :visible.sync="visible" width="480px" @closed="release">
      <el-input :value="shortUrl" readonly aria-label="分享链接" />
      <img v-if="qrImage" :src="qrImage" alt="分享二维码" width="256" height="256">
      <div>
        <a v-if="qrImage" :href="qrImage" download="展厅分享二维码.png">下载二维码</a>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'ExhibitShare',
  data() {
    return {
      options: [], links: [], selected: '', channel: 'qrcode', busy: false, loading: false,
      visible: false, shortUrl: '', qrImage: '', dates: null, stats: null, statsLoading: false
    }
  },
  async created() {
    const [options] = await Promise.all([request({ url: '/exh/share/options' }), this.loadLinks()])
    this.options = options.data
    if (this.$auth.hasPermi('exh:stat:query')) await this.loadStats()
  },
  beforeDestroy() {
    this.release()
  },
  methods: {
    targetName(row) {
      const option = this.options.find(item => this.targetKey(item) === this.targetKey(row))
      return option ? option.targetName : (row.targetType === 'zone' ? '展区 ' : '展项 ') + row.targetId
    },
    async loadStats() {
      this.statsLoading = true
      try {
        const params = this.dates ? { dateFrom: this.dates[0], dateTo: this.dates[1] } : {}
        this.stats = (await request({ url: '/exh/stat/access', params })).data
      } finally {
        this.statsLoading = false
      }
    },
    targetKey(row) {
      return row.targetType + ':' + row.targetId
    },
    async loadLinks() {
      this.loading = true
      try {
        this.links = (await request({ url: '/exh/share/list' })).data
      } finally {
        this.loading = false
      }
    },
    create() {
      const row = this.options.find(item => this.targetKey(item) === this.selected)
      if (row) return this.createFor({ ...row, channel: this.channel })
    },
    async createFor({ targetType, targetId, channel }) {
      if (this.busy) return
      this.busy = true
      try {
        const { data } = await request({ url: '/exh/share', method: 'post', data: { targetType, targetId, channel } })
        const png = await request({ url: '/files/qr/' + encodeURIComponent(data.shortCode) + '.png', responseType: 'blob' })
        this.release()
        this.shortUrl = data.shortUrl
        if (png.type === 'image/png') this.qrImage = URL.createObjectURL(png)
        else this.$modal.msgError('二维码加载失败')
        this.visible = true
        await this.loadLinks()
      } finally {
        this.busy = false
      }
    },
    release() {
      if (this.qrImage) URL.revokeObjectURL(this.qrImage)
      this.qrImage = ''
    }
  }
}
</script>
