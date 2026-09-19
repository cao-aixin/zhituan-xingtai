import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * 用户信息与 token 的全局状态（Pinia）
 * 登录成功后保存到本地，刷新页面不丢失
 */
export const useUserStore = defineStore('user', () => {
  // 从 localStorage 恢复登录态
  const user = ref(JSON.parse(localStorage.getItem('club-user') || 'null'))

  const token = computed(() => user.value?.token || '')
  const name = computed(() => user.value?.name || '')
  // 角色码列表：ADMIN / CLUB_LEADER / STUDENT
  const roleCodes = computed(() => user.value?.roleCodes || [])
  const managedClubId = computed(() => user.value?.managedClubId || null)

  const isLoggedIn = computed(() => !!user.value?.token)

  /** 是否拥有指定角色 */
  const hasRole = (roleCode) => roleCodes.value.includes(roleCode)

  /** 保存登录信息 */
  const setUser = (info) => {
    user.value = info
    localStorage.setItem('club-user', JSON.stringify(info))
  }

  /** 退出登录时清空 */
  const clearUser = () => {
    user.value = null
    localStorage.removeItem('club-user')
  }

  return { user, token, name, roleCodes, managedClubId, isLoggedIn, hasRole, setUser, clearUser }
})
