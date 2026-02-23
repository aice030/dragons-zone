/**
 * 计算文件的 SHA-256 哈希值（十六进制字符串）
 * 使用浏览器内置的 Web Crypto API，避免额外依赖。
 *
 * @param {File|Blob} file
 * @returns {Promise<string>} 64位十六进制哈希字符串
 */
export async function computeFileHash(file) {
  if (!file) {
    throw new Error('file is required for hash calculation')
  }

  const arrayBuffer = await file.arrayBuffer()
  const hashBuffer = await crypto.subtle.digest('SHA-256', arrayBuffer)
  const hashArray = Array.from(new Uint8Array(hashBuffer))
  const hashHex = hashArray.map((b) => b.toString(16).padStart(2, '0')).join('')
  return hashHex
}

