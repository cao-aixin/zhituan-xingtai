<template>
  <el-container class="layout-container">
    <!-- 左侧边栏 -->
    <el-aside width="220px" class="layout-aside">
      <div class="logo">智团星台</div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#001529"
        text-color="#c8c9cc"
        active-text-color="#ffffff"
      >
        <el-menu-item
          v-for="item in menus"
          :key="item.path"
          :index="item.path"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
        <!-- 消息中心：展示待办未读数角标 -->
        <el-menu-item index="/messages">
          <el-icon><Bell /></el-icon>
          <template #title>
            <span>消息中心</span>
            <el-badge v-if="todoUnread > 0" :value="todoUnread" class="menu-badge" />
          </template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="layout-header">
        <div class="header-title">{{ $route.meta.title || '' }}</div>
        <div class="header-right">
          <el-tag size="small" type="info" class="role-tag">{{ roleName }}</el-tag>
          <span class="user-name">{{ userStore.name }}</span>
          <el-button link type="danger" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <!-- 主内容区 -->
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>

    <!-- 【全角色可见】AI 活动问答助手：右下角悬浮按钮 + 聊天面板（skill=ACTIVITY_QA） -->
    <AiChatAssistant />
  </el-container>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getUnreadCount, logout } from '@/api'
// AI 活动问答助手（独立组件，全角色可见）
import AiChatAssistant from '@/components/ai-chat-assistant.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 角色码 -> 中文名
const roleName = computed(() => {
  const map = { ADMIN: '学校管理员', CLUB_LEADER: '社团负责人', STUDENT: '学生' }
  return userStore.roleCodes.map((r) => map[r] || r).join(' / ')
})

// 按当前角色过滤菜单
const roleMenus = {
  ADMIN: [
    { path: '/admin/dataOverview', title: '全局数据', icon: 'DataBoard' },
    { path: '/admin/clubAudit', title: '社团审核', icon: 'OfficeBuilding' },
    { path: '/admin/activityAudit', title: '活动审批', icon: 'Checked' }
  ],
  CLUB_LEADER: [
    { path: '/clubManager/clubInfo', title: '社团信息', icon: 'OfficeBuilding' },
    { path: '/clubManager/memberManage', title: '成员管理', icon: 'User' },
    { path: '/clubManager/activityCreate', title: '新建活动', icon: 'Plus' },
    { path: '/clubManager/jointActivity', title: '联合活动', icon: 'Connection' },
    { path: '/clubManager/clubStatistics', title: '社团统计', icon: 'TrendCharts' },
    { path: '/clubManager/handover', title: '换届管理', icon: 'Switch' },
    { path: '/clubManager/resourceLibrary', title: '社团资料库', icon: 'FolderOpened' }
  ],
  STUDENT: [
    { path: '/student/activityList', title: '活动大厅', icon: 'Calendar' },
    { path: '/student/clubList', title: '社团列表', icon: 'Grid' },
    { path: '/student/mySignUp', title: '我的报名', icon: 'Tickets' }
  ]
}
const menus = computed(() => {
  const list = []
  // 可能存在多角色，聚合所有角色可见菜单
  for (const code of userStore.roleCodes) {
    for (const m of roleMenus[code] || []) {
      if (!list.find((x) => x.path === m.path)) list.push(m)
    }
  }
  return list
})

// 待办消息未读数（首页/侧边栏角标）
const todoUnread = ref(0)
const refreshUnread = async () => {
  try {
    const data = await getUnreadCount({ msgType: 0 }) // 0=待办任务
    todoUnread.value = data.unreadCount || 0
  } catch (e) {
    /* 静默失败，不打断页面 */
  }
}

// 路由切换时刷新角标 + 定时轮询
watch(() => route.path, refreshUnread)
let timer = null
onMounted(() => {
  refreshUnread()
  timer = setInterval(refreshUnread, 30000)
})
onUnmounted(() => clearInterval(timer))

// 退出登录
const handleLogout = async () => {
  try {
    await logout()
  } catch (e) {
    /* 忽略登出接口异常 */
  }
  userStore.clearUser()
  router.push('/login')
}
</script>

<style scoped>
.layout-container {
  height: 100%;
}
.layout-aside {
  background-color: #001529;
}
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  letter-spacing: 2px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}
.el-menu {
  border-right: none;
}
.menu-badge {
  margin-left: 8px;
  vertical-align: middle;
}
.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}
.header-title {
  font-size: 16px;
  font-weight: bold;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.role-tag {
  background: #f0f2f5;
}
.user-name {
  font-size: 14px;
}
.layout-main {
  background: #f5f7fa;
}
</style>
