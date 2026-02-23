<template>
  <div class="media-browse-container" :class="{ embedded }">
    <div v-if="!embedded" class="media-browse-background"></div>

    <div class="media-browse-content">
      <!-- 导航栏 -->
      <NavBar v-if="!embedded">
        <template #left>
          <router-link to="/my-uploads" class="nav-back-btn">
            <svg class="back-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M19 12H5M12 19l-7-7 7-7" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <span>返回</span>
          </router-link>
        </template>
      </NavBar>

      <!-- 上传内容区域 -->
      <div class="upload-media-container">
        <h1 v-if="!embedded" class="upload-page-title">上传新内容</h1>

        <div class="upload-media-form">
          <!-- 上传图片/视频（入口） -->
          <div class="upload-media-field">
            <div class="upload-mode-switch">
              <button
                type="button"
                class="upload-mode-btn"
                :class="{ active: contentMode === 'image' }"
                @click="setContentMode('image')"
              >
                上传图片
              </button>
              <button
                type="button"
                class="upload-mode-btn"
                :class="{ active: contentMode === 'video' }"
                @click="setContentMode('video')"
              >
                上传视频
              </button>
            </div>

            <p v-if="contentMode === 'video'" class="upload-media-field-desc">
              视频仅支持单个上传。
            </p>
            <p v-else class="upload-media-field-desc">
              图片支持单独上传和批量上传，批量上传的所有图片共享相同标题和描述。
            </p>
          </div>

          <!-- 图片：队列窗口（只选 1 张也走这里） -->
          <div v-if="contentMode === 'image'" class="upload-media-field">
            <label class="upload-media-field-label">选择图片</label>
            <div class="batch-pick-row">
              <button type="button" class="batch-pick-btn" @click="triggerFolderSelect" :disabled="batchUploading">
                选择文件夹
              </button>
              <button type="button" class="batch-pick-btn" @click="triggerMultiSelect" :disabled="batchUploading">
                添加图片
              </button>
              <span class="batch-pick-hint">已选 {{ queue.length }} 张</span>
            </div>

            <input
              ref="folderInputRef"
              type="file"
              class="file-input"
              accept="image/*"
              multiple
              webkitdirectory
              @change="handleBatchPick"
            />
            <input
              ref="multiInputRef"
              type="file"
              class="file-input"
              accept="image/*"
              multiple
              @change="handleBatchPick"
            />

            <p v-if="batchError" class="field-error">{{ batchError }}</p>
            <p v-else-if="batchInfo" class="batch-info">{{ batchInfo }}</p>

            <div v-if="queue.length > 0" class="upload-queue-wrapper">
              <div class="upload-queue-header">
                <span class="upload-queue-count">待处理 {{ displayQueue.length }} 张</span>
                <button
                  type="button"
                  class="upload-queue-clear-btn"
                  :disabled="batchUploading"
                  @click="clearQueue"
                >
                  清空
                </button>
              </div>
              <div class="upload-queue-scroll">
              <table class="upload-queue-table">
                <thead>
                  <tr>
                    <th>文件</th>
                    <th class="col-status">状态</th>
                    <th class="col-remove"></th>
                  </tr>
                </thead>
                <tbody>
                  <template v-for="item in displayQueue" :key="item.key">
                    <tr>
                      <td class="queue-file">
                        <div class="queue-file-main">
                          <img
                            v-if="item.previewUrl"
                            :src="item.previewUrl"
                            class="queue-file-thumb"
                            alt=""
                            loading="lazy"
                          />
                          <div class="queue-file-meta">
                            <div class="queue-file-name">{{ item.name }}</div>
                            <div v-if="item.relativePath" class="queue-file-path">{{ item.relativePath }}</div>
                          </div>
                        </div>
                      </td>
                      <td class="col-status">
                        <span class="queue-status" :class="`status-${item.status}`">
                          {{ item.status === 'uploading' && item.progress != null ? `上传中 ${item.progress}%` : statusLabel(item.status) }}
                        </span>
                        <div v-if="item.status === 'uploading' && item.progress != null" class="queue-progress-bar-wrap">
                          <div class="queue-progress-bar" :style="{ width: item.progress + '%' }"></div>
                        </div>
                      </td>
                      <td class="col-remove">
                        <button
                          type="button"
                          class="queue-remove-btn"
                          :disabled="batchUploading || item.status === 'uploading'"
                          @click="removeQueueItem(item.key)"
                          aria-label="移除"
                          title="移除"
                        >
                          ×
                        </button>
                      </td>
                    </tr>
                    <!-- 方式B：失败原因作为“展开行”展示 -->
                    <tr v-if="item.status === 'failed' && item.errorMsg" class="queue-error-row">
                      <td class="queue-error-cell" colspan="3">
                        <div class="queue-error-text">{{ item.errorMsg }}</div>
                      </td>
                    </tr>
                  </template>
                </tbody>
              </table>
              </div>
            </div>
          </div>

          <!-- 视频：文件选择 -->
          <div v-else class="upload-media-field">
            <label class="upload-media-field-label">选择视频</label>
            <div class="file-select-wrapper">
              <input
                ref="fileInputRef"
                type="file"
                accept="video/*"
                class="file-input"
                @change="handleFileSelect"
              />
              <div v-if="selectedFile" class="file-selected">
                <span class="file-name">{{ selectedFile.name }}</span>
                <button type="button" class="file-remove-btn" @click="clearFile">×</button>
              </div>
              <div v-else class="file-select-placeholder" @click="triggerFileSelect">
                <svg class="file-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
                <span>点击选择视频文件</span>
              </div>
            </div>
            <p v-if="fileError" class="field-error">{{ fileError }}</p>
          </div>

          <!-- 封面图 -->
          <div v-if="contentMode === 'video'" class="upload-media-field">
            <label class="upload-media-field-label">封面，不传的话自动用视频第一帧，可能会很抽象</label>
            <div class="upload-media-cover-wrapper">
              <img
                v-if="coverPreview"
                :src="coverPreview"
                alt="封面预览"
                class="upload-media-cover-preview"
              />
              <div v-else class="upload-media-cover-placeholder">暂无封面</div>
              <button type="button" class="upload-cover-btn upload-cover-overlay-btn" @click="triggerCoverSelect">
                选择封面
              </button>
              <input
                ref="coverInputRef"
                type="file"
                accept="image/*"
                class="cover-input"
                @change="handleCoverSelect"
              />
            </div>
            <p v-if="coverError" class="field-error">{{ coverError }}</p>
          </div>

          <!-- 分割线 -->
          <div class="upload-media-divider"></div>

          <!-- 标题 -->
          <div class="upload-media-field">
            <label class="upload-media-field-label">标题</label>
            <input
              v-model="form.title"
              type="text"
              class="upload-media-input"
              placeholder="请输入标题"
              maxlength="32"
            />
          </div>

          <!-- 简介 -->
          <div class="upload-media-field">
            <label class="upload-media-field-label">简介</label>
            <textarea
              v-model="form.description"
              class="upload-media-textarea"
              placeholder="请输入简介"
              rows="4"
              maxlength="128"
            ></textarea>
          </div>

          <!-- 标签 -->
          <div class="upload-media-field">
            <label class="upload-media-field-label">标签</label>
            <p class="upload-media-field-desc">上传的内容默认是全员可见的，添加标签可以让拽根们根据成员更有针对性的浏览</p>
            <div class="upload-media-checkboxes">
              <label
                v-for="member in members"
                :key="member.id"
                class="upload-checkbox-label"
              >
                <input
                  type="checkbox"
                  :value="member.id"
                  :checked="form.visibleUserIds.includes(member.id)"
                  @change="handleZoneCheckboxChange(member.id, $event)"
                  class="upload-checkbox"
                />
                <span class="upload-checkbox-text">{{ member.name }}</span>
              </label>
            </div>
          </div>

          <!-- 分割线 -->
          <div class="upload-media-divider"></div>

          <!-- 提交按钮 -->
          <div class="upload-media-actions">
            <button type="button" class="btn-cancel" @click="handleCancel">取消</button>
            <template v-if="contentMode === 'video'">
              <div v-if="uploading" class="upload-progress-inline">
                <div class="upload-progress-bar-wrap">
                  <div class="upload-progress-bar" :style="{ width: uploadProgress + '%' }"></div>
                </div>
                <span class="upload-progress-text">{{ uploadProgress > 0 ? `上传中 ${uploadProgress}%` : '上传中...' }}</span>
              </div>
              <button
                type="button"
                class="btn-submit"
                :disabled="!canSubmit || uploading"
                @click="handleSubmit"
              >
                {{ uploading ? (uploadProgress > 0 ? `${uploadProgress}%` : '上传中...') : '提交' }}
              </button>
            </template>
            <template v-else>
              <button type="button" class="btn-cancel" @click="clearQueue" :disabled="batchUploading">清空队列</button>
              <button
                type="button"
                class="btn-submit"
                :disabled="queue.length === 0 || batchUploading"
                @click="startBatchUpload"
              >
                {{ batchUploading ? `上传中...（${batchDone}/${queue.length}）` : '开始上传' }}
              </button>
            </template>
          </div>
        </div>
      </div>

      <!-- 上传结果提示（小弹窗） -->
      <Teleport to="body">
        <Transition name="toast-fade">
          <div
            v-if="toastVisible"
            class="upload-toast"
            :class="toastType === 'success' ? 'upload-toast-success' : 'upload-toast-error'"
            role="alert"
            @click="toastVisible = false"
          >
            <span class="upload-toast-message">{{ toastMessage }}</span>
            <button type="button" class="upload-toast-close" aria-label="关闭">×</button>
          </div>
        </Transition>
      </Teleport>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from '@/components/NavBar.vue'
