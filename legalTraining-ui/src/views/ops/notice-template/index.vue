<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" show-icon class="tip"
      title="模板由业务与后台调度共同读取；占位符使用 {taskName} 形式，修改后立即生效。同一事件 10 分钟内对同一对象只发送一次。" />
    <el-button v-hasPermi="['ops:notice:add']" type="primary" @click="edit()">新增模板</el-button>
    <el-button v-hasPermi="['ops:notice:list']" @click="load">刷新</el-button>
    <el-table :data="rows" v-loading="loading" style="margin-top: 12px">
      <el-table-column prop="eventCode" label="事件编码" width="110" />
      <el-table-column prop="eventName" label="事件名称" width="150" />
      <el-table-column prop="titleTpl" label="标题模板" show-overflow-tooltip />
      <el-table-column prop="contentTpl" label="正文模板" show-overflow-tooltip />
      <el-table-column label="启用" width="80">
        <template slot-scope="{ row }">{{ row.status === '0' ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right" align="center" class-name="nowrap-actions">
        <template slot-scope="{ row }">
          <el-button v-hasPermi="['ops:notice:edit']" type="text" @click="edit(row)">编辑</el-button>
          <el-button v-hasPermi="['ops:notice:edit']" type="text" :loading="testing === row.eventCode" @click="test(row)">测试发送</el-button>
          <el-button v-hasPermi="['ops:notice:remove']" type="text" class="danger-text" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog :visible.sync="visible" :title="originalCode ? '编辑模板' : '新增模板'" width="680px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="事件编码">
          <el-input v-model.trim="form.eventCode" :disabled="!!originalCode" maxlength="16" placeholder="如 NT-02" />
        </el-form-item>
        <el-form-item label="事件名称"><el-input v-model.trim="form.eventName" maxlength="64" /></el-form-item>
        <el-form-item label="标题模板"><el-input v-model.trim="form.titleTpl" maxlength="200" /></el-form-item>
        <el-form-item label="正文模板">
          <el-input v-model="form.contentTpl" type="textarea" :rows="4" maxlength="2000" />
        </el-form-item>
        <el-form-item label="渠道">
          <el-select v-model="form.channels" style="width:160px">
            <el-option label="站内信" value="inner" />
          </el-select>
        </el-form-item>
        <el-form-item label="可关闭">
          <el-select v-model="form.closable" style="width:160px">
            <el-option label="是" value="1" />
            <el-option label="否" value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-select v-model="form.status" style="width:160px">
            <el-option label="启用" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'NoticeTemplate',
  data() {
    return { rows: [], loading: false, saving: false, testing: '', visible: false, originalCode: '', form: {} }
  },
  created() { this.load() },
  methods: {
    async load() {
      this.loading = true
      try {
        this.rows = (await request({ url: '/ops/notice-template/list' })).data
      } finally { this.loading = false }
    },
    edit(row) {
      this.originalCode = row ? row.eventCode : ''
      this.form = row ? { ...row } : { eventCode: '', eventName: '', titleTpl: '', contentTpl: '',
        channels: 'inner', closable: '1', status: '0' }
      this.visible = true
    },
    async save() {
      if (this.saving) return
      if (!this.form.eventCode || !this.form.titleTpl) return this.$modal.msgError('请填写事件编码和标题模板')
      this.saving = true
      try {
        await request({ url: '/ops/notice-template', method: this.originalCode ? 'put' : 'post', data: this.form })
        this.visible = false
        this.$modal.msgSuccess('模板已保存')
        await this.load()
      } finally { this.saving = false }
    },
    async test(row) {
      this.testing = row.eventCode
      try {
        const { data } = await request({ url: '/ops/notice-template/' + row.eventCode + '/test', method: 'post' })
        this.$modal.msgSuccess(data.message)
        await this.load()
      } finally { this.testing = '' }
    },
    async remove(row) {
      try { await this.$modal.confirm('确认删除模板「' + row.eventName + '」？') } catch { return }
      await request({ url: '/ops/notice-template/' + row.eventCode, method: 'delete' })
      await this.load()
    }
  }
}
</script>

<style scoped>
.tip { margin-bottom: 12px; }
.danger-text { color: #f56c6c; }
</style>
