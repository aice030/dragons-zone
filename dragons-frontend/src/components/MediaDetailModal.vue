<template>
  <Teleport to="body">
    <Transition name="modal-fade">
      <div v-if="visible" class="media-detail-modal-overlay" @click.self="close">
        <div class="media-detail-modal-box">
          <button type="button" class="media-detail-modal-close" aria-label="关闭" @click="close">×</button>

          <!-- 加载中 -->
          <div v-if="loading" class="media-detail-modal-loading">
            <div class="loading-spinner"></div>
            <p>加载中...</p>
          </div>

          <!-- 加载失败 -->
          <div v-else-if="error" class="media-detail-modal-error">
            <p>{{ errorMessage }}</p>
          </div>

          <!-- 内容 -->
          <template v-else-if="detail">
            <!-- 封面图 -->
            <div class="media-detail-modal-cover-wrapper">
              <img
                v-if="displayCoverUrl"
                :src="displayCoverUrl"
                :alt="detail.title || '封面'"
                class="media-detail-modal-cover"
                @error="handleCoverError"
              />
              <div v-else class="media-detail-modal-cover-placeholder">暂无封面</div>
              <!-- 视频时才显示更换封面按钮，默认显示在右下角 -->
              <div
                v-if="detail.category === 1"
                class="media-detail-modal-cover-overlay"
                :class="{ 'always-visible': detail.category === 1 }"
              >
                <div class="change-cover-row" @click.stop>
                  <button
                    type="button"
                    class="change-cover-btn"
                    :disabled="coverUploading"
                    @click="handleChangeCover"
                  >
                    更换封面
                  </button>

                  <input
                    ref="coverInputRef"
                    type="file"
                    accept="image/*"
                    class="change-cover-input"
                    @change="onCoverFileChange"
                  />

                  <button
                    v-if="isCoverChanging"
                    type="button"
                    class="change-cover-confirm-btn"
                    :disabled="!selectedCoverFile || coverUploading"
                    @click="confirmCoverChange"
                  >
                    {{ coverUploading ? '更换中...' : '确认更换' }}
                  </button>
                  <button
                    v-if="isCoverChanging"
                    type="button"
                    class="change-cover-cancel-btn"
                    :disabled="coverUploading"
                    @click="cancelCoverChange"
                  >
                    取消
                  </button>
                </div>
              </div>
            </div>

            <!-- 分割线 -->
            <div class="media-detail-modal-divider media-detail-modal-divider-strong"></div>

            <!-- 标题 -->
            <div class="media-detail-modal-field">
              <label class="media-detail-modal-field-label">标题</label>
              <input
                v-if="isEditing"
                v-model="editTitle"
                type="text"
                class="media-detail-modal-input"
                placeholder="请输入标题"
              />
              <div v-else class="media-detail-modal-text">{{ detail.title || '无标题' }}</div>
            </div>

            <!-- 简介 -->
            <div class="media-detail-modal-field">
              <div class="media-detail-modal-field-header">
                <label class="media-detail-modal-field-label">简介</label>
                <div class="field-actions">
                  <template v-if="!isEditing">
                    <button type="button" class="edit-btn" @click="startEditBaseInfo">编辑</button>
                  </template>
                  <template v-else>
                    <button
                      type="button"
                      class="field-confirm-btn"
                      :disabled="baseSaving"
                      @click="confirmEditBaseInfo"
                    >
                      {{ baseSaving ? '保存中...' : '确认' }}
                    </button>
                    <button
                      type="button"
                      class="field-cancel-btn"
                      :disabled="baseSaving"
                      @click="cancelEditBaseInfo"
                    >
                      取消
                    </button>
                  </template>
                </div>
              </div>
              <textarea
                v-if="isEditing"
                v-model="editDescription"
                class="media-detail-modal-textarea"
                placeholder="请输入简介"
                rows="4"
              ></textarea>
              <div v-else class="media-detail-modal-text">
                {{ detail.description || '无描述' }}
              </div>
            </div>

            <!-- 简介与标签分割线（更明显，避免编辑范围不清晰） -->
            <div class="media-detail-modal-divider media-detail-modal-divider-strong"></div>

            <!-- 标签 -->
            <div class="media-detail-modal-field">
              <div class="media-detail-modal-field-header">
                <label class="media-detail-modal-field-label">标签</label>
                <div class="field-actions">
                  <template v-if="!isZonesEditing">
                    <button type="button" class="edit-btn" @click="startEditZones">编辑标签</button>
                  </template>
                  <template v-else>
                    <button
                      type="button"
                      class="field-confirm-btn"
                      :disabled="zonesSaving"
                      @click="confirmEditZones"
                    >
                      {{ zonesSaving ? '保存中...' : '确认' }}
                    </button>
                    <button
                      type="button"
                      class="field-cancel-btn"
                      :disabled="zonesSaving"
                      @click="cancelEditZones"
                    >
                      取消
                    </button>
                  </template>
                </div>
              </div>
              <div class="media-detail-modal-zones-tags">
                <span
                  v-for="zoneId in (isZonesEditing ? editZoneIds : visibleZoneIds)"
                  :key="zoneId"
                  class="zone-tag"
                  :class="{ 'zone-tag-editable': isZonesEditing }"
                >
                  {{ getMemberName(zoneId) }}
                  <button
                    v-if="isZonesEditing"
                    type="button"
                    class="zone-tag-remove"
                    @click="removeZone(zoneId)"
                    aria-label="删除标签"
                  >
                    ×
                  </button>
                </span>
                <span v-if="(isZonesEditing ? editZoneIds : visibleZoneIds).length === 0 && !isZonesEditing" class="zone-tag-empty">无标签</span>
                <!-- 编辑模式下显示添加标签按钮 -->
                <div v-if="isZonesEditing" class="zone-tag-add-wrapper" @click.stop>
                  <button
                    type="button"
                    class="zone-tag-add-btn"
                    @click="showAddTagMenu = !showAddTagMenu"
                    aria-label="添加标签"
                  >
                    <svg class="add-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M12 5v14M5 12h14" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                  </button>
                  <!-- 标签选择菜单 -->
                  <Transition name="dropdown">
                    <div v-if="showAddTagMenu" class="zone-tag-menu">
                      <div
                        v-for="member in availableMembers"
                        :key="member.id"
                        class="zone-tag-menu-item"
                      >
                        <span class="zone-tag-menu-name">{{ member.name }}</span>
                        <button
                          type="button"
                          class="zone-tag-menu-add"
                          @click="addZone(member.id)"
                          aria-label="添加"
                        >
                          <svg class="add-icon-small" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M12 5v14M5 12h14" stroke-linecap="round" stroke-linejoin="round"/>
                          </svg>
                        </button>
                      </div>
                      <div v-if="availableMembers.length === 0" class="zone-tag-menu-empty">
                        暂无可用标签
                      </div>
                    </div>
                  </Transition>
                </div>
              </div>
            </div>

            <!-- 分割线 -->
            <div class="media-detail-modal-divider"></div>
          </template>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, watch, computed, onBeforeUnmount, nextTick } from 'vue'
