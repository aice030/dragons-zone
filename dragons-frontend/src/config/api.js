/**
 * API 配置
 * 
 * API 基础 URL 配置优先级：
 * 1. 环境变量 VITE_API_BASE_URL（推荐，可在 .env 文件中设置）
 * 2. 开发环境默认值：http://localhost:8080
 * 3. 生产环境默认值：空字符串（使用相对路径，配合 Nginx 反向代理）
 * 
 * 部署说明：
 * - 本地开发：在 .env 文件中设置 VITE_API_BASE_URL=http://localhost:8080
 * - 生产环境（同机部署）：不设置或设置为空字符串，使用相对路径 /api
 * - 生产环境（跨域）：设置为后端完整地址，如 https://api.yourdomain.com
 */

const getApiBaseURL = () => {
  // 优先使用环境变量
  if (import.meta.env.VITE_API_BASE_URL) {
    return import.meta.env.VITE_API_BASE_URL
  }
  
  // 开发环境默认值
  if (import.meta.env.DEV) {
    return 'http://localhost:8080'
  }
  
  // 生产环境默认使用相对路径（配合 Nginx 反向代理）
  return ''
}

export const API_BASE_URL = getApiBaseURL()
