<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <b>成员管理</b>
          <el-select v-model="clubId" style="width: 220px" placeholder="选择社团" @change="loadMembers">
            <el-option v-for="c in myClubs" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </div>
      </template>

      <el-table :data="members" v-loading="loading" border>
        <el-table-column prop="id" label="成员记录ID" width="110" />
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="userName" label="姓名" width="120" />
        <el-table-column prop="memberRole" label="角色" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : row.status === 0 ? 'warning' : 'danger'">
              {{ row.statusDesc || (row.status === 1 ? '正常' : row.status === 0 ? '待审核' : '已退出') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button type="success" size="small" @click="handleAudit(row, true)">通过</el-button>
              <el-button type="danger" size="small" @click="handleAudit(row, false)">拒绝</el-button>
            </template>
            <span v-else class="gray">-</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && clubId && members.length === 0" description="暂无成员" />
    </el-card>
  </div>
</template>

<script setup>
// 成员管理页（社团负责人）：查看本社团成员，审核入社申请（通过/拒绝）
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyClubs, getMemberList, auditMember } from '@/api'

const myClubs = ref([])
const clubId = ref(null)
const members = ref([])
const loading = ref(false)

const loadMembers = async () => {
  if (!clubId.value) return
  loading.value = true
  try {
    members.value = (await getMemberList(clubId.value)) || []
  } finally {
    loading.value = false
  }
}

// 入社审核：approved=true/false
const handleAudit = async (row, approved) => {
  if (!approved) {
    await ElMessageBox.confirm(`确认拒绝「${row.userName}」的入社申请？`, '提示', { type: 'warning' })
  }
  await auditMember(row.id, approved)
  ElMessage.success('审核完成')
  loadMembers()
}

onMounted(async () => {
  myClubs.value = (await getMyClubs()) || []
  if (myClubs.value.length > 0) {
    clubId.value = myClubs.value[0].id
    loadMembers()
  }
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.gray {
  color: #909399;
}
</style>