import { getMediaDetail, getMediaVisibleZones, updateMediaCover, updateMediaVisibleZones, updateMediaBaseInfo } from '@/api/media'
import { getMembers } from '@/config/members'

const props = defineProps({
  visible: { type: Boolean, default: false },
  mediaId: { type: Number, default: null }
})

const emit = defineEmits(['update:visible', 'close', 'cover-updated', 'base-updated'])

const detail = ref(null)
const coverUrl = ref('')
const pendingCoverPreviewUrl = ref('')
const loading = ref(false)
const error = ref(false)
const errorMessage = ref('')
const isEditing = ref(false)
const editTitle = ref('')
const editDescription = ref('')
const visibleZoneIds = ref([]) // 当前媒体已设置的可见专区ID列表
const editZoneIds = ref([]) // 编辑状态下的可见专区ID列表
const isZonesEditing = ref(false) // 标签编辑状态
const showAddTagMenu = ref(false) // 是否显示添加标签菜单
const members = getMembers()
const baseSaving = ref(false)
const zonesSaving = ref(false)

// 更换封面相关状态
const coverInputRef = ref(null)
const isCoverChanging = ref(false)
const selectedCoverFile = ref(null)
const coverUploading = ref(false)

const displayCoverUrl = computed(() => pendingCoverPreviewUrl.value || coverUrl.value)

// 获取可添加的成员列表（排除已添加的）
const availableMembers = computed(() => {
  return members.filter(member => !editZoneIds.value.includes(member.id))
})

// 根据成员ID获取成员名称
function getMemberName(memberId) {
  const member = members.find(m => m.id === memberId)
  return member ? member.name : `未知(${memberId})`
}

