<template>
  <BusinessRecords :config="config">
    <template #actions="{ row, reload }">
      <el-select :value="row.openLevel" size="small" :disabled="saving" @change="setLevel(row, $event, reload)">
        <el-option v-for="(label, level) in levels" :key="level" :label="label" :value="level" />
      </el-select>
    </template>
  </BusinessRecords>
</template>

<script>
import BusinessRecords from '@/components/BusinessRecords'
import request from '@/utils/request'

const levels = ['关闭', '内部开放', '对外开放']

export default {
  name: 'ZoneOpenLevels',
  components: { BusinessRecords },
  data() {
    return { levels, saving: false, config: {
      title: '展区开放配置', list: '/exh/zone/list', rowActions: true,
      columns: [
        { key: 'zoneCode', label: '展区编号' },
        { key: 'zoneName', label: '展区名称' },
        { key: 'openLevel', label: '开放级别', format: value => levels[value] }
      ]
    } }
  },
  methods: {
    async setLevel(row, level, reload) {
      const confirmed = await this.$modal.confirm('确认将展区「' + row.zoneName + '」设为' + levels[level] + '？').catch(() => false)
      if (confirmed === false) return
      this.saving = true
      try {
        await request({ url: '/exh/zone/' + row.zoneId + '/level', method: 'put', params: { level } })
        await reload()
      } finally { this.saving = false }
    }
  }
}
</script>
