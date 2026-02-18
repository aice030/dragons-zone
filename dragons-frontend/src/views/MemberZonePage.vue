<template>
  <div class="member-zone-container" :class="{ 'detail-open': showDetail }">
    <div class="member-zone-background"></div>

    <div class="member-zone-content">
      <!-- 导航栏 -->
      <NavBar />

      <!-- 简介区 -->
      <MemberIntroSection v-if="memberInfo" :member-info="memberInfo" />

      <!-- 图片/视频区 -->
      <MemberMediaSection
        v-if="currentMemberId"
        :member-id="currentMemberId"
        @card-click="handleCardClick"
        @media-list-update="handleMediaListUpdate"
      />

      <!-- 树洞区 -->
      <MemberTreeHoleSection
        v-if="currentMemberId && shouldShowTreeHole"
        :owner-id="currentMemberId"
        @message-updated="handleMessageUpdated"
      />

      <!-- 联系方式区域（纯展示，不可点击） -->
      <footer class="contact-footer">
        <div class="contact-content">
          <div class="contact-line" v-if="contactInfo.douyin">
            <svg class="contact-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M19.59 6.69a4.83 4.83 0 0 1-3.77-4.25V2h-3.45v13.67a2.89 2.89 0 0 1-5.2 1.74 2.89 2.89 0 0 1 2.31-4.64 2.93 2.93 0 0 1 .88.13V9.4a6.84 6.84 0 0 0-1-.05A6.33 6.33 0 0 0 5 20.1a6.34 6.34 0 0 0 10.86-4.43v-7a8.16 8.16 0 0 0 4.77 1.52v-3.4a4.85 4.85 0 0 1-1-.1z" fill="currentColor"/>
            </svg>
            <span>抖音号：{{ contactInfo.douyin }}</span>
          </div>
          <div class="contact-line" v-if="contactInfo.email">
            <svg class="contact-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <polyline points="22,6 12,13 2,6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <span>邮箱：{{ contactInfo.email }}</span>
          </div>
          <div class="contact-line" v-if="contactInfo.github">
            <svg class="contact-icon" viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2C6.477 2 2 6.477 2 12c0 4.42 2.865 8.17 6.839 9.49.5.092.682-.217.682-.482 0-.237-.008-.866-.013-1.7-2.782.603-3.369-1.34-3.369-1.34-.454-1.156-1.11-1.463-1.11-1.463-.908-.62.069-.608.069-.608 1.003.07 1.531 1.03 1.531 1.03.892 1.529 2.341 1.087 2.91.831.092-.646.35-1.086.636-1.336-2.22-.253-4.555-1.11-4.555-4.943 0-1.091.39-1.984 1.029-2.683-.103-.253-.446-1.27.098-2.647 0 0 .84-.269 2.75 1.025A9.578 9.578 0 0112 6.836c.85.004 1.705.114 2.504.336 1.909-1.294 2.747-1.025 2.747-1.025.546 1.377.203 2.394.1 2.647.64.699 1.028 1.592 1.028 2.683 0 3.842-2.339 4.687-4.566 4.935.359.309.678.919.678 1.852 0 1.336-.012 2.415-.012 2.743 0 .267.18.578.688.48C19.138 20.167 22 16.418 22 12c0-5.523-4.477-10-10-10z"/>
            </svg>
            <span>github：{{ contactInfo.github }}</span>
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
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import NavBar from '@/components/NavBar.vue'
import MemberIntroSection from '@/components/MemberIntroSection.vue'
import MemberMediaSection from '@/components/MemberMediaSection.vue'
import MemberTreeHoleSection from '@/components/MemberTreeHoleSection.vue'
import MediaDetail from '@/views/MediaDetail.vue'
import { useUserStore } from '@/stores/user'
import { getMemberById } from '@/config/members'
import { getContactInfo } from '@/config/contact'
import { setSEO, setStructuredData } from '@/utils/seo'

const route = useRoute()
const userStore = useUserStore()
const contactInfo = ref(getContactInfo())
const showDetail = ref(false)
const selectedMediaId = ref(null)
const mediaList = ref([]) // 用于详情页切换媒体

// 从路由参数获取成员ID
const currentMemberId = computed(() => {
  const memberId = parseInt(route.params.memberId)
  return memberId || null
})

// 获取成员信息
const memberInfo = computed(() => {
  if (!currentMemberId.value) return null
  return getMemberById(currentMemberId.value)
})

