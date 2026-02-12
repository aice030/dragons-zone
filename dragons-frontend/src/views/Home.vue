<template>
  <div class="home-container" :class="{ 'detail-open': showDetail }">
    <div class="home-background"></div>
    
    <div class="home-content">
      <!-- 导航栏 -->
      <div class="nav-bar">
        <div class="nav-content">
          <!-- 左侧：成员专区按钮和下拉菜单 -->
          <div class="member-zone-dropdown" @click.stop>
            <button 
              class="member-zone-btn"
              @click="toggleMemberDropdown"
              :class="{ active: showMemberDropdown }"
            >
              成员专区
              <svg class="dropdown-arrow" :class="{ open: showMemberDropdown }" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M6 9l6 6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </button>
            
            <!-- 下拉菜单 -->
            <Transition name="dropdown">
              <div v-if="showMemberDropdown" class="member-dropdown-menu">
                <button
                  v-for="member in members"
                  :key="member.id"
                  class="member-option"
                  :class="{ active: currentZoneId === member.id }"
                  @click="selectZone(member.id, member.name)"
                >
                  {{ member.name }}
                </button>
              </div>
            </Transition>
          </div>

          <!-- 右侧：登录/注册 或 昵称下拉菜单 -->
          <div class="nav-user-area">
            <template v-if="userStore.isLoggedIn">
              <div class="user-menu-dropdown" @click.stop>
                <button
                  type="button"
                  class="nav-nickname-btn"
                  :class="{ active: showUserMenu }"
                  @click="toggleUserMenu"
                >
                  {{ userStore.nickName }}
                </button>
                <Transition name="dropdown">
                  <div v-if="showUserMenu" class="user-dropdown-menu">
                    <template v-for="(item, index) in userMenuItems" :key="index">
                      <router-link
                        v-if="item.path"
                        :to="item.path"
                        class="user-menu-option"
                        @click="showUserMenu = false"
                      >
                        {{ item.label }}
                      </router-link>
                      <a
                        v-else-if="item.href"
                        :href="item.href"
                        target="_blank"
                        rel="noopener noreferrer"
                        class="user-menu-option"
                        @click="showUserMenu = false"
                      >
                        {{ item.label }}
                      </a>
                      <button
                        v-else-if="item.modal === 'changePassword'"
                        type="button"
                        class="user-menu-option"
                        @click="showChangePasswordModal = true; showUserMenu = false"
                      >
                        {{ item.label }}
                      </button>
                      <button
                        v-else-if="item.modal === 'deregister'"
                        type="button"
                        class="user-menu-option user-menu-logout"
                        @click="showDeregisterModal = true; showUserMenu = false"
                      >
                        {{ item.label }}
                      </button>
                      <button
                        v-else-if="item.logout"
                        type="button"
                        class="user-menu-option user-menu-logout"
                        @click="handleLogout"
                      >
                        {{ item.label }}
                      </button>
                    </template>
                  </div>
                </Transition>
              </div>
            </template>
            <template v-else>
              <button type="button" class="nav-btn nav-btn-ghost" @click="showLoginModal = true">登录</button>
              <button type="button" class="nav-btn nav-btn-primary" @click="showRegisterModal = true">注册</button>
            </template>
          </div>
        </div>
      </div>

      <!-- 登录弹窗 -->
      <LoginModal
        v-model:visible="showLoginModal"
        @success="showLoginModal = false"
        @forgot-password="onForgotPasswordClick"
      />
      <!-- 注册弹窗 -->
      <RegisterModal v-model:visible="showRegisterModal" @success="onRegisterSuccess" />
      <!-- 找回密码弹窗 -->
      <ForgotPasswordModal
        v-model:visible="showForgotPasswordModal"
        @success="onForgotPasswordSuccess"
      />
      <!-- 修改密码弹窗 -->
      <ChangePasswordModal v-model:visible="showChangePasswordModal" @success="showChangePasswordModal = false" />
      <!-- 注销账号确认弹窗 -->
      <DeregisterConfirmModal v-model:visible="showDeregisterModal" @success="showDeregisterModal = false" />

      <!-- 媒体列表区域 -->
      <div class="media-list-container">
        <!-- 筛选选择器 - 右上角 -->
        <div class="category-selector">
          <button
            class="category-option"
            :class="{ active: currentCategory === null }"
            @click="switchCategory(null)"
          >
            全部
          </button>
          <span class="category-separator">|</span>
          <button
            class="category-option"
            :class="{ active: currentCategory === 0 }"
            @click="switchCategory(0)"
          >
            图片
          </button>
          <span class="category-separator">|</span>
          <button
            class="category-option"
            :class="{ active: currentCategory === 1 }"
            @click="switchCategory(1)"
          >
            视频
          </button>
        </div>
        
        <!-- 媒体滚动带 -->
        <MediaStrip
          :media-list="mediaList"
          :loading="loading"
          @card-click="handleCardClick"
          @load-more="loadMore"
        />
      </div>
      
      <!-- 联系方式区域 -->
      <footer class="contact-footer">
        <div class="contact-content">
          <div class="contact-item" v-if="contactInfo.douyin">
            <a :href="`https://www.douyin.com/user/${contactInfo.douyin.replace('@', '')}`" target="_blank" rel="noopener noreferrer" class="contact-link">
              <svg class="contact-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M19.59 6.69a4.83 4.83 0 0 1-3.77-4.25V2h-3.45v13.67a2.89 2.89 0 0 1-5.2 1.74 2.89 2.89 0 0 1 2.31-4.64 2.93 2.93 0 0 1 .88.13V9.4a6.84 6.84 0 0 0-1-.05A6.33 6.33 0 0 0 5 20.1a6.34 6.34 0 0 0 10.86-4.43v-7a8.16 8.16 0 0 0 4.77 1.52v-3.4a4.85 4.85 0 0 1-1-.1z" fill="currentColor"/>
              </svg>
              <span>{{ contactInfo.douyin }}</span>
            </a>
          </div>
          
          <div class="contact-item" v-if="contactInfo.email">
            <a :href="`mailto:${contactInfo.email}`" class="contact-link">
              <svg class="contact-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <polyline points="22,6 12,13 2,6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              <span>{{ contactInfo.email }}</span>
            </a>
          </div>
          
          <div class="contact-item" v-if="contactInfo.github">
            <a :href="contactInfo.github" target="_blank" rel="noopener noreferrer" class="contact-link">
              <svg class="contact-icon" viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 2C6.477 2 2 6.477 2 12c0 4.42 2.865 8.17 6.839 9.49.5.092.682-.217.682-.482 0-.237-.008-.866-.013-1.7-2.782.603-3.369-1.34-3.369-1.34-.454-1.156-1.11-1.463-1.11-1.463-.908-.62.069-.608.069-.608 1.003.07 1.531 1.03 1.531 1.03.892 1.529 2.341 1.087 2.91.831.092-.646.35-1.086.636-1.336-2.22-.253-4.555-1.11-4.555-4.943 0-1.091.39-1.984 1.029-2.683-.103-.253-.446-1.27.098-2.647 0 0 .84-.269 2.75 1.025A9.578 9.578 0 0112 6.836c.85.004 1.705.114 2.504.336 1.909-1.294 2.747-1.025 2.747-1.025.546 1.377.203 2.394.1 2.647.64.699 1.028 1.592 1.028 2.683 0 3.842-2.339 4.687-4.566 4.935.359.309.678.919.678 1.852 0 1.336-.012 2.415-.012 2.743 0 .267.18.578.688.48C19.138 20.167 22 16.418 22 12c0-5.523-4.477-10-10-10z"/>
              </svg>
              <span>GitHub</span>
            </a>
          </div>
        </div>
        <p class="copyright">© 2026 Dragons Zone. All rights reserved.</p>
      </footer>
    </div>
    
    <!-- 详情页（条件渲染） -->
    <MediaDetail
      v-if="showDetail"
      :media-id="selectedMediaId"
      :media-list="mediaList"
      @close="handleCloseDetail"
      @switch-media="handleSwitchMedia"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import MediaStrip from '@/components/MediaStrip.vue'
