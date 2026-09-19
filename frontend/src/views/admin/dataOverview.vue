<template>
  <div>
    <!-- 统计卡片：由各列表接口汇总计算 -->
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="社团总数" :value="stats.clubCount" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="活动总数" :value="stats.activityCount" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="进行中活动" :value="stats.ongoingCount" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="用户总数" :value="stats.userCount" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 活动列表概览 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header><b>活动概览</b></template>
      <el-table :data="activities" v-loading="loading" border size="small">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="活动名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="clubName" label="发起社团" width="130" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.isJoint === 1 ? 'warning' : 'info'">
              {{ row.isJoint === 1 ? '联合' : '普通' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="160" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.statusDesc }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- AI 调用记录（最近50条） -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header><b>AI 调用记录（最近50条）</b></template>
      <el-table :data="aiRecords" border size="small">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="skill" label="技能" width="180" />
        <el-table-column prop="source" label="来源" width="120" />
        <el-table-column prop="statusDesc" label="状态" width="100" />
        <el-table-column prop="createTime" label="调用时间" width="170" />
        <el-table-column prop="result" label="结果摘要" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
// 全局数据页（学校管理员）：汇总社团/活动/用户数据 + AI 调用记录
// 说明：api.md 未提供独立统计接口，此处由列表接口汇总计算
import { onMounted, ref, computed } from 'vue'
import { getClubList, getActivityList, getUserList, getAiRecords } from '@/api'

const clubs = ref([])
const activities = ref([])
const users = ref([])
const aiRecords = ref([])
const loading = ref(false)

const stats = computed(() => ({
  clubCount: clubs.value.length,
  activityCount: activities.value.length,
  ongoingCount: activities.value.filter((a) => a.status === 3).length,
  userCount: users.value.length
}))

// 活动状态 -> el-tag 颜色
const statusTagType = (s) =>
  ({ 0: 'info', 1: 'warning', 2: 'warning', 3: 'success', 4: 'info', 5: 'danger' }[s] || 'info')

onMounted(async () => {
  loading.value = true
  try {
    const [c, a, u, r] = await Promise.all([
      getClubList(),
      getActivityList(),
      getUserList(),
      getAiRecords()
    ])
    clubs.value = c || []
    activities.value = a || []
    users.value = u.records || u || [] // 兼容分页/非分页返回
    aiRecords.value = r || []
  } finally {
    loading.value = false
  }
})
</script>
