/**
 * 使用准备上传接口返回的 STS 临时凭证创建阿里云 OSS 客户端（供分片上传等）
 * 凭证来自 prepareUpload 响应的 data.stsCredentials，未配置 STS 时为 null，不可用 SDK 直传。
 */
import OSS from 'ali-oss'

/**
 * 根据 STS 凭证创建 OSS 客户端
 * @param {Object} credentials - 准备上传返回的 stsCredentials：{ accessKeyId, accessKeySecret, securityToken, region, bucket }
 * @returns {OSS} 配置好 STS 的 OSS 实例，可用于 client.put()、client.multipartUpload() 等
 */
function normalizeOssRegion(region) {
  if (!region || typeof region !== 'string') return 'oss-cn-beijing'
  // ali-oss 要求 region 为地域 id，如 oss-cn-beijing，不能带 .aliyuncs.com
  const s = region.trim()
  const idx = s.indexOf('.aliyuncs.com')
  if (idx > 0) return s.slice(0, idx)
  return s || 'oss-cn-beijing'
}

export function createOssClient(credentials) {
  if (!credentials || !credentials.accessKeyId || !credentials.accessKeySecret) {
    throw new Error('无效的 STS 凭证，无法创建 OSS 客户端')
  }
  return new OSS({
    region: normalizeOssRegion(credentials.region),
    accessKeyId: credentials.accessKeyId,
    accessKeySecret: credentials.accessKeySecret,
    stsToken: credentials.securityToken,
    bucket: credentials.bucket
  })
}