import { getMembers } from '@/config/members'
import { prepareUpload, uploadComplete } from '@/api/media'
import { computeFileHash } from '@/utils/fileHash'
import { uploadFileToOss, uploadFileToOssMultipart } from '@/utils/ossUpload'

/** 超过此大小且后端返回了 stsCredentials 时走分块上传（5MB） */
const CHUNK_UPLOAD_THRESHOLD = 5 * 1024 * 1024

const props = defineProps({
  embedded: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['upload-success'])
const router = useRouter()

const fileInputRef = ref(null)
const coverInputRef = ref(null)
const selectedFile = ref(null)
const coverFile = ref(null)
const coverPreview = ref('')
const fileError = ref('')
const coverError = ref('')
const uploading = ref(false)
/** 分片上传进度 0～100，仅走分块上传时有值；PUT 直传时为 0 显示“上传中” */
const uploadProgress = ref(0)
const contentMode = ref('image') // 'image' | 'video'

// 批量上传
const folderInputRef = ref(null)
const multiInputRef = ref(null)
const queue = ref([]) // { key, file, name, relativePath, status, errorMsg, mediaId }
const batchUploading = ref(false)
const batchDone = ref(0)
const batchError = ref('')
const batchInfo = ref('')

// 上传结果提示
const toastVisible = ref(false)
const toastMessage = ref('')
const toastType = ref('success') // 'success' | 'error'
let toastTimer = null

const form = ref({
  title: '',
  description: '',
  visibleUserIds: []
})

const members = getMembers()

// 展示用队列：不显示已上传成功的图片
const displayQueue = computed(() =>
  queue.value.filter((item) => item.status !== 'success')
)

// 是否可以提交
const canSubmit = computed(() => {
  if (contentMode.value === 'image') return queue.value.length > 0
  // 视频模式：只需选择视频文件，封面可选（可提交时自动抽帧）
  return selectedFile.value !== null
})

function setContentMode(mode) {
  if (batchUploading.value || uploading.value) return
  contentMode.value = mode
  fileError.value = ''
  coverError.value = ''
  batchError.value = ''
  // 重要：切换“上传图片/上传视频”只切换视图，不清空用户已选内容
  // 清空时机：
  // - 离开页面（返回/取消）→ 组件卸载自然清空
  // - 刷新页面 → 内存状态丢失自然清空
}

// 导航栏相关函数已移至 NavBar 组件

function handleFileSelect(event) {
  const file = event.target.files?.[0]
  if (!file) {
    selectedFile.value = null
    fileError.value = ''
    return
  }

  // 按当前模式校验文件类型
  if (contentMode.value === 'image') {
    if (!file.type.startsWith('image/')) {
      fileError.value = '当前是上传图片，请选择图片文件'
      return
    }
  } else {
    if (!file.type.startsWith('video/')) {
      fileError.value = '当前是上传视频，请选择视频文件'
      return
    }
  }

  selectedFile.value = file
  fileError.value = ''
}

function clearFile() {
  selectedFile.value = null
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

function clearSingle() {
  clearFile()
  coverFile.value = null
  coverPreview.value = ''
  if (coverInputRef.value) coverInputRef.value.value = ''
}

function triggerFileSelect() {
  fileInputRef.value?.click()
}

function triggerFolderSelect() {
  folderInputRef.value?.click()
}

function triggerMultiSelect() {
  multiInputRef.value?.click()
}

function normalizePickedFiles(fileList) {
  const files = Array.from(fileList || [])
  return files.filter((f) => f && f.type && f.type.startsWith('image/'))
}

function fileFingerprint(file) {
  // 注意：多选单文件时拿不到目录路径，但选文件夹时有 webkitRelativePath。
  // 为了让两种选择方式能互相去重，这里用“文件名 + 大小 + 修改时间 + 类型”做统一指纹。
  // 代价：若不同目录下存在完全同名且 size/lastModified 相同的图片，会被视为重复（现实中较少）。
  return `${file.name}|${file.size}|${file.lastModified}|${file.type || ''}`
}

function handleBatchPick(event) {
  const files = normalizePickedFiles(event.target.files)
  if (!files.length) {
    batchError.value = '未选择到图片文件'
    batchInfo.value = ''
    return
  }
  batchError.value = ''
  batchInfo.value = ''

  const existing = new Set(queue.value.map((q) => q.fingerprint))
  let skipped = 0

  // 追加到队列（跨“单文件选择/选文件夹”做统一去重）
  for (const f of files) {
    const fp = fileFingerprint(f)
    if (existing.has(fp)) {
      skipped += 1
      continue
    }
    existing.add(fp)
    const previewUrl = URL.createObjectURL(f)
    queue.value.push({
      key: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
      fingerprint: fp,
      file: f,
      previewUrl,
      name: f.name,
      relativePath: f.webkitRelativePath || '',
      status: 'pending',
      errorMsg: '',
      mediaId: null
    })
  }

  if (skipped > 0) {
    batchInfo.value = `已忽略 ${skipped} 张重复图片`
  }

  // 清空 input，允许再次选择同一批
  if (event?.target) event.target.value = ''
}

function clearQueue() {
  if (batchUploading.value) return
  // 释放本地预览 URL，避免内存泄露
  for (const item of queue.value) {
    if (item?.previewUrl) {
      URL.revokeObjectURL(item.previewUrl)
    }
  }
  queue.value = []
  batchDone.value = 0
  batchError.value = ''
  batchInfo.value = ''
}

function removeQueueItem(key) {
  if (batchUploading.value) return
  const idx = queue.value.findIndex((q) => q.key === key)
  if (idx >= 0) {
    const item = queue.value[idx]
    if (item?.previewUrl) {
      URL.revokeObjectURL(item.previewUrl)
    }
    queue.value.splice(idx, 1)
  }
}

function statusLabel(status) {
  if (status === 'pending') return '待上传'
  if (status === 'uploading') return '上传中'
  if (status === 'success') return '成功'
  if (status === 'failed') return '失败'
  return '未知'
}

function triggerCoverSelect() {
  coverInputRef.value?.click()
}

/**
 * 从视频文件截取第一帧为 JPEG 图片（用于未选封面时自动生成封面）
 * @param {File} videoFile 视频文件
 * @returns {Promise<File|null>} 封面图片 File（cover.jpg），失败返回 null
 */
function extractFirstFrameFromVideo(videoFile) {
  return new Promise((resolve) => {
    let done = false
    const url = URL.createObjectURL(videoFile)
    const video = document.createElement('video')
    video.muted = true
    video.playsInline = true
    video.preload = 'auto'
    video.src = url

    const cleanup = () => {
      URL.revokeObjectURL(url)
      video.remove()
    }

    const finish = (file) => {
      if (done) return
      done = true
      cleanup()
      resolve(file)
    }

    const drawFrame = () => {
      if (done) return
      try {
        if (video.videoWidth === 0 || video.videoHeight === 0) {
          finish(null)
          return
        }
        const canvas = document.createElement('canvas')
        canvas.width = video.videoWidth
        canvas.height = video.videoHeight
        const ctx = canvas.getContext('2d')
        ctx.drawImage(video, 0, 0)
        canvas.toBlob(
          (blob) => {
            if (done) return
            if (!blob) {
              finish(null)
              return
            }
            finish(new File([blob], 'cover.jpg', { type: 'image/jpeg' }))
          },
          'image/jpeg',
          0.9
        )
      } catch (e) {
        console.warn('extractFirstFrameFromVideo draw error', e)
        finish(null)
      }
    }

    video.addEventListener('error', () => finish(null))
    video.addEventListener('seeked', () => {
      clearTimeout(fallbackTimer)
      drawFrame()
    }, { once: true })
    video.addEventListener('loadeddata', () => {
      video.currentTime = 0
    })
    const fallbackTimer = setTimeout(() => {
      if (done) return
      if (video.readyState >= 2 && video.videoWidth > 0) {
        drawFrame()
      } else {
        finish(null)
      }
    }, 2000)

    video.load()
  })
}

function handleCoverSelect(event) {
  const file = event.target.files?.[0]
  if (!file) {
    coverFile.value = null
    coverPreview.value = ''
    coverError.value = ''
    return
  }

  if (!file.type.startsWith('image/')) {
    coverError.value = '封面必须是图片文件'
    return
  }

  coverFile.value = file
  coverError.value = ''

  // 生成预览
  const reader = new FileReader()
  reader.onload = (e) => {
    coverPreview.value = e.target.result
  }
  reader.readAsDataURL(file)
}

function handleZoneCheckboxChange(zoneId, event) {
  if (event.target.checked) {
    if (!form.value.visibleUserIds.includes(zoneId)) {
      form.value.visibleUserIds.push(zoneId)
    }
  } else {
    const index = form.value.visibleUserIds.indexOf(zoneId)
    if (index > -1) {
      form.value.visibleUserIds.splice(index, 1)
    }
  }
}

function showToast(message, type = 'success') {
  if (toastTimer) clearTimeout(toastTimer)
  toastMessage.value = message
  toastType.value = type
  toastVisible.value = true
  toastTimer = setTimeout(() => {
    toastVisible.value = false
    toastTimer = null
  }, 2500)
}

/** 清空当前表单：文件、封面、标题、简介、标签、错误信息 */
function resetForm() {
  selectedFile.value = null
  coverFile.value = null
  coverPreview.value = ''
  fileError.value = ''
  coverError.value = ''
  batchError.value = ''
  batchInfo.value = ''
  form.value.title = ''
  form.value.description = ''
  form.value.visibleUserIds = []
  if (fileInputRef.value) fileInputRef.value.value = ''
  if (coverInputRef.value) coverInputRef.value.value = ''
  clearQueue()
}

function handleCancel() {
  router.push('/my-uploads')
}

async function handleSubmit() {
  if (!canSubmit.value || uploading.value) return

  // 验证
  if (!selectedFile.value) {
    fileError.value = '请选择视频文件'
    return
  }

  uploading.value = true
  fileError.value = ''
  coverError.value = ''
  uploadProgress.value = 0

  let mediaId = null
  try {
    const file = selectedFile.value
    const category = contentMode.value === 'video' ? 1 : 0

    // 第一步：计算文件 hash 并调用“准备上传”接口
    const fileHash = await computeFileHash(file)

    const prepareRes = await prepareUpload({
      fileHash,
      category,
      title: form.value.title || '',
      description: form.value.description || '',
      filename: file.name
    })

    if (!prepareRes || prepareRes.code !== 200) {
      throw new Error(prepareRes?.message || '准备上传失败')
    }

    const prepareData = prepareRes.data || {}
    mediaId = prepareData.mediaId
    const uploadUrl = prepareData.uploadUrl

    if (!mediaId || !uploadUrl) {
      throw new Error('准备上传返回数据不完整')
    }

    // 第二步：大文件且有 STS 时走分块上传，否则预签名 PUT 直传
    const hasSts = !!prepareData.stsCredentials
    const overThreshold = file.size >= CHUNK_UPLOAD_THRESHOLD
    console.log('上传方式判断 file_size:', file.size, 'hasSts:', hasSts, 'overThreshold(>=5MB):', overThreshold)
    if (hasSts && overThreshold) {
      await uploadFileToOssMultipart(prepareData.stsCredentials, prepareData.storagePath, file, {
        onProgress: (p) => { uploadProgress.value = Math.round((p ?? 0) * 100) }
      })
    } else {
      await uploadFileToOss(uploadUrl, file)
    }

    // 第三步：视频且未选封面时自动抽首帧作为封面
    let coverToSend = contentMode.value === 'video' ? coverFile.value : file
    if (contentMode.value === 'video' && !coverToSend) {
      const extractedCover = await extractFirstFrameFromVideo(file)
      coverToSend = extractedCover || null
    }

    // 第四步：通知上传结果
    const completeRes = await uploadComplete({
      mediaId,
      success: true,
      visibleUserIds: form.value.visibleUserIds || [],
      cover: contentMode.value === 'video' ? coverToSend : file
    })

    if (completeRes && completeRes.code !== 200) {
      throw new Error(completeRes.message || '通知上传结果失败')
    }

    showToast('上传成功', 'success')
    resetForm()
    if (props.embedded) {
      emit('upload-success')
    } else {
      setTimeout(() => {
        router.push('/my-uploads')
      }, 1200)
    }
  } catch (error) {
    console.error('上传失败:', error)
    if (mediaId != null) {
      try {
        await uploadComplete({ mediaId, success: false, visibleUserIds: [] })
      } catch (e) {
        console.error('通知上传失败结果失败', e)
      }
    }
    showToast('上传失败：' + (error?.message || '请重试'), 'error')
    fileError.value = error?.message || '上传失败，请重试'
  } finally {
    uploading.value = false
    uploadProgress.value = 0
  }
}

async function startBatchUpload() {
  if (batchUploading.value || queue.value.length === 0) return
  batchUploading.value = true
  batchDone.value = 0
  batchError.value = ''

  for (let i = 0; i < queue.value.length; i++) {
    const item = queue.value[i]
    // 已成功/失败的不再重复传（支持用户重复点击开始上传）
    if (item.status === 'success') {
      batchDone.value += 1
      continue
    }
    item.status = 'uploading'
    item.errorMsg = ''
    item.progress = 0

    try {
      const file = item.file
      const category = 0

      // 第一步：计算文件 hash 并调用“准备上传”接口
      const fileHash = await computeFileHash(file)
      let mediaId = null

      const prepareRes = await prepareUpload({
        fileHash,
        category,
        title: form.value.title || '',
        description: form.value.description || '',
        filename: file.name
      })

      if (!prepareRes || prepareRes.code !== 200) {
        throw new Error(prepareRes?.message || '准备上传失败')
      }

      const prepareData = prepareRes.data || {}
      mediaId = prepareData.mediaId
      const uploadUrl = prepareData.uploadUrl

      if (!mediaId || !uploadUrl) {
        throw new Error('准备上传返回数据不完整')
      }

      // 第二步：大文件且有 STS 时走分块上传，否则预签名 PUT 直传
      const hasSts = !!prepareData.stsCredentials
      const overThreshold = file.size >= CHUNK_UPLOAD_THRESHOLD
      console.log('上传方式判断 file_size:', file.size, 'hasSts:', hasSts, 'overThreshold(>=5MB):', overThreshold)
      if (hasSts && overThreshold) {
        await uploadFileToOssMultipart(prepareData.stsCredentials, prepareData.storagePath, file, {
          onProgress: (p) => { item.progress = Math.round((p ?? 0) * 100) }
        })
      } else {
        await uploadFileToOss(uploadUrl, file)
      }

      // 第三步：通知上传结果（图片封面即为原图）
      const completeRes = await uploadComplete({
        mediaId,
        success: true,
        visibleUserIds: form.value.visibleUserIds || [],
        cover: file
      })

      if (completeRes && completeRes.code !== 200) {
        throw new Error(completeRes.message || '通知上传结果失败')
      }

      item.status = 'success'
      item.mediaId = mediaId
    } catch (err) {
      if (mediaId != null) {
        try {
          await uploadComplete({ mediaId, success: false, visibleUserIds: [] })
        } catch (e) {
          console.error('通知上传失败结果失败', e)
        }
      }
      item.status = 'failed'
      item.errorMsg = err?.message || '上传失败'
    } finally {
      batchDone.value += 1
    }
  }

  const successCount = queue.value.filter((i) => i.status === 'success').length
  const failCount = queue.value.filter((i) => i.status === 'failed').length
  if (failCount === 0) {
    showToast('全部上传成功', 'success')
  } else {
    showToast(`上传完成：${successCount} 张成功，${failCount} 张失败`, 'error')
  }
  batchUploading.value = false
  resetForm()
}

// 导航栏相关事件监听已移至 NavBar 组件
// 离开页面时释放批量队列的缩略图 URL（保留此逻辑）
onBeforeUnmount(() => {
  if (toastTimer) clearTimeout(toastTimer)
  for (const item of queue.value) {
    if (item?.previewUrl) {
      URL.revokeObjectURL(item.previewUrl)
    }
  }
})
</script>

<style scoped>
/* 样式复用 media-detail-modal 和 media-browse 的样式 */

/* 上传结果提示（小弹窗） */
.upload-toast {
  position: fixed;
  top: 24px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2000;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  border-radius: 10px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
  cursor: pointer;
  min-width: 200px;
  max-width: 90vw;
}

.upload-toast-success {
  background: rgba(39, 174, 96, 0.95);
  color: #fff;
}

.upload-toast-error {
  background: rgba(231, 76, 60, 0.95);
  color: #fff;
}

.upload-toast-message {
  flex: 1;
  font-size: 0.95rem;
  font-weight: 500;
}

.upload-toast-close {
  background: none;
  border: none;
  color: inherit;
  font-size: 1.25rem;
  line-height: 1;
  cursor: pointer;
  opacity: 0.9;
  padding: 0 4px;
}

.upload-toast-close:hover {
  opacity: 1;
}

.toast-fade-enter-active,
.toast-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.toast-fade-enter-from,
.toast-fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-12px);
}

/* 单文件（视频）上传进度 */
.upload-progress-inline {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 160px;
}
.upload-progress-bar-wrap {
  width: 120px;
  height: 8px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 4px;
  overflow: hidden;
}
.upload-progress-bar {
  height: 100%;
  background: rgba(39, 174, 96, 0.9);
  border-radius: 4px;
  transition: width 0.2s ease;
}
.upload-progress-text {
  font-size: 0.9rem;
  color: rgba(255, 255, 255, 0.9);
}

/* 批量上传队列行内进度条 */
.queue-progress-bar-wrap {
  margin-top: 4px;
  width: 80px;
  height: 4px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
  overflow: hidden;
}
.queue-progress-bar {
  height: 100%;
  background: rgba(39, 174, 96, 0.9);
  border-radius: 2px;
  transition: width 0.2s ease;
}

/* 嵌入到“我的上传”页面时：去掉全屏页面感 */
.media-browse-container.embedded {
  min-height: auto;
  background-color: transparent;
  overflow: visible;
}

.media-browse-container.embedded .media-browse-content {
  padding-top: 0;
  min-height: auto;
}

.media-browse-container.embedded .upload-media-container {
  padding-top: 0.25rem;
  padding-bottom: 0;
}
</style>
