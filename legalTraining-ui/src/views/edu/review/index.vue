<template>
  <div class="app-container">
    <el-form inline @submit.native.prevent="load">
      <el-form-item label="任务">
        <el-select v-model="taskId" filterable placeholder="选择实训任务" @change="load">
          <el-option v-for="task in tasks" :key="task.taskId" :value="task.taskId" :label="task.title" />
        </el-select>
      </el-form-item>
      <el-button type="primary" native-type="submit" :disabled="!taskId">刷新</el-button>
      <el-button v-hasPermi="['edu:score:publish']" :disabled="!taskId" @click="publishScores">发布任务成绩</el-button>
    </el-form>
    <el-table v-loading="loading" :data="rows" row-key="submissionId" empty-text="请选择任务，或该任务暂无待处理作业">
      <el-table-column prop="studentNo" label="学号" />
      <el-table-column prop="studentName" label="学生" />
      <el-table-column prop="version" label="当前版本" />
      <el-table-column label="状态">
        <template slot-scope="{ row }">{{ row.lateApplyStatus === 1 ? '待审批补交' : '待批阅' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template slot-scope="{ row }">
          <el-button type="text" @click="open(row)">{{ row.lateApplyStatus === 1 ? '审批' : '查看并批阅' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-if="current" :visible.sync="visible" :title="current.studentName || '作业详情'" width="650px">
      <el-form label-width="100px">
        <template v-if="current.lateApplyStatus === 1">
          <el-form-item label="申请理由"><p class="submission-text">{{ current.lateReason }}</p></el-form-item>
          <el-form-item label="补交截止"><el-date-picker v-model="deadline" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" /></el-form-item>
        </template>
        <template v-else>
          <el-form-item label="作业说明"><p class="submission-text">{{ current.content }}</p></el-form-item>
          <el-form-item v-if="current.files && current.files.length" label="附件">
            <ul>
              <li v-for="file in current.files" :key="file.fileId">
                <a v-if="fileUrl(file.fileId)" :href="fileUrl(file.fileId)" target="_blank" rel="noopener noreferrer">{{ file.fileName }}</a>
                <span v-else>{{ file.fileName }}</span>
              </li>
            </ul>
          </el-form-item>
          <template v-if="returning">
            <el-form-item label="退回理由"><el-input v-model="reason" type="textarea" maxlength="500" /></el-form-item>
            <el-form-item label="重交截止"><el-date-picker v-model="deadline" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" /></el-form-item>
          </template>
          <template v-else>
            <template v-if="taskDetail.scoreMode === 2">
              <el-form-item v-for="item in items" :key="item.itemId" :label="item.itemName">
                <el-input-number v-model="item.score" :min="0" :max="item.maxScore" :precision="2" />
              </el-form-item>
            </template>
            <el-form-item v-else label="得分"><el-input-number v-model="totalScore" :min="0" :max="taskDetail.totalScore" :precision="2" /></el-form-item>
            <el-form-item label="评语"><el-input v-model="reviewComment" type="textarea" maxlength="2000" /></el-form-item>
          </template>
        </template>
      </el-form>
      <span slot="footer">
        <template v-if="current.lateApplyStatus === 1">
          <el-button v-hasPermi="['edu:review:audit']" :loading="saving" @click="approve(false)">驳回</el-button>
          <el-button v-hasPermi="['edu:review:audit']" type="primary" :loading="saving" :disabled="!deadline" @click="approve(true)">通过</el-button>
        </template>
        <template v-else-if="returning">
          <el-button @click="returning = false">返回批阅</el-button>
          <el-button v-hasPermi="['edu:review:return']" type="danger" :loading="saving" :disabled="!deadline || reason.trim().length < 20" @click="returnSubmission">确认退回</el-button>
        </template>
        <template v-else>
          <el-button v-hasPermi="['edu:review:return']" @click="returning = true">退回重交</el-button>
          <el-button v-hasPermi="['edu:review:add']" type="primary" :loading="saving" @click="saveReview">发布批阅</el-button>
        </template>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'EduReview',
  data() {
    return {
      taskId: null, tasks: [], rows: [], current: null, taskDetail: {}, items: [],
      loading: false, saving: false, visible: false, returning: false, deadline: null,
      reason: '', reviewComment: '', totalScore: 0
    }
  },
  async created() {
    const result = await request({ url: '/edu/task/list', params: { pageNum: 1, pageSize: 100 } })
    this.tasks = result.rows
  },
  methods: {
    async load() {
      if (!this.taskId) return
      const taskId = this.taskId
      this.loading = true
      this.rows = []
      try {
        const result = await request({ url: '/edu/submission/list', params: { taskId } })
        if (this.taskId === taskId) this.rows = result.data.filter(row => row.status === 2 || row.lateApplyStatus === 1)
      } finally {
        if (this.taskId === taskId) this.loading = false
      }
    },
    async open(row) {
      const taskId = this.taskId
      const [submission, task] = await Promise.all([
        request({ url: '/edu/submission/' + row.submissionId }),
        request({ url: '/edu/task/' + taskId })
      ])
      if (this.taskId !== taskId) return
      this.current = submission.data
      this.taskDetail = task.data
      this.items = (task.data.scoreItems || []).map(item => ({ ...item, score: 0 }))
      this.totalScore = 0
      this.reviewComment = ''
      this.reason = ''
      this.deadline = null
      this.returning = false
      this.visible = true
    },
    async perform(options, message) {
      if (this.saving) return
      this.saving = true
      try {
        await request(options)
        this.visible = false
        this.$modal.msgSuccess(message)
        await this.load()
      } finally {
        this.saving = false
      }
    },
    saveReview() {
      const data = { submissionId: this.current.submissionId, status: 1, reviewComment: this.reviewComment }
      if (this.taskDetail.scoreMode === 2) {
        data.scores = this.items.map(({ itemId, score }) => ({ itemId, score }))
      } else {
        data.totalScore = this.totalScore
      }
      return this.perform({ url: '/edu/review', method: 'post', data }, '批阅已发布')
    },
    returnSubmission() {
      return this.perform({
        url: '/edu/review/' + this.current.submissionId + '/return',
        method: 'post', params: { reason: this.reason.trim(), deadline: this.deadline }
      }, '作业已退回')
    },
    approve(approve) {
      return this.perform({
        url: '/edu/submission/' + this.current.submissionId + '/late-approve',
        method: 'put', params: { approve, deadline: approve ? this.deadline : undefined }
      }, '补交申请已处理')
    },
    async publishScores() {
      const taskId = this.taskId
      await this.$modal.confirm('确认发布该任务已批阅的成绩？')
      return this.perform({ url: '/edu/score/publish/' + taskId, method: 'post' }, '成绩已发布')
    },
    fileUrl(fileId) {
      return typeof fileId === 'string' && /^\/profile\/upload\/[\w./-]+$/.test(fileId) && !fileId.includes('..')
        ? process.env.VUE_APP_BASE_API + fileId : ''
    }
  }
}
</script>

<style scoped>
.submission-text { white-space: pre-wrap; overflow-wrap: anywhere; margin: 0; }
</style>
