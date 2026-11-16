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
    return { options: {}, original: {} }
  },
  computed: {
    config() {
      return {
        title: '班级管理', base: '/edu/class', permission: 'edu:class', id: 'classId',
        paged: true, remove: true, rowActions: true, defaults: {}, beforeOpen: this.loadEditorOptions,

        filters: [{"key":"className","label":"班级名称"},{"key":"classCode","label":"班级编码"}],
        fields: [
          { key: 'classCode', label: '班级编码', required: true },
          { key: 'className', label: '班级名称', required: true },
          this.selectField('termId', 'termName', '学期', true),
          { ...this.selectField('deptId', 'deptName', '院系', true), change: form => { form.majorId = null } },
          this.selectField('majorId', 'majorName', '专业'),
          { key: 'gradeYear', label: '入学年份', number: true },
          this.selectField('headTeacher', 'headTeacherName', '任课教师')
        ],
        prepare: form => ({
          ...form,
          majorId: form.majorId || (form.classId ? 0 : null),
          headTeacher: form.headTeacher || (form.classId ? 0 : null)
        }),
        columns: [{"key":"classId","label":"ID"},{"key":"classCode","label":"班级编码"},{"key":"className","label":"班级名称"},{"key":"termName","label":"学期"},{"key":"deptName","label":"院系"},{"key":"majorName","label":"专业"},{"key":"headTeacherName","label":"任课教师"},{"key":"studentCount","label":"在册人数"},{"key":"status","label":"状态"}]
      }
    }
  },
  methods: {
    async loadEditorOptions() {
      const { data } = await request({ url: '/edu/class/editor-options' })
      this.options = data
    },
    selectField(key, nameKey, label, required = false) {
      return {
        key, label, required, placeholder: '选择' + label,
        read: row => {
          this.original = row
          return row[key] || null
        },
        options: form => {
          const values = (this.options[key] || []).filter(option => key !== 'majorId' || option.deptId === form.deptId)
          const previous = this.original[key]
          if (previous && form[key] === previous && !values.some(option => option.value === previous)) {
            values.push({ value: previous, label: this.original[nameKey] || '原' + label + '不可用', disabled: true })
          }
          return values
        }
      }
    },
    async changeStatus(row, reload) {
      const status = row.status === 0 ? 1 : 0
      await this.$modal.confirm(status ? '确认归档该班级？' : '确认启用该班级？')
      await request({ url: '/edu/class/' + row.classId + '/status', method: 'put', params: { status } })
      await reload()
    }
  }
}
</script>
