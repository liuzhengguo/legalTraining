<template>
  <BusinessRecords :config="config">
    <template #toolbar><el-button @click="crossClass">查看跨班分组设置</el-button></template>
    
  </BusinessRecords>
</template>

<script>
import BusinessRecords from '@/components/BusinessRecords'
import request from '@/utils/request'

export default {
  name: 'EduGroup',
  components: { BusinessRecords },
  data() {
    return { config: {
      title: '分组管理', base: '/edu/group', permission: 'edu:group', id: 'groupId',
      paged: true, remove: true, rowActions: false, defaults: {},
      prepare: form => ({ ...form, memberIds: form.memberIds ? form.memberIds.split(/[,，]/).map(value => Number(value.trim())) : [] }),
      filters: [{"key":"groupName","label":"组名"}],
      fields: [
        { key: 'groupName', label: '组名', required: true },
        { key: 'classId', label: '班级 ID', required: true, number: true },
        { key: 'leaderId', label: '组长账号 ID', number: true },
        { key: 'memberIds', label: '成员账号 ID（逗号分隔）', read: row => (row.memberIds || (row.members || []).map(member => member.userId)).join(', ') }
      ],
      columns: [{"key":"groupId","label":"ID"},{"key":"groupName","label":"组名"},{"key":"className","label":"班级"},{"key":"leaderName","label":"组长"}]
    } }
  },
  methods: {
    async crossClass() {
      const { data } = await request({ url: '/edu/group/cross-class' })
      this.$alert(data ? '跨班分组已开启' : '跨班分组已关闭，可在系统参数 biz.group.crossClass 中设置。', '跨班分组')
    }
  }
}
</script>

