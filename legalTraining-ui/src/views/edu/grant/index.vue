<template>
  <div class="app-container">
    <h2>账号授权</h2>
    <el-input v-model.trim="search" clearable class="account-search" placeholder="搜索姓名或登录账号" aria-label="搜索账号" />
    <el-table v-loading="loading" :data="visibleAccounts" row-key="userId" empty-text="暂无符合条件的账号">
      <el-table-column prop="userName" label="登录账号" min-width="130" />
      <el-table-column prop="nickName" label="姓名" min-width="120" />
      <el-table-column prop="deptName" label="院系" min-width="180" />
      <el-table-column label="角色" min-width="160" show-overflow-tooltip>
        <template slot-scope="{ row }">
          {{ row.roles && row.roles.length ? row.roles.map(role => role.roleName).join('、') : '未分配角色' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template slot-scope="{ row }">
          <el-tag size="small" :type="row.status === '0' ? 'success' : 'danger'">{{ row.status === '0' ? '已启用' : '已停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" fixed="right" width="110" class-name="nowrap-actions">
        <template slot-scope="{ row }"><el-button type="text" @click="loadRoles(row)">查看授权</el-button></template>
      </el-table-column>
    </el-table>
    <pagination v-show="filteredAccounts.length > pageSize" :total="filteredAccounts.length" :page.sync="page" :limit.sync="pageSize" />

    <el-dialog :title="selected ? '账号授权 · ' + selected.userName : '账号授权'" :visible.sync="dialog" append-to-body width="min(560px, 94vw)">
      <el-form v-loading="loadingRoles" label-width="100px" @submit.native.prevent="save">
        <el-form-item label="角色">
          <el-select v-model="roleIds" multiple filterable :disabled="!canEdit" placeholder="选择角色" style="width: 100%">
            <el-option v-for="role in roles" :key="role.roleId" :label="role.roleName" :value="role.roleId" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="canEdit && needsReceiver" label="待办交接人">
          <el-select v-model="transferToUserId" clearable filterable placeholder="搜索姓名或账号" style="width: 100%">
            <el-option v-for="account in receivers" :key="account.userId"
              :label="[account.nickName, account.userName].filter(Boolean).join(' · ')" :value="account.userId" />
          </el-select>
        </el-form-item>
        <el-button v-if="canEdit" type="primary" native-type="submit" :loading="saving" :disabled="loadingRoles || !ready">保存授权</el-button>
      </el-form>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'
import { checkPermi } from '@/utils/permission'

export default {
  name: 'EduGrant',
  data() {
    return {
      accounts: [], search: '', page: 1, pageSize: 20, selected: null, transferToUserId: undefined,
      roles: [], roleIds: [], loading: false, loadingRoles: false, saving: false, dialog: false, ready: false
    }
  },
  computed: {
    canEdit() { return checkPermi(['edu:grant:edit']) },
    filteredAccounts() {
      const search = this.search.toLowerCase()
      return this.accounts.filter(account => [account.userName, account.nickName].some(value => value?.toLowerCase().includes(search)))
    },
    visibleAccounts() {
      const start = (this.page - 1) * this.pageSize
      return this.filteredAccounts.slice(start, start + this.pageSize)
    },
    needsReceiver() {
      return this.roles.some(role => role.flag && ['teacher', 'auditor'].includes(role.roleKey) && !this.roleIds.includes(role.roleId))
    },
    receivers() {
      return this.accounts.filter(account => account.status === '0' && account.userId !== this.selected?.userId)
    }
  },
  watch: {
    search() { this.page = 1 }
  },
  created() {
    return this.loadAccounts()
  },
  methods: {
    async loadAccounts() {
      this.loading = true
      try {
        const { data } = await request({ url: '/edu/grant/accounts' })
        this.accounts = data
      } finally { this.loading = false }
    },
    async loadRoles(account) {
      this.selected = account
      this.roles = []
      this.roleIds = []
      this.transferToUserId = undefined
      this.ready = false
      this.loadingRoles = true
      this.dialog = true
      try {
        const { data } = await request({ url: '/edu/grant/user-role/' + account.userId })
        if (this.selected !== account) return
        this.roles = data
        this.roleIds = data.filter(role => role.flag).map(role => role.roleId)
        this.ready = true
      } finally {
        if (this.selected === account) this.loadingRoles = false
      }
    },
    async save() {
      if (!this.ready || this.saving) return
      const account = this.selected
      const approved = await this.$modal.confirm('确认保存「' + account.userName + '」的角色授权？').catch(() => false)
      if (approved === false || this.selected !== account || !this.dialog) return
      this.saving = true
      try {
        const { data } = await request({ url: '/edu/grant/user-role', method: 'put', params: {
          userId: account.userId, roleIds: this.roleIds.join(','),
          transferToUserId: this.needsReceiver ? this.transferToUserId : undefined
        } })
        this.dialog = false
        if (data.warnings?.length) this.$alert(data.warnings.join('\n'), '角色授权已保存')
        else this.$modal.msgSuccess('角色授权已保存')
        await this.loadAccounts()
      } finally { this.saving = false }
    }
  }
}
</script>

<style scoped>
.account-search { width: min(100%, 290px); margin-bottom: 14px; }
</style>
