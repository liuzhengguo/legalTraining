<template>
  <div>
  <BusinessRecords ref="records" :config="config">
    <template #toolbar><el-button type="primary" @click="openApplication">发起下架申请</el-button></template>
    <template #column-targetName="{ row }">{{ targetLabel(row.targetType, row) }}</template>
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
      <el-form-item label="下架对象" required>
        <el-select v-model="application.targetId" filterable clearable :loading="loadingTargets"
          :placeholder="application.targetType === 'zone' ? '搜索并选择开放展区' : '搜索并选择已发布展项'"
          :no-data-text="targetsFailed ? '加载失败，请重新打开申请' : '暂无可选对象'" style="width: 100%">
          <el-option v-for="target in targetOptions" :key="target.targetId" :value="target.targetId"
            :label="targetLabel(application.targetType, target)" />
        </el-select>
      </el-form-item>
      <el-form-item label="原因类别">
        <el-select v-model="application.reasonType">
          <el-option v-for="(label, index) in reasons" :key="label" :label="label" :value="index" />
        </el-select>
      </el-form-item>
      <el-form-item label="具体原因"><el-input v-model.trim="application.reason" type="textarea" maxlength="1000" /></el-form-item>
      <el-form-item label="影响范围"><el-input v-model.trim="application.scopeDesc" type="textarea" maxlength="500" /></el-form-item>
      <el-form-item><el-checkbox v-model="application.urgent">立即下架，事后审批</el-checkbox></el-form-item>
      <el-form-item><el-button type="primary" native-type="submit" :loading="submitting" :disabled="!selectedTarget">提交申请</el-button></el-form-item>
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
      targets: {}, loadingTargets: false, targetsFailed: false,
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
  computed: {
    targetOptions() { return this.targets[this.application.targetType] || [] },
    selectedTarget() { return this.targetOptions.find(target => target.targetId === this.application.targetId) }
  },
  methods: {
    targetLabel(type, target) {
      const kind = type === 'zone' ? '展区' : '展项'
      const name = target.targetName || '名称不可用'
      return [kind, target.zoneName, name + '（#' + target.targetId + '）'].filter(Boolean).join(' · ')
    },
    async openApplication() {
      if (this.loadingTargets || this.submitting) return
      this.application = { targetType: 'item', targetId: undefined, reasonType: 0, reason: '', scopeDesc: '', urgent: false }
      this.targets = {}
      this.targetsFailed = false
      this.applicationOpen = true
      this.loadingTargets = true
      try {
        const { data } = await request({ url: '/exh/offline/targets' })
        this.targets = data
      } catch {
        this.targetsFailed = true
      } finally {
        this.loadingTargets = false
      }
    },
    async apply() {
      if (this.submitting) return
      if (!this.selectedTarget || !this.application.reason) return this.$modal.msgWarning('请选择下架对象并填写具体原因')
      const application = { ...this.application }
      this.submitting = true
      try {
        if (application.urgent) {
          const target = this.targetLabel(application.targetType, this.selectedTarget)
          const approved = await this.$modal.confirm('确认立即下架「' + target + '」并提交审批？').catch(() => false)
          if (approved === false) return
        }
        await request({ url: '/exh/offline/apply', method: 'post', data: application })
        this.applicationOpen = false
        await this.$refs.records.load()
      } finally { this.submitting = false }
    },
    async decide(row, approve, reload) {
      const result = await this.$prompt('申请对象：' + this.targetLabel(row.targetType, row) + '。请填写审批意见。', approve ? '同意下架' : '驳回申请', {
        inputValidator: value => (approve || !!value?.trim()) && (value || '').length <= 500 || '请填写有效意见（最多 500 字）'
      }).catch(() => null)
      if (!result) return
      await request({ url: '/exh/offline/' + row.reqId + '/approve', method: 'post', data: { approve, opinion: result.value } })
      await reload()
    }
  }
}
</script>
