<template>
  <div class="app-container">
    <el-alert v-if="!state.configured" type="warning" title="请先配置服务器备份目录、MySQL 客户端和上传目录。" :closable="false" />
    <el-form v-if="state.policy" inline @submit.native.prevent="savePolicy">
      <el-form-item label="启用计划"><el-switch v-model="state.policy.enabled" :disabled="!state.configured" /></el-form-item>
      <el-form-item label="全量间隔（小时）"><el-input-number v-model="state.policy.fullHours" :min="1" :max="8760" /></el-form-item>
      <el-form-item label="增量间隔（小时）"><el-input-number v-model="state.policy.incrementalHours" :min="1" :max="8760" /></el-form-item>
      <el-form-item label="保留天数"><el-input-number v-model="state.policy.retainDays" :min="1" :max="3650" /></el-form-item>
      <el-button native-type="submit" type="primary" :loading="working">保存策略</el-button>
    </el-form>
    <el-button :disabled="!state.configured || running || working" @click="start('full')">执行全量备份</el-button>
    <el-button :disabled="!state.configured || running || working" @click="start('incr')">执行增量备份</el-button>
    <el-button :loading="loading" @click="load(false)">刷新</el-button>
    <el-table :data="state.backups" empty-text="暂无备份">
      <el-table-column prop="id" label="备份编号" />
      <el-table-column label="类型"><template slot-scope="{ row }">{{ row.type === 'incr' ? '增量' : '全量' }}</template></el-table-column>
      <el-table-column prop="completedUtc" label="归档时间（UTC）" />
      <el-table-column prop="baseId" label="依赖的全量备份" />
      <el-table-column label="操作" width="150" fixed="right" align="right">
        <template slot-scope="{ row }"><el-button type="text" :disabled="!state.restoreConfigured || running || working" @click="open(row)">恢复到独立目标</el-button></template>
      </el-table-column>
    </el-table>
    <h3>运行记录</h3>
    <el-table :data="state.operations" empty-text="暂无运行记录">
      <el-table-column prop="type" label="操作" />
      <el-table-column label="状态"><template slot-scope="{ row }">{{ ['进行中', '成功', '失败'][row.status] }}</template></el-table-column>
      <el-table-column prop="operatorId" label="操作人编号" />
      <el-table-column prop="startTime" label="开始时间" />
      <el-table-column prop="endTime" label="结束时间" />
      <el-table-column prop="failure" label="结果说明" />
    </el-table>
    <el-dialog title="恢复备份" :visible.sync="restoreOpen" width="540px">
      <el-form label-width="110px" @submit.native.prevent="restore">
        <el-form-item label="备份编号">{{ selected.id }}</el-form-item>
        <el-form-item v-if="selected.type === 'incr'" label="回放截止 UTC">
          <el-input v-model.trim="recoverBeforeUtc" placeholder="例如 2026-11-15T02:00:00Z，留空恢复到归档点" />
        </el-form-item>
        <el-form-item><el-button native-type="submit" type="primary" :loading="working">执行恢复</el-button></el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>
<script>
import request from '@/utils/request'

export default {
  name: 'OpsBackup',
  data() {
    return { state: { backups: [], operations: [] }, loading: false, working: false, timer: null,
      restoreOpen: false, selected: {}, recoverBeforeUtc: '' }
  },
  computed: {
    running() { return this.state.operations.some(row => row.status === 0) }
  },
  created() {
    this.load(false)
    this.timer = setInterval(() => { if (this.running) this.load(true) }, 3000)
  },
  beforeDestroy() { clearInterval(this.timer) },
  methods: {
    async load(quiet) {
      if (this.loading) return
      this.loading = true
      try {
        const next = (await request({ url: '/ops/backup' })).data
        if (quiet) next.policy = this.state.policy
        this.state = next
      } finally { this.loading = false }
    },
    async perform(options) {
      if (this.working) return
      this.working = true
      try {
        await request(options)
        await this.load(false)
      } finally { this.working = false }
    },
    savePolicy() {
      return this.perform({ url: '/ops/backup/policy', method: 'put', data: this.state.policy })
    },
    start(type) {
      return this.perform({ url: '/ops/backup/' + type, method: 'post' })
    },
    open(row) {
      this.selected = row
      this.recoverBeforeUtc = ''
      this.restoreOpen = true
    },
    async restore() {
      if (this.recoverBeforeUtc && !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ$/.test(this.recoverBeforeUtc)) {
        return this.$modal.msgError('请按示例输入 UTC 时间')
      }
      try { await this.$modal.confirm('恢复将在服务器配置的独立空目标中执行。确定继续？') } catch { return }
      await this.perform({ url: '/ops/backup/restore', method: 'post',
        data: { id: this.selected.id, recoverBeforeUtc: this.recoverBeforeUtc || null } })
      this.restoreOpen = false
    }
  }
}
</script>
