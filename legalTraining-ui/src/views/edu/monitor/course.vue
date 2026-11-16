<template><BusinessRecords :config="config" /></template>

<script>
import BusinessRecords from '@/components/BusinessRecords'

export default {
  name: 'CourseMonitor',
  components: { BusinessRecords },
  data() {
    return { config: {
      title: '课程任务总览', list: '/edu/monitor/course',
      filters: [{ key: 'termId', label: '学期 ID' }],
      columns: [
        { key: 'courseName', label: '课程' },
        { key: 'termName', label: '学期' },
        { key: 'taskCount', label: '任务数' },
        { key: 'runningCount', label: '进行中' },
        { key: 'expectedCount', label: '应交' },
        { key: 'submittedCount', label: '已交' },
        { key: 'reviewedCount', label: '已批' },
        { key: 'submitRate', label: '提交率 (%)' },
        { key: 'reviewRate', label: '批阅率 (%)' },
        { key: 'avgScore', label: '平均分' }
      ]
    } }
  }
}
</script>
