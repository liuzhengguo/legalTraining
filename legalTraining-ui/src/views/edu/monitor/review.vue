<template><BusinessRecords :config="config" /></template>

<script>
import BusinessRecords from '@/components/BusinessRecords'

export default {
  name: 'ReviewMonitor',
  components: { BusinessRecords },
  data() {
    return { config: {
      title: '批阅进度', list: '/edu/monitor/review',
      filters: [{ key: 'courseId', label: '课程 ID' }],
      columns: [
        { key: 'courseName', label: '课程' },
        { key: 'teacherName', label: '教师' },
        { key: 'pendingCount', label: '待批' },
        { key: 'returnedCount', label: '已退回' },
        { key: 'overdueCount', label: '逾期未批' },
        { key: 'avgReviewHours', label: '平均批阅时长（小时）' }
      ]
    } }
  }
}
</script>
