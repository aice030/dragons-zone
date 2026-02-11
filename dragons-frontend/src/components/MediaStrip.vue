<template>
  <div class="media-strip-wrapper" ref="stripWrapper">
    <div class="media-strip" ref="strip">
      <MediaCard
        v-for="media in mediaList"
        :key="media.id"
        :media-id="media.id"
        :category="media.category"
        :title="media.title"
        :cover-path="media.coverPath"
        :span="getCardSpan(media)"
        @click="handleCardClick"
      />
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
import { ref, onMounted, onUnmounted } from 'vue'
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

// 获取卡片宽度（随机分配 span-1, span-2, span-3）
const getCardSpan = (media) => {
  // 可以根据需要调整逻辑，这里简单随机分配
  const random = Math.random()
  if (random < 0.6) return 1 // 60% 正常宽度
  if (random < 0.85) return 2 // 25% 1.5倍宽
  return 3 // 15% 2倍宽
}

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
/* 样式在 home.css 中定义 */
</style>
