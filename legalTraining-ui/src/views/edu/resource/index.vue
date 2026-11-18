<template>
  <div>
    <el-alert type="info" :closable="false" show-icon class="biz-tip"
      title="业务说明：分类按教学主题（如案例、文书、法规）维护层级，类型表示文件形态；上传后自动记录文件信息，无需填写内部标识。" />
    <BusinessRecords :config="config">
      <template #toolbar><el-button @click="categoriesOpen = true">资源分类</el-button></template>
      <template #form="{ form }">
        <el-form-item v-if="!categoryTree.length" label="">
          <el-alert type="warning" :closable="false" show-icon>
            <template slot="title">暂无资源分类，<el-button type="text" @click="openCategories">点此新增分类</el-button></template>
          </el-alert>
        </el-form-item>
        <el-form-item v-if="form.resType && form.resType !== 'link'" label="资源文件">
          <input type="file" :disabled="uploading" @change="upload($event, form)">
          <span v-if="uploading">上传中…</span>
          <div v-if="form.fileName" class="file-hint">已选择：{{ form.fileName }}<span v-if="form.fileSize">（{{ formatSize(form.fileSize) }}）</span></div>
        </el-form-item>
      </template>
      <template #actions="{ row, reload }">
        <el-button v-hasPermi="['edu:resource:edit']" type="text" @click="changeStatus(row, reload)">{{ row.status === 0 ? '下架' : '上架' }}</el-button>
        <el-button v-hasPermi="['edu:resource:edit']" type="text" @click="openCourses(row)">投放到课程</el-button>
        <el-button v-hasPermi="['edu:resource:query']" type="text" @click="openResource(row)">查看资源</el-button>
      </template>
    </BusinessRecords>
    <el-dialog title="资源分类维护" :visible.sync="categoriesOpen" width="min(760px, 95vw)" destroy-on-close>
      <BusinessRecords v-if="categoriesOpen" :config="categoryConfig" />
    </el-dialog>
    <el-dialog title="资源预览" :visible.sync="previewOpen" width="min(860px, 95vw)" @closed="closePreview">
      <img v-if="previewType.startsWith('image/')" :src="previewUrl" alt="资源图片" style="max-width:100%">
      <audio v-else-if="previewType.startsWith('audio/')" :src="previewUrl" controls />
      <video v-else-if="previewType.startsWith('video/')" :src="previewUrl" controls style="max-width:100%" />
      <iframe v-else-if="previewUrl" :src="previewUrl" title="文档预览" style="width:100%;height:60vh" />
    </el-dialog>
    <el-dialog :visible.sync="courseOpen" title="投放到课程" width="560px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon class="biz-tip"
        title="勾选要投放的课程，取消勾选即撤下；学生刷新课程即可看到生效资料。" />
      <div v-loading="courseLoading" class="course-picker">
        <el-checkbox-group v-model="selectedCourseIds">
          <div v-for="course in courseOptions" :key="course.courseId" class="course-row">
            <el-checkbox :label="course.courseId">{{ course.courseName }}</el-checkbox>
            <span class="course-term">{{ course.termName }}</span>
          </div>
        </el-checkbox-group>
        <div v-if="!courseOptions.length && !courseLoading" class="course-empty">暂无可投放的课程</div>
      </div>
      <span slot="footer">
        <el-button @click="courseOpen = false">取消</el-button>
        <el-button type="primary" :loading="courseSaving" @click="saveCourses">保存投放</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import BusinessRecords from '@/components/BusinessRecords'
import request from '@/utils/request'

const RES_TYPES = [
  { label: '文档', value: 'doc' },
  { label: '视频', value: 'video' },
  { label: '音频', value: 'audio' },
  { label: '图片', value: 'image' },
  { label: '外链', value: 'link' }
]
const VISIBLE_SCOPES = [
  { label: '仅本人', value: 0 },
  { label: '本课程', value: 1 },
  { label: '本班', value: 2 },
  { label: '全校', value: 3 }
]
const STATUS_OPTIONS = [{ label: '已上架', value: 0 }, { label: '已下架', value: 1 }]
const labelOf = (options, value) => {
  const found = options.find(item => item.value === value)
  return found ? found.label : value
}