// 判断是否显示树洞区
// level=1（管理员）：显示所有消息
// level=0（作者）或 level=2（普通用户）：显示自己的消息（后端已做过滤）
const shouldShowTreeHole = computed(() => {
  if (!userStore.isLoggedIn) return false
  const level = userStore.userInfo?.level
  // level=1（管理员）、level=0（作者）、level=2（普通用户）都显示
  return level === 0 || level === 1 || level === 2
})

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

function toggleUserMenu() {
  showUserMenu.value = !showUserMenu.value
}

function handleLogout() {
  userStore.logout()
  showLogoutConfirmModal.value = false
  showUserMenu.value = false
}

// 导航栏相关函数已移至 NavBar 组件

function handleCardClick(data) {
  selectedMediaId.value = data.mediaId
  showDetail.value = true
}

function handleCloseDetail() {
  showDetail.value = false
  selectedMediaId.value = null
}

function handleSwitchMedia(newMediaId) {
  selectedMediaId.value = newMediaId
}

function handleMediaListUpdate(newList) {
  mediaList.value = newList
}

function handleMessageUpdated() {
  // 树洞消息更新后的回调（可以用于刷新其他相关数据）
  console.log('树洞消息已更新')
}

// 监听成员信息变化，更新 SEO
watch(memberInfo, (newMemberInfo) => {
  if (newMemberInfo) {
    const specialtiesStr = newMemberInfo.specialties || ''

    // 构建页面标题
    const title = `${newMemberInfo.name}${newMemberInfo.fullName ? ` (${newMemberInfo.fullName})` : ''} - 成员专区 | Dragons Zone`
    
    // 构建页面描述
    let description = `${newMemberInfo.name}的成员专区`
    if (newMemberInfo.intro) {
      description += `，${newMemberInfo.intro}`
    }
    if (specialtiesStr) {
      description += `。特长：${specialtiesStr}`
    }
    if (newMemberInfo.hobbies) {
      description += `。爱好：${newMemberInfo.hobbies}`
    }
    if (newMemberInfo.dream) {
      description += `。梦想：${newMemberInfo.dream}`
    }
    
    // 构建关键词
    const keywords = [
      newMemberInfo.name,
      'Dragons Zone',
      '成员专区',
      '后浪'
    ]
    if (newMemberInfo.fullName) {
      keywords.push(newMemberInfo.fullName)
    }
    if (newMemberInfo.waihao) {
      keywords.push(newMemberInfo.waihao)
    }
    if (specialtiesStr) {
      keywords.push(...specialtiesStr.split('、'))
    }
    
    // 设置 SEO
    setSEO({
      title,
      description,
      keywords: keywords.join(','),
      image: newMemberInfo.avatar || undefined
    })

    // 设置结构化数据（JSON-LD）
    const structuredData = {
      '@context': 'https://schema.org',
      '@type': 'ProfilePage',
      mainEntity: {
        '@type': 'Person',
        name: newMemberInfo.name,
        alternateName: newMemberInfo.fullName || undefined,
        description: newMemberInfo.intro || undefined,
        image: newMemberInfo.avatar || undefined,
        knowsAbout: specialtiesStr ? specialtiesStr.split('、') : undefined
      },
      breadcrumb: {
        '@type': 'BreadcrumbList',
        itemListElement: [
          {
            '@type': 'ListItem',
            position: 1,
            name: '首页',
            item: window.location.origin
          },
          {
            '@type': 'ListItem',
            position: 2,
            name: '成员专区',
            item: `${window.location.origin}/browse`
          },
          {
            '@type': 'ListItem',
            position: 3,
            name: newMemberInfo.name,
            item: window.location.href
          }
        ]
      }
    }
    
    // 移除 undefined 字段
    const cleanStructuredData = JSON.parse(JSON.stringify(structuredData))
    setStructuredData(cleanStructuredData)
  }
}, { immediate: true })

// 监听路由变化，如果成员ID无效则跳转
onMounted(() => {
  if (!currentMemberId.value || !memberInfo.value) {
    router.push('/browse')
    return
  }
  // 初始化时加载媒体列表（通过MemberMediaSection组件）
})
</script>

<style scoped>
/* 复用 media-browse.css 的样式 */
.member-zone-container {
  min-height: 100vh;
  position: relative;
  overflow-x: hidden;
  overflow-y: auto;
  background-color: var(--bg-color);
}

.member-zone-background {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
  background-image: url('/images/home-background.jpg');
  background-size: cover;
  background-position: center;
  background-attachment: fixed;
  background-repeat: no-repeat;
}

.member-zone-content {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  transition: filter 0.3s ease;
}

.member-zone-container.detail-open {
  overflow: hidden;
}
</style>
