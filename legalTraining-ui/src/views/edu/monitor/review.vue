<template><BusinessRecords :config="config" /></template>

<script>
import BusinessRecords from '@/components/BusinessRecords'

function formatReviewStatus(count, present, absent) {
  if (count == null || count === '') return '未知'
  return Number(count) > 0 ? present : absent
}

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
        { key: 'pendingCount', label: '待批', format: count => formatReviewStatus(count, '有待批阅', '无待批阅') },
        { key: 'returnedCount', label: '已退回', format: count => formatReviewStatus(count, '有退回', '无退回') },
        { key: 'overdueCount', label: '逾期未批', format: count => formatReviewStatus(count, '有逾期未批', '无逾期未批') },
        { key: 'avgReviewHours', label: '平均批阅时长（小时）' }
      ]
    } }
  }
}
</script>
