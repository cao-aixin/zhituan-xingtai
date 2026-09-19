import request from './request'

/**
 * 后端 REST API 统一封装（路径与 docs/api.md 一一对应）
 */

// ============ 1. 认证 ============
export const login = (data) => request.post('/api/auth/login', data)
export const logout = () => request.post('/api/auth/logout')
export const getMe = () => request.get('/api/auth/me')

// ============ 2. 用户（管理员） ============
export const getUserList = (params) => request.get('/api/user/list', { params })
export const addUser = (data) => request.post('/api/user', data)
export const getUserDetail = (id) => request.get(`/api/user/${id}`)

// ============ 3. 社团 ============
export const getClubList = (params) => request.get('/api/club/list', { params })
export const getClubDetail = (id) => request.get(`/api/club/${id}`)
export const applyClub = (data) => request.post('/api/club/apply', data)
export const getPendingClubs = () => request.get('/api/club/pending')
export const auditClub = (data) => request.put('/api/club/audit', data)
export const getMyClubs = () => request.get('/api/club/my')

// ============ 4. 社团成员 ============
export const getMemberList = (clubId, params) =>
  request.get(`/api/club/${clubId}/member/list`, { params })
export const joinClub = (clubId) => request.post(`/api/club/${clubId}/member/join`)
export const quitClub = (clubId) => request.post(`/api/club/${clubId}/member/quit`)
export const auditMember = (memberId, approved) =>
  request.put(`/api/club/member/${memberId}/audit`, null, { params: { approved } })

// ============ 5. 活动 ============
export const createActivity = (data) => request.post('/api/activity', data)
export const updateActivity = (data) => request.put('/api/activity', data)
export const submitActivity = (id) => request.post(`/api/activity/${id}/submit`)
export const auditActivity = (id, data) => request.post(`/api/activity/${id}/audit`, data)
export const jointConfirm = (data) => request.post('/api/activity/joint-confirm', data)
export const getActivityList = (params) => request.get('/api/activity/list', { params })
export const getActivityDetail = (id) => request.get(`/api/activity/${id}`)
export const signupActivity = (id) => request.post(`/api/activity/${id}/signup`)
export const checkinActivity = (data) => request.post('/api/activity/signup/checkin', data)
export const getSignupList = (id) => request.get(`/api/activity/${id}/signups`)
export const getMySigned = () => request.get('/api/activity/my-signed')
export const finishActivity = (id) => request.post(`/api/activity/${id}/finish`)

// ============ 6. 站内消息 ============
export const getMessageList = (params) => request.get('/api/message/list', { params })
export const getUnreadCount = (params) => request.get('/api/message/unread-count', { params })
export const readMessage = (id) => request.put(`/api/message/${id}/read`)
export const readAllMessages = () => request.put('/api/message/read-all')

// ============ 7. AI 智能体 ============
// 统一入口：skill 取值 ACTIVITY_PLAN / ACTIVITY_SUMMARY / CLUB_ANALYSE / ACTIVITY_RECOMMEND
export const aiGenerate = (data) => request.post('/api/ai/generate', data)
export const getAiRecords = () => request.get('/api/ai/record/list')

// ============ 8. 文件上传 ============
export const uploadFile = (formData) =>
  request.post('/api/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })

// ============ 9. 社团换届（移植自旧版 zhituan-system） ============
export const handoverClub = (data) => request.post('/api/handover', data)
export const getHandoverList = (params) => request.get('/api/handover/list', { params })

// ============ 10. 社团资料库（移植自旧版 zhituan-system） ============
export const getResourceList = (params) => request.get('/api/resource/list', { params })
export const uploadResource = (formData) =>
  request.post('/api/resource/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
export const downloadResource = (id) =>
  request.get(`/api/resource/download/${id}`, { responseType: 'blob' })
export const deleteResource = (id) => request.delete(`/api/resource/${id}`)
