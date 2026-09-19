<template>
  <div>
    <!-- 社团基本信息 -->
    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div class="card-header">
          <b>社团统计</b>
          <el-button type="primary" @click="openAi">
            <el-icon><MagicStick /></el-icon>
            AI 运营分析
          </el-button>
        </div>
      </template>
      <el-descriptions v-if="club" :column="3" border>
        <el-descriptions-item label="社团名称">{{ club.name }}</el-descriptions-item>
        <el-descriptions-item label="标签">{{ club.tags || '-' }}</el-descriptions-item>
        <el-descriptions-item label="成员总数">{{ members.length }}</el-descriptions-item>
        <el-descriptions-item label="简介" :span="3">{{ club.intro || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else-if="!loading" description="您尚未管理任何社团" />
    </el-card>

    <!-- 活动统计列表 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header><b>活动数据</b></template>
      <el-table :data="activities" border size="small">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="活动名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.isJoint === 1 ? 'warning' : 'info'">
              {{ row.isJoint === 1 ? '联合' : '普通' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="160" />
        <el-table-column prop="capacity" label="名额" width="80" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ row.statusDesc }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报名情况" width="120">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="viewSignups(row)">
              查看报名({{ (signups[row.id] || []).length }})
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="activities.length === 0" description="暂无活动" />
    </el-card>

    <!-- AI 分析结果展示（编辑确认后展示在页面上） -->
    <el-card v-if="analysisResult" shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <b>AI 运营分析结果（来源：{{ analysisSource }}）</b>
        </div>
      </template>
      <div class="analysis-text">{{ analysisResult }}</div>
    </el-card>

    <!-- 【复用组件】AI 生成弹窗：CLUB_ANALYSE 技能，传入社团ID -->
    <AiGenerateDialog
      v-model="aiVisible"
      skill-type="CLUB_ANALYSE"
      :biz-id="club ? club.id : null"
      title="AI 运营分析"
      @confirm="onAiConfirm"
    />

    <!-- 报名列表弹窗 -->
    <el-dialog v-model="signupVisible" title="活动报名列表" width="560px">
      <el-table :data="currentSignups" border size="small">
        <el-table-column prop="userName" label="姓名" />
        <el-table-column prop="studentNo" label="学号" />
        <el-table-column prop="clubName" label="所属社团" />
        <el-table-column label="签到状态">
          <template #default="{ row }">
            <el-tag :type="row.checked === 1 ? 'success' : 'info'" size="small">
              {{ row.checked === 1 ? '已签到' : '未签到' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
// 社团统计页（社团负责人）：成员数、活动数据、报名情况 + AI 运营分析（AI分析按钮在这里）
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyClubs, getClubDetail, getMemberList, getActivityList, getSignupList } from '@/api'
import AiGenerateDialog from '@/components/ai-generate-dialog.vue'

const club = ref(null)
const members = ref([])
const activities = ref([])
const signups = ref({}) // activityId -> 报名列表
const loading = ref(false)

// AI 分析结果（确认后展示在页面上）
const aiVisible = ref(false)
const analysisResult = ref('')
const analysisSource = ref('')

// 报名弹窗
const signupVisible = ref(false)
const currentSignups = ref([])

// 活动状态 -> el-tag 颜色
const statusTagType = (s) =>
  ({ 0: 'info', 1: 'warning', 2: 'warning', 3: 'success', 4: 'info', 5: 'danger' }[s] || 'info')

// 打开 AI 分析弹窗
const openAi = () => {
  if (!club.value) {
    ElMessage.warning('暂无社团信息')
    return
  }
  aiVisible.value = true
}

// AI 确认回调：展示分析结果（统计页无表单，直接展示在页面）
const onAiConfirm = (content) => {
  analysisResult.value = content
  analysisSource.value = '弹窗内已标注'
  ElMessage.success('AI 分析结果已确认')
}

// 查看某活动报名列表
const viewSignups = async (row) => {
  currentSignups.value = signups.value[row.id] || (await loadSignups(row.id))
  signupVisible.value = true
}

const loadSignups = async (activityId) => {
  try {
    const data = (await getSignupList(activityId)) || []
    signups.value = { ...signups.value, [activityId]: data }
    return data
  } catch (e) {
    return []
  }
}

onMounted(async () => {
  loading.value = true
  try {
    const myClubs = (await getMyClubs()) || []
    if (myClubs.length > 0) {
      const clubId = myClubs[0].id
      const [detail, mem, acts] = await Promise.all([
        getClubDetail(clubId),
        getMemberList(clubId),
        getActivityList({})
      ])
      club.value = detail
      members.value = mem || []
      activities.value = acts || []
    }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.analysis-text {
  white-space: pre-wrap;
  line-height: 1.8;
  font-size: 14px;
}
</style>