async function load() {
  if (!props.mediaId) {
    detail.value = null
    coverUrl.value = ''
    pendingCoverPreviewUrl.value = ''
    return
  }
  loading.value = true
  error.value = false
  errorMessage.value = ''
  detail.value = null
  coverUrl.value = ''
  pendingCoverPreviewUrl.value = ''
  isEditing.value = false
  isZonesEditing.value = false
  isCoverChanging.value = false
  selectedCoverFile.value = null
  coverUploading.value = false
  visibleZoneIds.value = []
  editZoneIds.value = []
  try {
    const detailRes = await getMediaDetail(props.mediaId)
    if (detailRes?.data) {
      detail.value = detailRes.data
      editTitle.value = detailRes.data.title || ''
      editDescription.value = detailRes.data.description || ''
      // 加载封面图（后端详情返回 coverUrl）
      coverUrl.value = detailRes.data.coverUrl || ''
      // 加载可见专区列表
      try {
        const zonesRes = await getMediaVisibleZones(props.mediaId)
        if (zonesRes && zonesRes.code === 200 && zonesRes.data) {
          visibleZoneIds.value = Array.isArray(zonesRes.data) ? zonesRes.data : []
        } else {
          visibleZoneIds.value = []
        }
        editZoneIds.value = [...visibleZoneIds.value]
      } catch (zonesErr) {
        console.warn('加载可见专区失败:', zonesErr)
        visibleZoneIds.value = []
        editZoneIds.value = []
      }
    } else {
      error.value = true
      errorMessage.value = '媒体不存在'
    }
  } catch (err) {
    console.error('加载媒体详情失败:', err)
    error.value = true
    errorMessage.value = err.response?.status === 404 ? '媒体不存在或已被删除' : '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function handleCoverError() {
  coverUrl.value = ''
}

function startEditBaseInfo() {
  editTitle.value = detail.value?.title || ''
  editDescription.value = detail.value?.description || ''
  isEditing.value = true
}

function cancelEditBaseInfo() {
  if (baseSaving.value) return
  editTitle.value = detail.value?.title || ''
  editDescription.value = detail.value?.description || ''
  isEditing.value = false
}

async function confirmEditBaseInfo() {
  if (!props.mediaId || baseSaving.value) return
  baseSaving.value = true
  try {
    const res = await updateMediaBaseInfo(props.mediaId, {
      title: editTitle.value,
      description: editDescription.value
    })
    if (res?.code === 200) {
      if (detail.value) {
        detail.value.title = editTitle.value
        detail.value.description = editDescription.value
      }
      // 后端规则：更新基础信息后状态回到待审核
      emit('base-updated', {
        mediaId: props.mediaId,
        title: editTitle.value,
        description: editDescription.value,
        state: 6
      })
      isEditing.value = false
    }
  } catch (err) {
    console.error('更新标题/简介失败:', err)
  } finally {
    baseSaving.value = false
  }
}

function startEditZones() {
  editZoneIds.value = [...visibleZoneIds.value]
  isZonesEditing.value = true
  showAddTagMenu.value = false
}

function cancelEditZones() {
  if (zonesSaving.value) return
  editZoneIds.value = [...visibleZoneIds.value]
  isZonesEditing.value = false
  showAddTagMenu.value = false
}

async function confirmEditZones() {
  if (!props.mediaId || zonesSaving.value) return
  zonesSaving.value = true
  try {
    const res = await updateMediaVisibleZones(props.mediaId, editZoneIds.value)
    if (res?.code === 200) {
      visibleZoneIds.value = [...editZoneIds.value]
      isZonesEditing.value = false
      showAddTagMenu.value = false
    }
  } catch (err) {
    console.error('更新标签失败:', err)
  } finally {
    zonesSaving.value = false
  }
}

function removeZone(zoneId) {
  if (!isZonesEditing.value) return
  const index = editZoneIds.value.indexOf(zoneId)
  if (index > -1) {
    editZoneIds.value.splice(index, 1)
  }
}

function addZone(zoneId) {
  if (!isZonesEditing.value) return
  if (!editZoneIds.value.includes(zoneId)) {
    editZoneIds.value.push(zoneId)
  }
  // 添加后可以选择关闭菜单或保持打开
}

function handleClickOutside(event) {
  const menuWrapper = event.target.closest('.zone-tag-add-wrapper')
  if (!menuWrapper) {
    showAddTagMenu.value = false
  }
}

function handleChangeCover() {
  if (coverUploading.value) return
  isCoverChanging.value = true
  // 打开文件选择器
  nextTick(() => {
    coverInputRef.value?.click?.()
  })
}

function onCoverFileChange(event) {
  const file = event.target?.files?.[0]
  if (!file) return

  // 只允许图片（兜底校验）
  if (!file.type || !file.type.startsWith('image/')) {
    // 清理选择
    cancelCoverChange()
    return
  }

  selectedCoverFile.value = file

  // 本地预览（确认前也能看到效果）
  if (pendingCoverPreviewUrl.value) {
    URL.revokeObjectURL(pendingCoverPreviewUrl.value)
  }
  pendingCoverPreviewUrl.value = URL.createObjectURL(file)
}

async function confirmCoverChange() {
  if (!props.mediaId || !selectedCoverFile.value || coverUploading.value) return
  coverUploading.value = true
  try {
    const res = await updateMediaCover(props.mediaId, selectedCoverFile.value)
    if (res?.code === 200 && res?.data) {
      // 优先用后端返回的 coverUrl（预签名），否则保留本地预览
      if (res.data.coverUrl) {
        // 若之前是本地预览，释放 objectUrl
        if (pendingCoverPreviewUrl.value) {
          URL.revokeObjectURL(pendingCoverPreviewUrl.value)
          pendingCoverPreviewUrl.value = ''
        }
        coverUrl.value = res.data.coverUrl
      }
      // 同步 detail（若存在）
      if (detail.value) {
        detail.value.coverPath = res.data.coverPath || detail.value.coverPath
      }

      emit('cover-updated', {
        mediaId: props.mediaId,
        coverPath: res.data.coverPath,
        coverUrl: res.data.coverUrl,
        // 后端逻辑：更新封面后状态重置为待审核
        state: 6
      })
    }
  } catch (err) {
    console.error('更换封面失败:', err)
  } finally {
    coverUploading.value = false
    isCoverChanging.value = false
    selectedCoverFile.value = null
    // 清空 input 的值，避免选择同一文件不触发 change
    if (coverInputRef.value) coverInputRef.value.value = ''
  }
}

function cancelCoverChange() {
  if (coverUploading.value) return
  isCoverChanging.value = false
  selectedCoverFile.value = null
  if (pendingCoverPreviewUrl.value) {
    URL.revokeObjectURL(pendingCoverPreviewUrl.value)
    pendingCoverPreviewUrl.value = ''
  }
  if (coverInputRef.value) coverInputRef.value.value = ''
}

function close() {
  isEditing.value = false
  isZonesEditing.value = false
  showAddTagMenu.value = false
  isCoverChanging.value = false
  selectedCoverFile.value = null
  coverUploading.value = false
  if (pendingCoverPreviewUrl.value) {
    URL.revokeObjectURL(pendingCoverPreviewUrl.value)
    pendingCoverPreviewUrl.value = ''
  }
  emit('update:visible', false)
  emit('close')
}

watch(
  () => props.visible && props.mediaId,
  (show) => {
    if (show) {
      load()
      document.addEventListener('click', handleClickOutside)
    } else {
      detail.value = null
      coverUrl.value = ''
      if (pendingCoverPreviewUrl.value) {
        URL.revokeObjectURL(pendingCoverPreviewUrl.value)
      }
      pendingCoverPreviewUrl.value = ''
      error.value = false
      isEditing.value = false
      isZonesEditing.value = false
      showAddTagMenu.value = false
      isCoverChanging.value = false
      selectedCoverFile.value = null
      coverUploading.value = false
      visibleZoneIds.value = []
      editZoneIds.value = []
      document.removeEventListener('click', handleClickOutside)
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style scoped>
.media-detail-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.6);
  padding: 1rem;
}

.media-detail-modal-box {
  position: relative;
  width: 100%;
  max-width: 600px;
  max-height: 90vh;
  overflow: auto;
  background: var(--card-bg, #fff);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}

.media-detail-modal-close {
  position: absolute;
  top: 0.75rem;
  right: 0.75rem;
  z-index: 2;
  width: 36px;
  height: 36px;
  border: none;
  background: #dc2626;
  color: #fff;
  font-size: 1.5rem;
  line-height: 1;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.media-detail-modal-close:hover {
  background: #b91c1c;
}

.media-detail-modal-loading,
.media-detail-modal-error {
  padding: 3rem 2rem;
  text-align: center;
}

.media-detail-modal-error p {
  margin: 0;
  color: #e74c3c;
}

/* 封面图区域 */
.media-detail-modal-cover-wrapper {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #f5f5f5;
  overflow: hidden;
}

.media-detail-modal-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.media-detail-modal-cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(0, 0, 0, 0.4);
  font-size: 0.95rem;
}

.media-detail-modal-cover-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 1rem;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.6), transparent);
  opacity: 0;
  transition: opacity 0.3s ease;
  display: flex;
  justify-content: flex-end;
  align-items: flex-end;
}

/* 视频时默认显示，不需要悬浮 */
.media-detail-modal-cover-overlay.always-visible {
  opacity: 1;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.4), transparent);
}

