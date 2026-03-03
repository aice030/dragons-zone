<template>
  <div class="member-media-section">
    <div class="member-media-container">
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

      <!-- 网格模式：固定 4 列 + 分页（每页 3 行，共 12 个） -->
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
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import MediaStrip from '@/components/MediaStrip.vue'
import MediaCard from '@/components/MediaCard.vue'
import { getMediaList } from '@/api/media'

const props = defineProps({
  memberId: {
    type: Number,
    required: true
  }
})

const emit = defineEmits(['card-click', 'media-list-update'])

const currentCategory = ref(null)
const displayMode = ref('strip') // 'strip' | 'grid'

// 条带模式（无限滚动）
const stripMediaList = ref([])
const stripLoading = ref(false)
const stripPage = ref(1)
const stripSize = ref(10)
const stripHasMore = ref(true)

// 网格模式（分页：每页 3 行 * 4 列 = 12）
const gridMediaList = ref([])
const gridLoading = ref(false)
const gridPage = ref(1)
const gridSize = ref(12)
const gridTotal = ref(0)
const gridTotalPages = computed(() => Math.max(1, Math.ceil((gridTotal.value || 0) / gridSize.value)))

const mediaList = computed(() => (displayMode.value === 'strip' ? stripMediaList.value : gridMediaList.value))
const loading = computed(() => (displayMode.value === 'strip' ? stripLoading.value : gridLoading.value))

const loadStripList = async (reset = false) => {
  if (stripLoading.value || (!stripHasMore.value && !reset)) return
  stripLoading.value = true
  try {
    if (reset) {
      stripPage.value = 1
      stripMediaList.value = []
      stripHasMore.value = true
    }
    const response = await getMediaList(stripPage.value, stripSize.value, currentCategory.value, props.memberId)
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
      // 通知父组件媒体列表更新
      emit('media-list-update', stripMediaList.value)
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
    const response = await getMediaList(gridPage.value, gridSize.value, currentCategory.value, props.memberId)
    if (response?.data) {
      gridMediaList.value = response.data.list || []
      gridTotal.value = response.data.total || 0
      // 通知父组件媒体列表更新
      emit('media-list-update', gridMediaList.value)
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

function loadMore() {
  if (displayMode.value !== 'strip') return
  if (stripHasMore.value && !stripLoading.value) loadStripList(false)
}

function handleCardClick(data) {
  emit('card-click', data)
}

// 监听memberId变化，重新加载数据
watch(() => props.memberId, () => {
  stripMediaList.value = []
  stripPage.value = 1
  stripHasMore.value = true
  gridMediaList.value = []
  gridPage.value = 1
  gridTotal.value = 0
  reloadCurrentMode(true)
})

// 初始化加载
loadStripList(true)
</script>

<style scoped>
.member-media-section {
  position: relative;
  z-index: 1;
  padding: 2rem 0;
}

.member-media-container {
  position: relative;
  width: 100%;
  padding-top: 4rem; /* 为左/右上角按钮留出空间 */
  padding-right: 12rem; /* 为右上角筛选按钮留出空间，防止重叠 */
}

/* 复用 media-browse.css 中的样式类 */
.category-selector {
  position: absolute;
  top: 1rem;
  right: 2rem;
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.35rem 0.8rem;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  z-index: 10;
}

.filter-selector {
  left: 2rem;
  right: auto;
}

.display-mode-selector {
  right: 2rem;
  left: auto;
}

.category-option {
  padding: 0.3rem 0.6rem;
  border: none;
  background: transparent;
  color: var(--text-secondary, rgba(44, 62, 80, 0.7));
  font-size: 0.85rem;
  font-weight: 500;
  cursor: pointer;
  border-radius: 10px;
  transition: all 0.3s ease;
}

.category-option:hover {
  color: var(--accent-color, #4a90e2);
  background: rgba(74, 144, 226, 0.1);
}

.category-option.active {
  color: var(--accent-color, #4a90e2);
  background: rgba(74, 144, 226, 0.2);
  font-weight: 600;
}

.category-separator {
  color: rgba(44, 62, 80, 0.3);
  font-size: 0.8rem;
  user-select: none;
}

.media-grid-wrapper {
  width: calc(100% + 12rem);
  margin-right: -12rem;
  padding: 0 2rem 1.5rem;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.media-grid {
  width: 100%;
  max-width: 1320px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1rem;
}

.media-grid .media-card {
  width: 100%;
  height: auto;
  aspect-ratio: 5 / 4;
  flex-shrink: initial;
  animation: none;
}

.media-grid .media-card.span-2,
.media-grid .media-card.span-3 {
  width: 100%;
}

.media-grid-pagination {
  width: 100%;
  max-width: 1320px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  margin-top: 1rem;
  color: var(--text-secondary);
}

.media-grid-page-btn {
  border: none;
  cursor: pointer;
  padding: 0.55rem 0.9rem;
  border-radius: 10px;
  font-size: 0.9rem;
  font-weight: 600;
  color: white;
  background: var(--accent-color, #4a90e2);
  transition: transform 0.15s ease, opacity 0.2s ease, box-shadow 0.2s ease;
  box-shadow: 0 4px 14px rgba(74, 144, 226, 0.25);
}

.media-grid-page-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(74, 144, 226, 0.35);
}

.media-grid-page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none;
}

.media-grid-page-info {
  font-size: 0.9rem;
  font-weight: 500;
}
</style>
