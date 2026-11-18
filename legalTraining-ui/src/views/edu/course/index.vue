<template>
  <div class="app-container">
    <el-form inline @submit.native.prevent="search">
      <el-form-item label="课程名称">
        <el-input v-model.trim="query.courseName" clearable placeholder="搜索课程名称" />
      </el-form-item>
      <el-button native-type="submit">查询</el-button>
      <el-button v-hasPermi="['edu:course:add']" type="primary" @click="openForm()">新建课程</el-button>
    </el-form>
    <el-table v-loading="loading" :data="rows" row-key="courseId">
      <el-table-column prop="courseCode" label="课程编码" />
      <el-table-column prop="courseName" label="课程名称" />
      <el-table-column prop="termName" label="学期" />
      <el-table-column label="状态">
        <template slot-scope="{ row }">{{ statuses[row.status] }}</template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" align="center" header-align="center" width="440" class-name="nowrap-actions">
        <template slot-scope="{ row }">
          <el-button v-if="row.status !== 2" v-hasPermi="['edu:course:edit']" type="text" @click="$refs.caseReferences.show(row.courseId)">关联案例</el-button>
          <el-button v-if="row.status !== 2" v-hasPermi="['edu:course:edit']" type="text" @click="openForm(row)">编辑</el-button>
          <el-button v-hasPermi="['edu:course:add']" type="text" @click="openCopy(row)">复制</el-button>
          <el-button v-if="row.status !== 2" v-hasPermi="['edu:resource:edit']" type="text" @click="openResources(row)">投放资源</el-button>
          <el-button v-if="row.status !== 2" v-hasPermi="['edu:course:edit']" type="text" @click="openExhibits(row)">关联展厅</el-button>
          <el-button v-if="row.status === 0" v-hasPermi="['edu:course:edit']" type="text" @click="changeState(row, 'publish')">发布</el-button>
          <el-button v-if="row.status === 1" v-hasPermi="['edu:course:edit']" type="text" @click="changeState(row, 'archive')">归档</el-button>
        </template>
      </el-table-column>
    </el-table>
    <CaseReferences ref="caseReferences" type="course" />
    <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="load" />
    <el-dialog :visible.sync="editing" :title="form.courseId ? '编辑课程' : '新建课程'" width="590px">
      <el-form ref="courseForm" :model="form" :rules="rules" label-width="100px" @submit.native.prevent="save">
        <el-form-item label="课程编码" prop="courseCode"><el-input v-model.trim="form.courseCode" maxlength="32" /></el-form-item>
        <el-form-item label="课程名称" prop="courseName"><el-input v-model.trim="form.courseName" maxlength="100" /></el-form-item>
        <el-form-item label="学期" prop="termId">
          <el-select v-model="form.termId" :disabled="!!form.courseId" placeholder="请选择学期" @change="form.classIds = []">
            <el-option v-for="term in terms" :key="term.termId" :label="term.termName" :value="term.termId" />
          </el-select>
        </el-form-item>
        <el-form-item label="授课班级" prop="classIds">
          <el-select v-model="form.classIds" multiple filterable placeholder="请选择班级">
            <el-option v-for="entry in eligibleClasses" :key="entry.classId" :label="entry.className" :value="entry.classId" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程简介"><el-input v-model.trim="form.intro" type="textarea" maxlength="2000" /></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit" :loading="saving">保存</el-button></el-form-item>
      </el-form>
    </el-dialog>
    <el-dialog :visible.sync="exhibitOpen" title="关联展厅" width="580px">
      <el-select v-model="selectedExhibits" multiple filterable placeholder="选择展区或展项" style="width: 100%">
        <el-option v-for="entry in exhibitOptions" :key="exhibitKey(entry)" :value="exhibitKey(entry)"
                   :label="(entry.targetType === 'zone' ? '展区 · ' : '展项 · ') + entry.title" />
      </el-select>
      <span slot="footer"><el-button type="primary" :loading="saving" @click="saveExhibits">保存关联</el-button></span>
    </el-dialog>
    <el-dialog :visible.sync="resourceOpen" title="投放课程资源" width="760px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon class="resource-tip"
        title="从已上架资源中选择，保存后学生刷新课程即可看到；列表顺序即学生端的资料顺序。" />
      <div class="resource-block">
        <div class="resource-block-title">已投放资源（{{ pickedResources.length }}）</div>
        <div v-if="!pickedResources.length" class="resource-empty">尚未投放资源</div>
        <div v-for="(item, index) in pickedResources" :key="item.resourceId" class="picked-row">
          <span class="picked-name">{{ index + 1 }}. {{ item.title }}</span>
          <el-button type="text" :disabled="index === 0" @click="moveResource(index, -1)">上移</el-button>
          <el-button type="text" :disabled="index === pickedResources.length - 1" @click="moveResource(index, 1)">下移</el-button>
          <el-button type="text" class="danger-text" @click="removeResource(index)">移除</el-button>
        </div>
      </div>
      <div class="resource-block">
        <div class="resource-block-title">添加资源</div>
        <el-select v-model="addingResourceId" filterable clearable placeholder="选择已上架的教学资源" style="width:100%">
          <el-option v-for="item in resourceOptions" :key="item.resourceId" :label="item.title" :value="item.resourceId"
                     :disabled="isPicked(item.resourceId)" />
        </el-select>
        <el-button style="margin-top:8px" :disabled="!addingResourceId" @click="addResource">加入待投放列表</el-button>
      </div>
      <span slot="footer">
        <el-button @click="resourceOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveResources">保存投放</el-button>
      </span>
    </el-dialog>
    <el-dialog :visible.sync="copying" title="复制课程" width="590px">
      <el-form label-width="100px" @submit.native.prevent="copyCourse">
        <el-form-item label="目标学期">
          <el-select v-model="copyForm.termId" placeholder="请选择目标学期">
            <el-option v-for="term in terms" :key="term.termId" :label="term.termName" :value="term.termId" />
          </el-select>
        </el-form-item>
        <el-form-item label="新课程编码"><el-input v-model.trim="copyForm.courseCode" maxlength="32" /></el-form-item>
        <el-form-item label="新课程名称"><el-input v-model.trim="copyForm.courseName" maxlength="100" /></el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="saving"
                     :disabled="!copyForm.termId || !copyForm.courseCode || !copyForm.courseName">生成课程草稿</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>
