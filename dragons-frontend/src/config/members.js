/**
 * 成员专区配置
 *
 * 头像存放目录：public/images/members/（路径如 /images/members/avatar-3.jpg）
 *
 * 字段说明：
 * - id: 成员的用户ID（对应后端的 user_id）
 * - name: 成员专区标题
 * - waihao: 外号
 * - fullName: 姓名（大名）
 * - intro: 一段文本，个人介绍段落（h）
 * - specialties: 特长（e），字符串如 "游泳、唱歌"
 * - hobbies: 爱好（f）
 * - dream: 梦想（g）
 * - avatar: 头像路径（b，如 /images/members/avatar-3.jpg）
 */

// 共12名成员
export const MEMBERS = [
  {
    id: 3,
    name: '黑奎（癫长版）',
    waihao: '黑奎、黑神、癫长、螳螂小子、最有梗的男人',
    fullName: '原中奎',
    intro: '黑奎、小黑',
    specialties: '黑',
    hobbies: '街舞',
    dream: '赚钱',
    avatar: '/images/members/avatar-3.jpg'
  },
  {
    id: 4,
    name: '妍汁西米露',
    waihao: '黄豆豆、妍妍',
    fullName: '黄妍',
    intro: '',
    specialties: '腿特长',
    hobbies: '手工DIY',
    dream: '开个服装厂',
    avatar: '/images/members/avatar-4.jpg'
  },
  {
    id: 5,
    name: '高冷大米',
    waihao: '小米、大米、平头、片儿姐、百灵鸟',
    fullName: '米欣雨',
    intro: '',
    specialties: '',
    hobbies: '唱歌',
    dream: '环游世界',
    avatar: '/images/members/avatar-5.jpg'
  },
  {
    id: 6,
    name: '四眼龙',
    waihao: '贺贺、凤雏、张飞、四眼龙',
    fullName: '',
    intro: '',
    specialties: '',
    hobbies: '逛街、打扮',
    dream: '体验不一样的人生',
    avatar: '/images/members/avatar-6.jpg'
  },
  {
    id: 7,
    name: '大力仑爱吃菠菜',
    waihao: '大力仑',
    fullName: '李卓伦',
    intro: '',
    specialties: '长城⬇炮⬆！！！',
    hobbies: '跳舞',
    dream: '',
    avatar: '/images/members/avatar-7.jpg'
  },
  {
    id: 8,
    name: '超模牛',
    waihao: '小牛、神罚、芒果小子',
    fullName: '牛可心',
    intro: '',
    specialties: '唱歌、猫步',
    hobbies: '',
    dream: '逛遍全世界',
    avatar: '/images/members/avatar-8.jpg'
  },
  {
    id: 9,
    name: '卡皮巴拉段',
    waihao: '小段、邪恶小段',
    fullName: '段紫荆',
    intro: '',
    specialties: '说唱',
    hobbies: '开小卖部',
    dream: '逛遍全世界',
    avatar: '/images/members/avatar-9.jpg'
  },
  {
    id: 10,
    name: '寻宁启示',
    waihao: '归宁、微醺',
    fullName: '卢宁宁',
    intro: '',
    specialties: '',
    hobbies: '微醺、上网、睡觉',
    dream: '自己有100w粉丝',
    avatar: '/images/members/avatar-10.jpg'
  },
  {
    id: 11,
    name: '川西哼唧怪',
    waihao: '川西、猩猩、狒狒',
    fullName: '郭茜茜',
    intro: '',
    specialties: '',
    hobbies: '小孩',
    dream: '开一个幼儿园',
    avatar: '/images/members/avatar-11.jpg'
  },
  {
    id: 12,
    name: '导眼燕某人',
    waihao: '燕导、拾粪哥',
    fullName: '燕宇杰',
    intro: '',
    specialties: '清早起来去拾粪～',
    hobbies: '摄影、RC、维修、机械加工、3D打印',
    dream: '当一个有名的科学家导演',
    avatar: '/images/members/avatar-12.png'
  },
  {
    id: 13,
    name: '培宇·剪辑的神',
    waihao: '剪辑的神、培速更',
    fullName: '连培宇',
    intro: '',
    specialties: '',
    hobbies: '剪点小片',
    dream: '把账号做到1000w粉',
    avatar: '/images/members/avatar-13.jpg'
  },
  {
    id: 14,
    name: '翟社长',
    waihao: '翟小猪、翟社长、机械舞之神',
    fullName: '翟京凯',
    intro: '',
    specialties: '机械舞',
    hobbies: '桌球',
    dream: '',
    avatar: '/images/members/avatar-14.jpg'
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
