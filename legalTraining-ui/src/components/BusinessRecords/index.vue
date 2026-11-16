<template>
  <div class="app-container">
    <h2>{{ config.title }}</h2>
    <el-form inline @submit.native.prevent="search">
      <el-form-item v-for="field in config.filters" :key="field.key" :label="field.label">
        <el-select v-if="field.options" v-model="query[field.key]" clearable filterable
          :placeholder="field.placeholder || '全部'" style="width:160px">
          <el-option v-for="option in resolveOptions(field, query)" :key="option.value"
            :label="option.label" :value="option.value" />
        </el-select>
        <el-cascader v-else-if="field.type === 'cascader'" v-model="query[field.key]"
          :options="resolveOptions(field, query)" :props="field.props || treeProps" :emit-path="false"
          :show-all-levels="false" clearable filterable :placeholder="field.placeholder || '全部'"
          :no-data-text="field.emptyText || '暂无数据'" style="width:200px" />
        <el-input v-else v-model.trim="query[field.key]" clearable />
      </el-form-item>
      <el-form-item><el-button native-type="submit" type="primary">查询</el-button></el-form-item>
    </el-form>
    <div class="record-actions">
      <el-button v-if="config.fields" v-hasPermi="[config.permission + ':add']" type="primary" @click="open()">新增</el-button>
      <slot name="toolbar" :reload="load" :query="query" />
    </div>
    <el-table v-loading="loading" :data="rows" :row-key="config.tree ? config.id : undefined"
      :tree-props="config.tree ? { children: 'children', hasChildren: 'hasChildren' } : undefined"
      :default-expand-all="config.tree === true">
      <el-table-column v-for="column in config.columns" :key="column.key" :label="column.label" :prop="column.key" min-width="110" show-overflow-tooltip>
        <template slot-scope="{ row }">
          <slot :name="'column-' + column.key" :row="row" :value="row[column.key]">
            {{ column.format ? column.format(row[column.key]) : row[column.key] }}
          </slot>
        </template>
      </el-table-column>
      <el-table-column v-if="config.fields || config.rowActions" label="操作" fixed="right" align="right" width="240">
        <template slot-scope="{ row }">
          <el-button v-if="config.fields && !config.createOnly" v-hasPermi="[config.permission + ':edit']" type="text" @click="open(row)">编辑</el-button>
          <el-button v-if="config.remove" v-hasPermi="[config.permission + ':remove']" type="text" @click="remove(row)">删除</el-button>
          <slot name="actions" :row="row" :reload="load" />
        </template>
      </el-table-column>
    </el-table>
    <pagination v-if="config.paged && total" :total="total" :page.sync="pageNum" :limit.sync="pageSize" @pagination="load" />
    <el-dialog :visible.sync="dialog" :title="config.title" width="min(560px, 95vw)" append-to-body>
      <el-form label-width="110px" @submit.native.prevent="save">
        <template v-for="field in editorFields">
          <el-form-item v-if="fieldVisible(field, form)" :key="field.key" :label="field.label"
            :required="fieldRequired(field, form)">
            <el-cascader v-if="field.type === 'cascader'" v-model="form[field.key]"
              :options="resolveOptions(field, form)" :props="field.props || treeProps" :emit-path="false"
              :show-all-levels="field.showAllLevels !== false" clearable filterable
              :placeholder="field.placeholder || '请选择'" :no-data-text="field.emptyText || '暂无数据'"
              style="width:100%"
              :disabled="field.disabled && field.disabled(form)" @change="field.change && field.change(form)" />
            <el-select v-else-if="field.options" v-model="form[field.key]" :placeholder="field.placeholder || '请选择'"
              filterable clearable :multiple="field.multiple" :disabled="field.disabled && field.disabled(form)"
              @change="field.change && field.change(form)">
              <el-option v-for="option in resolveOptions(field, form)" :key="option.value"
                :label="option.label" :value="option.value" :disabled="option.disabled" />
            </el-select>
            <el-input-number v-else-if="field.number" v-model="form[field.key]" :min="0" :precision="0" controls-position="right" />
            <el-input v-else-if="field.type === 'password'" v-model="form[field.key]" type="password" show-password
              autocomplete="new-password" :placeholder="field.placeholder" :maxlength="field.maxlength" />
            <el-input v-else v-model.trim="form[field.key]" :type="field.type || 'text'" :maxlength="field.maxlength"
              :disabled="field.disabled && field.disabled(form)" />
          </el-form-item>
        </template>
        <slot name="form" :form="form" />
        <el-button native-type="submit" type="primary" :loading="saving" :disabled="config.saveDisabled && config.saveDisabled()">保存</el-button>
      </el-form>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'BusinessRecords',
  props: { config: { type: Object, required: true } },
  data() {
    return { rows: [], query: {}, total: 0, pageNum: 1, pageSize: 20, loading: false, saving: false, dialog: false, form: {},
      treeProps: { value: 'value', label: 'label', children: 'children', checkStrictly: true } }
  },
  computed: {
    editorFields() {
      return this.form[this.config.id] == null && this.config.createFields ? this.config.createFields : this.config.fields
    }
  },
  created() { this.load() },
  methods: {
    resolveOptions(field, scope) {
      return typeof field.options === 'function' ? field.options(scope) : (field.options || [])
    },
    fieldVisible(field, scope) {
      if (field.hidden) return false
      return typeof field.visible !== 'function' || field.visible(scope)
    },
    fieldRequired(field, scope) {
      return typeof field.required === 'function' ? field.required(scope) : !!field.required
    },
    // 级联选择的值统一归一为末级 ID：兼容 emitPath 生效与否两种形态
    scalarValue(field, value) {
      if (field.type === 'cascader' && Array.isArray(value)) {
        return value.length ? value[value.length - 1] : null
      }
      return value
    },
    search() {
      this.pageNum = 1
      return this.load()
    },
    async load() {
      this.loading = true
      try {
        const params = { ...this.query }
        ;(this.config.filters || []).forEach(field => {
          params[field.key] = this.scalarValue(field, params[field.key])
        })
        if (this.config.paged) Object.assign(params, { pageNum: this.pageNum, pageSize: this.pageSize })
        const response = await request({ url: this.config.list || this.config.base + '/list', params })
        this.rows = response.rows || response.data || []
        this.total = response.total || 0
      } finally { this.loading = false }
    },
    async open(row) {
      if (this.config.beforeOpen) await this.config.beforeOpen(row)
      const form = { ...this.config.defaults }
      if (row) {
        const id = row[this.config.id]
        const response = await request({ url: this.config.base + '/' + id })
        form[this.config.id] = id
        this.config.fields.forEach(field => {
          form[field.key] = field.read ? field.read(response.data) : response.data[field.key]
        })
      }
      this.form = form
      this.dialog = true
    },
    async save() {
      const missing = this.editorFields.find(field => this.fieldVisible(field, this.form)
        && this.fieldRequired(field, this.form)
        && (this.form[field.key] == null || this.form[field.key] === ''
          || (Array.isArray(this.form[field.key]) && !this.form[field.key].length)))
      if (missing) return this.$modal.msgWarning('请填写' + missing.label)
      this.saving = true
      try {
        const payload = { ...this.form }
        this.editorFields.forEach(field => {
          payload[field.key] = this.scalarValue(field, payload[field.key])
        })
        const data = this.config.prepare ? this.config.prepare(payload) : payload
        await request({ url: this.config.base, method: this.form[this.config.id] == null ? 'post' : 'put', data })
        this.dialog = false
        this.$modal.msgSuccess('已保存')
        await this.load()
      } finally { this.saving = false }
    },
    async remove(row) {
      const approved = await this.$modal.confirm('确认删除这条记录？').catch(() => false)
      if (approved === false) return
      await request({ url: this.config.base + '/' + row[this.config.id], method: 'delete' })
      this.$modal.msgSuccess('已删除')
      await this.load()
    }
  }
}
</script>

<style scoped>
.record-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
</style>
