/**
 * 计算文件的哈希值，用于后端幂等（去重）。
 * - 安全上下文（HTTPS/localhost）：使用 SHA-256，保证内容唯一。
 * - 非安全上下文（如 HTTP 公网 IP）：退化为「文件名+大小+修改时间」，不保证全局唯一，仅尽量降低重复。
 *
 * @param {File|Blob} file
 * @returns {Promise<string>} 哈希或伪哈希字符串
 */
export async function computeFileHash(file) {
  if (!file) {
    throw new Error('file is required for hash calculation')
  }

  const hasSubtle = typeof crypto !== 'undefined' && crypto.subtle && typeof crypto.subtle.digest === 'function'
  if (hasSubtle) {
    const arrayBuffer = await file.arrayBuffer()
    const hashBuffer = await crypto.subtle.digest('SHA-256', arrayBuffer)
    const hashArray = Array.from(new Uint8Array(hashBuffer))
    return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('')
  }

  // 非安全上下文（无 crypto.subtle）：退化为元数据拼接，不保证不同文件不会碰撞
  console.warn('computeFileHash: crypto.subtle 不可用，使用元数据伪哈希（建议使用 HTTPS 以获得 SHA-256）')
  const name = file.name != null ? String(file.name) : ''
  const size = file.size != null ? Number(file.size) : 0
  const lastModified = file.lastModified != null ? Number(file.lastModified) : 0
  const type = file.type != null ? String(file.type) : ''
  return `fallback:${name}|${size}|${lastModified}|${type}`
}

