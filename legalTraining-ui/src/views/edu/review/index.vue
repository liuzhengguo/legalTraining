<template>
  <div class="app-container">
    <el-form inline @submit.native.prevent="load">
      <el-form-item label="任务">
        <el-select v-model="taskId" filterable placeholder="选择实训任务" @change="load">
          <el-option v-for="task in tasks" :key="task.taskId" :value="task.taskId" :label="task.title" />
        </el-select>
      </el-form-item>
      <el-button type="primary" native-type="submit" :disabled="!taskId">刷新</el-button>
      <el-button v-hasPermi="['edu:review:batch']" :disabled="!taskId || !selected.length" @click="openBatch">批量批阅（{{ selected.length }}）</el-button>
      <el-button v-hasPermi="['edu:score:publish']" :disabled="!taskId" @click="publishScores">发布任务成绩</el-button>
    </el-form>
    <el-form v-if="teamSettings" inline>
      <el-form-item label="组内任一成员可提交">
        <el-switch :value="teamSettings.anyMemberSubmit" :disabled="saving || !$auth.hasPermi('edu:task:edit')" @change="changeTeamSettings" />
      </el-form-item>
    </el-form>
    <el-table ref="reviewTable" v-loading="loading" :data="rows" row-key="submissionId" empty-text="请选择任务，或该任务暂无待处理作业" @selection-change="onSelect">
      <el-table-column type="selection" width="42" :selectable="row => row.lateApplyStatus !== 1" />
      <el-table-column prop="studentNo" label="学号" />
      <el-table-column prop="studentName" label="学生" />
      <el-table-column prop="version" label="当前版本" />
      <el-table-column label="状态">
        <template slot-scope="{ row }">{{ row.lateApplyStatus === 1 ? '待审批补交' : '待批阅' }}</template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" align="center" width="140" class-name="nowrap-actions">
        <template slot-scope="{ row }">
          <el-button type="text" @click="open(row)">{{ row.lateApplyStatus === 1 ? '审批' : '查看并批阅' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <section v-if="legacyRows.length && $auth.hasPermi('edu:task:edit')">
      <h3>旧版个人作业</h3>
      <el-table :data="legacyRows">
        <el-table-column prop="studentNo" label="学号" />
        <el-table-column prop="studentName" label="学生" />
        <el-table-column prop="version" label="版本" />
        <el-table-column label="操作" width="155" fixed="right" align="center" class-name="nowrap-actions">
          <template slot-scope="{ row }">
            <el-button type="text" :disabled="saving" @click="migrateLegacy(row)">迁移为小组作业</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
    <el-dialog v-if="current" :visible.sync="visible" :title="current.studentName || '作业详情'" width="650px">
      <el-form label-width="100px">
        <template v-if="current.lateApplyStatus === 1">
          <el-form-item label="申请理由"><p class="submission-text">{{ current.lateReason }}</p></el-form-item>
          <el-form-item label="补交截止"><el-date-picker v-model="deadline" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" /></el-form-item>
        </template>
        <template v-else>
          <el-form-item v-if="teamMembers.length" label="小组贡献">
            <ul><li v-for="member in teamMembers" :key="member.userId">
              {{ member.name }}：{{ member.contribution || '未填写' }}
            </li></ul>
          </el-form-item>
          <el-form-item label="作业说明"><p class="submission-text">{{ current.content }}</p></el-form-item>
          <el-form-item v-if="current.files && current.files.length" label="附件">
            <ul>
              <li v-for="file in current.files" :key="file.fileId">
                <a href="javascript:;" @click="openAttachment(file)">{{ file.fileName }}</a>
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
            <el-form-item label="常用评语">
              <el-select v-model="pickedComment" clearable placeholder="选择常用评语插入" style="width: 100%" @change="applyComment">
                <el-option v-for="(text, idx) in commonComments" :key="idx" :label="text" :value="idx" />
              </el-select>
            </el-form-item>
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
    <el-dialog :visible.sync="batchOpen" title="批量批阅" width="min(560px, 95vw)" append-to-body>
      <p class="batch-tip">将对已选的 {{ selected.length }} 份作业统一发布批阅结果（仅百分制任务，等级制请逐条批阅）。</p>
      <el-form label-width="100px">
        <el-form-item label="统一得分"><el-input-number v-model="batchScore" :min="0" :max="taskDetail.totalScore || 100" :precision="2" /></el-form-item>
        <el-form-item label="常用评语">
          <el-select v-model="batchPicked" clearable placeholder="选择常用评语插入" style="width: 100%" @change="v => batchComment = v == null ? batchComment : commonComments[v]">
            <el-option v-for="(text, idx) in commonComments" :key="idx" :label="text" :value="idx" />
          </el-select>
        </el-form-item>
        <el-form-item label="评语"><el-input v-model="batchComment" type="textarea" maxlength="2000" /></el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="batchOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="batchScore == null" @click="doBatch">确认批量发布</el-button>
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
      legacyRows: [], teamMembers: [], teamSettings: null, loading: false, saving: false, visible: false, returning: false, deadline: null,
      reason: '', reviewComment: '', totalScore: 0, attachmentUrls: {},
      selected: [], commonComments: [], pickedComment: null,
      batchOpen: false, batchScore: 0, batchComment: '', batchPicked: null
    }
  },
  async created() {
    const defaults = [
      '完成度较高，思路清晰，继续保持良好的学习习惯。',
      '基本完成要求，部分细节需要完善，请对照评价标准再作修改。',
      '分析较为深入，能够结合实际案例，论证充分。',
      '存在概念性错误，请重新梳理相关知识点后再提交。',
      '材料详实、结构完整，符合规范要求。'
    ]
    try {
      const saved = JSON.parse(localStorage.getItem('edu.review.commonComments') || 'null')
      this.commonComments = Array.isArray(saved) && saved.length ? saved : defaults
    } catch (e) { this.commonComments = defaults }
    const result = await request({ url: '/edu/task/list', params: { pageNum: 1, pageSize: 100 } })
    this.tasks = result.rows
  },
  methods: {
    onSelect(rows) { this.selected = rows },
    applyComment(idx) {
      if (idx == null) return
      const text = this.commonComments[idx]
      if (text) this.reviewComment = this.reviewComment ? this.reviewComment + '\n' + text : text
      this.pickedComment = null
    },
    openBatch() {
      if (this.taskDetail.scoreMode === 2) {
        return this.$modal.msgWarning('多维度评分任务请逐条批阅')
      }
      this.batchScore = 0
      this.batchComment = ''
      this.batchPicked = null
      this.batchOpen = true
    },
    async doBatch() {
      if (this.saving || this.batchScore == null || !this.selected.length) return
      const items = this.selected.map(row => ({ submissionId: row.submissionId,
        totalScore: this.batchScore, reviewComment: this.batchComment }))
      this.saving = true
      try {
        const { data } = await request({ url: '/edu/review/batch', method: 'post', data: items })
        this.batchOpen = false
        this.selected = []
        this.$refs.reviewTable && this.$refs.reviewTable.clearSelection && this.$refs.reviewTable.clearSelection()
        const msg = data && data.fail > 0
          ? `批量批阅完成：成功 ${data.ok || 0} 份，失败 ${data.fail} 份`
          : `批量批阅完成：成功 ${(data && data.ok) || items.length} 份`
        this.$modal.msgSuccess(msg)
        await this.load()
      } finally { this.saving = false }
    },
    async migrateLegacy(row) {
      const taskId = this.taskId
      try { await this.$modal.confirm('将该学生的旧版作业作为小组共享作业？原作业编号、附件和版本保留。') } catch { return }
      if (taskId !== this.taskId) return
      this.saving = true
      try {
        await request({ url: '/edu/submission/team/' + taskId + '/legacy/' + row.submissionId, method: 'put' })
        this.$modal.msgSuccess('已迁移为小组作业')
        await this.load()
      } finally { this.saving = false }
    },
    async changeTeamSettings(anyMemberSubmit) {
      const taskId = this.taskId
      this.saving = true
      try {
        await request({ url: '/edu/submission/team/' + taskId + '/settings', method: 'put', params: { anyMemberSubmit } })
        if (taskId === this.taskId && this.teamSettings) this.teamSettings.anyMemberSubmit = anyMemberSubmit
      } finally { this.saving = false }
    },
    async load() {
      if (!this.taskId) return
      const taskId = this.taskId
      this.loading = true
      this.rows = []
      this.legacyRows = []
      this.teamSettings = null
      try {
        const task = this.tasks.find(entry => entry.taskId === taskId)
        const [result, settings] = await Promise.all([
          request({ url: '/edu/submission/list', params: { taskId } }),
          task && task.finishMode && request({ url: '/edu/submission/team/' + taskId + '/settings' })
        ])
        if (settings && this.taskId === taskId) {
          this.teamSettings = settings.data
          this.legacyRows = result.data.filter(row => row.subjectType === 0 && row.status !== 3)
        }
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
      this.teamMembers = submission.teamMembers || []
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
    // F01：批阅附件走带业务鉴权的接口取 blob，不再拼 /profile 静态直链
    async openAttachment(file) {
      const key = file.fileId
      try {
        let url = this.attachmentUrls[key]
        if (!url) {
          const blob = await request({
            url: '/edu/submission/' + this.current.submissionId + '/files/' + encodeURIComponent(key) + '/content',
            responseType: 'blob'
          })
          if (!(blob instanceof Blob) || blob.type.includes('json')) {
            this.$modal.msgError('附件读取失败或无权访问')
            return
          }
          url = URL.createObjectURL(blob)
          this.$set(this.attachmentUrls, key, url)
        }
        window.open(url, '_blank', 'noopener,noreferrer')
      } catch (e) {
        this.$modal.msgError('附件读取失败或无权访问')
      }
    }
  },
  beforeDestroy() {
    Object.values(this.attachmentUrls).forEach(url => URL.revokeObjectURL(url))
  }
}
</script>

<style scoped>
.submission-text { white-space: pre-wrap; overflow-wrap: anywhere; margin: 0; }
.batch-tip { color: #606266; line-height: 1.6; margin-top: 0; }
</style>
