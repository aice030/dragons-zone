import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { login as apiLogin, register as apiRegister } from '@/api/user'

const TOKEN_KEY = 'token'
const USER_INFO_KEY = 'userInfo'

/**
 * 从 localStorage 读取并解析用户信息
 */
function loadUserInfo() {
  try {
    const raw = localStorage.getItem(USER_INFO_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const userInfo = ref(loadUserInfo())

  const isLoggedIn = computed(() => !!token.value && !!userInfo.value)
  const nickName = computed(() => userInfo.value?.nickName ?? '')

  /**
   * 登录成功后写入 token 和 userInfo
   */
  function setLoginResult(data) {
    if (!data?.token || !data?.userInfo) return
    token.value = data.token
    userInfo.value = data.userInfo
    localStorage.setItem(TOKEN_KEY, data.token)
    localStorage.setItem(USER_INFO_KEY, JSON.stringify(data.userInfo))
  }

  /**
   * 登出：清除本地状态和 localStorage
   */
  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_INFO_KEY)
  }

  /**
   * 调用登录接口并更新 store
   * @param {string} loginName
   * @param {string} password
   * @returns {Promise} 接口返回的 data（含 token、userInfo）
   */
  async function login(loginName, password) {
    const res = await apiLogin(loginName, password)
    const data = res?.data
    if (data?.token && data?.userInfo) {
      setLoginResult(data)
      return data
    }
    throw new Error(res?.message || '登录失败')
  }

  /**
   * 调用注册接口；注册成功后不自动登录，需用户再点登录
   * @param {Object} data - { loginName, password, nickName, phoneNumber }
   * @returns {Promise} 接口返回的 data
   */
  async function register(data) {
    const res = await apiRegister(data)
    if (res && res.code !== 200) {
      throw new Error(res.message || '注册失败')
    }
    return res?.data
  }

  /**
   * 初始化：从 localStorage 恢复（页面刷新时 Pinia 会重新创建，需同步）
   */
  function initFromStorage() {
    const t = localStorage.getItem(TOKEN_KEY)
    const u = loadUserInfo()
    token.value = t || ''
    userInfo.value = u
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    nickName,
    setLoginResult,
    logout,
    login,
    register,
    initFromStorage
  }
})
