<template>
  <div>
    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div class="card-header">
          <b>社团信息</b>
          <el-tag v-if="club" :type="club.status === 1 ? 'success' : 'warning'">
            {{ club.status === 1 ? '正常运营' : club.status === 0 ? '待审核' : '已驳回' }}
          </el-tag>
        </div>
      </template>
      <el-descriptions v-if="club" :column="2" border>
        <el-descriptions-item label="社团名称">{{ club.name }}</el-descriptions-item>
        <el-descriptions-item label="标签">{{ club.tags || '-' }}</el-descriptions-item>
        <el-descriptions-item label="简介" :span="2">{{ club.intro || '-' }}</el-descriptions-item>
        <el-descriptions-item label="会长ID">{{ club.leaderId }}</el-descriptions-item>
        <el-descriptions-item label="社团ID">{{ club.id }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else-if="!loading" description="您尚未管理任何社团" />
    </el-card>

    <!-- AI 通知润色：负责人发站内消息前的文案打磨工具（skill=NOTICE_POLISH） -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <b>通知文案润色</b>
          <el-button type="primary" plain size="small" @click="openPolish">
            <el-icon><MagicStick /></el-icon>
            润色草稿
          </el-button>
        </div>
      </template>
      <el-form label-width="90px" style="max-width: 680px">
        <el-form-item label="通知草稿">
          <el-input
            v-model="noticeDraft"
            type="textarea"
            :rows="5"
            placeholder="粘贴或输入待发送的通知草稿，AI 会按选定风格改写并保留原意"
          />
        </el-form-item>
        <el-form-item label="通知风格">
          <el-radio-group v-model="noticeStyle">
            <el-radio-button value="正式">正式</el-radio-button>
            <el-radio-button value="亲切">亲切</el-radio-button>
            <el-radio-button value="紧急">紧急</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 【复用组件】AI 通知润色弹窗：text=草稿正文，style=风格，可切换风格重新生成 -->
    <AiGenerateDialog
      v-model="polishVisible"
      skill-type="NOTICE_POLISH"
      v-model:text="polishText"
      v-model:style="polishStyle"
      title="AI 通知润色"
      @confirm="onPolishConfirm"
    />
  </div>
</template>

<script setup>
// 社团信息页（社团负责人）：展示我管理的社团详情
// - 额外提供「AI 通知润色」：输入草稿 + 选风格 -> 生成润色稿（skill=NOTICE_POLISH），
//   仅回填展示供复制使用，不直接发送任何消息
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyClubs, getClubDetail } from '@/api'
import AiGenerateDialog from '@/components/ai-generate-dialog.vue'

const club = ref(null)
const loading = ref(false)

// ============ AI 通知润色 ============
const noticeDraft = ref('')      // 页面上的草稿输入
const noticeStyle = ref('正式')   // 页面上的风格选择
const polishVisible = ref(false)
const polishText = ref('')        // 传给弹窗的草稿正文
const polishStyle = ref('正式')    // 传给弹窗的风格

// 打开润色弹窗：把页面草稿与风格带入组件
const openPolish = () => {
  if (!noticeDraft.value || !noticeDraft.value.trim()) {
    ElMessage.warning('请先输入通知草稿，AI 将据此润色')
    return
  }
  polishText.value = noticeDraft.value.trim()
  polishStyle.value = noticeStyle.value || '正式'
  polishVisible.value = true
}

// 润色结果确认：回填到草稿框，便于负责人继续编辑或复制到消息发送
const onPolishConfirm = (content) => {
  noticeDraft.value = content
  ElMessage.success('润色结果已回填到草稿框，可继续编辑后用于发送通知')
}

onMounted(async () => {
  loading.value = true
  try {
    const myClubs = (await getMyClubs()) || []
    if (myClubs.length > 0) {
      // 展示我管理的第一个社团详情
      club.value = await getClubDetail(myClubs[0].id)
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
</style>