export default {
  name: 'EduResource',
  components: { BusinessRecords },
  data() {
    const categoryProps = { value: 'categoryId', label: 'categoryName', children: 'children',
      checkStrictly: true, expandTrigger: 'hover', emitPath: false }
    return {
      uploading: false,
      classOptions: [],
      courseOpen: false, courseLoading: false, courseSaving: false,
      courseOptions: [], selectedCourseIds: [], linkedCourseIds: [], courseResourceId: null,
      categoriesOpen: false,
      previewOpen: false,
      previewUrl: '',
      previewType: '',
      categoryTree: [],
      categoryProps,
      categoryConfig: {
        title: '分类信息', base: '/edu/resource/category', list: '/edu/resource/category/tree',
        permission: 'edu:resource', id: 'categoryId', remove: true, tree: true,
        defaults: { parentId: 0, orderNum: 0, status: '0' },
        beforeOpen: () => this.loadCategories(),
        fields: [
          { key: 'categoryName', label: '分类名称', required: true, maxlength: 64 },
          { key: 'parentId', label: '上级分类', type: 'cascader', options: form => this.parentOptions(form),
            props: categoryProps, placeholder: '顶层分类可不选' },
          { key: 'orderNum', label: '排序', number: true },
          { key: 'status', label: '状态', options: [{ label: '启用', value: '0' }, { label: '停用', value: '1' }] }
        ],
        columns: [
          { key: 'categoryName', label: '分类名称' },
          { key: 'resourceCount', label: '资源数' },
          { key: 'orderNum', label: '排序' },
          { key: 'status', label: '状态', format: value => value === '0' ? '启用' : '停用' }
        ]
      },
      config: {
        title: '资源管理', base: '/edu/resource', permission: 'edu:resource', id: 'resourceId',
        paged: true, remove: true, rowActions: true,
        actionColumn: { align: 'center', width: 320, className: 'resource-actions' },
        defaults: { resType: 'doc', visibleScope: 1, allowDownload: '0' },
        beforeOpen: () => { this.loadCategories(); this.loadClasses() },
        saveDisabled: () => this.uploading,
        filters: [
          { key: 'title', label: '名称' },
          { key: 'resType', label: '类型', options: RES_TYPES },
          { key: 'categoryId', label: '分类', options: () => this.flatCategories() },
          { key: 'status', label: '状态', options: STATUS_OPTIONS }
        ],
        fields: [
          { key: 'title', label: '资源名称', required: true, maxlength: 200 },
          { key: 'resType', label: '资源类型', required: true, options: RES_TYPES },
          { key: 'categoryId', label: '所属分类', options: form => this.flatCategories(form), placeholder: '请选择分类' },
          { key: 'externalUrl', label: '外链地址', placeholder: 'https://…',
            visible: form => form.resType === 'link', required: form => form.resType === 'link' },
          { key: 'visibleScope', label: '可见范围', required: true, options: VISIBLE_SCOPES },
          { key: 'classId', label: '归属班级', placeholder: '本班资源必须选择归属班级',
            options: () => this.classOptions, visible: form => form.visibleScope === 2,
            required: form => form.visibleScope === 2 },
          { key: 'allowDownload', label: '下载权限', required: true,
            options: [{ label: '允许下载', value: '1' }, { label: '仅在线预览', value: '0' }] },
          { key: 'fileId', hidden: true }, { key: 'fileName', hidden: true },
          { key: 'fileSize', hidden: true }, { key: 'fileExt', hidden: true }
        ],
        columns: [
          { key: 'title', label: '资源名称' },
          { key: 'resType', label: '类型', format: value => labelOf(RES_TYPES, value) },
          { key: 'categoryName', label: '分类' },
          { key: 'visibleScope', label: '可见范围', format: value => labelOf(VISIBLE_SCOPES, value) },
          { key: 'className', label: '归属班级', format: value => value || '—' },
          { key: 'allowDownload', label: '下载权限', format: value => value === '1' ? '允许下载' : '仅预览' },
          { key: 'status', label: '状态', format: value => value === 0 ? '已上架' : '已下架' },
          { key: 'refCount', label: '引用次数' },
          { key: 'ownerName', label: '上传人' },
          { key: 'createTime', label: '上传时间' }
        ]
      }
    }
  },
  watch: {
    categoriesOpen(open) { if (!open) this.loadCategories() }
  },
  created() { this.loadCategories(); this.loadClasses() },
  methods: {
    // F03：加载班级选项，供"本班"资源选择归属班级
    async loadClasses() {
      try {
        const { data } = await request({ url: '/edu/course/options' })
        const terms = new Map((data.terms || []).map(t => [t.termId, t.termName]))
        this.classOptions = (data.classes || []).map(c => ({
          value: c.classId,
          label: [c.className, terms.get(c.termId)].filter(Boolean).join(' · ')
        }))
      } catch (e) { this.classOptions = [] }
    },
    // 反向入口：从资源页维护该资源投放到了哪些课程（TC-02-01）
    async openCourses(row) {
      this.courseResourceId = row.resourceId
      this.courseOpen = true
      this.courseLoading = true
      try {
        const [courses, linked] = await Promise.all([
          request({ url: '/edu/course/list', params: { pageNum: 1, pageSize: 1000 } }),
          request({ url: '/edu/resource/' + row.resourceId + '/courses' })
        ])
        this.courseOptions = (courses.rows || []).filter(course => course.status !== 2)
        this.linkedCourseIds = linked.data || []
        this.selectedCourseIds = this.linkedCourseIds.slice()
      } finally { this.courseLoading = false }
    },
    async saveCourses() {
      if (this.courseSaving) return
      this.courseSaving = true
      try {
        const before = new Set(this.linkedCourseIds)
        const after = new Set(this.selectedCourseIds)
        for (const courseId of before) {
          if (!after.has(courseId)) {
            await request({ url: '/edu/resource/' + this.courseResourceId + '/course/' + courseId, method: 'delete' })
          }
        }
        for (const courseId of after) {
          if (!before.has(courseId)) {
            await request({ url: '/edu/resource/' + this.courseResourceId + '/course/' + courseId, method: 'post' })
          }
        }
        this.courseOpen = false
        this.$modal.msgSuccess('课程投放已更新')
      } finally { this.courseSaving = false }
    },
    openCategories() { this.categoriesOpen = true },
    async loadCategories() {
      try {
        const { data } = await request({ url: '/edu/resource/category/tree' })
        this.categoryTree = data || []
      } catch (e) {
        this.categoryTree = []
      }
    },
    // 上级分类：用名称选层级，新增时可把分类挂到任意已有分类下；编辑时排除自身与子孙避免成环
    parentOptions(form) {
      const selfId = form && form.categoryId
      const prune = nodes => nodes
        .filter(node => node.categoryId !== selfId)
        .map(node => ({ categoryId: node.categoryId, categoryName: node.categoryName, children: prune(node.children || []) }))
      return [{ categoryId: 0, categoryName: '顶层分类' }, ...prune(this.categoryTree)]
    },
    // 所属分类：把分类树拍平为一维下拉，选项显示完整路径（如「法律法规 / 法律」），不再多级联动
    flatCategories(form) {
      const currentId = form && form.categoryId
      const result = []
      const walk = (nodes, prefix) => {
        (nodes || []).forEach(node => {
          const label = prefix ? prefix + ' / ' + node.categoryName : node.categoryName
          result.push({
            value: node.categoryId,
            label: node.status === '1' ? label + '（已停用）' : label,
            disabled: node.status === '1' && node.categoryId !== currentId
          })
          walk(node.children || [], label)
        })
      }
      walk(this.categoryTree, '')
      return result
    },
    formatSize(bytes) {
      if (!bytes) return ''
      return bytes >= 1024 * 1024 ? (bytes / 1024 / 1024).toFixed(1) + ' MB' : Math.ceil(bytes / 1024) + ' KB'
    },
    async upload(event, form) {
      const file = event.target.files[0]
      if (!file || this.uploading) return
      const data = new FormData()
      data.append('file', file)
      this.uploading = true
      try {
        const response = await request({ url: '/common/upload/secure', method: 'post',
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

<style scoped>
::v-deep .resource-actions > .cell {
  white-space: nowrap;
  text-overflow: clip;
}
.biz-tip { margin-bottom: 12px; }
.file-hint { color: #606266; font-size: 12px; margin-top: 4px; }
.course-picker { max-height: 46vh; overflow-y: auto; }
.course-row { display: flex; align-items: center; justify-content: space-between; padding: 4px 0; }
.course-term { color: #909399; font-size: 12px; }
.course-empty { color: #909399; font-size: 13px; padding: 12px 0; }
</style>
