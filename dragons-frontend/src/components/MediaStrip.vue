<template>
  <div class="media-strip-wrapper" ref="stripWrapper">
    <div class="media-strip" ref="strip">
      <!-- 两行独立排布：避免“同列上下卡片宽度不一致”导致的空洞 -->
      <div class="media-strip-row">
        <MediaCard
          v-for="media in rows.row1"
          :key="media.id"
          :media-id="media.id"
          :category="media.category"
          :title="media.title"
          :cover-url="media.coverUrl"
          :span="getCardSpan(media)"
          @click="handleCardClick"
        />
      </div>
      <div class="media-strip-row">
        <MediaCard
          v-for="media in rows.row2"
          :key="media.id"
          :media-id="media.id"
          :category="media.category"
          :title="media.title"
          :cover-url="media.coverUrl"
          :span="getCardSpan(media)"
          @click="handleCardClick"
        />
      </div>
    </div>
    
    <div v-if="loading" class="loading">
      <div class="loading-spinner"></div>
    </div>
    
    <div v-if="!loading && mediaList.length === 0" class="loading">
      <p>暂无内容</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import MediaCard from './MediaCard.vue'

const props = defineProps({
  mediaList: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['card-click', 'load-more'])

const stripWrapper = ref(null)
const strip = ref(null)

// span 缓存：同一媒体每次渲染保持一致，避免“同一张图宽度跳动”
const spanById = ref({})

// 获取卡片宽度（随机分配 span-1, span-2, span-3；对同一 id 固定）
const getCardSpan = (media) => {
  const id = media?.id
  if (id !== undefined && id !== null) {
    const cached = spanById.value[id]
    if (cached) return cached
  }

  const random = Math.random()
  const span = random < 0.6 ? 1 : random < 0.85 ? 2 : 3
  if (id !== undefined && id !== null) {
    spanById.value = { ...spanById.value, [id]: span }
  }
  return span
}

// 两行分配：按 span 近似均衡分配到两行
const rows = computed(() => {
  const row1 = []
  const row2 = []
  let w1 = 0
  let w2 = 0

  for (const media of props.mediaList || []) {
    const span = getCardSpan(media)
    if (w1 <= w2) {
      row1.push(media)
      w1 += span
    } else {
      row2.push(media)
      w2 += span
    }
  }
  return { row1, row2 }
})

// 处理卡片点击
const handleCardClick = (data) => {
  emit('card-click', data)
}

// 滚动到底部加载更多（移动端）
const handleScroll = () => {
  if (!stripWrapper.value || props.loading) return
  
  const wrapper = stripWrapper.value
  const scrollLeft = wrapper.scrollLeft
  const scrollWidth = wrapper.scrollWidth
  const clientWidth = wrapper.clientWidth
  
  // 横向滚动：接近右边界时加载更多
  if (scrollLeft + clientWidth >= scrollWidth - 100) {
    emit('load-more')
  }
}

onMounted(() => {
  if (stripWrapper.value) {
    stripWrapper.value.addEventListener('scroll', handleScroll)
  }
})

onUnmounted(() => {
  if (stripWrapper.value) {
    stripWrapper.value.removeEventListener('scroll', handleScroll)
  }
})
</script>

<style scoped>
/* 样式在 media-browse.css 中定义 */
</style>
