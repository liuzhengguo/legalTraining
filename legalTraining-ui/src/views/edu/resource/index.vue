<template>
  <div>
  <BusinessRecords :config="config">
    <template #toolbar><el-button @click="categoriesOpen = true">资源分类</el-button></template>
    <template #form="{ form }">
      <el-form-item v-if="form.resType && form.resType !== 'link'" label="资源文件">
        <input type="file" :disabled="uploading" @change="upload($event, form)">
        <span v-if="uploading">上传中…</span>
      </el-form-item>
    </template>
    <template #actions="{ row, reload }"><el-button v-hasPermi="['edu:resource:edit']" type="text" @click="changeStatus(row, reload)">{{ row.status === 0 ? '下架' : '上架' }}</el-button>
      <el-button v-hasPermi="['edu:resource:query']" type="text" @click="openResource(row)">查看资源</el-button></template>
  </BusinessRecords>
  <el-dialog title="资源分类" :visible.sync="categoriesOpen" width="min(760px, 95vw)" destroy-on-close>
    <BusinessRecords v-if="categoriesOpen" :config="categoryConfig" />
  </el-dialog>
  <el-dialog title="资源预览" :visible.sync="previewOpen" width="min(860px, 95vw)" @closed="closePreview">
    <img v-if="previewType.startsWith('image/')" :src="previewUrl" alt="资源图片" style="max-width:100%">
    <audio v-else-if="previewType.startsWith('audio/')" :src="previewUrl" controls />
    <video v-else-if="previewType.startsWith('video/')" :src="previewUrl" controls style="max-width:100%" />
    <iframe v-else-if="previewUrl" :src="previewUrl" title="文档预览" style="width:100%;height:60vh" />
  </el-dialog>
  </div>
</template>

<script>
import BusinessRecords from '@/components/BusinessRecords'
import request from '@/utils/request'

export default {
  name: 'EduResource',
  components: { BusinessRecords },
  data() {
    return { uploading: false, categoriesOpen: false, previewOpen: false, previewUrl: '', previewType: '', categoryConfig: {
      title: '资源分类维护', base: '/edu/resource/category', list: '/edu/resource/category/tree',
      permission: 'edu:resource', id: 'categoryId', remove: true, tree: true,
      defaults: { parentId: 0, orderNum: 0, status: '0' },
      fields: [
        { key: 'categoryName', label: '分类名称', required: true },
        { key: 'parentId', label: '上级分类 ID', number: true },
        { key: 'orderNum', label: '排序', number: true },
        { key: 'status', label: '状态', options: [{ label: '启用', value: '0' }, { label: '停用', value: '1' }] }
      ],
      columns: [
        { key: 'categoryName', label: '分类名称' }, { key: 'categoryId', label: '分类 ID' },
        { key: 'orderNum', label: '排序' }, { key: 'resourceCount', label: '资源数' }
      ]
    }, config: {
      title: '资源管理', base: '/edu/resource', permission: 'edu:resource', id: 'resourceId',
      paged: true, remove: true, rowActions: true, defaults: {"resType":"link","visibleScope":1,"allowDownload":"0"},
      saveDisabled: () => this.uploading,
      
      filters: [{"key":"title","label":"名称"}],
      fields: [
        { key: 'title', label: '名称', required: true },
        { key: 'resType', label: '类型', required: true, options: [{"label":"外链","value":"link"},{"label":"文档","value":"doc"},{"label":"视频","value":"video"},{"label":"音频","value":"audio"},{"label":"图片","value":"image"}] },
        { key: 'categoryId', label: '分类 ID', number: true },
        { key: 'externalUrl', label: '外链地址' },
        { key: 'fileId', label: '已上传文件标识' },
        { key: 'visibleScope', label: '可见范围', required: true, options: [{"label":"课程","value":1},{"label":"班级","value":2},{"label":"全校","value":3}] },
        { key: 'allowDownload', label: '允许下载', required: true, options: [{"label":"允许","value":"1"},{"label":"禁止","value":"0"}] }
      ],
      columns: [{"key":"resourceId","label":"ID"},{"key":"title","label":"名称"},{"key":"resType","label":"类型"},{"key":"categoryName","label":"分类"},{"key":"status","label":"状态"},{"key":"refCount","label":"引用次数"}]
    } }
  },
  methods: {
    async upload(event, form) {
      const file = event.target.files[0]
      if (!file || this.uploading) return
      const data = new FormData()
      data.append('file', file)
      this.uploading = true
      try {
        const response = await request({ url: '/common/upload', method: 'post',
          headers: { 'Content-Type': 'multipart/form-data', repeatSubmit: false }, data })
        this.$set(form, 'fileId', response.fileName)
        this.$set(form, 'fileName', file.name)
        this.$set(form, 'fileSize', file.size)
        this.$set(form, 'fileExt', file.name.includes('.') ? file.name.split('.').pop().toLowerCase() : '')
      } finally { this.uploading = false }
    },
    async changeStatus(row, reload) {
      const status = row.status === 0 ? 1 : 0
      await request({ url: '/edu/resource/' + row.resourceId + '/status', method: 'put', params: { status } })
      await reload()
    },
    async openResource(row) {
      const url = '/edu/resource/' + row.resourceId
      if (row.resType === 'link') {
        const { data } = await request({ url })
        if (/^https?:\/\//i.test(data.externalUrl)) window.open(data.externalUrl, '_blank', 'noopener,noreferrer')
        else this.$modal.msgWarning('暂无可访问的外链')
        return
      }
      const blob = await request({ url: url + '/preview', responseType: 'blob' })
      if (blob.type.includes('json')) {
        const result = JSON.parse(await blob.text())
        return this.$modal.msgError(result.msg || '预览失败')
      }
      this.closePreview()
      this.previewUrl = URL.createObjectURL(blob)
      this.previewType = blob.type
      this.previewOpen = true
    },
    closePreview() {
      if (this.previewUrl) URL.revokeObjectURL(this.previewUrl)
      this.previewUrl = ''
      this.previewType = ''
    }
  },
  beforeDestroy() { this.closePreview() }
}
</script>

