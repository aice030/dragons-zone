import api from './media'

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