/* 图片时保持悬浮显示 */
.media-detail-modal-cover-wrapper:hover .media-detail-modal-cover-overlay:not(.always-visible) {
  opacity: 1;
}

.change-cover-btn,
.change-cover-confirm-btn,
.change-cover-cancel-btn {
  padding: 0.38rem 0.7rem;
  border-radius: 9px;
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s, transform 0.05s;
  white-space: nowrap;
  border: 1px solid transparent;
}

/* 次按钮：更换封面 / 取消 */
.change-cover-btn {
  background: rgba(255, 255, 255, 0.92);
  border-color: rgba(255, 255, 255, 0.92);
  color: #1f2937;
}

.change-cover-btn:hover {
  background: #fff;
  border-color: #fff;
}

/* 取消：红色 */
.change-cover-cancel-btn {
  background: rgba(220, 38, 38, 0.92);
  border-color: rgba(220, 38, 38, 0.92);
  color: #fff;
}

.change-cover-cancel-btn:hover {
  background: rgba(220, 38, 38, 1);
  border-color: rgba(220, 38, 38, 1);
}

/* 主按钮：确认更换 */
.change-cover-confirm-btn {
  background: rgba(37, 99, 235, 0.95); /* 蓝色主按钮 */
  border-color: rgba(37, 99, 235, 0.95);
  color: #fff;
}

