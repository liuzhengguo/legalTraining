<template>
  <div>
  <BusinessRecords ref="records" :config="config">
    <template #toolbar><el-button type="primary" @click="openApplication">发起下架申请</el-button></template>
    <template #actions="{ row, reload }">
      <template v-if="[0, 3].includes(row.status)">
        <el-button type="text" @click="decide(row, true, reload)">同意</el-button>
        <el-button type="text" @click="decide(row, false, reload)">驳回</el-button>
      </template>
    </template>
  </BusinessRecords>
  <el-dialog title="展厅下架申请" :visible.sync="applicationOpen" width="min(560px, 95vw)">
    <el-form label-width="105px" @submit.native.prevent="apply">
      <el-form-item label="对象类型">
        <el-select v-model="application.targetType" @change="application.targetId = undefined">
          <el-option label="单个展项" value="item" /><el-option label="整个展区" value="zone" />
        </el-select>
      </el-form-item>
      <el-form-item label="对象 ID"><el-input-number v-model="application.targetId" :min="1" :precision="0" /></el-form-item>
      <el-form-item label="原因类别">
        <el-select v-model="application.reasonType">
          <el-option v-for="(label, index) in reasons" :key="label" :label="label" :value="index" />
        </el-select>
      </el-form-item>
      <el-form-item label="具体原因"><el-input v-model.trim="application.reason" type="textarea" maxlength="1000" /></el-form-item>
      <el-form-item label="影响范围"><el-input v-model.trim="application.scopeDesc" type="textarea" maxlength="500" /></el-form-item>
      <el-form-item><el-checkbox v-model="application.urgent">立即下架，事后审批</el-checkbox></el-form-item>
      <el-form-item><el-button type="primary" native-type="submit" :loading="submitting">提交申请</el-button></el-form-item>
    </el-form>
  </el-dialog>
  </div>
</template>

<script>
import BusinessRecords from '@/components/BusinessRecords'
import request from '@/utils/request'

export default {
  name: 'ExhibitOfflineAudit',
  components: { BusinessRecords },
  data() {
    return { applicationOpen: false, application: {}, submitting: false,
      reasons: ['内容错误', '版权投诉', '合规要求', '其他'], config: {
      title: '紧急下架审批', list: '/exh/offline/list', rowActions: true,
      columns: [
        { key: 'targetName', label: '申请对象' }, { key: 'applyByName', label: '申请人' },
        { key: 'reasonTypeName', label: '原因类别' }, { key: 'reason', label: '原因' },
        { key: 'scopeDesc', label: '影响范围' }, { key: 'applyTime', label: '申请时间' },
        { key: 'status', label: '状态', format: value => ['待审批', '已批准', '已驳回', '先下后批'][value] },
        { key: 'auditOpinion', label: '审批意见' }
      ]
    } }
  },
  methods: {
    openApplication() {
      this.application = { targetType: 'item', reasonType: 0, reason: '', scopeDesc: '', urgent: false }
      this.applicationOpen = true
    },
    async apply() {
      if (!this.application.targetId || !this.application.reason) return this.$modal.msgWarning('请填写对象 ID 和具体原因')
      if (this.application.urgent) {
        const approved = await this.$modal.confirm('此操作会立即下架所选对象。确认提交？').catch(() => false)
        if (approved === false) return
      }
      this.submitting = true
      try {
        await request({ url: '/exh/offline/apply', method: 'post', data: this.application })
        this.applicationOpen = false
        await this.$refs.records.load()
      } finally { this.submitting = false }
    },
    async decide(row, approve, reload) {
      const result = await this.$prompt('请填写审批意见', approve ? '同意下架' : '驳回申请', {
        inputValidator: value => (approve || !!value?.trim()) && (value || '').length <= 500 || '请填写有效意见（最多 500 字）'
      }).catch(() => null)
      if (!result) return
      await request({ url: '/exh/offline/' + row.reqId + '/approve', method: 'post', data: { approve, opinion: result.value } })
      await reload()
    }
  }
}
</script>
