<template>
  <div class="media-detail-wrapper">
    <div class="media-detail-container" v-if="!error">
      <div class="media-detail-backdrop" @click="handleClose"></div>
      <div class="media-detail-content">
      <!-- 顶部操作栏：关闭、点赞、下载 -->
      <div class="top-action-bar">
        <button class="close-btn" @click="handleClose" aria-label="关闭">×</button>
        
        <!-- 点赞和下载按钮（仅在媒体加载完成后显示） -->
        <div v-if="!loading && mediaDetail" class="top-action-icons">
          <!-- 点赞按钮 -->
          <button 
            class="top-like-btn" 
            :class="{ 'liked': isLiked }"
            @click="handleLike"
            aria-label="点赞"
          >
            <svg class="like-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path 
                v-if="!isLiked" 
                d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"
                stroke="currentColor"
                stroke-width="1.5"
                fill="none"
              />
              <path 
                v-else 
                d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"
                fill="currentColor"
              />
            </svg>
            <span class="like-count">{{ likeCount }}</span>
          </button>
          
          <!-- 下载按钮 -->
          <button 
            class="top-download-btn"
            @click="handleDownload"
            :title="mediaDetail.title || '下载'"
            aria-label="下载"
          >
            <svg class="download-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path 
                d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </button>
        </div>
      </div>
      
      <!-- 左箭头按钮 -->
      <button 
        v-if="!loading && mediaDetail && hasPrevious"
        class="nav-arrow nav-arrow-left"
        @click="goToPrevious"
        aria-label="上一个"
      >
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M15 18l-6-6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
      
      <!-- 右箭头按钮 -->
      <button 
        v-if="!loading && mediaDetail && hasNext"
        class="nav-arrow nav-arrow-right"
        @click="goToNext"
        aria-label="下一个"
      >
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M9 18l6-6-6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
      
      <!-- 加载状态 -->
      <div v-if="loading" class="loading-container">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>
      
      <!-- 媒体内容 -->
      <div v-else-if="mediaDetail" class="media-wrapper">
        <div class="media-display">
          <!-- 图片 -->
          <img
            v-if="mediaDetail.category === 0 && mediaUrl"
            :src="mediaUrl"
            :alt="mediaDetail.title || '图片'"
            class="media-image"
            @error="handleImageError"
          />
          
          <!-- 视频 -->
          <video
            v-else-if="mediaDetail.category === 1 && mediaUrl"
            :src="mediaUrl"
            controls
            preload="metadata"
            class="media-video"
            @error="handleVideoError"
          >
            您的浏览器不支持视频播放
          </video>
          
          <!-- 加载占位符 -->
          <div v-if="!mediaUrl && !loading" class="media-placeholder">
            <div class="loading-spinner"></div>
            <p>加载媒体资源...</p>
          </div>

          <!-- 底部悬浮触发区：模仿抖音，鼠标移到下部才显示描述 -->
          <div class="media-bottom-hover-zone" aria-hidden="true"></div>

          <!-- 标题+描述：默认隐藏，悬浮下部触发区才显示 -->
          <div v-if="mediaDetail.title || mediaDetail.description" class="media-description-under">
            <div v-if="mediaDetail.title" class="media-description-title">{{ mediaDetail.title }}</div>
            <div v-if="mediaDetail.description" class="media-description-text">简介：{{ mediaDetail.description }}</div>
          </div>
        </div>
        
        <!-- 媒体信息（底部时间信息） -->
        <div class="media-info">
          <div class="media-meta">
            <span v-if="mediaDetail.updateTime" class="media-time">
              {{ formatTime(mediaDetail.updateTime) }}
            </span>
          </div>
        </div>
      </div>
    </div>
    </div>
    
    <!-- 错误状态 -->
    <div v-if="error" class="error-container">
      <div class="error-content">
        <h2>加载失败</h2>
        <p>{{ errorMessage }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { getMediaDetail, getMediaDownloadUrl, getLikeStatus, likeMedia, unlikeMedia } from '@/api/media'

// Props：接收媒体ID和媒体列表
const props = defineProps({
  mediaId: {
    type: Number,
    required: true
  },
  mediaList: {
    type: Array,
    default: () => []
  }
})

// Events：关闭详情事件和切换媒体事件
const emit = defineEmits(['close', 'switch-media'])

const mediaDetail = ref(null)
const mediaUrl = ref('')
const downloadUrl = ref('')
const loading = ref(true)
const error = ref(false)
const errorMessage = ref('')

// 计算当前媒体在列表中的索引
const currentIndex = computed(() => {
  if (!props.mediaList || props.mediaList.length === 0) {
    return -1
  }
  return props.mediaList.findIndex(item => item.id === props.mediaId)
})

// 是否有上一个
const hasPrevious = computed(() => {
  return currentIndex.value > 0
})

// 是否有下一个
const hasNext = computed(() => {
  return currentIndex.value >= 0 && currentIndex.value < props.mediaList.length - 1
})

// 切换到上一个媒体
const goToPrevious = () => {
  if (!hasPrevious.value) return
  
  const prevIndex = currentIndex.value - 1
  const prevMedia = props.mediaList[prevIndex]
  if (prevMedia && prevMedia.id) {
    emit('switch-media', prevMedia.id)
  }
}

// 切换到下一个媒体
const goToNext = () => {
  if (!hasNext.value) return
  
  const nextIndex = currentIndex.value + 1
  const nextMedia = props.mediaList[nextIndex]
  if (nextMedia && nextMedia.id) {
    emit('switch-media', nextMedia.id)
  }
}

// 点赞相关状态
const isLiked = ref(false)
const likeCount = ref(0)
const isThrottling = ref(false)
let throttleTimer = null

// 下载防刷状态
const isDownloadThrottling = ref(false)
let downloadThrottleTimer = null

// 加载媒体详情
const loadMediaDetail = async () => {
  if (!props.mediaId) {
    error.value = true
    errorMessage.value = '媒体ID无效'
    loading.value = false
    return
  }
  
  loading.value = true
  error.value = false
  
  try {
    // 获取详情
    const detailResponse = await getMediaDetail(props.mediaId)
    
    if (detailResponse?.data) {
      mediaDetail.value = detailResponse.data
      
      // 获取预览/播放 URL
      try {
        const downloadResponse = await getMediaDownloadUrl(props.mediaId)
        if (downloadResponse?.data?.downloadUrl) {
          mediaUrl.value = downloadResponse.data.downloadUrl
          downloadUrl.value = downloadResponse.data.downloadUrl
        }
      } catch (downloadError) {
        console.error('获取媒体URL失败:', downloadError)
        error.value = true
        errorMessage.value = '无法加载媒体资源'
      }
      // 已登录时查询当前用户是否已赞
      try {
        const statusRes = await getLikeStatus(props.mediaId)
        isLiked.value = statusRes?.data === true
      } catch (_) {
        isLiked.value = false
      }
    } else {
      error.value = true
      errorMessage.value = '媒体不存在'
    }
  } catch (err) {
    console.error('加载媒体详情失败:', err)
    error.value = true
    
    if (err.response?.status === 404) {
      errorMessage.value = '媒体不存在或已被删除'
    } else {
      errorMessage.value = '加载失败，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}

// 图片加载错误处理
const handleImageError = () => {
  error.value = true
  errorMessage.value = '图片加载失败'
}

// 视频加载错误处理
const handleVideoError = () => {
  error.value = true
  errorMessage.value = '视频加载失败'
}

// 格式化时间
const formatTime = (timeString) => {
  if (!timeString) return ''
  try {
    const date = new Date(timeString)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch {
    return timeString
  }
}

// 点赞处理函数
const handleLike = async () => {
  if (isThrottling.value || !props.mediaId) return
  isThrottling.value = true
  const wasLiked = isLiked.value
  const prevCount = likeCount.value
  // 乐观更新
  if (wasLiked) {
    isLiked.value = false
    likeCount.value = Math.max(0, likeCount.value - 1)
  } else {
    isLiked.value = true
    likeCount.value += 1
  }
  try {
    if (wasLiked) {
      await unlikeMedia(props.mediaId)
    } else {
      await likeMedia(props.mediaId)
    }
  } catch (err) {
    isLiked.value = wasLiked
    likeCount.value = prevCount
    if (err.response?.status === 401) {
      console.warn('点赞需先登录')
    } else {
      console.error('点赞操作失败:', err)
    }
  } finally {
    if (throttleTimer) clearTimeout(throttleTimer)
    throttleTimer = setTimeout(() => {
      isThrottling.value = false
      throttleTimer = null
    }, 500)
  }
}

// 下载处理函数
const handleDownload = async () => {
  // 防刷检查（1秒）
  if (isDownloadThrottling.value) {
    return
  }
  
  if (!props.mediaId) {
    return
  }
  
  // 设置防刷状态
  isDownloadThrottling.value = true
  
  try {
    // 调用后端API获取最新的临时下载链接
    const response = await getMediaDownloadUrl(props.mediaId)
    
    if (!response?.data?.downloadUrl) {
      console.error('获取下载链接失败：响应数据为空')
      return
    }
    
    const tempDownloadUrl = response.data.downloadUrl
    
    // 直接打开下载链接，让浏览器处理下载（避免前端访问OSS的跨域问题）
    window.open(tempDownloadUrl, '_blank')
  } catch (error) {
    console.error('获取下载链接失败:', error)
  } finally {
    // 1秒后解除防刷状态
    if (downloadThrottleTimer) {
      clearTimeout(downloadThrottleTimer)
    }
    downloadThrottleTimer = setTimeout(() => {
      isDownloadThrottling.value = false
      downloadThrottleTimer = null
    }, 1000)
  }
}

// 关闭详情
const handleClose = () => {
  emit('close')
}

// 初始化点赞数
watch(() => mediaDetail.value?.likeCount, (newCount) => {
  if (newCount !== undefined) {
    likeCount.value = newCount || 0
  }
}, { immediate: true })

// 监听 mediaId 变化，重新加载详情
watch(() => props.mediaId, (newId) => {
  if (newId) {
    loadMediaDetail()
    // 切换媒体时 isLiked 由 loadMediaDetail 内 getLikeStatus 结果设置
    // 清除防刷定时器
    if (throttleTimer) {
      clearTimeout(throttleTimer)
      throttleTimer = null
    }
    isThrottling.value = false
    // 清除下载防刷定时器
    if (downloadThrottleTimer) {
      clearTimeout(downloadThrottleTimer)
      downloadThrottleTimer = null
    }
    isDownloadThrottling.value = false
  }
}, { immediate: true })

// 组件挂载时加载数据
onMounted(() => {
  if (props.mediaId) {
    loadMediaDetail()
  }
})

// 组件卸载前清理定时器
onBeforeUnmount(() => {
  if (throttleTimer) {
    clearTimeout(throttleTimer)
    throttleTimer = null
  }
  if (downloadThrottleTimer) {
    clearTimeout(downloadThrottleTimer)
    downloadThrottleTimer = null
  }
})
</script>

<style scoped>
/* 样式在 media-detail.css 中定义 */
</style>
