<template>
  <div
    class="media-card"
    :class="cardClass"
    @click="handleClick"
  >
    <img
      v-if="coverUrl"
      :src="coverUrl"
      :alt="title || '媒体封面'"
      class="media-card-image"
      @error="handleImageError"
    />
    <div v-else class="media-card-placeholder">
      <span>加载中...</span>
    </div>
    
    <!-- 视频播放按钮图标 -->
    <div v-if="category === 1" class="media-card-play-icon">
      <svg width="60" height="60" viewBox="0 0 60 60" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="30" cy="30" r="30" fill="rgba(0, 0, 0, 0.6)"/>
        <path d="M24 18L24 42L42 30L24 18Z" fill="white"/>
      </svg>
    </div>
    
    <div v-if="title" class="media-card-overlay">
      <h3 class="media-card-title">{{ title }}</h3>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, watch } from 'vue'
import { getMediaDownloadUrl } from '@/api/media'

const props = defineProps({
  mediaId: {
    type: Number,
    required: true
  },
  category: {
    type: Number,
    required: true // 0=图片, 1=视频
  },
  title: {
    type: String,
    default: ''
  },
  coverPath: {
    type: String,
    default: ''
  },
  span: {
    type: Number,
    default: 1 // 1, 2, 3 对应不同宽度
  }
})

const emit = defineEmits(['click'])

const coverUrl = ref('')
const imageError = ref(false)

// 卡片样式类
const cardClass = computed(() => {
  return {
    'span-2': props.span === 2,
    'span-3': props.span === 3
  }
})

// 不再需要类型样式类和文本

// 加载封面图 URL
const loadCoverUrl = async () => {
  if (!props.coverPath || !props.mediaId) {
    return
  }
  
  try {
    // 使用 download 接口获取预览 URL
    const response = await getMediaDownloadUrl(props.mediaId)
    if (response?.data?.downloadUrl) {
      coverUrl.value = response.data.downloadUrl
      imageError.value = false
    }
  } catch (error) {
    console.error('加载封面图失败:', error)
    imageError.value = true
  }
}

// 图片加载错误处理
const handleImageError = () => {
  imageError.value = true
  coverUrl.value = ''
}

// 点击卡片
const handleClick = () => {
  emit('click', {
    mediaId: props.mediaId,
    category: props.category
  })
}

// 监听 mediaId 变化，重新加载封面
watch(() => props.mediaId, () => {
  loadCoverUrl()
})

// 组件挂载时加载封面
onMounted(() => {
  loadCoverUrl()
})
</script>

<style scoped>
.media-card-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  font-size: 0.9rem;
}
</style>
