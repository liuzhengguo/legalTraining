<template>
  <div>
  <BusinessRecords ref="records" :config="config">
    <template #column-status="{ value }">
      <el-tag v-if="String(value) === '0'" size="small" type="success" effect="dark">已启用</el-tag>
      <el-tag v-else-if="String(value) === '1'" size="small" type="danger" effect="dark">已停用</el-tag>
      <span v-else>—</span>
    </template>
    <template #toolbar>
      <el-button v-hasPermi="['edu:student:import']" @click="importOpen = true">批量导入</el-button>
    </template>
    <template #actions="{ row, reload }"><el-button v-hasPermi="['edu:student:edit']" type="text" @click="changeStatus(row, reload)">{{ row.status === '0' ? '停用' : '启用' }}</el-button>
      <el-button v-hasPermi="['edu:student:resetPwd']" type="text" @click="resetPassword(row)">重置密码</el-button>
      <el-button v-hasPermi="['edu:student:query']" type="text" @click="memberships(row)">历史归属</el-button>
      <el-button v-hasPermi="['edu:student:transfer']" type="text" @click="transfer(row, reload)">转班</el-button>
    </template>
  </BusinessRecords>
    <el-dialog title="学生账号批量导入" :visible.sync="importOpen" width="min(740px, 95vw)">
    <el-form label-width="100px">
      <el-form-item label="目标学期">
        <el-select v-model="termId" filterable clearable placeholder="默认当前学期" style="width: 100%" @change="preview = null">
          <el-option v-for="term in termOptions" :key="term.value" :label="term.label" :value="term.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="导入文件">
        <input type="file" accept=".xlsx,.xls" @change="file = $event.target.files[0]; preview = null">
        <el-button type="text" @click="downloadTemplate">下载模板</el-button>
      </el-form-item>
    </el-form>
    <el-button :loading="importBusy" :disabled="!file" @click="previewImport">校验导入文件</el-button>
    <template v-if="preview">
      <p>总计 {{ preview.totalRows }} 行，有效 {{ preview.successRows }} 行，失败 {{ preview.failRows }} 行。</p>
      <el-table v-if="failures.length" :data="failures">
        <el-table-column prop="rowNo" label="行号" />
        <el-table-column prop="failReason" label="失败原因" />
      </el-table>
      <p v-if="preview.overLimit">{{ preview.message }}</p>
      <el-button type="primary" :loading="importBusy" :disabled="preview.overLimit" @click="confirmImport">确认导入有效记录</el-button>
    </template>
  </el-dialog>
  <el-dialog title="学生转班" :visible.sync="transferOpen" append-to-body width="min(460px, 95vw)">
    <el-form label-width="90px">
      <el-form-item label="学生"><span>{{ transferRow && transferRow.nickName }}（{{ transferRow && transferRow.userName }}）</span></el-form-item>
      <el-form-item label="目标班级">
        <el-select v-model="transferClassId" filterable placeholder="搜索班级名称" style="width: 100%">
          <el-option v-for="c in classOptions" :key="c.value" :label="c.label" :value="c.value" />
        </el-select>
      </el-form-item>
    </el-form>
    <span slot="footer">
      <el-button @click="transferOpen = false">取消</el-button>
      <el-button type="primary" :disabled="!transferClassId" @click="confirmTransfer">确认转班</el-button>
    </span>
  </el-dialog>
  </div>
</template>

<script>
import BusinessRecords from '@/components/BusinessRecords'
import request from '@/utils/request'
import { saveAs } from 'file-saver'

