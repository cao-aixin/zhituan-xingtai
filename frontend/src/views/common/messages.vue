<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <b>消息中心</b>
          <el-button size="small" @click="handleReadAll">全部标为已读</el-button>
        </div>
      </template>

      <!-- 消息类型切换：0待办任务 1普通通知 -->
      <el-tabs v-model="msgType" @tab-change="loadList">
        <el-tab-pane label="待办任务" name="0">
          <el-table :data="list" v-loading="loading" border>
            <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
            <el-table-column prop="createTime" label="时间" width="170" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.isRead === 1 ? 'info' : 'danger'" size="small">
                  {{ row.isRead === 1 ? '已读' : '未读' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button v-if="row.isRead === 0" size="small" @click="handleRead(row)">标为已读</el-button>
                <el-button v-if="row.bizId" type="primary" size="small" @click="goHandle(row)">
                  去处理
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="普通通知" name="1">
          <el-table :data="list" v-loading="loading" border>
            <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="content" label="内容" min-width="300" show-overflow-tooltip />
            <el-table-column prop="createTime" label="时间" width="170" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.isRead === 1 ? 'info' : 'danger'" size="small">
                  {{ row.isRead === 1 ? '已读' : '未读' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button v-if="row.isRead === 0" size="small" @click="handleRead(row)">标为已读</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
      <el-empty v-if="!loading && list.length === 0" description="暂无消息" />
    </el-card>
  </div>
</template>

<script setup>
// 消息中心（全角色）：区分待办任务(msgType=0)/普通通知(msgType=1)
// 待办消息带 bizId（关联活动ID），点「去处理」跳转对应处理页
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMessageList, readMessage, readAllMessages } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const msgType = ref('0')
const list = ref([])
const loading = ref(false)

const loadList = async () => {
  loading.value = true
  try {
    list.value = (await getMessageList({ msgType: msgType.value })) || []
  } finally {
    loading.value = false
  }
}

// 标记单条已读
const handleRead = async (row) => {
  await readMessage(row.id)
  loadList()
}

// 全部已读
const handleReadAll = async () => {
  await readAllMessages()
  loadList()
}

// 待办跳转处理页：社团负责人去联合活动页，其余去活动审批/我的报名
const goHandle = (row) => {
  if (userStore.hasRole('CLUB_LEADER')) {
    router.push('/clubManager/jointActivity')
  } else if (userStore.hasRole('ADMIN')) {
    router.push('/admin/activityAudit')
  } else {
    router.push('/student/mySignUp')
  }
}

onMounted(loadList)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
