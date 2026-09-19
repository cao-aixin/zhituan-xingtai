<template>
  <div>
    <!-- 待审核社团 -->
    <el-card shadow="never">
      <template #header><b>待审核社团</b></template>
      <el-table :data="pendingList" v-loading="loading" border>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="社团名称" />
        <el-table-column prop="intro" label="简介" show-overflow-tooltip />
        <el-table-column prop="tags" label="标签" width="180" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button type="success" size="small" @click="handleAudit(row, 1)">通过</el-button>
            <el-button type="danger" size="small" @click="handleAudit(row, 2)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && pendingList.length === 0" description="暂无待审核社团" />
    </el-card>

    <!-- 全部社团列表 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <b>全部社团</b>
          <el-input
            v-model="keyword"
            placeholder="搜索社团名称"
            style="width: 220px"
            clearable
            @keyup.enter="loadClubs"
            @clear="loadClubs"
          >
            <template #append>
              <el-button @click="loadClubs"><el-icon><Search /></el-icon></el-button>
            </template>
          </el-input>
        </div>
      </template>
      <el-table :data="clubList" v-loading="clubLoading" border>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="社团名称" />
        <el-table-column prop="intro" label="简介" show-overflow-tooltip />
        <el-table-column prop="tags" label="标签" width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '正常' : row.status === 0 ? '待审核' : '已驳回' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
// 社团审核页（学校管理员）：查看待审核列表，通过/驳回（驳回需填理由）
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPendingClubs, auditClub, getClubList } from '@/api'

const pendingList = ref([])
const clubList = ref([])
const loading = ref(false)
const clubLoading = ref(false)
const keyword = ref('')

// 加载待审核列表
const loadPending = async () => {
  loading.value = true
  try {
    pendingList.value = (await getPendingClubs()) || []
  } finally {
    loading.value = false
  }
}

// 加载全部社团
const loadClubs = async () => {
  clubLoading.value = true
  try {
    clubList.value = (await getClubList(keyword.value ? { keyword: keyword.value } : {})) || []
  } finally {
    clubLoading.value = false
  }
}

// 审核：1=通过 2=驳回（驳回时弹出理由输入框）
const handleAudit = async (row, result) => {
  let rejectReason = ''
  if (result === 2) {
    const { value } = await ElMessageBox.prompt('请输入驳回理由', '驳回社团申请', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '驳回理由',
      inputValidator: (v) => (v && v.trim() ? true : '驳回理由不能为空')
    })
    rejectReason = value
  } else {
    await ElMessageBox.confirm(`确认通过「${row.name}」的创建申请？`, '提示', { type: 'warning' })
  }
  await auditClub({ id: row.id, auditResult: result, rejectReason: rejectReason || undefined })
  ElMessage.success('审核完成')
  loadPending()
  loadClubs()
}

onMounted(() => {
  loadPending()
  loadClubs()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
