<template>
  <BusinessRecords :config="config">
    
    <template #actions="{ row, reload }"><el-button v-hasPermi="['edu:class:edit']" type="text" @click="changeStatus(row, reload)">{{ row.status === 0 ? '归档' : '启用' }}</el-button></template>
  </BusinessRecords>
</template>

<script>
import BusinessRecords from '@/components/BusinessRecords'
import request from '@/utils/request'

export default {
  name: 'EduClass',
  components: { BusinessRecords },
  data() {
    return { config: {
      title: '班级管理', base: '/edu/class', permission: 'edu:class', id: 'classId',
      paged: true, remove: true, rowActions: true, defaults: {},
      
      filters: [{"key":"className","label":"班级名称"},{"key":"classCode","label":"班级编码"}],
      fields: [
        { key: 'classCode', label: '班级编码', required: true },
        { key: 'className', label: '班级名称', required: true },
        { key: 'termId', label: '学期 ID', required: true, number: true },
        { key: 'deptId', label: '院系 ID', required: true, number: true },
        { key: 'majorId', label: '专业 ID', number: true },
        { key: 'gradeYear', label: '入学年份', number: true },
        { key: 'headTeacher', label: '任课教师 ID', number: true }
      ],
      columns: [{"key":"classId","label":"ID"},{"key":"classCode","label":"班级编码"},{"key":"className","label":"班级名称"},{"key":"termName","label":"学期"},{"key":"deptName","label":"院系"},{"key":"majorName","label":"专业"},{"key":"headTeacherName","label":"任课教师"},{"key":"studentCount","label":"在册人数"},{"key":"status","label":"状态"}]
    } }
  },
  methods: {
    async changeStatus(row, reload) {
      const status = row.status === 0 ? 1 : 0
      await this.$modal.confirm(status ? '确认归档该班级？' : '确认启用该班级？')
      await request({ url: '/edu/class/' + row.classId + '/status', method: 'put', params: { status } })
      await reload()
    }
  }
}
</script>
