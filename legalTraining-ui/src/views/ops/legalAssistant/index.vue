<template>
  <div class="app-container">
    <h2>法律助手记录</h2>
    <el-form inline @submit.native.prevent="search">
      <el-form-item label="搜索">
        <el-input v-model.trim="query.keyword" clearable placeholder="姓名、账号或问题内容" />
      </el-form-item>
      <el-button native-type="submit" type="primary">查询</el-button>
    </el-form>
    <el-table v-loading="loading" :data="rows" row-key="recordId" empty-text="暂无学生提问记录">
      <el-table-column prop="userName" label="登录账号" min-width="120" />
      <el-table-column prop="nickName" label="姓名" min-width="110" />
      <el-table-column prop="question" label="问题" min-width="240" show-overflow-tooltip />
      <el-table-column prop="createTime" label="提问时间" width="170" />
      <el-table-column label="状态" width="100">
        <template slot-scope="{ row }">
          <el-tag size="small" :type="types[row.status]">{{ states[row.status] || '未知' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="95" fixed="right" align="center" class-name="nowrap-actions">
        <template slot-scope="{ row }"><el-button type="text" @click="current = row; detailOpen = true">查看</el-button></template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="load" />
    <el-dialog title="问答详情" :visible.sync="detailOpen" width="min(680px, 94vw)" append-to-body>
      <template v-if="current">
        <p>{{ current.nickName || current.userName }} · {{ current.createTime }} · {{ states[current.status] }}</p>
        <h3>问题</h3>
        <p class="record-text">{{ current.question }}</p>
        <h3>回答</h3>
        <p class="record-text">{{ current.answer || current.errorMessage || '尚未收到回答' }}</p>
        <small>模型接入点 {{ current.modelEndpoint }}</small>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'LegalAssistantRecords',
  data() {
    return {
      query: { keyword: '', pageNum: 1, pageSize: 20 }, rows: [], total: 0, loading: false, detailOpen: false, current: null,
      states: { answered: '已回答', failed: '回答失败', pending: '待回答' },
      types: { answered: 'success', failed: 'danger', pending: 'warning' }
    }
  },
  created() { this.load() },
  methods: {
    search() {
      this.query.pageNum = 1
      this.load()
    },
    async load() {
      this.loading = true
      try {
        const result = await request({ url: '/ops/legal-assistant/records', params: this.query })
        this.rows = result.rows
        this.total = result.total
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.record-text { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
