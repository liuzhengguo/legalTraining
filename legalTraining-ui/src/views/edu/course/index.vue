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
      <el-table-column label="操作" width="260">
        <template slot-scope="{ row }">
          <el-button v-if="row.status !== 2" v-hasPermi="['edu:course:edit']" type="text" @click="openForm(row)">编辑</el-button>
          <el-button v-hasPermi="['edu:course:add']" type="text" @click="openCopy(row)">复制</el-button>
          <el-button v-if="row.status !== 2" v-hasPermi="['edu:course:edit']" type="text" @click="openExhibits(row)">关联展厅</el-button>
          <el-button v-if="row.status === 0" v-hasPermi="['edu:course:edit']" type="text" @click="changeState(row, 'publish')">发布</el-button>
          <el-button v-if="row.status === 1" v-hasPermi="['edu:course:edit']" type="text" @click="changeState(row, 'archive')">归档</el-button>
        </template>
      </el-table-column>
    </el-table>
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
import request from '@/utils/request'

export default {
  name: 'EduCourse',
  data() {
    return {
      query: { pageNum: 1, pageSize: 10, courseName: '' },
      rows: [], total: 0, terms: [], classes: [], form: {}, editing: false, loading: false, saving: false,
      copying: false, copyForm: {}, sourceCourseId: null,
      exhibitOpen: false, exhibitCourseId: null, exhibitOptions: [], selectedExhibits: [],
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
