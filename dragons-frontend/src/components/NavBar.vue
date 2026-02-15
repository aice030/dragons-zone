<template>
  <div class="nav-bar">
    <div class="nav-content">
      <!-- 左侧：自定义内容或占位 -->
      <div class="nav-left">
        <slot name="left">
          <div class="nav-left-placeholder"></div>
        </slot>
      </div>

      <!-- 中间：图片&视频集按钮 -->
      <div class="nav-center">
        <router-link to="/browse" class="nav-media-collection-btn">
          图片&视频集
        </router-link>
      </div>

      <!-- 右侧：成员专区按钮和下拉菜单 + 登录/注册 或 昵称下拉菜单 -->
      <div class="nav-right">
        <!-- 成员专区按钮和下拉菜单 -->
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
              <!-- 第一行：提示文字 -->
              <div class="member-dropdown-header">
                请选择你的英雄
              </div>
              <!-- 成员网格：每行3人，共4行 -->
              <div class="member-grid">
                <button
                  v-for="member in members"
                  :key="member.id"
                  class="member-grid-item"
                  :class="{ active: currentMemberId === member.id }"
                  @click="selectZone(member.id, member.name)"
                >
                  {{ member.name }}
                </button>
              </div>
            </div>
          </Transition>
        </div>

        <!-- 登录/注册 或 昵称下拉菜单 -->
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
                      @click="showLogoutConfirmModal = true; showUserMenu = false"
                    >
                      {{ item.label }}
                    </button>
                  </template>
                </div>
              </Transition>
            </div>
          </template>
          <template v-else>
            <button type="button" class="nav-btn nav-btn-primary" @click="showLoginModal = true">登录</button>
            <button type="button" class="nav-btn nav-btn-ghost" @click="showRegisterModal = true">注册</button>
          </template>
        </div>
      </div>
    </div>

    <!-- 登录弹窗 -->
    <LoginModal
      v-model:visible="showLoginModal"
      @success="onLoginSuccess"
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
    <!-- 退出登录确认弹窗 -->
    <SimpleConfirmModal
      v-model:visible="showLogoutConfirmModal"
      title="退出登录"
      message="确定要退出登录吗？"
      confirm-text="退出"
      @confirm="handleLogout"
      @cancel="showLogoutConfirmModal = false"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getMembers } from '@/config/members'
import { getUserMenuItems } from '@/config/userMenu'
import LoginModal from '@/components/LoginModal.vue'
import RegisterModal from '@/components/RegisterModal.vue'
import ChangePasswordModal from '@/components/ChangePasswordModal.vue'
import ForgotPasswordModal from '@/components/ForgotPasswordModal.vue'
import DeregisterConfirmModal from '@/components/DeregisterConfirmModal.vue'
import SimpleConfirmModal from '@/components/SimpleConfirmModal.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const userMenuItems = computed(() => getUserMenuItems(userStore.userInfo))
const members = ref(getMembers())

// 下拉菜单状态
const showUserMenu = ref(false)
const showMemberDropdown = ref(false)

// 弹窗状态
const showLoginModal = ref(false)
const showRegisterModal = ref(false)
const showForgotPasswordModal = ref(false)
const showChangePasswordModal = ref(false)
const showDeregisterModal = ref(false)
const showLogoutConfirmModal = ref(false)

// 当前成员ID（用于高亮显示）
const currentMemberId = computed(() => {
  const memberId = route.params.memberId
  return memberId ? Number(memberId) : null
})

function toggleUserMenu() {
  showUserMenu.value = !showUserMenu.value
}

function toggleMemberDropdown() {
  showMemberDropdown.value = !showMemberDropdown.value
}

function selectZone(memberId, memberName) {
  showMemberDropdown.value = false
  if (memberId === 0) {
    // 公共区：跳转到浏览页
    router.push('/browse')
  } else {
    // 成员专区：跳转到成员专区页
    router.push(`/member/${memberId}`)
  }
}

function handleLogout() {
  userStore.logout()
  showLogoutConfirmModal.value = false
  showUserMenu.value = false
  // 退出登录后，统一跳转到欢迎页
  router.push('/')
}

function onLoginSuccess() {
  showLoginModal.value = false
}

function onRegisterSuccess() {
  showRegisterModal.value = false
  showLoginModal.value = true
}

function onForgotPasswordClick() {
  showLoginModal.value = false
  showForgotPasswordModal.value = true
}

function onForgotPasswordSuccess() {
  showForgotPasswordModal.value = false
  showLoginModal.value = true
}

function handleClickOutside(event) {
  const memberDropdown = event.target.closest('.member-zone-dropdown')
  const userDropdown = event.target.closest('.user-menu-dropdown')
  if (!memberDropdown) showMemberDropdown.value = false
  if (!userDropdown) showUserMenu.value = false
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style scoped>
/* 导航栏（层级高于分类选择器，保证昵称下拉不被挡住） */
.nav-bar {
  padding: 0.8rem 1rem;
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  min-height: 3.5rem;
}

.nav-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
}

/* 导航栏左侧区域 */
.nav-left {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
}

