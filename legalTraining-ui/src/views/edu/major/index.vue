<template>
  <BusinessRecords :config="config">
    
    
  </BusinessRecords>
</template>

<script>
import BusinessRecords from '@/components/BusinessRecords'


export default {
  name: 'EduMajor',
  components: { BusinessRecords },
  data() {
    return { config: {
      title: '专业管理', base: '/edu/major', permission: 'edu:major', id: 'majorId',
      paged: true, remove: true, rowActions: false, defaults: {"status":"0"},
      
      filters: [{"key":"majorName","label":"专业名称"},{"key":"majorCode","label":"专业编码"}],
      fields: [
        { key: 'majorCode', label: '专业编码', required: true },
        { key: 'majorName', label: '专业名称', required: true },
        { key: 'deptId', label: '院系 ID', required: true, number: true },
        { key: 'degreeType', label: '学位类型' },
        { key: 'status', label: '状态', required: true, options: [{"label":"启用","value":"0"},{"label":"停用","value":"1"}] }
      ],
      columns: [{"key":"majorId","label":"ID"},{"key":"majorCode","label":"专业编码"},{"key":"majorName","label":"专业名称"},{"key":"degreeType","label":"学位类型"},{"key":"status","label":"状态"},{"key":"deptName","label":"院系"}]
    } }
  }
}
</script>
