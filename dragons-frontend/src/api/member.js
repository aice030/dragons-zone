/**
 * 成员相关 API（预留，后端接口尚未实现）
 *
 * 当前成员简介数据来自前端配置 src/config/members.js。
 * 待后端 GET /api/member/{memberId}/intro 接口就绪后，可在此实现并切换数据源。
 */

import api from './media'

/**
 * 获取成员简介信息（占位，当前未使用）
 * @param {number} memberId - 成员用户ID
 * @returns {Promise<Object>} 成员简介数据
 *
 * 预留接口：GET /api/member/{memberId}/intro
 */
export function getMemberIntro(memberId) {
  return api.get(`/api/member/${memberId}/intro`)
}
