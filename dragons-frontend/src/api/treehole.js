import api from './media'

/**
 * 获取树洞留言列表
 * @param {number} ownerId - 树洞主人用户ID
 * @param {number} page - 页码（默认1）
 * @param {number} size - 每页数量（默认10）
 * @returns {Promise} 留言列表数据
 */
export function getTreeHoleMessages(ownerId, page = 1, size = 10) {
  return api.get(`/api/treehole/${ownerId}/messages`, {
    params: { page, size }
  })
}

/**
 * 向树洞投递留言
 * @param {number} ownerId - 树洞主人用户ID
 * @param {string} content - 留言内容
 * @param {number|null} rootMessageId - 根消息ID（可选，为空表示投递新留言，非空表示回复）
 * @returns {Promise} 新留言ID
 */
export function sendTreeHoleMessage(ownerId, content, rootMessageId = null) {
  return api.post(`/api/treehole/${ownerId}/sent/messages`, {
    content,
    rootMessageId
  })
}

/**
 * 标记留言为已读（仅树洞主人可操作）
 * @param {number} messageId - 留言ID
 * @returns {Promise}
 */
export function markMessageRead(messageId) {
  return api.put(`/api/treehole/messages/${messageId}/read`)
}

/**
 * 树洞主人删除留言（全局删除）
 * @param {number} messageId - 留言ID
 * @returns {Promise}
 */
export function deleteMessageByOwner(messageId) {
  return api.delete(`/api/treehole/messages/${messageId}`)
}

/**
 * 发送者删除留言（仅对发送者不可见）
 * @param {number} messageId - 留言ID
 * @returns {Promise}
 */
export function deleteMessageBySender(messageId) {
  return api.delete(`/api/treehole/messages/${messageId}/sender`)
}

/**
 * 更新树洞状态（仅树洞主人或管理员可操作）
 * @param {number} ownerId - 树洞主人用户ID
 * @param {number} state - 新状态（0=正常，2=禁止投递）
 * @returns {Promise}
 */
export function updateTreeHoleState(ownerId, state) {
  return api.put(`/api/treehole/${ownerId}/state`, { state })
}

/**
 * 获取树洞信息（用于查询树洞状态）
 * @param {number} ownerId - 树洞主人用户ID
 * @returns {Promise} 树洞信息（包含 state）
 */
export function getTreeHoleInfo(ownerId) {
  return api.get(`/api/treehole/${ownerId}`)
}

/**
 * 分享树洞消息给其他树洞主人
 * @param {number} ownerId - 树洞主人用户ID
 * @param {number} messageId - 消息ID
 * @param {number[]} ownerIds - 接收方树洞主人用户ID列表
 * @returns {Promise}
 */
export function shareTreeHoleMessage(ownerId, messageId, ownerIds) {
  return api.post(`/api/treehole/${ownerId}/messages/${messageId}/share`, {
    ownerIds
  })
}

/**
 * 查询当前用户（树洞主人）是否已拉黑某用户
 * @param {number} blockedUserId - 被查询用户ID
 * @returns {Promise<{ data: boolean }>} data 为 true 表示已拉黑，false 表示未拉黑或已解除
 */
export function checkBlockStatus(blockedUserId) {
  return api.get('/api/treeholeBlacklist/check', {
    params: { blockedUserId }
  })
}

/**
 * 拉黑用户（树洞主人拉黑发送者）
 * @param {number} blockedUserId - 被拉黑用户ID
 * @param {string} reason - 拉黑原因（可选）
 * @returns {Promise}
 */
export function blockUser(blockedUserId, reason = '') {
  return api.post('/api/treeholeBlacklist/block', {
    blockedUserId,
    reason
  })
}

/**
 * 解除拉黑（树洞主人解除对某用户的拉黑）
 * @param {number} blockedUserId - 被解除拉黑的用户ID
 * @returns {Promise}
 */
export function unblockUser(blockedUserId) {
  return api.post('/api/treeholeBlacklist/unblock', {
    blockedUserId
  })
}