<script>
import CaseReferences from '@/components/CaseReferences.vue'
import request from '@/utils/request'

export default {
  components: { CaseReferences },
  name: 'EduCourse',
  data() {
    return {
      query: { pageNum: 1, pageSize: 10, courseName: '' },
      rows: [], total: 0, terms: [], classes: [], form: {}, editing: false, loading: false, saving: false,
      copying: false, copyForm: {}, sourceCourseId: null,
      exhibitOpen: false, exhibitCourseId: null, exhibitOptions: [], selectedExhibits: [],
      resourceOpen: false, resourceCourseId: null, resourceOptions: [], pickedResources: [], addingResourceId: null,
      statuses: ['草稿', '已发布', '已归档'],
      rules: {
        courseCode: [{ required: true, message: '请输入课程编码' }],
        courseName: [{ required: true, message: '请输入课程名称' }],
        termId: [{ required: true, message: '请选择学期' }],
        classIds: [{ required: true, type: 'array', message: '请选择授课班级' }]
      }
    }
  },
  computed: {
    eligibleClasses() { return this.classes.filter(entry => entry.termId === this.form.termId) }
  },
  created() { this.load() },
  methods: {
    // ---------- 课程资源投放（TC-02-01） ----------
    async openResources(row) {
      const [all, current] = await Promise.all([
        request({ url: '/edu/resource/list', params: { status: 0, pageNum: 1, pageSize: 1000 } }),
        request({ url: '/edu/resource/course/' + row.courseId })
      ])
      this.resourceOptions = all.rows || []
      this.pickedResources = (current.data || []).map(item => ({ resourceId: item.resourceId, title: item.title }))
      this.resourceCourseId = row.courseId
      this.addingResourceId = null
      this.resourceOpen = true
    },
    isPicked(resourceId) { return this.pickedResources.some(item => item.resourceId === resourceId) },
    addResource() {
      const found = this.resourceOptions.find(item => item.resourceId === this.addingResourceId)
      if (found && !this.isPicked(found.resourceId)) {
        this.pickedResources.push({ resourceId: found.resourceId, title: found.title })
        this.$modal.msgSuccess('已加入列表，点击「保存投放」后生效')
      }
      this.addingResourceId = null
    },
    moveResource(index, delta) {
      const target = index + delta
      if (target < 0 || target >= this.pickedResources.length) return
      const next = this.pickedResources.slice()
      const [item] = next.splice(index, 1)
      next.splice(target, 0, item)
      this.pickedResources = next
    },
    removeResource(index) { this.pickedResources.splice(index, 1) },
    async saveResources() {
      if (this.saving) return
      this.saving = true
      try {
        const data = this.pickedResources.map((item, sortNo) => ({ resourceId: item.resourceId, sortNo }))
        await request({ url: '/edu/resource/course/' + this.resourceCourseId, method: 'put', data })
        this.resourceOpen = false
        this.$modal.msgSuccess('课程资源已保存')
      } finally { this.saving = false }
    },
    exhibitKey(entry) { return entry.targetType + ':' + entry.targetId },
    async openExhibits(row) {
      const [options, current] = await Promise.all([
        request({ url: '/edu/course/exhibit-options' }),
        request({ url: '/edu/course/' + row.courseId + '/exhibits' })
      ])
      this.exhibitCourseId = row.courseId
      this.exhibitOptions = options.data
      this.selectedExhibits = current.data.map(this.exhibitKey)
      this.exhibitOpen = true
    },
    async saveExhibits() {
      if (this.saving) return
      this.saving = true
      try {
        const data = this.selectedExhibits.map((key, sortNo) => {
          const [targetType, targetId] = key.split(':')
          return { targetType, targetId, sortNo }
        })
        await request({ url: '/edu/course/' + this.exhibitCourseId + '/exhibits', method: 'put', data })
        this.exhibitOpen = false
        this.$modal.msgSuccess('已保存课程展厅关联')
      } finally { this.saving = false }
    },
    async openCopy(row) {
      this.terms = (await request({ url: '/edu/course/options' })).data.terms
      this.sourceCourseId = row.courseId
      this.copyForm = {
        termId: this.terms.find(term => term.termId !== row.termId)?.termId,
        courseCode: row.courseCode.slice(0, 27) + '-COPY',
        courseName: row.courseName.slice(0, 96) + '（副本）'
      }
      this.copying = true
    },
    async copyCourse() {
      if (this.saving || !this.copyForm.termId || !this.copyForm.courseCode || !this.copyForm.courseName) return
      this.saving = true
      try {
        const { data } = await request({
          url: '/edu/course/' + this.sourceCourseId + '/copy', method: 'post', params: this.copyForm
        })
        this.copying = false
        this.query.courseName = this.copyForm.courseName
        this.query.pageNum = 1
        await this.load()
        const warning = data.offlineResources?.length
          ? '。源资源已下架：' + data.offlineResources.join('、') : ''
        await this.$alert('任务模板 ' + data.taskCopied + ' 个，课程资源 ' + data.resourceCopied + ' 项' + warning, '课程草稿已生成')
      } finally { this.saving = false }
    },
    search() {
      this.query.pageNum = 1
      this.load()
    },
    async load() {
      this.loading = true
      try {
        const result = await request({ url: '/edu/course/list', params: this.query })
        this.rows = result.rows
        this.total = result.total
      } finally { this.loading = false }
    },
    async openForm(row) {
      const { data } = await request({ url: '/edu/course/options' })
      this.terms = data.terms
      this.classes = data.classes
      const course = row ? (await request({ url: '/edu/course/' + row.courseId })).data : {}
      this.form = {
        courseId: course.courseId, courseCode: course.courseCode || '', courseName: course.courseName || '',
        termId: course.termId || this.terms[0]?.termId, classIds: course.classIds || [], intro: course.intro || ''
      }
      this.editing = true
      this.$nextTick(() => this.$refs.courseForm.clearValidate())
    },
    async save() {
      if (this.saving || !await this.$refs.courseForm.validate().catch(() => false)) return
      this.saving = true
      try {
        await request({ url: '/edu/course', method: this.form.courseId ? 'put' : 'post', data: this.form })
        this.editing = false
        this.$modal.msgSuccess('课程已保存')
        await this.load()
      } finally { this.saving = false }
    },
    async changeState(row, action) {
      const label = action === 'publish' ? '发布' : '归档'
      try { await this.$modal.confirm('确认' + label + '课程「' + row.courseName + '」？') } catch { return }
      await request({ url: '/edu/course/' + row.courseId + '/' + action, method: 'put' })
      this.$modal.msgSuccess('课程已' + label)
      await this.load()
    }
  }
}
</script>
<style>
.nowrap-actions .cell { white-space: nowrap; }
.resource-tip { margin-bottom: 12px; }
.resource-block { margin-bottom: 16px; }
.resource-block-title { font-weight: 600; margin-bottom: 8px; color: #303133; }
.resource-empty { color: #909399; font-size: 13px; padding: 8px 0; }
.picked-row { display: flex; align-items: center; gap: 8px; padding: 4px 0; border-bottom: 1px solid #ebeef5; }
.picked-name { flex: 1; color: #606266; }
.danger-text { color: #f56c6c; }
</style>
