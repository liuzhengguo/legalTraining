<template>
  <BusinessRecords :config="config" />
</template>

<script>
import BusinessRecords from '@/components/BusinessRecords'
import request from '@/utils/request'

const statuses = [{ label: '启用', value: '0' }, { label: '停用', value: '1' }]

export default {
  name: 'EduTeacher',
  components: { BusinessRecords },
  data() {
    return { departments: [], users: [] }
  },
  computed: {
    config() {
      return {
        title: '教师管理', base: '/edu/teacher', permission: 'edu:teacher', id: 'userId',
        paged: true, beforeOpen: this.loadUsers,
        filters: [{ key: 'userName', label: '登录账号' }],
        createFields: [
          { key: 'linkedUserId', label: '登录账号', required: true, options: this.users, placeholder: '搜索姓名或登录账号' }
        ],
        fields: [
          { key: 'userName', label: '登录账号', required: true, maxlength: 20, disabled: form => !!form.userId },
          { key: 'nickName', label: '教师姓名', required: true, maxlength: 30 },
          { key: 'deptId', label: '院系', required: true, options: this.departments },
          { key: 'status', label: '状态', required: true, options: statuses },
          { key: 'password', label: '密码', type: 'password', maxlength: 20, placeholder: '留空则保留原密码' }
        ],
        columns: [
          { key: 'userName', label: '登录账号' },
          { key: 'nickName', label: '教师姓名' },
          { key: 'deptId', label: '院系', format: id => this.departments.find(dept => dept.value === id)?.label || '—' },
          { key: 'status', label: '状态', format: value => statuses.find(status => status.value === value)?.label }
        ]
      }
    }
  },
  async created() {
    const { data } = await request({ url: '/edu/teacher/dept-options' })
    this.departments = data
  },
  methods: {
    async loadUsers(row) {
      if (!row) {
        const { data } = await request({ url: '/edu/teacher/user-options' })
        this.users = data
      }
    }
  }
}
</script>
