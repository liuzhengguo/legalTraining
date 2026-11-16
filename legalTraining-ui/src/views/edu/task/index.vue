<template>
  <div class="app-container">
    <el-form inline @submit.native.prevent="load">
      <el-form-item label="课程">
        <el-select v-model="courseId" clearable filterable @change="load">
          <el-option v-for="row in courses" :key="row.courseId" :value="row.courseId" :label="row.courseName" />
        </el-select>
      </el-form-item>
      <el-button native-type="submit">查询</el-button>
      <el-button type="primary" v-hasPermi="['edu:task:add']" @click="open()">新建任务</el-button>
    </el-form>
    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="title" label="任务" />
      <el-table-column prop="courseName" label="课程" />
      <el-table-column label="完成方式"><template slot-scope="{ row }">{{ modes[row.finishMode] }}</template></el-table-column>
      <el-table-column label="状态"><template slot-scope="{ row }">{{ statuses[row.status] }}</template></el-table-column>
      <el-table-column prop="deadline" label="截止时间" />
      <el-table-column label="操作" fixed="right" align="right" width="340">
        <template slot-scope="{ row }">
          <el-button v-hasPermi="['edu:task:edit']" type="text" @click="$refs.caseReferences.show(row.taskId)">关联案例</el-button>
          <el-button type="text" @click="preview(row)">学生视角预览</el-button>
          <el-button v-if="row.status === 0" v-hasPermi="['edu:task:edit']" type="text" @click="open(row)">编辑</el-button>
          <el-button v-if="row.status === 0" v-hasPermi="['edu:task:publish']" type="text" @click="transition(row, 'publish', '发布任务')">发布</el-button>
          <el-button v-if="[1, 2].includes(row.status)" v-hasPermi="['edu:task:edit']" type="text" @click="openUpdate(row, 'deadline')">延期</el-button>
          <el-button v-if="[1, 2].includes(row.status)" v-hasPermi="['edu:task:edit']" type="text" @click="openUpdate(row, 'notice')">补充说明</el-button>
          <el-button v-if="[1, 2].includes(row.status)" v-hasPermi="['edu:task:recall']" type="text" @click="openUpdate(row, 'recall')">撤回</el-button>
        </template>
      </el-table-column>
    </el-table>
    <CaseReferences ref="caseReferences" type="task" />
    <pagination v-show="total > 0" :total="total" :page.sync="pageNum" :limit.sync="pageSize" @pagination="query" />

    <el-dialog title="实训任务" :visible.sync="visible" width="660px">
      <el-form label-width="108px" @submit.native.prevent="save">
        <el-form-item label="课程">
          <el-select v-model="form.courseId" :disabled="!!form.taskId" filterable>
            <el-option v-for="row in courses" :key="row.courseId" :value="row.courseId" :label="row.courseName" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题"><el-input v-model.trim="form.title" maxlength="200" /></el-form-item>
        <el-form-item label="实训要求"><el-input v-model="form.requirement" type="textarea" :rows="5" /></el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="form.startTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" /></el-form-item>
        <el-form-item label="截止时间"><el-date-picker v-model="form.deadline" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" /></el-form-item>
        <el-form-item label="截止策略">
          <el-select v-model="form.deadlinePolicy">
            <el-option v-for="(label, value) in policies" :key="value" :label="label" :value="value" />
          </el-select>
          <el-input-number v-if="form.deadlinePolicy === 1" v-model="form.graceDays" :min="1" aria-label="宽限天数" />
        </el-form-item>
        <el-form-item label="完成方式">
          <el-select v-model="form.finishMode">
            <el-option v-for="(label, value) in modes" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.finishMode" label="每组人数">
          <el-input-number v-model="form.groupMin" :min="1" :max="form.groupMax" aria-label="最少人数" />
          至 <el-input-number v-model="form.groupMax" :min="form.groupMin" aria-label="最多人数" />
        </el-form-item>
        <el-form-item label="评分方式">
          <el-select v-model="form.scoreMode">
            <el-option v-for="(label, value) in scoring" :key="value" :label="label" :value="value" />
          </el-select>
          <el-input-number v-model="form.totalScore" :min="1" :max="1000" aria-label="满分" />
        </el-form-item>
        <template v-if="form.scoreMode === 2">
          <el-form-item v-for="(item, index) in form.scoreItems" :key="index" :label="'评分项 ' + (index + 1)">
            <el-input v-model.trim="item.itemName" class="score-name" placeholder="名称" />
            <el-input-number v-model="item.weight" :min="0.01" :max="100" :precision="2" aria-label="权重百分比" />
            <el-button type="text" @click="form.scoreItems.splice(index, 1)">移除</el-button>
          </el-form-item>
          <el-form-item label="权重合计">
            <span>{{ weightTotal }}%</span>
            <el-button type="text" @click="form.scoreItems.push({ itemName: '', weight: 10 })">增加评分项</el-button>
            <el-button v-if="weightTotal && weightTotal !== 100" type="text" @click="normalize">归一化至 100%</el-button>
          </el-form-item>
        </template>
        <el-form-item><el-button native-type="submit" type="primary" :loading="saving">保存草稿</el-button></el-form-item>
      </el-form>
    </el-dialog>
    <el-dialog title="学生视角预览" :visible.sync="previewOpen" width="640px">
      <h3>{{ previewTask.title }}</h3>
      <p>{{ previewTask.courseName }} · {{ modes[previewTask.finishMode] }}</p>
      <p>{{ previewTask.deadlineText }}</p>
      <p class="requirement">{{ previewTask.requirement }}</p>
      <p>{{ scoring[previewTask.scoreMode] }} · 满分 {{ previewTask.totalScore }}</p>
      <ul><li v-for="item in previewTask.scoreItems" :key="item.itemId">{{ item.itemName }}（{{ item.weight }}%）</li></ul>
    </el-dialog>
    <el-dialog :title="updateTitles[updateAction]" :visible.sync="updateOpen" width="560px">
      <el-form label-width="92px" @submit.native.prevent="saveUpdate">
        <el-form-item v-if="updateAction === 'deadline'" label="新截止时间">
          <el-date-picker v-model="updateForm.deadline" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" />
        </el-form-item>
        <el-form-item :label="updateAction === 'notice' ? '补充说明' : '原因'">
          <el-input v-model.trim="updateForm.text" type="textarea" maxlength="500" />
        </el-form-item>
        <el-form-item><el-button native-type="submit" type="primary" :loading="saving">确认</el-button></el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script>
