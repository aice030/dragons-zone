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
              <table class="upload-queue-table">
                <thead>
                  <tr>
                    <th>文件</th>
                    <th class="col-status">状态</th>
                    <th class="col-remove"></th>
                  </tr>
                </thead>
                <tbody>
                  <template v-for="item in queue" :key="item.key">
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
                          {{ statusLabel(item.status) }}
                        </span>
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
            <label class="upload-media-field-label">封面 <span class="required">*</span></label>
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
              <button
                type="button"
                class="btn-submit"
                :disabled="!canSubmit || uploading"
                @click="handleSubmit"
              >
                {{ uploading ? '上传中...' : '提交' }}
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
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from '@/components/NavBar.vue'
import { useUserStore } from '@/stores/user'
import { getMembers } from '@/config/members'
import { uploadMedia } from '@/api/media'

defineProps({
  embedded: {
    type: Boolean,
    default: false
  }
})

const router = useRouter()
const userStore = useUserStore()

const fileInputRef = ref(null)
const coverInputRef = ref(null)
const selectedFile = ref(null)
const coverFile = ref(null)
const coverPreview = ref('')
const fileError = ref('')
const coverError = ref('')
const uploading = ref(false)
const contentMode = ref('image') // 'image' | 'video'

// 批量上传
const folderInputRef = ref(null)
const multiInputRef = ref(null)
const queue = ref([]) // { key, file, name, relativePath, status, errorMsg, mediaId }
const batchUploading = ref(false)
const batchDone = ref(0)
const batchError = ref('')
const batchInfo = ref('')

const form = ref({
  title: '',
  description: '',
  visibleUserIds: []
})

const members = getMembers()

// 是否可以提交
const canSubmit = computed(() => {
  if (contentMode.value === 'image') return queue.value.length > 0
  // 视频模式：需选择视频文件且选择封面
  return selectedFile.value !== null && coverFile.value !== null
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
  if (contentMode.value === 'video' && !coverFile.value) {
    coverError.value = '请选择封面'
    return
  }

  uploading.value = true
  fileError.value = ''
  coverError.value = ''

  try {
    const fd = new FormData()
    fd.append('file', selectedFile.value)
    const category = contentMode.value === 'video' ? 1 : 0
    fd.append('category', String(category))
    fd.append('visibleUserIds', JSON.stringify(form.value.visibleUserIds || []))
    if (form.value.title) fd.append('title', form.value.title)
    if (form.value.description) fd.append('description', form.value.description)
    if (contentMode.value === 'video') fd.append('cover', coverFile.value)

    const res = await uploadMedia(fd)
    if (res && res.code !== 200) {
      throw new Error(res.message || '上传失败')
    }
    router.push('/my-uploads')
  } catch (error) {
    console.error('上传失败:', error)
    fileError.value = error?.message || '上传失败，请重试'
  } finally {
    uploading.value = false
  }
}

async function startBatchUpload() {
  if (batchUploading.value || queue.value.length === 0) return
  batchUploading.value = true
  batchDone.value = 0
  batchError.value = ''

  // 批量仅图片
  const category = 0

  for (let i = 0; i < queue.value.length; i++) {
    const item = queue.value[i]
    // 已成功/失败的不再重复传（支持用户重复点击开始上传）
    if (item.status === 'success') {
      batchDone.value += 1
      continue
    }
    item.status = 'uploading'
    item.errorMsg = ''

    try {
      const fd = new FormData()
      fd.append('file', item.file)
      // 图片类型：封面即为该图片本身
      fd.append('cover', item.file)
      fd.append('category', String(category))
      fd.append('visibleUserIds', JSON.stringify(form.value.visibleUserIds || []))
      if (form.value.title) fd.append('title', form.value.title)
      if (form.value.description) fd.append('description', form.value.description)

      const res = await uploadMedia(fd)
      if (res && res.code !== 200) {
        throw new Error(res.message || '上传失败')
      }
      item.status = 'success'
      item.mediaId = res?.data?.mediaId ?? null
    } catch (err) {
      item.status = 'failed'
      item.errorMsg = err?.message || '上传失败'
    } finally {
      batchDone.value += 1
    }
  }

  batchUploading.value = false
}

// 导航栏相关事件监听已移至 NavBar 组件
// 离开页面时释放批量队列的缩略图 URL（保留此逻辑）
onBeforeUnmount(() => {
  for (const item of queue.value) {
    if (item?.previewUrl) {
      URL.revokeObjectURL(item.previewUrl)
    }
  }
})
</script>

<style scoped>
/* 样式复用 media-detail-modal 和 media-browse 的样式 */

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
