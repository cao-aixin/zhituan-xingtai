<template>
  <div>
    <el-card shadow="never">
      <template #header><b>联合活动管理</b></template>
      <el-table :data="list" v-loading="loading" border>
        <el-table-column type="expand">
          <template #default="{ row }">
            <!-- 受邀社团确认状态明细 -->
            <div v-if="row.jointJoins && row.jointJoins.length" style="padding: 8px 24px">
              <b>受邀社团：</b>
              <el-tag
                v-for="j in row.jointJoins"
                :key="j.id"
                :type="joinTagType(j.joinStatus)"
                style="margin-right: 10px"
              >
                {{ j.clubName }} · {{ j.joinStatusDesc }}
                <template v-if="j.refuseReason">（拒绝理由：{{ j.refuseReason }}）</template>
              </el-tag>
            </div>
            <div v-else style="padding: 8px 24px; color: #909399">普通活动，无受邀社团</div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="活动名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="clubName" label="发起社团" width="130" />
        <el-table-column label="我的身份" width="110">
          <template #default="{ row }">
            <el-tag :type="row.clubId === myClubId ? 'primary' : 'info'" size="small">
              {{ row.clubId === myClubId ? '发起方' : '受邀方' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="160" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <!-- 状态标签：待社团确认/待校方审批/进行中/已结束/已驳回 不同颜色 -->
            <el-tag :type="statusTagType(row.status)">{{ row.statusDesc }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <!-- 受邀方：待确认时可同意/拒绝 -->
            <template v-if="myJoinOf(row) && myJoinOf(row).joinStatus === 0">
              <el-button type="success" size="small" @click="handleConfirm(row, true)">同意参与</el-button>
              <el-button type="danger" size="small" @click="handleConfirm(row, false)">拒绝</el-button>
            </template>
            <!-- 发起方：全部确认后可提交校方审批 -->
            <template v-else-if="row.clubId === myClubId && row.status === 1">
              <el-button type="primary" size="small" @click="handleSubmit(row)">提交校方审批</el-button>
            </template>
            <span v-else class="gray">{{ row.rejectReason ? '驳回理由：' + row.rejectReason : '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 拒绝理由弹窗 -->
    <el-dialog v-model="refuseVisible" title="拒绝参与联合活动" width="420px">
      <el-input v-model="refuseReason" type="textarea" :rows="3" placeholder="请输入拒绝理由" />
      <template #footer>
        <el-button @click="refuseVisible = false">取消</el-button>
        <el-button type="primary" @click="doRefuse">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 联合活动管理页（社团负责人）：
// - 受邀社团负责人：同意 / 拒绝（弹出拒绝理由弹窗）
// - 发起负责人：受邀社团全部确认后提交校方审批
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getActivityList, jointConfirm, submitActivity, getMyClubs } from '@/api'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const list = ref([])
const loading = ref(false)
const myClubs = ref([])
const myClubId = ref(null)

// 拒绝理由弹窗状态
const refuseVisible = ref(false)
const refuseReason = ref('')
const refuseActivityId = ref(null)

// 活动状态 -> el-tag 颜色（待社团确认/待校方审批/进行中/已结束/已驳回）
const statusTagType = (s) =>
  ({ 0: 'info', 1: 'warning', 2: 'warning', 3: 'success', 4: 'info', 5: 'danger' }[s] || 'info')

// 联合确认状态 -> el-tag 颜色
const joinTagType = (s) => ({ 0: 'info', 1: 'success', 2: 'danger' }[s] || 'info')

// 找到我在该活动中的联合确认记录
const myJoinOf = (row) => {
  if (!row.jointJoins || !myClubId.value) return null
  return row.jointJoins.find((j) => j.clubId === myClubId.value) || null
}

const loadList = async () => {
  loading.value = true
  try {
    // 后端自动按角色过滤：我发起的 + 受邀参与的联合活动
    list.value = (await getActivityList({})) || []
  } finally {
    loading.value = false
  }
}

// 受邀确认：agree=true 直接同意；agree=false 弹出拒绝理由弹窗
const handleConfirm = (row, agree) => {
  if (agree) {
    ElMessageBox.confirm(`确认参与「${row.title}」联合活动？`, '提示', { type: 'warning' }).then(
      () => doConfirm(row.id, true, '')
    )
  } else {
    refuseActivityId.value = row.id
    refuseReason.value = ''
    refuseVisible.value = true
  }
}

const doRefuse = async () => {
  if (!refuseReason.value.trim()) {
    ElMessage.warning('拒绝理由不能为空')
    return
  }
  await doConfirm(refuseActivityId.value, false, refuseReason.value.trim())
  refuseVisible.value = false
}

const doConfirm = async (activityId, agree, refuseReason) => {
  await jointConfirm({ activityId, agree, refuseReason: refuseReason || undefined })
  ElMessage.success(agree ? '已同意参与' : '已拒绝参与')
  loadList()
}

// 发起方提交校方审批
const handleSubmit = async (row) => {
  await ElMessageBox.confirm(
    `确认将「${row.title}」提交校方审批？（需全部受邀社团已确认）`,
    '提示',
    { type: 'warning' }
  )
  await submitActivity(row.id)
  ElMessage.success('已提交校方审批')
  loadList()
}

onMounted(async () => {
  myClubs.value = (await getMyClubs()) || []
  myClubId.value = myClubs.value.length > 0 ? myClubs.value[0].id : userStore.managedClubId
  loadList()
})
</script>

<style scoped>
.gray {
  color: #909399;
  font-size: 12px;
}
</style>
