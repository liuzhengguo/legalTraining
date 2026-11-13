<template>
  <div class="app-container">
    <el-form inline @submit.native.prevent>
      <el-form-item label="成果标题">
        <el-input v-model.trim="keyword" clearable placeholder="搜索成果标题" />
      </el-form-item>
      <el-form-item label="审核状态">
        <el-select v-model="status" clearable placeholder="全部状态">
          <el-option v-for="value in allowedStatuses" :key="value" :value="value" :label="statuses[value]" />
        </el-select>
      </el-form-item>
      <el-button @click="load">刷新</el-button>
    </el-form>
    <el-table :data="visibleRows" empty-text="没有符合条件的成果">
      <el-table-column prop="title" label="成果" />
      <el-table-column prop="zoneName" label="目标展区" />
      <el-table-column prop="scopeDesc" label="授权范围" />
      <el-table-column prop="signMode" label="署名" />
      <el-table-column label="状态">
        <template slot-scope="{ row }">{{ statuses[row.status] }}</template>
      </el-table-column>
      <el-table-column label="操作">
        <template slot-scope="{ row }">
          <el-button v-if="row.status === 1" v-hasPermi="['exh:audit:list']" type="text" @click="prepare(row)">审核</el-button>
          <el-button v-else v-hasPermi="['exh:audit:list']" type="text" @click="prepare(row)">审核记录</el-button>
          <el-button v-if="row.status === 2" v-hasPermi="['exh:item:list']" type="text" :disabled="saving" @click="convert(row)">生成展项</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog :visible.sync="open" title="成果审核" width="620px">
      <p class="achievement-content">{{ content }}</p>
      <el-checkbox-group v-if="current && current.status === 1" v-model="checks">
        <el-checkbox v-for="(label, code) in criteria" :key="code" :label="code">{{ label }}</el-checkbox>
      </el-checkbox-group>
      <el-input v-if="current && current.status === 1" v-model.trim="opinion" type="textarea" maxlength="1000" placeholder="审核意见，驳回时必填" />
      <section v-for="entry in history" :key="entry.reviewId">
        <h4>{{ entry.result === 1 ? '审核通过' : '审核驳回' }}</h4>
        <p>{{ entry.auditor }} · {{ entry.auditTime }}</p>
        <p v-if="entry.checks && entry.checks.length">{{ entry.checks.map(code => criteria[code] || code).join('、') }}</p>
        <p v-if="entry.opinion" class="achievement-content">{{ entry.opinion }}</p>
      </section>
      <span v-if="current && current.status === 1" slot="footer">
        <el-button :loading="saving" :disabled="!opinion" @click="decide(false)">驳回</el-button>
        <el-button :loading="saving" :disabled="checks.length !== 5" type="primary" @click="decide(true)">通过</el-button>
      </span>
    </el-dialog>
  </div>
</template>
<script>
import request from '@/utils/request'
export default {
  name: 'ExhibitAudit',
  props: { approvedOnly: Boolean },
  data() {
    return {
      rows: [], history: [], keyword: '', status: '', current: null, checks: [], opinion: '', content: '', open: false, saving: false,
      statuses: ['', '待审核', '审核通过', '审核驳回', '已撤回', '已生成展项'],
      criteria: { accuracy: '内容准确', compliance: '合规', copyright: '版权来源', privacy: '个人信息脱敏', usability: '格式可用' }
    }
  },
  computed: {
    allowedStatuses() { return this.approvedOnly ? [2, 5] : [1, 2, 3, 4, 5] },
    visibleRows() {
      const keyword = this.keyword.toLowerCase()
      return this.rows.filter(row => this.allowedStatuses.includes(row.status)
        && (this.status === '' || row.status === this.status)
        && row.title.toLowerCase().includes(keyword))
    }
  },
  created() { this.load() },
  methods: {
    async load() {
      this.rows = (await request({ url: '/exh/audit/list' })).data
    },
    async prepare(row) {
      const { data } = await request({ url: '/exh/audit/' + row.recId })
      this.content = data.content
      this.history = data.history || []
      this.current = row
      this.checks = []
      this.opinion = ''
      this.open = true
    },
    async perform(url, data, message) {
      if (this.saving) return
      this.saving = true
      try {
        await request({ url, method: 'post', data })
        this.open = false
        this.$modal.msgSuccess(message)
        await this.load()
      } finally { this.saving = false }
    },
    decide(approve) {
      return this.perform('/exh/audit/' + this.current.recId, { approve, checks: this.checks, opinion: this.opinion }, '审核结果已保存')
    },
    async convert(row) {
      await this.perform('/exh/audit/' + row.recId + '/exhibit', null, '已生成展项草稿')
      this.$emit('converted')
    }
  }
}
</script>
<style scoped>
.achievement-content { white-space: pre-wrap; overflow-wrap: anywhere; }
.el-checkbox-group { margin-bottom: 1rem; }
</style>
