import axios from 'axios'
import { API_BASE_URL } from '@/config/api'

// 配置 axios 基础 URL
const api = axios.create({
  baseURL: API_BASE_URL,
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
 * 获取热门内容排行榜
 * @param {string|number|null} category - 不传=全部，0=图片，1=视频
 * @param {number} size - 返回条数，默认 20，最大 100
 * @returns {Promise} HotListItem[] { id, category, title, description, coverUrl, likeCount }
 */
export function getMediaRank(category = null, size = 20) {
  const params = { size }
  if (category !== null && category !== undefined && category !== '') {
    params.category = category
  }
  return api.get('/api/mediaVisible/rank', { params })
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
 * 点赞（需登录）
 * POST /api/media/{id}/like
 * @param {number} mediaId - 媒体ID
 * @returns {Promise}
 */
export function likeMedia(mediaId) {
  return api.post(`/api/media/${mediaId}/like`)
}

/**
 * 取消点赞（需登录）
 * POST /api/media/{id}/unlike
 * @param {number} mediaId - 媒体ID
 * @returns {Promise}
 */
export function unlikeMedia(mediaId) {
  return api.post(`/api/media/${mediaId}/unlike`)
}

/**
 * 查询当前用户是否已赞某媒体（需登录）
 * GET /api/userLikeRecord/media/{mediaId}/status
 * @param {number} mediaId - 媒体ID
 * @returns {Promise<{ data: boolean }>} data: true 已点赞，false 未点赞
 */
export function getLikeStatus(mediaId) {
  return api.get(`/api/userLikeRecord/media/${mediaId}/status`)
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
 * @param {number|null} category - 类型筛选：null=全部，0=图片，1=视频
 * @returns {Promise} { data: { total, page, size, list } }
 */
export function getAuditPendingList(page = 1, size = 10, category = null) {
  const params = { page, size }
  if (category !== null) params.category = category
  return api.get('/api/media/audit/pending', { params })
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
 * 准备上传媒体资源（两阶段上传 - 第一步）
 *
 * POST /api/media/upload
 * Content-Type: multipart/form-data
 *
 * FormData 参数：
 * - file_hash: string（必填，前端计算的文件哈希）
 * - category: 0=图片，1=视频（必填）
 * - title: string（可选）
 * - description: string（可选）
 * - filename: string（可选，原始文件名，用于推断扩展名）
 *
 * 返回：
 * - code: 200
 * - data: { mediaId, storagePath, uploadUrl, uploadUrlExpireSeconds }
 */
export function prepareUpload({ fileHash, category, title, description, filename }) {
  const formData = new FormData()
  formData.append('file_hash', fileHash)
  formData.append('category', String(category))
  if (title !== undefined && title !== null && title !== '') {
    formData.append('title', title)
  }
  if (description !== undefined && description !== null && description !== '') {
    formData.append('description', description)
  }
  if (filename) {
    formData.append('filename', filename)
  }
  return api.post('/api/media/upload', formData)
}

/**
 * 通知上传结果（两阶段上传 - 第二步）
 *
 * POST /api/media/upload/complete
 * Content-Type: multipart/form-data
 *
 * FormData 参数：
 * - mediaId: number（必填）
 * - success: boolean（必填）
 * - visibleUserIds: string（必填，JSON 数组字符串）
 * - cover: File（success=true 时必填；success=false 可不传）
 * - code: string（可选，失败时错误码）
 * - message: string（可选，失败时错误描述）
 */
export function uploadComplete({ mediaId, success, visibleUserIds, cover, code, message }) {
  const formData = new FormData()
  formData.append('mediaId', String(mediaId))
  formData.append('success', String(!!success))
  formData.append('visibleUserIds', JSON.stringify(visibleUserIds || []))

  if (success && cover) {
    formData.append('cover', cover)
  }
  if (!success) {
    if (code) formData.append('code', code)
    if (message) formData.append('message', message)
  }

  return api.post('/api/media/upload/complete', formData)
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