.change-cover-confirm-btn:hover {
  background: rgba(37, 99, 235, 1);
  border-color: rgba(37, 99, 235, 1);
}

.change-cover-btn:active,
.change-cover-confirm-btn:active,
.change-cover-cancel-btn:active {
  transform: translateY(1px);
}

.change-cover-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  justify-content: flex-end;
}

.change-cover-input {
  display: none;
}

.change-cover-confirm-btn:disabled,
.change-cover-cancel-btn:disabled,
.change-cover-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

/* 分割线 */
.media-detail-modal-divider {
  height: 1px;
  background: rgba(0, 0, 0, 0.1);
  margin: 0;
}

.media-detail-modal-divider-strong {
  height: 2px;
  background: rgba(0, 0, 0, 0.16);
}

/* 字段区域 */
.media-detail-modal-field {
  padding: 1.25rem 1.5rem;
}

.media-detail-modal-field-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.media-detail-modal-field-label {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-primary, #2c3e50);
  margin-bottom: 0.5rem;
  display: block;
}

.media-detail-modal-text {
  font-size: 0.95rem;
  color: var(--text-secondary, rgba(44, 62, 80, 0.8));
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  min-height: 1.5em;
}

.media-detail-modal-input {
  width: 100%;
  padding: 0.5rem 0.75rem;
  border: 1.5px solid rgba(31, 41, 55, 0.28);
  border-radius: 6px;
  font-size: 0.95rem;
  color: var(--text-primary, #2c3e50);
  background: rgba(255, 255, 255, 0.95);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.media-detail-modal-input:focus {
  outline: none;
  border-color: var(--accent-color, #4a90e2);
  box-shadow: 0 0 0 3px rgba(74, 144, 226, 0.22);
}

.media-detail-modal-textarea {
  width: 100%;
  padding: 0.5rem 0.75rem;
  border: 1.5px solid rgba(31, 41, 55, 0.28);
  border-radius: 6px;
  font-size: 0.95rem;
  color: var(--text-primary, #2c3e50);
  font-family: inherit;
  resize: vertical;
  background: rgba(255, 255, 255, 0.95);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.media-detail-modal-textarea:focus {
  outline: none;
  border-color: var(--accent-color, #4a90e2);
  box-shadow: 0 0 0 3px rgba(74, 144, 226, 0.22);
}

.field-actions {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.field-confirm-btn,
.field-cancel-btn {
  padding: 0.35rem 0.7rem;
  border-radius: 9px;
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s, transform 0.05s;
  white-space: nowrap;
  border: 1px solid transparent;
}

/* 确认：与“确认更换封面”同色系 */
.field-confirm-btn {
  background: rgba(37, 99, 235, 0.95);
  border-color: rgba(37, 99, 235, 0.95);
  color: #fff;
}

.field-confirm-btn:hover {
  background: rgba(37, 99, 235, 1);
  border-color: rgba(37, 99, 235, 1);
}

/* 取消：红色 */
.field-cancel-btn {
  background: rgba(220, 38, 38, 0.92);
  border-color: rgba(220, 38, 38, 0.92);
  color: #fff;
}

.field-cancel-btn:hover {
  background: rgba(220, 38, 38, 1);
  border-color: rgba(220, 38, 38, 1);
}

.field-confirm-btn:active,
.field-cancel-btn:active {
  transform: translateY(1px);
}

.field-confirm-btn:disabled,
.field-cancel-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.edit-btn {
  padding: 0.35rem 0.75rem;
  background: var(--accent-color, #4a90e2);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 0.85rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}

.edit-btn:hover {
  background: #3a7bc8;
}

/* 可见专区标签区域 */
.media-detail-modal-zones-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.zone-tag {
  display: inline-flex;
  align-items: center;
  padding: 0.35rem 0.75rem;
  background: rgba(74, 144, 226, 0.15);
  color: var(--accent-color, #4a90e2);
  border-radius: 16px;
  font-size: 0.85rem;
  font-weight: 500;
  position: relative;
}

.zone-tag-editable {
  padding-right: 1.75rem;
}

.zone-tag-remove {
  position: absolute;
  right: 0.25rem;
  top: 50%;
  transform: translateY(-50%);
  width: 18px;
  height: 18px;
  border: none;
  background: rgba(0, 0, 0, 0.1);
  color: var(--accent-color, #4a90e2);
  border-radius: 50%;
  font-size: 1rem;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.zone-tag-remove:hover {
  background: rgba(0, 0, 0, 0.2);
}

/* 添加标签按钮 */
.zone-tag-add-wrapper {
  position: relative;
  display: inline-block;
}

.zone-tag-add-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px dashed rgba(74, 144, 226, 0.5);
  background: rgba(74, 144, 226, 0.05);
  color: var(--accent-color, #4a90e2);
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.2s;
}

.zone-tag-add-btn:hover {
  background: rgba(74, 144, 226, 0.15);
  border-color: var(--accent-color, #4a90e2);
}

.add-icon {
  width: 16px;
  height: 16px;
}

/* 标签选择菜单 */
.zone-tag-menu {
  position: absolute;
  bottom: calc(100% + 0.5rem);
  left: 0;
  min-width: 200px;
  max-height: 300px;
  overflow-y: auto;
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 1001;
  padding: 0.5rem 0;
}

.zone-tag-menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 0.75rem;
  cursor: pointer;
  transition: background 0.2s;
}

.zone-tag-menu-item:hover {
  background: rgba(74, 144, 226, 0.08);
}

.zone-tag-menu-name {
  flex: 1;
  font-size: 0.9rem;
  color: var(--text-primary, #2c3e50);
}

.zone-tag-menu-add {
  width: 24px;
  height: 24px;
  border: none;
  background: rgba(74, 144, 226, 0.15);
  color: var(--accent-color, #4a90e2);
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
  flex-shrink: 0;
}

.zone-tag-menu-add:hover {
  background: rgba(74, 144, 226, 0.25);
}

.add-icon-small {
  width: 14px;
  height: 14px;
}

.zone-tag-menu-empty {
  padding: 1rem;
  text-align: center;
  color: var(--text-secondary, rgba(44, 62, 80, 0.5));
  font-size: 0.85rem;
}

/* 下拉菜单动画 */
.dropdown-enter-active,
.dropdown-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

.zone-tag-empty {
  display: inline-flex;
  align-items: center;
  padding: 0.35rem 0.75rem;
  color: var(--text-secondary, rgba(44, 62, 80, 0.5));
  font-size: 0.85rem;
  font-style: italic;
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.25s ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}

.modal-fade-enter-active .media-detail-modal-box,
.modal-fade-leave-active .media-detail-modal-box {
  transition: transform 0.25s ease;
}

.modal-fade-enter-from .media-detail-modal-box,
.modal-fade-leave-to .media-detail-modal-box {
  transform: scale(0.95);
}
</style>
