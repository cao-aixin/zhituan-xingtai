<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <b>活动审批</b>
          <el-select v-model="status" style="width: 160px" @change="loadList">
            <el-option label="待校方审批" :value="2" />
            <el-option label="进行中" :value="3" />
            <el-option label="已结束" :value="4" />
            <el-option label="已驳回" :value="5" />
            <el-option label="待社团确认" :value="1" />
            <el-option label="全部" :value="''" />
          </el-select>
        </div>
      </template>
      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="活动名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="clubName" label="发起社团" width="130" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isJoint === 1 ? 'warning' : 'info'" size="small">
              {{ row.isJoint === 1 ? '联合活动' : '普通活动' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="location" label="地点" width="120" show-overflow-tooltip />
        <el-table-column prop="startTime" label="开始时间" width="160" />
        <el-table-column prop="endTime" label="结束时间" width="160" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.statusDesc }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="受邀社团" min-width="160">
          <template #default="{ row }">
            <template v-if="row.isJoint === 1 && row.jointJoins && row.jointJoins.length">
              <el-tag
                v-for="j in row.jointJoins"
                :key="j.id"
                size="small"
                :type="j.joinStatus === 1 ? 'success' : j.joinStatus === 2 ? 'danger' : 'info'"
                style="margin-right: 6px; margin-bottom: 4px"
              >
                {{ j.clubName }}·{{ j.joinStatusDesc }}
              </el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 2">
              <!-- AI 风险巡检：仅做审批辅助，不改变审批结果 -->
              <el-button type="warning" plain size="small" @click="openRisk(row)">
                <el-icon><MagicStick /></el-icon>
                AI 风险巡检
              </el-button>
              <el-button type="success" size="small" @click="handleAudit(row, true)">通过</el-button>
              <el-button type="danger" size="small" @click="handleAudit(row, false)">驳回</el-button>
            </template>
            <span v-else class="gray">{{ row.status === 3 ? '审批已通过' : '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 【复用组件】AI 风险巡检：展示风险清单与处置建议，仅供参考、不改变审批结果 -->
    <AiGenerateDialog
      v-model="riskVisible"
      skill-type="RISK_INSPECT"
      :biz-id="riskActivityId"
      title="AI 活动风险巡检"
      @confirm="onRiskConfirm"
    />
  </div>
</template>

<script setup>
// 活动审批页（学校管理员）：查看待审批活动并通过/驳回（驳回需填理由）
// - 额外提供「AI 风险巡检」按钮：对待审批活动做风险扫描（时间冲突/安全/合规/资源）
//   巡检结果仅供审批参考，**不改变审批结果**，是否通过仍由管理员点击「通过/驳回」决定
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getActivityList, auditActivity } from '@/api'
import AiGenerateDialog from '@/components/ai-generate-dialog.vue'

const list = ref([])
const loading = ref(false)
const status = ref(2) // 默认展示待校方审批

// ============ AI 风险巡检（skill=RISK_INSPECT） ============
const riskVisible = ref(false)
const riskActivityId = ref(null)

// 打开风险巡检弹窗：以当前行活动ID作为入参
const openRisk = (row) => {
  riskActivityId.value = row.id
  riskVisible.value = true
}

// 巡检结果确认回调：仅提示可复制留档，不回填任何审批表单字段
const onRiskConfirm = (content) => {
  if (navigator.clipboard) {
    navigator.clipboard.writeText(content).then(
      () => ElMessage.success('巡检报告已复制到剪贴板，可粘贴留档（不影响审批结果）'),
      () => ElMessage.success('巡检完成，请据此综合判断（不影响审批结果）')
    )
  } else {
    ElMessage.success('巡检完成，请据此综合判断（不影响审批结果）')
  }
}

// 活动状态 -> el-tag 颜色
const statusTagType = (s) =>
  ({ 0: 'info', 1: 'warning', 2: 'warning', 3: 'success', 4: 'info', 5: 'danger' }[s] || 'info')

const loadList = async () => {
  loading.value = true
  try {
    const params = {}
    if (status.value !== '') params.status = status.value
    list.value = (await getActivityList(params)) || []
  } finally {
    loading.value = false
  }
}

// 审批：approved=true 通过 / false 驳回（驳回填理由）
const handleAudit = async (row, approved) => {
  let rejectReason = ''
  if (!approved) {
    const { value } = await ElMessageBox.prompt('请输入驳回理由', `驳回「${row.title}」`, {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '驳回理由',
      inputValidator: (v) => (v && v.trim() ? true : '驳回理由不能为空')
    })
    rejectReason = value
  } else {
    await ElMessageBox.confirm(`确认通过「${row.title}」并开始进行？`, '提示', { type: 'warning' })
  }
  await auditActivity(row.id, { approved, rejectReason: rejectReason || undefined })
  ElMessage.success('审批完成')
  loadList()
}

onMounted(loadList)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.gray {
  color: #909399;
  font-size: 12px;
}
</style>
