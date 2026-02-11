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

export default api
