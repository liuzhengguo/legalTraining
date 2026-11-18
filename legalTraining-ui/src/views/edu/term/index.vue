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
      <el-table-column label="操作" fixed="right" align="center" width="320" class-name="nowrap-actions">
        <template slot-scope="{ row }">
          <el-button v-if="row.status !== 2" v-hasPermi="['edu:term:edit']" type="text" @click="openEditor(row)">编辑</el-button>
          <el-button v-if="row.status !== 2 && row.isCurrent !== '1'" v-hasPermi="['edu:term:switch']" type="text" @click="switchTo(row)">设为当前学期</el-button>
          <el-button v-if="row.status !== 2" v-hasPermi="['edu:term:archive']" type="text" @click="openArchive(row)">归档</el-button>
          <el-button v-if="row.status !== 2 && row.isCurrent !== '1'" v-hasPermi="['edu:term:remove']" type="text" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog title="归档学期" :visible.sync="archiveVisible" append-to-body width="min(560px, 95vw)">
      <div v-if="archiveCheck">
        <p class="archive-msg">{{ archiveCheck.message }}</p>
        <ul v-if="archiveCheck.needForce" class="archive-list">
          <li v-if="archiveCheck.unreviewedSubmissions">未批阅提交 {{ archiveCheck.unreviewedSubmissions }} 份</li>
          <li v-if="archiveCheck.unpublishedScores">未发布成绩 {{ archiveCheck.unpublishedScores }} 条</li>
          <li v-if="archiveCheck.missingCourseTotals">缺失课程总评 {{ archiveCheck.missingCourseTotals }} 人/课</li>
          <li v-if="archiveCheck.inProgressTasks">进行中任务 {{ archiveCheck.inProgressTasks }} 个</li>
          <li v-if="archiveCheck.pendingRecommendations">待处理成果推荐 {{ archiveCheck.pendingRecommendations }} 条</li>
        </ul>
        <p v-if="archiveCheck.needForce && !archiveCheck.canForce" class="archive-warn">存在未完成事项，仅系统管理员可强制归档；请先完成批阅与成绩汇总。</p>
        <template v-if="archiveCheck.needForce && archiveCheck.canForce">
          <el-checkbox v-model="forceArchive">我已知晓未完成事项，强制归档</el-checkbox>
          <el-input v-model="archiveReason" type="textarea" :rows="3" maxlength="500" placeholder="请填写强制归档原因（必填）" style="margin-top: 10px" />
        </template>
      </div>
      <span slot="footer">
        <el-button @click="archiveVisible = false">取消</el-button>
        <el-button type="primary" :loading="archiving"
          :disabled="!archiveCheck || (archiveCheck.needForce && (!forceArchive || !archiveReason.trim()))"
          @click="doArchive">确认归档</el-button>
      </span>
    </el-dialog>
    <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="load" />
    <el-dialog :title="form.termId ? '编辑学期' : '新增学期'" :visible.sync="editing" append-to-body width="min(540px, 94vw)">
      <el-form label-width="110px" @submit.native.prevent="save">
        <el-form-item label="学年" required>
          <el-select id="term-school-year" v-model="form.schoolYear" filterable placeholder="选择学年" style="width: 100%">
            <el-option v-for="year in schoolYears" :key="year" :label="year" :value="year" />
          </el-select>
        </el-form-item>
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
      form: {}, states: ['筹备', '进行中', '已归档'], editing: false, saving: false, loading: false,
      archiveVisible: false, archiveCheck: null, archiveTerm: null, forceArchive: false, archiveReason: '', archiving: false }
  },
  computed: {
    schoolYears() {
      const now = new Date()
      const start = now.getFullYear() - (now.getMonth() < 8 ? 1 : 0)
      const nearby = Array.from({ length: 16 }, (_, index) => {
        const year = start + 5 - index
        return `${year}-${year + 1}`
      })
      const existing = this.rows.map(row => row.schoolYear).concat(this.form.schoolYear)
      return Array.from(new Set([...nearby, ...existing.filter(Boolean)])).sort().reverse()
    }
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
    async openArchive(row) {
      this.archiveTerm = row
      this.forceArchive = false
      this.archiveReason = ''
      this.archiveCheck = null
      this.archiveVisible = true
      try {
        const { data } = await request({ url: '/edu/term/' + row.termId + '/archive-check' })
        this.archiveCheck = data
      } catch (e) { this.archiveVisible = false }
    },
    async doArchive() {
      if (!this.archiveTerm) return
      const needForce = this.archiveCheck && this.archiveCheck.needForce
      if (needForce && (!this.forceArchive || !this.archiveReason.trim())) {
        return this.$modal.msgError('请先勾选强制归档并填写原因')
      }
      this.archiving = true
      try {
        await request({
          url: '/edu/term/' + this.archiveTerm.termId + '/archive',
          method: 'post',
          params: { force: needForce ? true : false, reason: needForce ? this.archiveReason.trim() : undefined }
        })
        this.archiveVisible = false
        this.$modal.msgSuccess('归档完成')
        await this.load()
      } finally { this.archiving = false }
    },
    exportRows() { this.download('/edu/term/export', { termName: this.query.termName }, '学年学期.xlsx') }
  }
}
</script>
<style scoped>
.archive-msg { line-height: 1.6; margin: 0 0 10px; }
.archive-list { color: #e6a23c; padding-left: 20px; margin: 0 0 10px; }
.archive-warn { color: #f56c6c; line-height: 1.6; }
</style>
