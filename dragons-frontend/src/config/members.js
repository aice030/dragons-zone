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
 * - mbti: MBTI人格类型
 * - intro: 一段文本，个人介绍段落（h）
 * - hobbies: 爱好（f）
 * - talents: 特长，字符串如 "游泳、唱歌"
 * - specialties: 才艺（e），字符串如 "游泳、唱歌"
 * - dream: 梦想（g）
 * - avatar: 头像路径（b，如 /images/members/avatar-3.jpg）
 */

// 共12名成员
export const MEMBERS = [
  {
    id: 3,
    name: '黑奎（癫长版）',
    waihao: '小黑、黑驴蛋',
    fullName: '原中奎',
    mbti: 'ENTP',
    intro: '癫长，语文课代表，东兴少主，后浪的神（已蹦极版）\n济源邓超 / 黑子韬，黑的男人 ——— 是太阳！',
    hobbies: '女',
    talents: '黑',
    specialties: '街舞，后空翻',
    dream: '赚钱',
    avatar: '/images/members/avatar-3.jpg'
  },
  {
    id: 4,
    name: '妍汁西米露',
    waihao: '黄豆豆、妍妍',
    fullName: '黄妍',
    mbti: 'INTJ',
    intro: '后浪脑子，腿精，美妍相机\n后我浪妍女主，社牛i人兼微社恐摩托御姐（已卖车版）',
    hobbies: '手工DIY',
    talents: '腿特长',
    specialties: '黑桃A',
    dream: '开个服装厂',
    avatar: '/images/members/avatar-4.jpg'
  },
  {
    id: 5,
    name: '高冷大米',
    waihao: '小米、平头、米乖、片儿姐',
    fullName: '米欣雨',
    mbti: 'ENFP',
    intro: '尧仔炒粉推广大使，永远热烈，永远阳光的后浪百灵鸟（腿脚不利索版）\n全网唯一用iphone17 pro max爱马仕橙的老年人',
    hobbies: '骑摩托',
    talents: '逊',
    specialties: '唱歌',
    dream: '环游世界',
    avatar: '/images/members/avatar-5.jpg'
  },
  {
    id: 6,
    name: '四眼龙',
    waihao: '贺贺、凤雏、张飞、四眼龙，安康鱼',
    fullName: '贺欣平',
    mbti: 'ENFP',
    intro: '武将，凤雏，女中张飞，后浪笑点泪点最低之人\n给我洗袜紫⬇贺贺贺贺贺贺贺贺贺贺贺贺贺贺贺贺贺',
    hobbies: '逛街、吃饭、打扮',
    talents: '特别愚蠢',
    specialties: '小兔子乖乖～把门开开～快点开开～我要回来～',
    dream: '体验不一样的人生',
    avatar: '/images/members/avatar-6.jpg'
  },
  {
    id: 7,
    name: '大力仑爱吃菠菜',
    waihao: '大力仑',
    fullName: '李卓伦',
    mbti: 'INTJ',
    intro: '后浪小太阳，倩霞制造，长城炮官方野生代言人\n电竞少女赋能未半而中道崩殂，祝愿身体健康哟～',
    hobbies: 'hiphop，跳舞',
    talents: '跳舞',
    specialties: '长城⬇炮⬆！！！',
    dream: '活着',
    avatar: '/images/members/avatar-7.jpg'
  },
  {
    id: 8,
    name: '芒果小牛',
    waihao: '小牛、神罚、芒果小子',
    fullName: '牛可心',
    mbti: 'ENFJ（E属性很低）',
    intro: '后浪摄影剪辑之一，狮猫互娱集团首席超模\n一只爱吃菜的芒果牛，一天炫完小黑牛肉干',
    hobbies: '长得帅的男的',
    talents: '拍摄、剪辑、立人设',
    specialties: '唱歌、模特步',
    dream: '发癫吧后浪成为一个综艺（必能实现）',
    avatar: '/images/members/avatar-8.jpg'
  },
  {
    id: 9,
    name: '卡皮巴拉段',
    waihao: '小段、卡皮巴拉，邪恶小段',
    fullName: '段紫荆',
    mbti: 'ISFP',
    intro: '后浪摄影剪辑之一，紫荆解忧杂货铺CEO\n慢条斯里的rapper，染发膏杀手，炫彩皮肤最多的女人',
    hobbies: '爱吃东西',
    talents: '说话特别慢',
    specialties: '说唱',
    dream: '逛遍全世界',
    avatar: '/images/members/avatar-9.jpg'
  },
  {
    id: 10,
    name: '寻宁启示',
    waihao: '归宁、微醺',
    fullName: '卢宁宁',
    mbti: 'INTP',
    intro: '后浪生理年龄最长者兼心理年龄最小者\n长卿五味固定NPC，疙瘩汤推广大使',
    hobbies: '微醺',
    talents: '睡觉',
    specialties: '我要喝，疙～瘩汤~',
    dream: '自己有100w粉丝',
    avatar: '/images/members/avatar-10.jpg'
  },
  {
    id: 11,
    name: '川西哼唧怪',
    waihao: '川西、猩猩、狒狒',
    fullName: '郭茜茜',
    mbti: 'ENFP',
    intro: '后浪气血最足的女人，剁椒鱼头车主\n似是因为家太远很少被强开导致气血充沛',
    hobbies: '小孩',
    talents: '特别擅长带小孩',
    specialties: '它能实现小小愿望有神奇魔法～听说每个小孩都想要得到它～',
    dream: '开幼儿园',
    avatar: '/images/members/avatar-11.jpg'
  },
  {
    id: 12,
    name: '导眼燕某人',
    waihao: '燕导、拾粪哥',
    fullName: '燕宇杰',
    mbti: 'ENTJ',
    intro: '全能燕导，最有安全感的男人\n香菇王子，买香菇找海东，出手就是一个亿',
    hobbies: '摄影、RC、机械加工、3D打印',
    talents: '胡子特长，眉毛也是',
    specialties: '清早起来去拾粪～当，当当，当当当，当，当当，当当，当当当，当～',
    dream: '当一个有名的科学家导演',
    avatar: '/images/members/avatar-12.png'
  },
  {
    id: 13,
    name: '培宇·剪辑的神',
    waihao: '剪辑的神、小猪佩奇',
    fullName: '连培宇',
    mbti: 'ENFP',
    intro: '后浪摄影剪辑之一，喜欢剪点小片，片里冒点小烟\n培宇剪片辛苦啦，但是培速更',
    hobbies: '剪点小片',
    talents: '会剪点小片',
    specialties: '跳舞',
    dream: '把发癫吧后浪账号做到1000w粉',
    avatar: '/images/members/avatar-13.jpg'
  },
  {
    id: 14,
    name: '翟社长',
    waihao: '翟小猪、翟社长、机械舞之神',
    fullName: '翟京凯',
    mbti: 'INTJ',
    intro: '揪咪揪咪chua组合成员，男人帮最容易反水者\n随机刷新在台球厅和大洋\n',
    hobbies: '上网、台球',
    talents: '台球',
    specialties: '机械舞',
    dream: '环游世界',
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
