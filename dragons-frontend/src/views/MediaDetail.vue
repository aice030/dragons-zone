<template>
  <div class="media-detail-wrapper">
    <div class="media-detail-container" v-if="!error">
      <div class="media-detail-backdrop" @click="handleClose"></div>
      <div class="media-detail-content">
      <button class="close-btn" @click="handleClose" aria-label="关闭">×</button>
      
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
        </div>
        
        <!-- 媒体信息 -->
        <div class="media-info">
          <h2 class="media-title">{{ mediaDetail.title || '无标题' }}</h2>
          <p v-if="mediaDetail.description" class="media-description">
            {{ mediaDetail.description }}
          </p>
          <div class="media-meta">
            <div class="action-icons">
              <!-- 点赞按钮 -->
              <button 
                class="like-btn" 
                :class="{ 'liked': isLiked }"
                @click="handleLike"
              >
                <svg class="like-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <!-- 空心爱心路径 -->
                  <path 
                    v-if="!isLiked" 
                    d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"
                    stroke="currentColor"
                    stroke-width="1.5"
                    fill="none"
                  />
                  <!-- 实心爱心路径 -->
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
                v-if="mediaDetail"
                class="download-icon-btn"
                @click="handleDownload"
                :title="mediaDetail.title || '下载'"
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
import { getMediaDetail, getMediaDownloadUrl } from '@/api/media'

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
const handleLike = () => {
  // 防刷检查
  if (isThrottling.value) {
    return
  }
  
  // 设置防刷状态
  isThrottling.value = true
  
  // 切换点赞状态
  if (isLiked.value) {
    // 取消点赞
    isLiked.value = false
    likeCount.value = Math.max(0, likeCount.value - 1)
    // TODO: 调用后端API - 取消点赞（likeCount -1）
    // await cancelLike(props.mediaId)
  } else {
    // 点赞
    isLiked.value = true
    likeCount.value += 1
    // TODO: 调用后端API - 点赞（likeCount +1）
    // await likeMedia(props.mediaId)
  }
  
  // 500ms 后解除防刷状态
  if (throttleTimer) {
    clearTimeout(throttleTimer)
  }
  throttleTimer = setTimeout(() => {
    isThrottling.value = false
    throttleTimer = null
  }, 500)
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
    
    if (response?.data?.downloadUrl) {
      const tempDownloadUrl = response.data.downloadUrl
      
      // 使用 fetch 下载文件内容
      try {
        const fileResponse = await fetch(tempDownloadUrl)
        if (!fileResponse.ok) {
          throw new Error('下载失败')
        }
        
        const blob = await fileResponse.blob()
        
        // 获取文件扩展名（从 URL 提取）
        const urlExtension = tempDownloadUrl.split('.').pop()?.split('?')[0] || ''
        
        // 生成默认文件名
        let finalFileName = ''
        const mediaTitle = mediaDetail.value?.title?.trim()
        
        if (mediaTitle) {
          // 如果标题存在，使用 {title}.{扩展名}
          finalFileName = mediaTitle.includes('.') ? mediaTitle : `${mediaTitle}.${urlExtension}`
        } else {
          // 如果标题为空，使用默认名称
          const category = mediaDetail.value?.category
          const defaultName = category === 0 ? 'dragons-img' : 'dragons-video'
          finalFileName = `${defaultName}.${urlExtension}`
        }
        
        // 尝试使用 File System Access API 让用户选择保存路径（Chrome/Edge）
        if ('showSaveFilePicker' in window) {
          try {
            const fileHandle = await window.showSaveFilePicker({
              suggestedName: finalFileName,
              types: [{
                description: '媒体文件',
                accept: {
                  [blob.type || 'application/octet-stream']: [`.${urlExtension}`]
                }
              }]
            })
            
            const writable = await fileHandle.createWritable()
            await writable.write(blob)
            await writable.close()
            return // 成功保存，直接返回
          } catch (saveError) {
            // 用户取消了保存对话框，或者 API 调用失败
            if (saveError.name === 'AbortError') {
              // 用户取消，不显示错误
              return
            }
            console.warn('File System Access API 失败，使用传统下载方式:', saveError)
            // 继续使用传统下载方式
          }
        }
        
        // 回退到传统下载方式（会使用浏览器的默认下载路径或弹出保存对话框）
        const blobUrl = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = blobUrl
        // 不设置 download 属性，让浏览器使用默认行为（可能会弹出保存对话框）
        link.download = finalFileName
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        
        // 释放 blob URL
        setTimeout(() => {
          URL.revokeObjectURL(blobUrl)
        }, 100)
      } catch (fetchError) {
        console.error('下载文件失败:', fetchError)
        // 如果 fetch 失败，回退到直接使用链接下载
        // 生成默认文件名
        const urlExtension = tempDownloadUrl.split('.').pop()?.split('?')[0] || ''
        const mediaTitle = mediaDetail.value?.title?.trim()
        let fallbackFileName = ''
        
        if (mediaTitle) {
          fallbackFileName = mediaTitle.includes('.') ? mediaTitle : `${mediaTitle}.${urlExtension}`
        } else {
          const category = mediaDetail.value?.category
          const defaultName = category === 0 ? 'dragons-img' : 'dragons-video'
          fallbackFileName = `${defaultName}.${urlExtension}`
        }
        
        const link = document.createElement('a')
        link.href = tempDownloadUrl
        link.download = fallbackFileName
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
      }
    } else {
      console.error('获取下载链接失败：响应数据为空')
    }
  } catch (error) {
    console.error('获取下载链接失败:', error)
    // 可以在这里添加错误提示，比如使用 toast 组件
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
    // 重置点赞状态（切换媒体时）
    isLiked.value = false
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
