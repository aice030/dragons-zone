import api from './media'

/**
 * 根据用户ID获取昵称
 * @param {number} userId - 用户ID
 * @returns {Promise} 用户昵称
 */
export function getNickNameById(userId) {
  return api.get(`/api/user/${userId}/nickname`)
}

/**
 * 用户登录
 * @param {string} loginName - 登录名
 * @param {string} password - 密码
 * @returns {Promise} { token, userInfo: { id, loginName, nickName, level } }
 */
export function login(loginName, password) {
  return api.post('/api/user/login', { loginName, password })
}

/**
 * 用户注册
 * @param {Object} data - { loginName, password, nickName, phoneNumber }
 * @returns {Promise} { userId, loginName }
 */
export function register(data) {
  return api.post('/api/user/register', data)
}

/**
 * 通过手机号修改密码（需登录，手机号须与当前账号绑定一致）
 * @param {string} phoneNumber - 11 位手机号
 * @param {string} newPassword - 新密码
 * @returns {Promise}
 */
export function resetPasswordByPhone(phoneNumber, newPassword) {
  return api.post('/api/user/resetPasswordByPhone', { phoneNumber, newPassword })
}

/**
 * 找回密码（未登录，通过登录名+手机号验证身份后设置新密码）
 * @param {string} loginName - 登录名
 * @param {string} phoneNumber - 11 位手机号（需与账号绑定一致）
 * @param {string} newPassword - 新密码
 * @returns {Promise}
 */
export function forgotPassword(loginName, phoneNumber, newPassword) {
  return api.post('/api/user/forgotPassword', { loginName, phoneNumber, newPassword })
}

/**
 * 注销账号（需登录，密码二次确认）
 * @param {string} password - 当前密码
 * @returns {Promise}
 */
export function deregister(password) {
  return api.post('/api/user/deregister', { password })
}

/**
 * 获取用户列表（分页，仅作者可操作）
 * @param {number} page - 页码（默认1）
 * @param {number} size - 每页数量（默认20，最大100）
 * @returns {Promise} { data: { total, list: [{ id, nickName, level, state }] } }
 */
export function getUserList(page = 1, size = 20) {
  return api.get('/api/user/list', { params: { page, size } })
}

/**
 * 修改用户等级（仅作者/管理员可操作）
 * @param {number} targetUserId - 目标用户ID
 * @param {number} level - 新等级（0=作者，1=管理员，2=普通用户，3=游客）
 * @returns {Promise}
 */
export function updateUserLevel(targetUserId, level) {
  return api.put(`/api/user/${targetUserId}/level`, { level })
}

/**
 * 修改用户状态（仅作者/管理员可操作）
 * @param {number} targetUserId - 目标用户ID
 * @param {number} state - 新状态（0=正常，1=逻辑删除，2=黑名单）
 * @returns {Promise}
 */
export function updateUserState(targetUserId, state) {
  return api.put(`/api/user/${targetUserId}/state`, { state })
}

/**
 * 记录用户上传前承诺（需登录）
 * @param {number} currentUserId - 当前登录用户ID（必须与后端 JWT 一致）
 * @returns {Promise} 通用 Result
 */
export function recordUploadPromise(currentUserId) {
  return api.post(`/api/user/${currentUserId}/promise`)
}
