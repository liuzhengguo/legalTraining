<template>
  <div class="app-container">
    <h2>账号授权</h2>
    <el-form inline @submit.native.prevent="load">
      <el-form-item label="用户 ID"><el-input-number v-model="userId" :min="2" :precision="0" /></el-form-item>
      <el-form-item><el-button type="primary" native-type="submit" :loading="loading">查询</el-button></el-form-item>
    </el-form>
    <el-form v-if="selectedId" label-width="125px">
      <el-form-item label="角色">
        <el-select v-model="roleIds" multiple filterable placeholder="选择角色">
          <el-option v-for="role in roles" :key="role.roleId" :label="role.roleName" :value="role.roleId" />
        </el-select>
      </el-form-item>
      <el-form-item label="待办交接人 ID"><el-input-number v-model="transferToUserId" :min="1" :precision="0" /></el-form-item>
      <el-form-item><el-button v-hasPermi="['edu:grant:edit']" type="primary" :loading="saving" @click="save">保存授权</el-button></el-form-item>
    </el-form>
  </div>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'EduGrant',
  data() {
    return { userId: undefined, selectedId: null, transferToUserId: undefined, roles: [], roleIds: [], loading: false, saving: false }
  },
  methods: {
    async load() {
      if (!this.userId) return this.$modal.msgWarning('请填写用户 ID')
      this.loading = true
      this.selectedId = null
      const id = this.userId
      try {
        const { data } = await request({ url: '/edu/grant/user-role/' + id })
        this.roles = data
        this.roleIds = data.filter(role => role.flag).map(role => role.roleId)
        this.selectedId = id
      } finally { this.loading = false }
    },
    async save() {
      if (this.userId !== this.selectedId) return this.$modal.msgWarning('请先查询当前用户')
      const approved = await this.$modal.confirm('确认覆盖该账号的角色授权？').catch(() => false)
      if (approved === false) return
      this.saving = true
      try {
        await request({ url: '/edu/grant/user-role', method: 'put', params: {
          userId: this.selectedId, roleIds: this.roleIds.join(','), transferToUserId: this.transferToUserId
        } })
        this.$modal.msgSuccess('角色授权已保存')
        await this.load()
      } finally { this.saving = false }
    }
  }
}
</script>
