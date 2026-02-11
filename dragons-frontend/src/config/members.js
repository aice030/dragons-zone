/**
 * 成员专区配置
 * 
 * 说明：
 * - id: 成员的用户ID（对应后端的 user_id）
 * - name: 成员显示名称
 * 
 * 后续可以扩展字段：
 * - avatar: 头像URL
 * - description: 成员简介
 * - order: 显示顺序
 */

// 共12名成员
export const MEMBERS = [
  {id: 3, name:'黑奎·癫长'},
  {id: 4, name:'妍汁西米露'},
  {id: 5, name:'高冷大米'},
  {id: 6, name:'四眼龙'},
  {id: 7, name:'大力仑爱吃菠菜'},
  {id: 8, name:'芒果小牛'},
  {id: 9, name:'邪恶小段'},
  {id: 10, name:'寻宁启示'},
  {id: 11, name:'川西哼唧怪'},
  {id: 12, name:'导眼燕某人'},
  {id: 13, name:'剪辑的神'},
  {id: 14, name:'翟小猪'}
]

/**
 * 获取成员列表
 * @returns {Array} 成员列表
 */
export function getMembers() {
  return MEMBERS
}

/**
 * 根据ID获取成员信息
 * @param {number} id - 成员ID
 * @returns {Object|null} 成员信息
 */
export function getMemberById(id) {
  return MEMBERS.find(member => member.id === id) || null
}
