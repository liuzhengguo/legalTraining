<template>
  <div v-loading="loading" class="app-container">
    <h2>我的待办</h2>
    <p v-if="todo">{{ todo.message }}</p>
    <el-table v-if="todo" :data="todo.labels.filter(item => todo.counts[item.key] > 0)">
      <el-table-column label="待办事项" prop="name" />
      <el-table-column label="说明" prop="desc" />
      <el-table-column label="数量">
        <template slot-scope="{ row }">{{ todo.counts[row.key] }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right" align="right">
        <template slot-scope="{ row }">
          <router-link v-if="row.key === 'review' && can('edu:review:list') || row.key === 'audit' && can('exh:audit:list')" :to="row.path">处理</router-link>
          <a v-else-if="studentUrl(row.key)" :href="studentUrl(row.key)">打开学生端</a>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>
import request from '@/utils/request'
import auth from '@/plugins/auth'

export default {
  name: 'PortalTodo',
  data() { return { todo: null, loading: false } },
  async created() {
    this.loading = true
    try { this.todo = (await request({ url: '/portal/todo' })).data } finally { this.loading = false }
  },
  methods: {
    can: auth.hasPermi,
    studentUrl(key) {
      const path = { submit: '/course', authorize: '/achievements' }[key]
      const base = process.env.VUE_APP_STUDENT_URL || (process.env.NODE_ENV === 'development' ? 'http://localhost:5173' : '')
      return base && path ? base.replace(/\/$/, '') + path : ''
    }
  }
}
</script>
