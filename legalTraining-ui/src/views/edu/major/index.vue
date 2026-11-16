<template>
  <BusinessRecords :config="config" />
</template>

<script>
import BusinessRecords from '@/components/BusinessRecords'
import request from '@/utils/request'

export default {
  name: 'EduMajor',
  components: { BusinessRecords },
  data() {
    return { departments: [], currentDepartment: null }
  },
  created() {
    request({ url: '/edu/major/dept-options' }).then(({ data }) => {
      this.departments = data
    }).catch(() => {
      // The request interceptor displays the load error.
    })
  },
  computed: {
    config() {
      const options = this.departments.slice()
      if (this.currentDepartment && !options.some(option => option.value === this.currentDepartment.value)) {
        options.push({ ...this.currentDepartment, disabled: true })
      }
      return {
        title: '专业管理', base: '/edu/major', permission: 'edu:major', id: 'majorId',
        paged: true, remove: true, rowActions: false, defaults: {"status":"0"},

        filters: [{"key":"majorName","label":"专业名称"},{"key":"majorCode","label":"专业编码"}],
        fields: [
          { key: 'majorCode', label: '专业编码', required: true },
          { key: 'majorName', label: '专业名称', required: true },
          { key: 'deptId', label: '院系', required: true, options, read: this.readDepartment },
          { key: 'degreeType', label: '学位类型' },
          { key: 'status', label: '状态', required: true, options: [{"label":"启用","value":"0"},{"label":"停用","value":"1"}] }
        ],
        columns: [{"key":"majorId","label":"ID"},{"key":"majorCode","label":"专业编码"},{"key":"majorName","label":"专业名称"},{"key":"degreeType","label":"学位类型"},{"key":"status","label":"状态"},{"key":"deptName","label":"院系"}]
      }
    }
  },
  methods: {
    readDepartment(row) {
      this.currentDepartment = row.deptId == null ? null : {
        value: row.deptId, label: row.deptName || '院系（' + row.deptId + '）'
      }
      return row.deptId
    }
  }
}
</script>
