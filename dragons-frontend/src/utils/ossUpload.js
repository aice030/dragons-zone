/**
 * 使用预签名 PUT URL 将文件上传到对象存储
 *
 * @param {string} uploadUrl - 后端返回的预签名 PUT URL
 * @param {File|Blob} file - 要上传的文件
 * @returns {Promise<void>}
 */
export async function uploadFileToOss(uploadUrl, file) {
  if (!uploadUrl) {
    throw new Error('缺少上传地址 uploadUrl')
  }
  if (!file) {
    throw new Error('缺少待上传文件')
  }

  // 用无 type 的 Blob 发送，避免浏览器对 File 自动加 Content-Type 导致与预签名不一致（403）
  const body =
    file instanceof File && file.type
      ? new Blob([await file.arrayBuffer()], { type: '' })
      : file

  const response = await fetch(uploadUrl, {
    method: 'PUT',
    headers: {},
    body
  })

  if (!response.ok) {
    throw new Error(`OSS 上传失败（HTTP ${response.status}）`)
  }
}

/**
 * 使用 OSS SDK 分片上传（需准备上传返回的 stsCredentials）
 *
 * @param {Object} credentials - 准备上传返回的 stsCredentials
 * @param {string} storagePath - 对象存储路径（object key）
 * @param {File} file - 要上传的文件
 * @param {{ onProgress?: (percent: number) => void }} options - onProgress 回调，传入 0～1 的进度，前端可乘 100 显示
 * @returns {Promise<void>}
 */
/** 分片上传时并发上传的分片数量 */
const MULTIPART_PARALLEL = 10
/** 每片大小（字节），OSS 要求 100KB～5GB，常用 1MB～5MB */
const MULTIPART_PART_SIZE = 2 * 1024 * 1024

export async function uploadFileToOssMultipart(credentials, storagePath, file, options = {}) {
  const { onProgress } = options
  if (!credentials || !storagePath || !file) {
    throw new Error('分片上传缺少 credentials、storagePath 或 file')
  }
  console.log('正在执行分块上传，file_size:', file.size)
  const { createOssClient } = await import('@/utils/ossClient')
  const client = createOssClient(credentials)
  await client.multipartUpload(storagePath, file, {
    parallel: MULTIPART_PARALLEL,
    partSize: MULTIPART_PART_SIZE,
    progress: (p) => {
      const percent = typeof p === 'number' ? p : (p && typeof p === 'object' && typeof p.percent === 'number' ? p.percent : 0)
      onProgress?.(Math.min(1, Math.max(0, percent)))
    }
  })
}

