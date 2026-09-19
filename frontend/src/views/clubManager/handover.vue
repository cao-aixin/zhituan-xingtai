<template>
  <div>
    <!-- 发起换届 -->
    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header><b>发起换届</b></template>
      <el-form :model="form" label-width="110px" style="max-width: 560px">
        <el-form-item label="选择社团" required>
          <el-select v-model="form.clubId" placeholder="选择社团" style="width: 100%">
            <el-option v-for="c in myClubs" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="新负责人学号" required>
          <el-input v-model="form.newLeaderStudentNo" placeholder="输入新负责人的学号（如 20230101）" />
        </el-form-item>
        <el-form-item label="交接说明">
          <el-input v-model="form.note" type="textarea" :rows="3" placeholder="选填，如：原负责人毕业，工作资料已随资料库完整移交" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submitHandover">确认换届</el-button>
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="换届后新负责人自动成为本社团会长，历史资料随社团完整移交，双方均会收到站内通知。"
      />
    </el-card>

    <!-- 换届记录 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <b>换届记录</b>
          <el-button size="small" @click="loadRecords">刷新</el-button>
        </div>
      </template>
      <el-table :data="records" v-loading="loading" border>
        <el-table-column prop="id" label="记录ID" width="90" />
        <el-table-column prop="clubName" label="社团" min-width="140" />
        <el-table-column prop="oldLeaderName" label="原负责人" width="110" />
        <el-table-column prop="newLeaderName" label="新负责人" width="110" />
        <el-table-column prop="note" label="交接说明" min-width="220" show-overflow-tooltip />
        <el-table-column prop="createTime" label="换届时间" width="170" />
      </el-table>
      <el-empty v-if="!loading && records.length === 0" description="暂无换届记录" />
    </el-card>
  </div>
</template>

<script setup>
// 换届管理页（社团负责人）：发起换届 + 查看换届记录（移植自旧版 zhituan-system）
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyClubs, handoverClub, getHandoverList } from '@/api'

const myClubs = ref([])
const records = ref([])
const loading = ref(false)
const submitting = ref(false)
const form = reactive({ clubId: null, newLeaderStudentNo: '', note: '' })

const loadRecords = async () => {
  loading.value = true
  try {
    records.value = (await getHandoverList(form.clubId ? { clubId: form.clubId } : {})) || []
  } finally {
    loading.value = false
  }
}

const submitHandover = async () => {
  if (!form.clubId) return ElMessage.warning('请先选择社团')
  if (!form.newLeaderStudentNo) return ElMessage.warning('请输入新负责人学号')
  await ElMessageBox.confirm(
    `确认将社团负责人移交给学号「${form.newLeaderStudentNo}」的同学？换届后原负责人将失去管理权限。`,
    '换届确认',
    { type: 'warning' }
  )
  submitting.value = true
  try {
    await handoverClub({ ...form })
    ElMessage.success('换届完成，已通知新旧负责人')
    form.newLeaderStudentNo = ''
    form.note = ''
    loadRecords()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  myClubs.value = (await getMyClubs()) || []
  if (myClubs.value.length > 0) {
    form.clubId = myClubs.value[0].id
  }
  loadRecords()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
