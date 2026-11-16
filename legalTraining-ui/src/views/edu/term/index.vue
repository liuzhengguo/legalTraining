<template>
  <div class="app-container">
    <el-form inline @submit.native.prevent="search">
      <el-form-item label="学期名称"><el-input v-model.trim="query.termName" placeholder="搜索学期" clearable /></el-form-item>
      <el-button native-type="submit">查询</el-button>
      <el-button v-hasPermi="['edu:term:add']" type="primary" @click="openEditor()">新增学期</el-button>
      <el-button v-hasPermi="['edu:term:export']" @click="exportRows">导出</el-button>
    </el-form>
    <el-table :data="rows" v-loading="loading" row-key="termId">
      <el-table-column prop="schoolYear" label="学年" />
      <el-table-column prop="termName" label="学期名称" min-width="160" />
      <el-table-column prop="startDate" label="开始日期" min-width="100" />
      <el-table-column prop="endDate" label="结束日期" min-width="100" />
      <el-table-column label="状态">
        <template slot-scope="{ row }">{{ states[row.status] }}<span v-if="row.isCurrent === '1'"> · 当前</span></template>
      </el-table-column>
      <el-table-column label="操作" min-width="220">
        <template slot-scope="{ row }">
          <el-button v-if="row.status !== 2" v-hasPermi="['edu:term:edit']" type="text" @click="openEditor(row)">编辑</el-button>
          <el-button v-if="row.status !== 2 && row.isCurrent !== '1'" v-hasPermi="['edu:term:switch']" type="text" @click="switchTo(row)">设为当前学期</el-button>
          <el-button v-if="row.status !== 2 && row.isCurrent !== '1'" v-hasPermi="['edu:term:remove']" type="text" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="load" />
    <el-dialog :title="form.termId ? '编辑学期' : '新增学期'" :visible.sync="editing" append-to-body width="min(540px, 94vw)">
      <el-form label-width="110px" @submit.native.prevent="save">
        <el-form-item label="学年" required><el-input v-model.trim="form.schoolYear" maxlength="16" placeholder="例如 2026-2027" /></el-form-item>
        <el-form-item label="学期" required>
          <el-select v-model="form.termNo">
            <el-option label="第一学期" :value="1" /><el-option label="第二学期" :value="2" /><el-option label="短学期" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="显示名称" required><el-input v-model.trim="form.termName" maxlength="64" placeholder="输入学期名称" /></el-form-item>
        <el-form-item label="开始日期" required><el-date-picker v-model="form.startDate" type="date" value-format="yyyy-MM-dd" placeholder="选择开始日期" /></el-form-item>
        <el-form-item label="结束日期" required><el-date-picker v-model="form.endDate" type="date" value-format="yyyy-MM-dd" placeholder="选择结束日期" /></el-form-item>
        <el-button type="primary" native-type="submit" :loading="saving">保存学期</el-button>
      </el-form>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'EduTerm',
  data() {
    return { query: { pageNum: 1, pageSize: 10, termName: '' }, rows: [], total: 0,
      form: {}, states: ['筹备', '进行中', '已归档'], editing: false, saving: false, loading: false }
  },
  created() { this.load() },
  methods: {
    async load() {
      this.loading = true
      try {
        const { rows, total } = await request({ url: '/edu/term/list', params: this.query })
        this.rows = rows
        this.total = total
      } finally { this.loading = false }
    },
    search() {
      this.query.pageNum = 1
      this.load()
    },
    openEditor(row) {
      const { termId, schoolYear, termName, startDate, endDate, termNo = 1 } = row || {}
      this.form = { termId, schoolYear, termName, startDate, endDate, termNo }
      this.editing = true
    },
    async save() {
      const { schoolYear, termName, startDate, endDate } = this.form
      if (!schoolYear || !termName || !startDate || !endDate) return this.$modal.msgError('请填写学期名称、学年及起止日期')
      if (endDate < startDate) return this.$modal.msgError('学期结束日期不能早于开始日期')
      this.saving = true
      try {
        await request({ url: '/edu/term', method: this.form.termId ? 'put' : 'post', data: this.form })
        this.editing = false
        await this.load()
      } finally { this.saving = false }
    },
    async switchTo(row) {
      const path = '/edu/term/' + row.termId
      const preview = await request({ url: path + '/switch-check' })
      try { await this.$modal.confirm(preview.data.message) } catch (_) { return }
      const result = await request({ url: path + '/current', method: 'put' })
      this.$modal.msgSuccess(result.data.message)
      await this.load()
    },
    async remove(row) {
      try { await this.$modal.confirm('删除学期「' + row.termName + '」？') } catch (_) { return }
      await request({ url: '/edu/term/' + row.termId, method: 'delete' })
      await this.load()
    },
    exportRows() { this.download('/edu/term/export', { termName: this.query.termName }, '学年学期.xlsx') }
  }
}
</script>
