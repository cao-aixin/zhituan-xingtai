<template>
  <div>
    <el-card shadow="never">
      <template #header><b>我的报名</b></template>
      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="id" label="活动ID" width="80" />
        <el-table-column prop="title" label="活动名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="clubName" label="发起社团" width="130" />
        <el-table-column prop="location" label="地点" width="140" show-overflow-tooltip />
        <el-table-column prop="startTime" label="开始时间" width="160" />
        <el-table-column prop="endTime" label="结束时间" width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.statusDesc }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && list.length === 0" description="暂无报名记录" />
    </el-card>
  </div>
</template>

<script setup>
// 我的报名页（学生）：展示我报名的活动列表
import { onMounted, ref } from 'vue'
import { getMySigned } from '@/api'

const list = ref([])
const loading = ref(false)

// 活动状态 -> el-tag 颜色
const statusTagType = (s) =>
  ({ 0: 'info', 1: 'warning', 2: 'warning', 3: 'success', 4: 'info', 5: 'danger' }[s] || 'info')

onMounted(async () => {
  loading.value = true
  try {
    list.value = (await getMySigned()) || []
  } finally {
    loading.value = false
  }
})
</script>
