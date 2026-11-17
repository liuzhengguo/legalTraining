<template>
  <el-dialog title="关联案例" :visible.sync="visible" width="min(720px, 94vw)" append-to-body>
    <div v-loading="loading">
      <h3>已关联</h3>
      <p v-if="!linked.length">暂无关联案例</p>
      <ul v-else>
        <li v-for="item in linked" :key="item.id">
          <el-button type="text" @click="view(item)">{{ item.title }}</el-button>
          <el-button type="text" :disabled="saving" @click="remove(item)">移除</el-button>
        </li>
      </ul>
      <el-form inline @submit.native.prevent="search(1)">
        <el-form-item label="案例库">
          <el-select v-model="source" @change="search(1)">
            <el-option label="校内案例库" value="local" />
            <el-option label="外部案例库" value="external" />
          </el-select>
        </el-form-item>
        <el-form-item label="案例关键词">
          <el-input v-model.trim="keyword" maxlength="200" clearable placeholder="输入案例关键词" />
        </el-form-item>
        <el-button native-type="submit" :loading="searching">检索案例</el-button>
        <el-button v-hasPermi="['edu:case:manage']" @click="openLibrary">维护案例库</el-button>
      </el-form>
      <p v-if="unavailable" role="status">案例库暂不可用，已保存的关联可保留或移除。</p>
      <el-table v-else-if="searched" :data="results" empty-text="未找到匹配的案例">
        <el-table-column prop="title" label="案例" />
        <el-table-column prop="source" label="来源" />
        <el-table-column label="操作" fixed="right" align="right" width="130">
          <template slot-scope="{row}">
            <el-button type="text" @click="view(row)">详情</el-button>
            <el-button type="text" :disabled="saving || linked.some(item => item.id === row.id)" @click="attach(row)">关联</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="total > 10" layout="prev, pager, next" :total="total"
        :page-size="10" :current-page="page" @current-change="search" />
      <section v-if="detail">
        <h3>{{ detail.title }}</h3>
        <p v-if="detail.source">{{ detail.source }}</p>
        <p v-if="detail.summary">{{ detail.summary }}</p>
        <p class="case-content">{{ detail.content }}</p>
      </section>
    </div>
  </el-dialog>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'CaseReferences',
  props: { type: { type: String, required: true } },
  data() {
    return { id: null, visible: false, source: 'local', keyword: '', submittedKeyword: '', page: 1, total: 0,
      results: [], linked: [], searched: false, unavailable: false, loading: false,
      searching: false, saving: false, detail: null }
  },
  computed: {
    endpoint() { return '/ops/case-reference/' + this.type + '/' + this.id }
  },
  methods: {
    async show(id) {
      this.id = id
      this.keyword = ''
      this.source = 'local'
      this.submittedKeyword = ''
      this.results = []
      this.linked = []
      this.searched = false
      this.unavailable = false
      this.detail = null
      this.total = 0
      this.visible = true
      this.loading = true
      try { await Promise.all([this.refresh(), this.search(1)]) } finally { this.loading = false }
    },
    openLibrary() {
      this.visible = false
      this.$router.push({ name: 'EduCaseLibrary' })
    },
    async refresh() {
      const { data } = await request({ url: this.endpoint })
      this.linked = data
    },
    async search(page) {
      if (page === 1) this.submittedKeyword = this.keyword
      this.searching = true
      try {
        const { data } = await request({ url: this.endpoint + '/search', params: { keyword: this.submittedKeyword, pageNum: page, source: this.source } })
        this.results = data.rows
        this.total = data.total
        this.unavailable = data.degrade
        this.page = page
        this.searched = true
      } finally { this.searching = false }
    },
    async view(item) {
      this.detail = null
      const { data } = await request({ url: this.endpoint + '/detail/' + encodeURIComponent(item.id) })
      if (data.degrade) return this.$modal.msgWarning('案例详情暂不可用')
      this.detail = data.case
    },
    async attach(item) {
      await this.change(item, 'post')
    },
    async remove(item) {
      await this.change(item, 'delete')
    },
    async change(item, method) {
      this.saving = true
      try {
        await request({ url: this.endpoint + '/' + encodeURIComponent(item.id), method })
        await this.refresh()
      } finally { this.saving = false }
    }
  }
}
</script>

<style scoped>
.case-content { white-space: pre-wrap; overflow-wrap: anywhere; max-height: 14rem; overflow: auto; }
</style>
