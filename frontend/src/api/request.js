import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

/**
 * axios 统一封装
 * - 请求拦截：自动携带 satoken 请求头（直传 token 值，无 Bearer 前缀）
 * - 响应拦截：code !== 200 视为业务失败，统一弹 msg；401 跳转登录页
 */
const request = axios.create({
  baseURL: '', // 开发模式经 vite 代理转发，生产模式与后端同域部署
  timeout: 60000
})

// 请求拦截器：注入 satoken 认证头
request.interceptors.request.use((config) => {
  const raw = localStorage.getItem('club-user')
  if (raw) {
    try {
      const user = JSON.parse(raw)
      if (user && user.token) {
        config.headers['satoken'] = user.token
      }
    } catch (e) {
      // 忽略本地存储解析异常
    }
  }
  return config
})

// 响应拦截器：统一处理业务码
request.interceptors.response.use(
  (response) => {
    // 文件流（blob）响应直接透传，不做 Result 结构解析（如资料库下载）
    if (response.config.responseType === 'blob') {
      return response
    }
    const res = response.data
    // 统一返回结构 {code, msg, data}
    if (res.code === 200) {
      return res.data
    }
    // 401 未登录/登录过期：清空本地状态并跳转登录页
    if (res.code === 401) {
      localStorage.removeItem('club-user')
      ElMessage.error(res.msg || '登录已过期，请重新登录')
      if (router.currentRoute.value.path !== '/login') {
        router.push('/login')
      }
      return Promise.reject(new Error(res.msg || '未登录'))
    }
    // 其余非 200 视为业务失败，弹出后端 msg
    ElMessage.error(res.msg || '操作失败')
    return Promise.reject(new Error(res.msg || '操作失败'))
  },
  (error) => {
    // 网络层错误
    ElMessage.error('网络异常，请稍后重试')
    return Promise.reject(error)
  }
)

export default request
