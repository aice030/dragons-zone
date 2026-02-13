<template>
  <div class="media-browse-container" :class="{ 'detail-open': showDetail }">
    <div class="media-browse-background"></div>

    <div class="media-browse-content">
      <!-- 导航栏 -->
      <NavBar />

      <!-- 媒体列表区域 -->
      <div class="media-list-container">
        <!-- 筛选选择器 - 左上角 -->
        <div class="category-selector filter-selector">
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

        <!-- 显示模式切换 - 右上角 -->
        <div class="category-selector display-mode-selector">
          <button
            class="category-option"
            :class="{ active: displayMode === 'strip' }"
            @click="switchDisplayMode('strip')"
          >
            条带
          </button>
          <span class="category-separator">|</span>
          <button
            class="category-option"
            :class="{ active: displayMode === 'grid' }"
            @click="switchDisplayMode('grid')"
          >
            网格
          </button>
        </div>

        <!-- 条带模式：双行横向滚动 + 无限加载 -->
        <MediaStrip
          v-if="displayMode === 'strip'"
          :media-list="mediaList"
          :loading="loading"
          @card-click="handleCardClick"
          @load-more="loadMore"
        />

        <!-- 网格模式：固定 4 列 + 分页（每页 5 行） -->
        <div v-else class="media-grid-wrapper">
          <div class="media-grid">
            <MediaCard
              v-for="media in mediaList"
              :key="media.id"
              :media-id="media.id"
              :category="media.category"
              :title="media.title"
              :cover-url="media.coverUrl"
              :span="1"
              @click="handleCardClick"
            />
          </div>

          <div class="media-grid-pagination">
            <button
              type="button"
              class="media-grid-page-btn"
              :disabled="gridPage <= 1 || loading"
              @click="goToGridPage(gridPage - 1)"
            >
              上一页
            </button>
            <span class="media-grid-page-info">
              第 {{ gridPage }} / {{ gridTotalPages }} 页（共 {{ gridTotal }} 条）
            </span>
            <button
              type="button"
              class="media-grid-page-btn"
              :disabled="gridPage >= gridTotalPages || loading"
              @click="goToGridPage(gridPage + 1)"
            >
              下一页
            </button>
          </div>
        </div>
      </div>

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
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { setSEO } from '@/utils/seo'
import NavBar from '@/components/NavBar.vue'
import MediaStrip from '@/components/MediaStrip.vue'
import MediaCard from '@/components/MediaCard.vue'
import MediaDetail from '@/views/MediaDetail.vue'
import { getMediaList } from '@/api/media'
import { getMembers } from '@/config/members'
import { getContactInfo } from '@/config/contact'

const router = useRouter()

const currentCategory = ref(null)
const currentZoneId = ref(0)
const currentZoneName = ref('公共区')
const displayMode = ref('strip') // 'strip' | 'grid'

// 条带模式（无限滚动）
const stripMediaList = ref([])
const stripLoading = ref(false)
const stripPage = ref(1)
const stripSize = ref(20)
const stripHasMore = ref(true)

// 网格模式（分页：每页 5 行 * 4 列 = 20）
const gridMediaList = ref([])
const gridLoading = ref(false)
const gridPage = ref(1)
const gridSize = ref(20)
const gridTotal = ref(0)
const gridTotalPages = computed(() => Math.max(1, Math.ceil((gridTotal.value || 0) / gridSize.value)))

const mediaList = computed(() => (displayMode.value === 'strip' ? stripMediaList.value : gridMediaList.value))
const loading = computed(() => (displayMode.value === 'strip' ? stripLoading.value : gridLoading.value))
const members = ref(getMembers())
const contactInfo = ref(getContactInfo())
const showDetail = ref(false)
const selectedMediaId = ref(null)

const loadStripList = async (reset = false) => {
  if (stripLoading.value || (!stripHasMore.value && !reset)) return
  stripLoading.value = true
  try {
    if (reset) {
      stripPage.value = 1
      stripMediaList.value = []
      stripHasMore.value = true
    }
    const response = await getMediaList(stripPage.value, stripSize.value, currentCategory.value, currentZoneId.value)
    if (response?.data) {
      const newList = response.data.list || []
      if (reset) {
        stripMediaList.value = newList
      } else {
        stripMediaList.value.push(...newList)
      }
      const total = response.data.total || 0
      stripHasMore.value = stripMediaList.value.length < total
      if (stripHasMore.value) stripPage.value += 1
    }
  } catch (error) {
    console.error('加载媒体列表失败:', error)
  } finally {
    stripLoading.value = false
  }
}

const loadGridList = async (reset = false) => {
  if (gridLoading.value) return
  gridLoading.value = true
  try {
    if (reset) gridPage.value = 1
    const response = await getMediaList(gridPage.value, gridSize.value, currentCategory.value, currentZoneId.value)
    if (response?.data) {
      gridMediaList.value = response.data.list || []
      gridTotal.value = response.data.total || 0
    }
  } catch (error) {
    console.error('加载媒体列表失败:', error)
  } finally {
    gridLoading.value = false
  }
}

function reloadCurrentMode(reset = true) {
  if (displayMode.value === 'strip') return loadStripList(reset)
  return loadGridList(reset)
}

function switchCategory(category) {
  if (currentCategory.value === category) return
  currentCategory.value = category
  // 重置两种模式的缓存，确保切换模式时数据与筛选一致
  stripMediaList.value = []
  stripPage.value = 1
  stripHasMore.value = true
  gridMediaList.value = []
  gridPage.value = 1
  gridTotal.value = 0
  reloadCurrentMode(true)
}

// selectZone 函数已移除，由 NavBar 组件统一处理

onMounted(() => {
  // 设置浏览页面的 SEO
  setSEO({
    title: '图片&视频集 - Dragons Zone',
    description: '浏览所有成员的图片和视频内容，支持按类型和成员专区筛选',
    keywords: 'Dragons Zone,图片,视频,媒体浏览,后浪'
  })
  
  loadStripList(true)
})

function loadMore() {
  if (displayMode.value !== 'strip') return
  if (stripHasMore.value && !stripLoading.value) loadStripList(false)
}

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

function switchDisplayMode(mode) {
  if (displayMode.value === mode) return
  displayMode.value = mode
  // 切换到网格时，强制按分页规则拉一页数据；切回条带则复用已加载缓存（空则拉取）
  if (displayMode.value === 'grid') {
    loadGridList(true)
  } else if (stripMediaList.value.length === 0) {
    loadStripList(true)
  }
}

function goToGridPage(nextPage) {
  const p = Math.min(Math.max(1, nextPage), gridTotalPages.value)
  if (p === gridPage.value) return
  gridPage.value = p
  loadGridList(false)
}
</script>

<style scoped>
/* 样式在 media-browse.css 中定义 */
</style>
