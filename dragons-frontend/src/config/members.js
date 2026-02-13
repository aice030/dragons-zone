/**
 * 成员专区配置
 * 
 * 字段说明：
 * - id: 成员的用户ID（对应后端的 user_id）
 * - name: 成员显示名称
 * - nickname: 成员昵称（可选）
 * - intro: 成员介绍（可选）
 * - specialties: 特长列表（可选，数组）
 * - highlights: 名场面文字描述（可选）
 * - avatar: 成员照片URL（可选）
 */

// 共12名成员
export const MEMBERS = [
  {
    id: 3,
    name: '黑奎·癫长',
    nickname: '',
    intro: '',
    specialties: [],
    highlights: '',
    avatar: ''
  },
  {
    id: 4,
    name: '妍汁西米露',
    nickname: '',
    intro: '',
    specialties: [],
    highlights: '',
    avatar: ''
  },
  {
    id: 5,
    name: '高冷大米',
    nickname: '',
    intro: '',
    specialties: [],
    highlights: '',
    avatar: ''
  },
  {
    id: 6,
    name: '四眼龙',
    nickname: '',
    intro: '',
    specialties: [],
    highlights: '',
    avatar: ''
  },
  {
    id: 7,
    name: '大力仑爱吃菠菜',
    nickname: '',
    intro: '',
    specialties: [],
    highlights: '',
    avatar: ''
  },
  {
    id: 8,
    name: '芒果小牛',
    nickname: '',
    intro: '',
    specialties: [],
    highlights: '',
    avatar: ''
  },
  {
    id: 9,
    name: '邪恶小段',
    nickname: '',
    intro: '',
    specialties: [],
    highlights: '',
    avatar: ''
  },
  {
    id: 10,
    name: '寻宁启示',
    nickname: '',
    intro: '',
    specialties: [],
    highlights: '',
    avatar: ''
  },
  {
    id: 11,
    name: '川西哼唧怪',
    nickname: '',
    intro: '',
    specialties: [],
    highlights: '',
    avatar: ''
  },
  {
    id: 12,
    name: '导眼燕某人',
    nickname: '',
    intro: '',
    specialties: [],
    highlights: '',
    avatar: ''
  },
  {
    id: 13,
    name: '剪辑的神',
    nickname: '',
    intro: '',
    specialties: [],
    highlights: '',
    avatar: ''
  },
  {
    id: 14,
    name: '翟小猪',
    nickname: '',
    intro: '',
    specialties: [],
    highlights: '',
    avatar: ''
  }
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
