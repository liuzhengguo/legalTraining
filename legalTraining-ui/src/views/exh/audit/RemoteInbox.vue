<template>
  <div class="remote-inbox">
    <el-button @click="show">接收的成果</el-button>
    <el-dialog title="接收的成果" :visible.sync="open" width="min(850px, 96vw)" append-to-body>
      <el-button @click="load">刷新</el-button>
      <el-table :data="rows" empty-text="暂无接收成果">
        <el-table-column prop="title" label="成果" />
        <el-table-column prop="scopeDesc" label="授权范围" />
        <el-table-column label="审核状态">
          <template slot-scope="{row}">{{ decisions[row.status] }}</template>
        </el-table-column>
        <el-table-column label="回写状态">
          <template slot-scope="{row}">{{ row.callbackState == null ? '待审核' : callbacks[row.callbackState] }}</template>
        </el-table-column>
        <el-table-column label="操作">
          <template slot-scope="{row}">
            <el-button v-hasPermi="['exh:audit:list']" type="text" @click="inspect(row)">{{ row.status === 1 ? '审核' : '查看' }}</el-button>
            <el-button v-if="row.status === 2 && row.callbackState === 1 && !row.itemId"
              v-hasPermi="['exh:item:list']" type="text" @click="chooseZone(row)">生成展项</el-button>
            <span v-if="row.itemId">展项 {{ row.itemId }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
    <el-dialog title="生成接收成果展项" :visible.sync="choosingZone" width="420px" append-to-body>
      <el-select v-model="zoneId" placeholder="选择本馆展区" filterable>
        <el-option v-for="zone in zones" :key="zone.zoneId" :value="zone.zoneId" :label="zone.zoneName" />
      </el-select>
      <el-button slot="footer" type="primary" :disabled="!zoneId" :loading="saving" @click="createDraft">生成草稿</el-button>
    </el-dialog>
    <el-dialog title="接收成果审核" :visible.sync="editing" width="min(700px, 94vw)" append-to-body>
      <template v-if="current">
        <h3>{{ current.title }}</h3>
        <p>{{ current.signMode }} · {{ current.scopeDesc }} · {{ current.proofType === 'offline' ? '线下授权' : '线上授权' }} · {{ current.confirmedAt }}</p>
        <p v-if="current.reason">{{ current.reason }}</p>
        <p class="remote-content">{{ current.content }}</p>
        <section v-if="attachments.length" aria-label="成果附件和授权凭证">
          <h4>附件和授权凭证</h4>
          <ul>
            <li v-for="file in attachments" :key="file.id">
              <el-button type="text" @click="downloadEvidence(file)">{{ file.name }}</el-button>
            </li>
          </ul>
        </section>
        <template v-if="current.status === 1">
          <el-checkbox-group v-model="checks">
            <el-checkbox v-for="(label, code) in criteria" :key="code" :label="code">{{ label }}</el-checkbox>
          </el-checkbox-group>
          <el-input v-model.trim="opinion" type="textarea" maxlength="1000" placeholder="审核意见，驳回时必填" />
        </template>
        <p v-else>{{ current.opinion }}</p>
      </template>
      <span v-if="current && current.status === 1" slot="footer">
        <el-button :disabled="!opinion" :loading="saving" @click="decide(false)">驳回</el-button>
        <el-button type="primary" :disabled="checks.length !== 5" :loading="saving" @click="decide(true)">通过</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'
import { saveAs } from 'file-saver'
const path = '/exh/audit/remote'
export default {
  name: 'RemoteInbox',
  data() {
    return {
      rows: [], open: false, editing: false, saving: false, current: null, id: null,
      choosingZone: false, zones: [], zoneId: null, draftSourceId: null,
      attachments: [],
      checks: [], opinion: '', decisions: { 1: '待审核', 2: '已通过', 3: '已驳回' },
      callbacks: ['待回写', '已回写', '回写失败'],
      criteria: { accuracy: '准确性', compliance: '合规', copyright: '版权', privacy: '脱敏', usability: '可用性' }
    }
  },
  methods: {
    async show() {
      await this.load()
      this.open = true
    },
    async load() {
      this.rows = (await request({ url: path })).data
    },
    async inspect(row) {
      this.current = (await request({ url: path + '/' + row.id })).data
      this.id = row.id
      this.checks = []
      this.opinion = ''
      this.attachments = []
      this.editing = true
      const { data } = await request({ url: path + '/' + row.id + '/evidence' })
      if (this.id === row.id) this.attachments = data
    },
    async downloadEvidence(file) {
      const data = await request({ url: path + '/' + this.id + '/evidence/' + encodeURIComponent(file.id), responseType: 'blob' })
      if (data.type !== 'application/octet-stream') return this.$modal.msgError('附件暂不可下载')
      saveAs(data, file.name)
    },
    async chooseZone(row) {
      this.zones = (await request({ url: '/exh/zone/list' })).data
      this.zoneId = null
      this.draftSourceId = row.id
      this.choosingZone = true
    },
    async createDraft() {
      this.saving = true
      try {
        await request({ url: path + '/' + this.draftSourceId + '/exhibit', method: 'post', data: { zoneId: this.zoneId } })
        this.choosingZone = false
        this.$modal.msgSuccess('展项草稿已生成')
        await this.load()
      } finally { this.saving = false }
    },
    async decide(approve) {
      this.saving = true
      try {
        await request({ url: path + '/' + this.id, method: 'post', data: { approve, checks: this.checks, opinion: this.opinion } })
        this.editing = false
        this.$modal.msgSuccess('审核结果已保存')
        await this.load()
      } finally { this.saving = false }
    }
  }
}
</script>

<style scoped>
.remote-inbox { display: inline-block; margin-left: .65rem; }
.remote-content { white-space: pre-wrap; overflow-wrap: anywhere; max-height: 16rem; overflow: auto; }
</style>
