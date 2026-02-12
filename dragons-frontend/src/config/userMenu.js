/**
 * 用户下拉菜单配置（点击导航栏昵称后显示）
 *
 * 说明：
 * - label: 菜单项显示文字
 * - path: 站内路由路径，点击后跳转并关闭下拉
 * - href: 站外链接（如 'https://...'），点击后新窗口打开
 * - modal: 弹窗类型，如 'changePassword' 修改密码、'deregister' 注销账号
 * - logout: 设为 true 表示「退出登录」，点击后执行登出并关闭下拉
 *
 * 同一项只填 path、href、modal、logout 其中之一即可。
 */

export const USER_MENU_ITEMS = [
  { label: '修改密码', modal: 'changePassword' },
  { label: '我的上传', path: '/my-uploads' },
  { label: '我的留言', path: '/profile' },
  { label: '注销账号', modal: 'deregister' },
  { label: '退出登录', logout: true }
]

/**
 * 获取用户下拉菜单项
 * @returns {Array<{ label: string, path?: string, href?: string, logout?: boolean }>}
 */
export function getUserMenuItems() {
  return USER_MENU_ITEMS
}