export default {
  name: 'EduStudent',
  components: { BusinessRecords },
  data() {
    return { termId: undefined, file: null, importOpen: false, importBusy: false, preview: null, failures: [],
      termOptions: [], classOptions: [], transferOpen: false, transferRow: null, transferClassId: undefined,
      config: {
      title: '学生账号', base: '/edu/student', permission: 'edu:student', id: 'userId',
      paged: true, remove: false, rowActions: true, defaults: {},
      createOnly: true,
      filters: [{"key":"nickName","label":"姓名"},{"key":"studentNo","label":"学号"}],
      fields: [
        { key: 'userName', label: '登录账号', required: true },
        { key: 'nickName', label: '姓名', required: true },
        { key: 'studentNo', label: '学号', required: true },
        { key: 'classId', label: '班级', required: true, options: () => this.classOptions },
        { key: 'phonenumber', label: '手机号' }
      ],
      columns: [{"key":"userId","label":"ID"},{"key":"userName","label":"登录账号"},{"key":"nickName","label":"姓名"},{"key":"studentNo","label":"学号"},{"key":"className","label":"班级"},{"key":"majorName","label":"专业"},
        { key: 'status', label: '状态' }]
    } }
  },
  created() { this.loadOptions() },
  methods: {
    async loadOptions() {
      try {
        const { data } = await request({ url: '/edu/student/import-options' })
        this.termOptions = data.terms || []
        this.classOptions = data.classes || []
      } catch (e) { this.termOptions = []; this.classOptions = [] }
    },
    async downloadTemplate() {
      const blob = await request({ url: '/edu/import/template', responseType: 'blob' })
      if (blob.type.includes('json')) {
        const result = JSON.parse(await blob.text())
        return this.$modal.msgError(result.msg || '模板下载失败')
      }
      saveAs(blob, '学生导入模板.xlsx')
    },
    async previewImport() {
      if (!this.file || this.importBusy) return
      const payload = new FormData()
      payload.append('file', this.file)
      this.preview = null
      this.importBusy = true
      try {
        const { data } = await request({ url: '/edu/import/preview', method: 'post', params: { termId: this.termId },
          headers: { 'Content-Type': 'multipart/form-data', repeatSubmit: false }, data: payload })
        this.failures = (await request({ url: '/edu/import/' + data.batchId + '/fail-list' })).data || []
        this.preview = data
      } finally { this.importBusy = false }
    },
    async confirmImport() {
      if (!this.preview || this.importBusy) return
      this.importBusy = true
      try {
        const { data } = await request({ url: '/edu/import/' + this.preview.batchId + '/confirm', method: 'post' })
        this.preview = null
        this.importOpen = false
        this.$modal.msgSuccess(data.message)
        await this.$refs.records.load()
      } finally { this.importBusy = false }
    },
    transfer(row, reload) {
      this.transferRow = row
      this.transferReload = reload
      this.transferClassId = undefined
      this.transferOpen = true
    },
    async confirmTransfer() {
      if (!this.transferRow || !this.transferClassId) return
      await request({ url: '/edu/transfer/class', method: 'post',
        params: { userId: this.transferRow.userId, toClassId: this.transferClassId } })
      this.transferOpen = false
      this.$modal.msgSuccess('转班成功')
      if (this.transferReload) await this.transferReload()
    },
    async changeStatus(row, reload) {
      const status = row.status === '0' ? '1' : '0'
      await this.$modal.confirm(status === '1' ? '确认停用该学生账号？' : '确认启用该学生账号？')
      await request({ url: '/edu/student/' + row.userId + '/status', method: 'put', params: { status } })
      await reload()
    },
    async resetPassword(row) {
      await this.$modal.confirm('确认重置该学生密码？')
      const { data } = await request({ url: '/edu/student/' + row.userId + '/reset-pwd', method: 'put' })
      this.$alert(typeof data === 'string' ? data : '已重置', '密码重置')
    },
    async memberships(row) {
      const { data } = await request({ url: '/edu/transfer/memberships/' + row.userId })
      this.$alert(data.map(entry => entry.className + (entry.isCurrent === '1' ? '（当前）' : '（历史）')).join('、') || '暂无归属记录', '班级归属')
    }
  }
}
</script>
