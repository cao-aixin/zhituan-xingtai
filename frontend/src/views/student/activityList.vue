<template>
  <div>
    <!-- AI 个性化推荐：页面加载完毕自动调用 -->
    <el-card shadow="never" class="ai-card">
      <template #header>
        <div class="card-header">
          <b>
            <el-icon><MagicStick /></el-icon>
            AI 个性化推荐活动
          </b>
          <el-tag v-if="recommendSource" :type="recommendSource === 'AI大模型' ? 'success' : 'warning'" size="small">
            来源：{{ recommendSource }}
          </el-tag>
        </div>
      </template>
      <div v-if="recommendLoading" class="ai-loading">
        <el-icon class="is-loading" :size="24"><Loading /></el-icon>
        <span>AI 正在根据您的兴趣为您推荐活动…</span>
      </div>
      <div v-else-if="recommendContent" class="recommend-text">{{ recommendContent }}</div>
      <el-empty v-else description="暂无推荐内容" :image-size="60" />
    </el-card>

    <!-- 活动列表（学生视角：进行中/已结束） -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header><b>全部活动</b></template>
      <el-table :data="activities" v-loading="loading" border>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="活动名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="clubName" label="发起社团" width="130" />
        <el-table-column prop="location" label="地点" width="130" show-overflow-tooltip />
        <el-table-column prop="startTime" label="开始时间" width="160" />
        <el-table-column prop="capacity" label="名额" width="70" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.statusDesc }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 3">
              <el-button
                type="primary"
                size="small"
                :disabled="row.signed"
                @click="handleSignup(row)"
              >
                {{ row.signed ? '已报名' : '报名' }}
              </el-button>
              <el-button size="small" @click="openCheckin(row)">签到</el-button>
            </template>
            <span v-else class="gray">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 签到弹窗：输入4位签到码 -->
    <el-dialog v-model="checkinVisible" title="活动签到" width="400px">
      <el-form label-width="80px">
        <el-form-item label="活动">
          <span>{{ checkinTarget ? checkinTarget.title : '' }}</span>
        </el-form-item>
        <el-form-item label="签到码">
          <el-input v-model="checkinCode" placeholder="请输入4位签到码" maxlength="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="checkinVisible = false">取消</el-button>
        <el-button type="primary" @click="doCheckin">签到</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 活动大厅（学生首页）：加载完毕自动调 AI 个性化推荐活动列表；支持报名与签到
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getActivityList, signupActivity, checkinActivity, aiGenerate } from '@/api'

const activities = ref([])
const loading = ref(false)

// AI 推荐状态
const recommendLoading = ref(false)
const recommendContent = ref('')
const recommendSource = ref('')

// 签到弹窗
const checkinVisible = ref(false)
const checkinTarget = ref(null)
const checkinCode = ref('')

// 活动状态 -> el-tag 颜色
const statusTagType = (s) =>
  ({ 0: 'info', 1: 'warning', 2: 'warning', 3: 'success', 4: 'info', 5: 'danger' }[s] || 'info')

const loadActivities = async () => {
  loading.value = true
  try {
    // 学生视角：后端自动返回进行中/已结束活动
    activities.value = (await getActivityList({})) || []
  } finally {
    loading.value = false
  }
}

// 自动加载 AI 个性化推荐（学生登录首页）
const loadRecommend = async () => {
  recommendLoading.value = true
  try {
    const data = await aiGenerate({ skill: 'ACTIVITY_RECOMMEND' })
    recommendContent.value = data.content || ''
    recommendSource.value = data.source || ''
  } catch (e) {
    // AI 失败不影响活动列表展示
    recommendContent.value = ''
  } finally {
    recommendLoading.value = false
  }
}

// 报名活动（仅进行中，后端校验重复与名额）
const handleSignup = async (row) => {
  await signupActivity(row.id)
  ElMessage.success('报名成功')
  loadActivities()
}

// 打开签到弹窗
const openCheckin = (row) => {
  checkinTarget.value = row
  checkinCode.value = ''
  checkinVisible.value = true
}

// 提交签到
const doCheckin = async () => {
  if (!checkinCode.value.trim()) {
    ElMessage.warning('请输入签到码')
    return
  }
  await checkinActivity({ activityId: checkinTarget.value.id, checkinCode: checkinCode.value.trim() })
  ElMessage.success('签到成功')
  checkinVisible.value = false
}

onMounted(() => {
  loadActivities()
  loadRecommend() // 首页加载完毕自动调 AI 个性化推荐
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.ai-card {
  background: linear-gradient(135deg, #f0f5ff 0%, #fff 60%);
}
.ai-loading {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 0;
  color: #909399;
}
.recommend-text {
  white-space: pre-wrap;
  line-height: 1.8;
  font-size: 14px;
}
.gray {
  color: #909399;
}
</style>
