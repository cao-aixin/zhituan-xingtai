<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <b>社团列表</b>
          <div>
            <el-button type="primary" plain @click="applyVisible = true">
              <el-icon><Plus /></el-icon>
              申请创建社团
            </el-button>
          </div>
        </div>
      </template>

      <el-row :gutter="16" v-loading="loading">
        <el-col :span="8" v-for="c in clubs" :key="c.id" style="margin-bottom: 16px">
          <el-card shadow="hover">
            <div class="club-name">
              {{ c.name }}
              <el-tag v-if="c.status === 1" type="success" size="small">正常</el-tag>
            </div>
            <div class="club-tags">
              <el-tag v-for="t in (c.tags || '').split(',').filter(Boolean)" :key="t" size="small" type="info" style="margin-right: 6px">
                {{ t }}
              </el-tag>
            </div>
            <div class="club-intro">{{ c.intro || '暂无简介' }}</div>
            <div style="text-align: right">
              <el-button type="primary" size="small" @click="handleJoin(c)">申请入社</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-if="!loading && clubs.length === 0" description="暂无社团" />
    </el-card>

    <!-- 申请创建社团弹窗 -->
    <el-dialog v-model="applyVisible" title="申请创建社团" width="480px">
      <el-form ref="applyFormRef" :model="applyForm" :rules="applyRules" label-width="80px">
        <el-form-item label="社团名称" prop="name">
          <el-input v-model="applyForm.name" placeholder="请输入社团名称" />
        </el-form-item>
        <el-form-item label="标签" prop="tags">
          <el-input v-model="applyForm.tags" placeholder="多个标签用英文逗号分隔，如：文艺,思辨" />
        </el-form-item>
        <el-form-item label="简介" prop="intro">
          <el-input v-model="applyForm.intro" type="textarea" :rows="4" placeholder="请输入社团简介" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="applyLoading" @click="handleApply">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 社团列表页（学生）：浏览正常社团、申请入社、申请创建社团
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getClubList, joinClub, applyClub } from '@/api'

const clubs = ref([])
const loading = ref(false)

// 申请创建社团弹窗
const applyVisible = ref(false)
const applyLoading = ref(false)
const applyFormRef = ref()
const applyForm = reactive({ name: '', tags: '', intro: '' })
const applyRules = {
  name: [{ required: true, message: '请输入社团名称', trigger: 'blur' }],
  intro: [{ required: true, message: '请输入社团简介', trigger: 'blur' }]
}

const loadClubs = async () => {
  loading.value = true
  try {
    // 学生视角：后端自动只返回正常社团
    clubs.value = (await getClubList()) || []
  } finally {
    loading.value = false
  }
}

// 申请入社（进入待审核，由社团负责人审核）
const handleJoin = async (c) => {
  await ElMessageBox.confirm(`确认申请加入「${c.name}」？申请后需社团负责人审核。`, '提示', {
    type: 'warning'
  })
  await joinClub(c.id)
  ElMessage.success('入社申请已提交，等待社团负责人审核')
}

// 提交创建社团申请（申请人自动成为会长，社团进入待审核）
const handleApply = () => {
  applyFormRef.value.validate(async (valid) => {
    if (!valid) return
    applyLoading.value = true
    try {
      await applyClub({ ...applyForm })
      ElMessage.success('创建申请已提交，等待学校管理员审核')
      applyVisible.value = false
      Object.assign(applyForm, { name: '', tags: '', intro: '' })
    } finally {
      applyLoading.value = false
    }
  })
}

onMounted(loadClubs)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.club-name {
  font-size: 16px;
  font-weight: bold;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.club-tags {
  margin: 8px 0;
}
.club-intro {
  color: #909399;
  font-size: 13px;
  min-height: 40px;
  margin-bottom: 8px;
}
</style>
