<template>
  <div class="app-container">
    <el-form inline @submit.native.prevent="loadScores">
      <el-form-item label="实训任务">
        <el-select v-model="taskId" filterable placeholder="请选择任务" @change="loadScores">
          <el-option v-for="item in tasks" :key="item.taskId" :value="item.taskId" :label="item.title" />
        </el-select>
      </el-form-item>
      <el-button native-type="submit" :disabled="!taskId">查询</el-button>
    </el-form>
    <el-table :data="scores">
      <el-table-column prop="studentNo" label="学号" />
      <el-table-column prop="studentName" label="学生" />
      <el-table-column prop="finalScore" label="得分" />
      <el-table-column label="操作">
        <template slot-scope="{ row }">
          <el-button v-if="row.submissionId && !recommendations.some(item => item.submissionId === row.submissionId)"
                     v-hasPermi="['edu:score:recommend']" type="text" @click="prepare(row)">推荐成果</el-button>
        </template>
      </el-table-column>
    </el-table>
    <h3>成果推荐记录</h3>
    <el-table :data="recommendations">
      <el-table-column prop="title" label="成果" />
      <el-table-column prop="zoneName" label="展区" />
      <el-table-column label="状态">
        <template slot-scope="{ row }">{{ statuses[row.status] }}</template>
      </el-table-column>
      <el-table-column prop="opinion" label="审核意见" />
      <el-table-column label="展项状态">
        <template slot-scope="{ row }">
          <span v-if="row.exhibitId">{{ exhibitStatuses[row.exhibitStatus] || '未知' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="授权操作">
        <template slot-scope="{ row }">
          <template v-if="row.status === 0">
            <el-button type="text" :disabled="saving" @click="remind(row)">再次提醒</el-button>
            <el-button type="text" @click="prepareAuthorization(row)">登记线下授权</el-button>
          </template>
          <el-button v-if="row.hasAuthFile" type="text" @click="downloadAuthorization(row)">下载授权书</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog :visible.sync="open" title="推荐成果" width="550px">
      <el-form label-width="90px" @submit.native.prevent="recommend">
        <el-form-item label="推荐理由"><el-input v-model.trim="form.reason" type="textarea" maxlength="1000" /></el-form-item>
        <el-form-item label="目标展区">
          <el-select v-model="form.zoneId" filterable>
            <el-option v-for="zone in zones" :key="zone.zoneId" :value="zone.zoneId" :label="zone.zoneName" />
          </el-select>
        </el-form-item>
        <el-form-item label="推荐等级"><el-input v-model.trim="form.levelCode" maxlength="16" /></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit" :loading="saving" :disabled="!form.reason || !form.zoneId || !form.levelCode">发起授权</el-button></el-form-item>
      </el-form>
    </el-dialog>
    <el-dialog :visible.sync="authorizationOpen" title="登记线下授权" width="550px">
      <el-form label-width="90px" @submit.native.prevent="uploadAuthorization">
        <el-form-item label="署名方式">
          <el-select v-model="authorization.signMode">
            <el-option v-for="value in ['实名', '部分匿名', '完全匿名']" :key="value" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="展示范围">
          <el-select v-model="authorization.scopeMode">
            <el-option v-for="value in ['仅展厅内', '含对外分享']" :key="value" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="PDF 授权书">
          <input :key="authorization.recId" type="file" accept=".pdf,application/pdf" @change="authorization.file = $event.target.files[0]" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="saving" :disabled="!authorization.file">登记并送审</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>
<script>
import request from '@/utils/request'
import { saveAs } from 'file-saver'
export default {
  name: 'EduScore',
  data() {
    return {
      tasks: [], taskId: null, scores: [], recommendations: [], zones: [], form: {}, open: false, saving: false,
      authorizationOpen: false, authorization: {},
      statuses: ['待授权', '待审核', '审核通过', '审核驳回', '已撤回或拒绝', '已生成展项'],
      exhibitStatuses: ['草稿', '待发布', '已发布', '已下架', '发布失败']
    }
  },
  async created() {
    const tasks = await request({ url: '/edu/task/list', params: { pageNum: 1, pageSize: 100 } })
    this.tasks = tasks.rows
    await this.loadRecommendations()
  },
  methods: {
    async downloadAuthorization(row) {
      const file = await request({ url: '/edu/rec/' + row.recId + '/auth-file', responseType: 'blob' })
      if (file.type === 'application/pdf') {
        saveAs(file, '成果展示授权书.pdf')
      } else {
        this.$modal.msgError('无法读取授权书')
      }
    },
    prepareAuthorization(row) {
      this.authorization = { recId: row.recId, signMode: '完全匿名', scopeMode: '仅展厅内', file: null }
      this.authorizationOpen = true
    },
    async remind(row) {
      if (this.saving) return
      this.saving = true
      try {
        await request({ url: '/edu/rec/' + row.recId + '/auth-request', method: 'post' })
        this.$modal.msgSuccess('已发送授权提醒')
      } finally { this.saving = false }
    },
    async uploadAuthorization() {
      if (this.saving || !this.authorization.file) return
      const { recId, signMode, scopeMode, file } = this.authorization
      const data = new FormData()
      data.append('file', file)
      data.append('signMode', signMode)
      data.append('scopeMode', scopeMode)
      this.saving = true
      try {
        await request({ url: '/edu/rec/' + recId + '/auth-file', method: 'post', data,
          headers: { repeatSubmit: false, 'Content-Type': 'multipart/form-data' } })
        this.authorizationOpen = false
        this.$modal.msgSuccess('授权已登记，成果已送审')
        await this.loadRecommendations()
      } finally { this.saving = false }
    },
    async loadRecommendations() {
      if (this.$auth.hasPermi('edu:score:recommend')) {
        this.recommendations = (await request({ url: '/edu/rec/list' })).data
      }
    },
    async loadScores() {
      const taskId = this.taskId
      const [scores, submissions] = await Promise.all([
        request({ url: '/edu/score/list', params: { taskId, pageNum: 1, pageSize: 100 } }),
        request({ url: '/edu/submission/list', params: { taskId, status: 3 } })
      ])
      if (taskId !== this.taskId) return
      const byUser = new Map(submissions.data.map(item => [item.userId, item.submissionId]))
      this.scores = scores.rows.filter(row => byUser.has(row.userId)).map(row => ({ ...row, submissionId: byUser.get(row.userId) }))
    },
    async prepare(row) {
      this.zones = (await request({ url: '/edu/rec/options' })).data
      this.form = { submissionId: row.submissionId, zoneId: null, reason: '', levelCode: '' }
      this.open = true
    },
    async recommend() {
      if (this.saving) return
      this.saving = true
      try {
        await request({ url: '/edu/rec', method: 'post', data: this.form })
        this.open = false
        this.$modal.msgSuccess('已发起学生授权')
        await this.loadRecommendations()
      } finally { this.saving = false }
    }
  }
}
</script>