import MediaDetail from '@/views/MediaDetail.vue'
import LoginModal from '@/components/LoginModal.vue'
import RegisterModal from '@/components/RegisterModal.vue'
import ChangePasswordModal from '@/components/ChangePasswordModal.vue'
import ForgotPasswordModal from '@/components/ForgotPasswordModal.vue'
import DeregisterConfirmModal from '@/components/DeregisterConfirmModal.vue'
import { useUserStore } from '@/stores/user'
import { getMediaList } from '@/api/media'
import { getMembers } from '@/config/members'
import { getUserMenuItems } from '@/config/userMenu'
import { getContactInfo } from '@/config/contact'

const userStore = useUserStore()
const userMenuItems = getUserMenuItems()
const showUserMenu = ref(false)
const showChangePasswordModal = ref(false)
const showForgotPasswordModal = ref(false)
const showDeregisterModal = ref(false)
const showLoginModal = ref(false)
const showRegisterModal = ref(false)

/** 注册成功后关闭注册弹窗，并打开登录弹窗让用户登录 */
function onRegisterSuccess() {
  showRegisterModal.value = false
  showLoginModal.value = true
}

/** 登录框点击「找回密码」：关闭登录弹窗，打开找回密码弹窗 */
function onForgotPasswordClick() {
  showLoginModal.value = false
  showForgotPasswordModal.value = true
}

