<template>
  <div>
    <BusinessRecords :config="config">
      <template #toolbar="{ query }">
        <el-button v-hasPermi="['edu:score:export']" :loading="exporting" @click="exportReport('score', query, 'xlsx')">导出成绩(Excel)</el-button>
        <el-button v-hasPermi="['edu:score:export']" :loading="exporting" @click="exportReport('score', query, 'csv')">导出成绩(CSV)</el-button>
        <el-button v-hasPermi="['edu:export:package']" :loading="exporting" @click="exportReport('package', query)">导出成果清单</el-button>
        <el-button v-hasPermi="['edu:export:package']" :loading="exporting" @click="exportReport('package-archive', query)">导出成果包(ZIP)</el-button>
        <el-button v-hasPermi="['edu:export:ledger']" :loading="exporting" @click="exportReport('ledger', query)">导出过程台账</el-button>
        <el-button v-hasPermi="['edu:export:query']" @click="history">导出记录</el-button>
      </template>
    </BusinessRecords>
    <el-dialog title="导出记录" :visible.sync="showHistory" width="min(800px, 95vw)">
      <el-table :data="exports">
        <el-table-column prop="id" label="编号" />
        <el-table-column label="类型"><template slot-scope="{ row }">{{ exportTypes[row.export_type] }}</template></el-table-column>
        <el-table-column prop="row_count" label="行数" />
        <el-table-column label="状态"><template slot-scope="{ row }">{{ ['生成中', '已完成', '失败'][row.status] }}</template></el-table-column>
        <el-table-column label="操作" width="90" fixed="right" align="center" class-name="nowrap-actions"><template slot-scope="{ row }">
          <el-button v-if="row.status === 1" type="text" @click="downloadExport(row.id, row.export_type)">下载</el-button>
        </template></el-table-column>
      </el-table>
      <el-button @click="history">刷新记录</el-button>
    </el-dialog>
  </div>
</template>

<script>
import { saveAs } from 'file-saver'
import BusinessRecords from '@/components/BusinessRecords'
import request from '@/utils/request'

export default {
  name: 'EduScoreSummary',
  components: { BusinessRecords },
  data() {
    return { exporting: false, showHistory: false, exports: [],
      exportTypes: { score: '成绩', package: '成果清单', result_package: '成果清单',
        result_package_zip: '成果包', ledger: '过程台账', process_ledger: '过程台账' },
      config: {
        title: '成绩汇总', list: '/edu/score/summary',
        filters: [{ key: 'termId', label: '学期 ID' }, { key: 'courseId', label: '课程 ID' }],
        columns: [
          { key: 'courseName', label: '课程' }, { key: 'className', label: '班级' }, { key: 'termName', label: '学期' },
          { key: 'scoredCount', label: '已发布成绩数' }, { key: 'avgScore', label: '平均分' },
          { key: 'maxScore', label: '最高分' }, { key: 'minScore', label: '最低分' }, { key: 'passRate', label: '及格率 (%)' }
        ]
      }
    }
  },
  methods: {
    async exportReport(type, query, format) {
      if (!/^[1-9]\d*$/.test(query.courseId || '')) return this.$modal.msgWarning('请填写课程 ID')
      this.exporting = true
      try {
        const url = type === 'package-archive' ? '/edu/export/package/archive' : '/edu/export/' + type
        const body = { courseId: query.courseId }
        if (format) body.format = format
        const { data } = await request({ url, method: 'post', data: body })
        if (data.async) this.$modal.msgSuccess('已开始生成，可在导出记录查看进度')
        else await this.downloadExport(data.exportId, type)
      } finally { this.exporting = false }
    },
    async downloadExport(id, type) {
      const blob = await request({ url: '/edu/export/tasks/' + id + '/download', responseType: 'blob' })
      if (blob.type.includes('json')) {
        const result = JSON.parse(await blob.text())
        return this.$modal.msgError(result.msg || '下载失败')
      }
      const ext = blob.type.includes('spreadsheet') ? 'xlsx' : blob.type.includes('zip') ? 'zip' : 'csv'
      const label = type === 'package-archive' ? '成果包' : (this.exportTypes[type] || '导出')
      saveAs(blob, label + '-' + id + '.' + ext)
    },
    async history() {
      this.exports = (await request({ url: '/edu/export/tasks' })).data
      this.showHistory = true
    }
  }
}
</script>