.nav-left-placeholder {
  flex: 1;
  min-width: 0;
}

/* 返回按钮样式（用于左侧 slot） */
.nav-back-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  border: none;
  background: transparent;
  color: #2c3e50;
  font-size: 0.95rem;
  font-weight: 500;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  text-decoration: none;
}

.nav-back-btn:hover {
  background: rgba(74, 144, 226, 0.1);
  color: #4a90e2;
}

.back-icon {
  width: 20px;
  height: 20px;
}

/* 导航栏中间区域 */
.nav-center {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-media-collection-btn {
  padding: 0.5rem 1rem;
  border: none;
  background: rgba(74, 144, 226, 0.1);
  color: #4a90e2;
  font-size: 0.95rem;
  font-weight: 500;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
}

.nav-media-collection-btn:hover {
  background: rgba(74, 144, 226, 0.15);
  color: #4a90e2;
}

/* 导航栏右侧区域 */
.nav-right {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.75rem;
  min-width: 0;
}

/* 导航栏右侧：登录/注册 或 用户信息 */
.nav-user-area {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.nav-btn {
  padding: 0.45rem 1rem;
  border-radius: 20px;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  text-decoration: none;
  border: none;
}

.nav-btn-ghost {
  background: transparent;
  color: #4a90e2;
}

.nav-btn-ghost:hover {
  background: rgba(74, 144, 226, 0.1);
}

.nav-btn-primary {
  background: #4a90e2;
  color: #fff;
}

.nav-btn-primary:hover {
  background: #3a7bc8;
  color: #fff;
}

/* 用户昵称下拉菜单 */
.user-menu-dropdown {
  position: relative;
}

.nav-nickname-btn {
  padding: 0.5rem 0.85rem;
  border: none;
  background: transparent;
  color: #2c3e50;
  font-size: 1.12rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  border-radius: 10px;
  cursor: pointer;
  transition: color 0.25s ease, background 0.25s ease, box-shadow 0.25s ease;
}

.nav-nickname-btn:hover {
  color: #4a90e2;
  background: rgba(74, 144, 226, 0.08);
}

.nav-nickname-btn.active {
  color: #4a90e2;
  background: rgba(74, 144, 226, 0.12);
  box-shadow: 0 0 0 1px rgba(74, 144, 226, 0.2);
}

.user-dropdown-menu {
  position: absolute;
  top: calc(100% + 0.5rem);
  right: 0;
  min-width: 140px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  overflow: hidden;
  z-index: 100;
}

.user-menu-option {
  display: block;
  width: 100%;
  padding: 0.6rem 1rem;
  border: none;
  background: none;
  color: #2c3e50;
  font-size: 0.9rem;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
  transition: background 0.2s ease;
}

.user-menu-option:hover {
  background: rgba(74, 144, 226, 0.1);
  color: #4a90e2;
}

.user-menu-logout:hover {
  background: rgba(231, 76, 60, 0.1);
  color: #e74c3c;
}

/* 成员专区下拉菜单 */
.member-zone-dropdown {
  position: relative;
}

.member-zone-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  border: none;
  background: rgba(74, 144, 226, 0.1);
  color: #4a90e2;
  font-size: 0.95rem;
  font-weight: 500;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.member-zone-btn:hover {
  background: rgba(74, 144, 226, 0.15);
}

.member-zone-btn.active {
  background: rgba(74, 144, 226, 0.2);
  color: #4a90e2;
}

.dropdown-arrow {
  width: 16px;
  height: 16px;
  transition: transform 0.3s ease;
}

.dropdown-arrow.open {
  transform: rotate(180deg);
}

.member-dropdown-menu {
  position: absolute;
  top: calc(100% + 0.5rem);
  right: 0;
  min-width: 360px; /* 3 列 × 约 8em/列，容纳 7 字成员名 */
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  overflow: hidden;
  z-index: 100;
}

.member-dropdown-header {
  padding: 0.9rem 1rem;
  font-family: 'ZCOOL XiaoWei', 'ZCOOL 小薇体', serif;
  font-size: 1.35rem;
  font-weight: 400;
  color: #0d0d0d;
  letter-spacing: 0.12em;
  text-shadow: 1px 1px 0 rgba(0, 0, 0, 0.1), 2px 2px 4px rgba(0, 0, 0, 0.15);
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
  text-align: center;
}

.member-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(8em, 1fr));
  gap: 0.5rem;
  padding: 0.75rem;
}

.member-grid-item {
  padding: 0.5rem 0.75rem;
  min-width: 8em; /* 确保 7 个汉字能完整显示 */
  border: 1px solid rgba(0, 0, 0, 0.1);
  background: #fff;
  color: #2c3e50;
  font-size: 0.9rem;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.member-grid-item:hover {
  background: rgba(74, 144, 226, 0.1);
  border-color: #4a90e2;
  color: #4a90e2;
}

.member-grid-item.active {
  background: rgba(74, 144, 226, 0.15);
  border-color: #4a90e2;
  color: #4a90e2;
  font-weight: 500;
}

/* 下拉菜单动画 */
.dropdown-enter-active,
.dropdown-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