/** 找回密码成功后关闭弹窗并打开登录弹窗，让用户用新密码登录 */
function onForgotPasswordSuccess() {
  showForgotPasswordModal.value = false
  showLoginModal.value = true
}

function toggleUserMenu() {
  showUserMenu.value = !showUserMenu.value
}

function handleLogout() {
  userStore.logout()
  showUserMenu.value = false
}

const currentCategory = ref(null) // null=全部, 0=图片, 1=视频
const currentZoneId = ref(0) // 0=公共区, 其他=成员专区ID
const currentZoneName = ref('公共区')
const mediaList = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const hasMore = ref(true)

// 成员专区相关
const members = ref(getMembers())
const showMemberDropdown = ref(false)

// 联系方式
const contactInfo = ref(getContactInfo())

// 详情展开状态
const showDetail = ref(false)
const selectedMediaId = ref(null)

// 加载媒体列表
const loadMediaList = async (reset = false) => {
  if (loading.value || (!hasMore.value && !reset)) return
  
  loading.value = true
  
  try {
    if (reset) {
      page.value = 1
      mediaList.value = []
      hasMore.value = true
    }
    
    const response = await getMediaList(
      page.value,
      size.value,
      currentCategory.value,
      currentZoneId.value // 当前专区ID
    )
    
    if (response?.data) {
      const newList = response.data.list || []
      
      if (reset) {
        mediaList.value = newList
      } else {
        mediaList.value.push(...newList)
      }
      
      // 判断是否还有更多
      const total = response.data.total || 0
      hasMore.value = mediaList.value.length < total
      
      if (hasMore.value) {
        page.value += 1
      }
    }
  } catch (error) {
    console.error('加载媒体列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 切换分类
const switchCategory = (category) => {
  if (currentCategory.value === category) return
  
  currentCategory.value = category
  loadMediaList(true) // 重置并重新加载
}

// 切换成员专区
const selectZone = (zoneId, zoneName) => {
  if (currentZoneId.value === zoneId) {
    showMemberDropdown.value = false
    return
  }
  
  currentZoneId.value = zoneId
  currentZoneName.value = zoneName
  showMemberDropdown.value = false
  loadMediaList(true) // 重置并重新加载
}

// 切换下拉菜单显示/隐藏
const toggleMemberDropdown = () => {
  showMemberDropdown.value = !showMemberDropdown.value
}

// 点击外部关闭下拉菜单
const handleClickOutside = (event) => {
  const memberDropdown = event.target.closest('.member-zone-dropdown')
  const userDropdown = event.target.closest('.user-menu-dropdown')
  if (!memberDropdown) showMemberDropdown.value = false
  if (!userDropdown) showUserMenu.value = false
}

onMounted(() => {
  loadMediaList(true)
  document.addEventListener('click', handleClickOutside)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside)
})

// 加载更多
const loadMore = () => {
  if (hasMore.value && !loading.value) {
    loadMediaList(false)
  }
}

// 处理卡片点击 - 内联展开详情
const handleCardClick = (data) => {
  selectedMediaId.value = data.mediaId
  showDetail.value = true
}

// 关闭详情
const handleCloseDetail = () => {
  showDetail.value = false
  selectedMediaId.value = null
}

// 切换媒体
const handleSwitchMedia = (newMediaId) => {
  selectedMediaId.value = newMediaId
}

</script>

<style scoped>
/* 样式在 home.css 中定义 */
</style>
