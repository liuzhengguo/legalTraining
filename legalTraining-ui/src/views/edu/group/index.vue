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
    return { choices: { classes: [], students: [], crossClass: false }, original: {} }
  },
  computed: {
    config() {
      return {
        title: '分组管理', base: '/edu/group', permission: 'edu:group', id: 'groupId',
        paged: true, remove: true, defaults: { memberIds: [] }, beforeOpen: this.loadOptions,
        filters: [{ key: 'groupName', label: '组名' }],
        prepare: form => ({
          ...form,
          leaderId: form.memberIds.includes(form.leaderId) ? form.leaderId : null
        }),
        fields: [
          { key: 'groupName', label: '组名', required: true },
          {
            key: 'classId', label: '班级', required: true, placeholder: '搜索班级名称或学期',
            options: this.classOptions, change: this.changeClass
          },
          {
            key: 'memberIds', label: '成员', multiple: true, placeholder: '搜索学生姓名或账号',
            disabled: form => !form.classId, options: this.memberOptions, change: this.keepValidLeader,
            read: row => {
              this.original = row
              return row.memberIds || (row.members || []).map(member => member.userId)
            }
          },
          {
            key: 'leaderId', label: '组长', placeholder: '从已选成员中选择',
            disabled: form => !form.memberIds?.length,
            options: form => this.memberOptions(form).filter(option => form.memberIds?.includes(option.value)),
            read: row => row.leaderId || null
          }
        ],
        columns: [
          { key: 'groupName', label: '组名' },
          { key: 'className', label: '班级' },
          { key: 'leaderName', label: '组长' }
        ]
      }
    }
  },
  methods: {
    async loadOptions() {
      const { data } = await request({ url: '/edu/group/editor-options' })
      this.choices = data
      this.original = {}
    },
    classOptions(form) {
      return this.choices.classes.filter(entry => entry.status === 0 || entry.value === form.classId)
        .map(entry => ({ value: entry.value, label: [entry.name, entry.term].filter(Boolean).join(' · ') }))
    },
    memberOptions(form) {
      if (!form.classId) return []
      const options = new Map()
      for (const student of this.choices.students) {
        if (!this.choices.crossClass && student.classId !== form.classId) continue
        if (!options.has(student.value)) {
          const name = student.name || student.account
          options.set(student.value, {
            value: student.value,
            label: [name, student.account === name ? '' : student.account, this.choices.crossClass && student.className]
              .filter(Boolean).join(' · ')
          })
        }
      }
      if (form.classId === this.original.classId) {
        for (const member of this.original.members || []) {
          if (form.memberIds.includes(member.userId) && !options.has(member.userId)) {
            options.set(member.userId, { value: member.userId, label: member.studentName || '原成员（资料不可用）' })
          }
        }
      }
      return Array.from(options.values())
    },
    changeClass(form) {
      if (!this.choices.crossClass) form.memberIds = []
      this.keepValidLeader(form)
    },
    keepValidLeader(form) {
      if (!form.memberIds.includes(form.leaderId)) form.leaderId = null
    },
    async crossClass() {
      const { data } = await request({ url: '/edu/group/cross-class' })
      this.$alert(data ? '跨班分组已开启' : '跨班分组已关闭，可在系统参数 biz.group.crossClass 中设置。', '跨班分组')
    }
  }
}
</script>
