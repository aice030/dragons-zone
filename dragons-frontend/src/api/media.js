import axios from 'axios'

// 配置 axios 基础 URL（根据实际后端地址调整）
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 10000
})

// 请求拦截器：添加 JWT Token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器：处理错误
api.interceptors.response.use(
  (response) => {
    return response.data
  },
  (error) => {
    if (error.response?.status === 401) {
      // Token 过期，清除本地存储
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    }
    return Promise.reject(error)
  }
)

/**
 * 获取媒体列表
 * @param {number} page - 页码（默认1）
 * @param {number} size - 每页数量（默认10）
 * @param {number|null} category - 类型筛选：null=混合，0=图片，1=视频
 * @param {number} zoneUserId - 专区ID：0=公共区，其他=成员专区ID
 * @returns {Promise} 媒体列表数据
 */
export function getMediaList(page = 1, size = 20, category = null, zoneUserId = 0) {
  const params = {
    page,
    size,
    currentUserId: zoneUserId
  }
  
  // 只有当 category 不为 null 时才添加参数
  if (category !== null) {
    params.category = category
  }
  
  return api.get('/api/mediaVisible/list', { params })
}

/**
 * 获取媒体详情
 * @param {number} mediaId - 媒体ID
 * @returns {Promise} 媒体详情数据
 */
export function getMediaDetail(mediaId) {
  return api.get(`/api/media/${mediaId}`)
}

/**
 * 获取媒体下载URL（预览URL）
 * @param {number} mediaId - 媒体ID
 * @returns {Promise} 下载URL数据
 */
export function getMediaDownloadUrl(mediaId) {
  return api.get(`/api/media/${mediaId}/download`)
}

/**
 * 获取"我的上传"列表（需登录）
 * @param {number} page - 页码（默认1）
 * @param {number} size - 每页数量（默认10，最大100）
 * @param {number|null} category - 类型筛选：null=全部，0=图片，1=视频
 * @returns {Promise} 我的上传列表数据 { total, list: [{ id, category, state, title, coverPath }] }
 */
export function getMyUploads(page = 1, size = 10, category = null) {
  const params = { page, size }
  if (category !== null) {
    params.category = category
  }
  return api.get('/api/mediaVisible/my/list', { params })
}

/**
 * 获取媒体的可见专区列表（根据媒体ID查询该媒体属于哪些成员专区）
 * @param {number} mediaId - 媒体ID
 * @returns {Promise} 可见专区ID列表 { data: [userId1, userId2, ...] }
 */
export function getMediaVisibleZones(mediaId) {
  return api.get(`/api/mediaVisible/${mediaId}/zones`)
}

/**
 * 更新媒体的可见专区（需登录）
 * @param {number} mediaId - 媒体ID
 * @param {Array<number>} visibleUserIds - 可见专区ID列表
 * @returns {Promise} 更新结果
 */
export function updateMediaVisibleZones(mediaId, visibleUserIds) {
  return api.put(`/api/media/${mediaId}/visible`, null, {
    params: {
      visibleUserIds: JSON.stringify(visibleUserIds)
    }
  })
}

/**
 * 更新媒体基础信息（标题/简介）（需登录）
 * @param {number} mediaId - 媒体ID
 * @param {{title?: string, description?: string}} payload
 * @returns {Promise} 更新结果 { data: { mediaId, storagePath, category, visibleUserIds } }
 */
export function updateMediaBaseInfo(mediaId, { title, description } = {}) {
  const body = new URLSearchParams()
  // 注意：不传某字段则不修改；传空字符串则清空
  if (title !== undefined) body.append('title', title)
  if (description !== undefined) body.append('description', description)
  return api.put(`/api/media/${mediaId}`, body, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
  })
}

/**
 * 更新视频封面（需登录）
 * @param {number} mediaId - 媒体ID
 * @param {File} coverFile - 封面图片文件
 * @returns {Promise} 更新结果 { data: { mediaId, coverPath, coverUrl } }
 */
export function updateMediaCover(mediaId, coverFile) {
  const formData = new FormData()
  formData.append('cover', coverFile)
  // axios 会自动设置 multipart/form-data 边界；无需手动设置 Content-Type
  return api.put(`/api/media/${mediaId}/cover`, formData)
}

/**
 * 删除媒体（需登录；仅上传者本人）
 * @param {number} mediaId - 媒体ID
 * @returns {Promise}
 */
export function deleteMedia(mediaId) {
  return api.delete(`/api/media/${mediaId}/delete`)
}

/**
 * 获取待审核媒体列表（仅作者/管理员可用）
 * GET /api/media/audit/pending
 * @param {number} page
 * @param {number} size
 * @returns {Promise} { data: { total, page, size, list } }
 */
export function getAuditPendingList(page = 1, size = 10) {
  return api.get('/api/media/audit/pending', { params: { page, size } })
}

/**
 * 批量审核通过（仅作者/管理员可用）
 * POST /api/media/audit/approve
 * @param {Array<number>} mediaIds
 */
export function auditApprove(mediaIds = []) {
  return api.post('/api/media/audit/approve', { mediaIds })
}

/**
 * 批量审核驳回（仅作者/管理员可用）
 * POST /api/media/audit/reject
 * @param {Array<number>} mediaIds
 */
export function auditReject(mediaIds = []) {
  return api.post('/api/media/audit/reject', { mediaIds })
}

/**
 * 上传媒体资源（图片/视频）- 单文件上传
 *
 * POST /api/media/upload
 * Content-Type: multipart/form-data
 *
 * FormData 参数：
 * - file: File（必填）
 * - category: 0=图片，1=视频（必填）
 * - visibleUserIds: JSON数组字符串，例如 [] 或 [3,4]（必填，可为空数组）
 * - title: string（可选）
 * - description: string（可选）
 * - cover: File（可选，仅视频建议传）
 */
export function uploadMedia(formData) {
  return api.post('/api/media/upload', formData)
}

export default api
