<template>
  <el-tabs v-model="tab">
    <el-tab-pane label="展项" name="items"><ExhibitManagement :key="revision" mode="item" /></el-tab-pane>
    <el-tab-pane label="通过审核的成果" name="approved" lazy>
      <ExhibitAudit approved-only @converted="revision++" />
    </el-tab-pane>
  </el-tabs>
</template>
<script>
import ExhibitManagement from '../ExhibitManagement.vue'
import ExhibitAudit from '../audit/index.vue'
export default {
  name: 'ExhibitItem',
  components: { ExhibitManagement, ExhibitAudit },
  data() { return { tab: 'items', revision: 0 } }
}
</script>
