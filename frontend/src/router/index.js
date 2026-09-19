import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

/**
 * 路由配置
 * - meta.roles 限定可访问角色（ADMIN=学校管理员 / CLUB_LEADER=社团负责人 / STUDENT=学生）
 * - 全局前置守卫：未登录跳转登录页；角色不匹配跳转到自己角色的首页
 */
const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/login/index.vue'), meta: { title: '登录' } },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/student/activityList',
    children: [
      // ===== 学校管理员 =====
      {
        path: 'admin/clubAudit',
        name: 'adminClubAudit',
        component: () => import('@/views/admin/clubAudit.vue'),
        meta: { title: '社团审核', roles: ['ADMIN'] }
      },
      {
        path: 'admin/activityAudit',
        name: 'adminActivityAudit',
        component: () => import('@/views/admin/activityAudit.vue'),
        meta: { title: '活动审批', roles: ['ADMIN'] }
      },
      {
        path: 'admin/dataOverview',
        name: 'adminDataOverview',
        component: () => import('@/views/admin/dataOverview.vue'),
        meta: { title: '全局数据', roles: ['ADMIN'] }
      },
      // ===== 社团负责人 =====
      {
        path: 'clubManager/clubInfo',
        name: 'clubManagerClubInfo',
        component: () => import('@/views/clubManager/clubInfo.vue'),
        meta: { title: '社团信息', roles: ['CLUB_LEADER'] }
      },
      {
        path: 'clubManager/memberManage',
        name: 'clubManagerMemberManage',
        component: () => import('@/views/clubManager/memberManage.vue'),
        meta: { title: '成员管理', roles: ['CLUB_LEADER'] }
      },
      {
        path: 'clubManager/activityCreate',
        name: 'clubManagerActivityCreate',
        component: () => import('@/views/clubManager/activityCreate.vue'),
        meta: { title: '新建活动', roles: ['CLUB_LEADER'] }
      },
      {
        path: 'clubManager/jointActivity',
        name: 'clubManagerJointActivity',
        component: () => import('@/views/clubManager/jointActivity.vue'),
        meta: { title: '联合活动', roles: ['CLUB_LEADER'] }
      },
      {
        path: 'clubManager/clubStatistics',
        name: 'clubManagerClubStatistics',
        component: () => import('@/views/clubManager/clubStatistics.vue'),
        meta: { title: '社团统计', roles: ['CLUB_LEADER'] }
      },
      {
        path: 'clubManager/handover',
        name: 'clubManagerHandover',
        component: () => import('@/views/clubManager/handover.vue'),
        meta: { title: '换届管理', roles: ['CLUB_LEADER'] }
      },
      {
        path: 'clubManager/resourceLibrary',
        name: 'clubManagerResourceLibrary',
        component: () => import('@/views/clubManager/resourceLibrary.vue'),
        meta: { title: '社团资料库', roles: ['CLUB_LEADER'] }
      },
      // ===== 学生 =====
      {
        path: 'student/clubList',
        name: 'studentClubList',
        component: () => import('@/views/student/clubList.vue'),
        meta: { title: '社团列表', roles: ['STUDENT'] }
      },
      {
        path: 'student/activityList',
        name: 'studentActivityList',
        component: () => import('@/views/student/activityList.vue'),
        meta: { title: '活动大厅', roles: ['STUDENT'] }
      },
      {
        path: 'student/mySignUp',
        name: 'studentMySignUp',
        component: () => import('@/views/student/mySignUp.vue'),
        meta: { title: '我的报名', roles: ['STUDENT'] }
      },
      // ===== 公共：站内消息 =====
      {
        path: 'messages',
        name: 'messages',
        component: () => import('@/views/common/messages.vue'),
        meta: { title: '消息中心', roles: ['ADMIN', 'CLUB_LEADER', 'STUDENT'] }
      }
    ]
  },
  // 兜底：未匹配路径跳转登录页
  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 各角色登录后的默认首页
export const roleHome = {
  ADMIN: '/admin/dataOverview',
  CLUB_LEADER: '/clubManager/clubInfo',
  STUDENT: '/student/activityList'
}

// 全局前置守卫
router.beforeEach((to) => {
  const userStore = useUserStore()
  // 未登录只能访问登录页
  if (!userStore.isLoggedIn && to.path !== '/login') {
    return '/login'
  }
  // 已登录访问登录页时跳转到角色首页
  if (userStore.isLoggedIn && to.path === '/login') {
    return roleHome[userStore.roleCodes[0]] || '/login'
  }
  // 角色权限校验：无权限则跳转本角色首页
  if (to.meta && to.meta.roles && to.meta.roles.length > 0) {
    const allowed = to.meta.roles.some((r) => userStore.hasRole(r))
    if (!allowed) {
      return roleHome[userStore.roleCodes[0]] || '/login'
    }
  }
  return true
})

export default router