import CaseReferences from '@/components/CaseReferences.vue'
import request from '@/utils/request'

export default {
  components: { CaseReferences },
  name: 'EduTask',
  data() {
    return {
      courses: [], rows: [], courseId: null, pageNum: 1, pageSize: 20, total: 0, loading: false,
      form: { scoreItems: [] }, visible: false, saving: false, previewOpen: false, previewTask: {},
      updateOpen: false, updateAction: '', updateId: null, updateForm: {},
      modes: ['个人', '教师指定分组', '自由组队'],
      statuses: ['草稿', '进行中', '已截止', '已撤回', '已归档'],
      policies: ['严格截止', '宽限提交', '审批补交', '不限'],
      scoring: ['百分制', '等级制', '多维度加权'],
      updateTitles: { deadline: '任务延期', notice: '补充说明', recall: '撤回任务' }
    }
  },
  computed: {
    weightTotal() {
      return Math.round(this.form.scoreItems.reduce((sum, row) => sum + Number(row.weight || 0), 0) * 100) / 100
    }
  },
  async created() {
    const result = await request({ url: '/edu/course/list', params: { pageNum: 1, pageSize: 100 } })
    this.courses = result.rows
    await this.query()
  },
  methods: {
    load() {
      this.pageNum = 1
      return this.query()
    },
    async query() {
      this.loading = true
      try {
        const result = await request({ url: '/edu/task/list', params: {
          courseId: this.courseId, pageNum: this.pageNum, pageSize: this.pageSize
        } })
        this.rows = result.rows
        this.total = result.total
      } finally { this.loading = false }
    },
    async open(row) {
      const defaults = { courseId: this.courseId, title: '', requirement: '', startTime: '', deadline: '',
        deadlinePolicy: 0, graceDays: 3, finishMode: 0, groupMin: 2, groupMax: 4, scoreMode: 0, totalScore: 100 }
      const details = row ? (await request({ url: '/edu/task/' + row.taskId })).data : {}
      this.form = { ...defaults, ...details, scoreItems: details.scoreItems || [] }
      this.visible = true
    },
    normalize() {
      let hundredths = 10000
      let remainingWeight = this.weightTotal
      this.form.scoreItems.forEach((item, index, items) => {
        const weight = Number(item.weight)
        const next = index === items.length - 1 ? hundredths
          : Math.min(hundredths - (items.length - index - 1), Math.max(1, Math.round(weight / remainingWeight * hundredths)))
        item.weight = next / 100
        hundredths -= next
        remainingWeight -= weight
      })
    },
    async save() {
      if (this.saving) return
      const { courseId, title, requirement, startTime, deadline, scoreMode, scoreItems, totalScore } = this.form
      if (!courseId || !title || !requirement || !startTime || !deadline) return this.$modal.msgError('请填写课程、标题、实训要求和时间')
      if (scoreMode === 2 && (!scoreItems.length || scoreItems.some(row => !row.itemName || !row.weight))) {
        return this.$modal.msgError('请填写完整的评分项')
      }
      const data = { ...this.form, scoreItems: scoreMode === 2
        ? scoreItems.map((row, index) => ({ ...row, sortNo: index, maxScore: Math.round(row.weight * totalScore) / 100 }))
        : [] }
      if (scoreMode === 2 && this.weightTotal === 100) {
        const subtotal = data.scoreItems.slice(0, -1).reduce((sum, row) => sum + row.maxScore, 0)
        data.scoreItems[data.scoreItems.length - 1].maxScore = Math.round((totalScore - subtotal) * 100) / 100
      }
      this.saving = true
      try {
        await request({ url: '/edu/task', method: data.taskId ? 'put' : 'post', data })
        this.visible = false
        await this.query()
      } finally { this.saving = false }
    },
    async preview(row) {
      this.previewTask = (await request({ url: '/edu/task/' + row.taskId + '/preview' })).data
      this.previewOpen = true
    },
    async transition(row, action, label) {
      try { await this.$modal.confirm('确定' + label + '「' + row.title + '」？') } catch { return }
      await request({ url: '/edu/task/' + row.taskId + '/' + action, method: 'put' })
      await this.query()
    },
    openUpdate(row, action) {
      this.updateId = row.taskId
      this.updateAction = action
      this.updateForm = { deadline: row.deadline, text: '' }
      this.updateOpen = true
    },
    async saveUpdate() {
      const { deadline, text } = this.updateForm
      if (this.saving) return
      if (this.updateAction === 'deadline' && !deadline) return this.$modal.msgError('请选择新截止时间')
      if (this.updateAction === 'notice' && !text) return this.$modal.msgError('请输入补充说明')
      const params = this.updateAction === 'deadline' ? { deadline, reason: text }
        : this.updateAction === 'notice' ? { notice: text } : { reason: text }
      this.saving = true
      try {
        await request({ url: '/edu/task/' + this.updateId + '/' + this.updateAction, method: 'put', params })
        this.updateOpen = false
        await this.query()
      } finally { this.saving = false }
    }
  }
}
</script>

<style scoped>
.score-name { width: 14rem; margin-right: .75rem; }
.requirement { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
