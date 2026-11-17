<template>
  <main class="app-container">
    <h2>案例库</h2>
    <el-form inline @submit.native.prevent="search(1)">
      <el-form-item label="关键词">
        <el-input v-model.trim="keyword" clearable maxlength="200" placeholder="案例名称、摘要或来源" />
      </el-form-item>
      <el-button native-type="submit">查询</el-button>
      <el-button type="primary" @click="open()">新增案例</el-button>
    </el-form>
    <el-table v-loading="loading" :data="rows" row-key="id" empty-text="暂无案例，可点击「新增案例」维护">
      <el-table-column prop="title" label="案例名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="source" label="来源" min-width="120" show-overflow-tooltip />
      <el-table-column prop="updatedAt" label="更新时间" width="170" />
      <el-table-column label="状态" width="100">
        <template slot-scope="{ row }">
          <el-tag size="small" :type="Number(row.active) ? 'success' : 'info'">
            {{ Number(row.active) ? '可关联' : '已归档' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" align="right" header-align="center" width="180">
        <template slot-scope="{ row }">
          <el-button type="text" @click="open(row, true)">查看</el-button>
          <el-button v-if="canMaintain(row)" type="text" @click="open(row)">编辑</el-button>
          <el-button v-if="canMaintain(row)" type="text" @click="toggle(row)">{{ Number(row.active) ? '归档' : '恢复' }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-if="total > 20" layout="prev, pager, next, total" :total="total" :page-size="20"
      :current-page="page" @current-change="search" />
    <el-dialog :title="readOnly ? '案例详情' : form.id ? '编辑案例' : '新增案例'" :visible.sync="visible"
      width="min(720px, 94vw)" append-to-body>
      <el-form :model="form" :disabled="readOnly || saving" label-width="85px" @submit.native.prevent="save">
        <el-form-item label="案例名称" required><el-input v-model.trim="form.title" maxlength="250" /></el-form-item>
        <el-form-item label="来源"><el-input v-model.trim="form.source" maxlength="250" /></el-form-item>
        <el-form-item label="摘要"><el-input v-model.trim="form.summary" type="textarea" :rows="3" maxlength="1000" /></el-form-item>
        <el-form-item label="正文" required>
          <el-input v-model.trim="form.content" type="textarea" :rows="10" maxlength="50000" />
        </el-form-item>
        <el-form-item v-if="!readOnly">
          <el-button type="primary" native-type="submit" :loading="saving">保存案例</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
  </main>
</template>

<script>
import request from '@/utils/request'

const endpoint = '/ops/local-cases'
const emptyForm = () => ({ title: '', source: '', summary: '', content: '' })

export default {
  name: 'EduCaseLibrary',
  data() {
    return { keyword: '', submittedKeyword: '', page: 1, total: 0, rows: [], form: emptyForm(),
      loading: false, saving: false, visible: false, readOnly: false }
  },
  created() { this.search(1) },
  methods: {
    canMaintain(row) {
      return this.$store.getters.roles.some(role => ['admin', 'registrar'].includes(role))
        || String(row.createdBy) === String(this.$store.state.user.id)
    },
    async search(page) {
      if (page === 1) this.submittedKeyword = this.keyword
      this.loading = true
      try {
        const { data } = await request({ url: endpoint, params: { keyword: this.submittedKeyword, pageNum: page } })
        this.rows = data.rows
        this.total = data.total
        this.page = page
      } finally { this.loading = false }
    },
    async open(row, readOnly = false) {
      this.form = row ? (await request({ url: endpoint + '/' + row.id })).data : emptyForm()
      this.readOnly = readOnly
      this.visible = true
    },
    async save() {
      if (this.readOnly || this.saving) return
      const { id, title, source, summary, content } = this.form
      if (!title || !content) return this.$modal.msgWarning('请填写案例名称和正文')
      this.saving = true
      try {
        await request({ url: id ? endpoint + '/' + id : endpoint, method: id ? 'put' : 'post',
          data: { title, source, summary, content } })
        this.visible = false
        this.$modal.msgSuccess('案例已保存')
        await this.search(this.page)
      } finally { this.saving = false }
    },
    async toggle(row) {
      const active = !Number(row.active)
      const confirmed = await this.$modal.confirm(active ? '恢复该案例？' : '归档后，关联页面将暂时隐藏该案例。').catch(() => false)
      if (confirmed === false) return
      await request({ url: endpoint + '/' + row.id + '/status', method: 'put', data: { active } })
      this.$modal.msgSuccess(active ? '案例已恢复' : '案例已归档')
      await this.search(this.page)
    }
  }
}
</script>
