<template>
  <div class="media-browse-container">
    <div class="media-browse-background"></div>

    <div class="media-browse-content">
      <!-- 导航栏 -->
      <NavBar />
      <MediaDetailModal
        v-model:visible="showDetailModal"
        :media-id="detailMediaId"
        @cover-updated="handleCoverUpdated"
        @base-updated="handleBaseUpdated"
        @close="detailMediaId = null"
      />

      <!-- 批量删除确认弹窗 -->
      <Teleport to="body">
        <Transition name="modal-fade">
          <div
            v-if="showBulkDeleteConfirm"
            class="bulk-delete-modal-overlay"
            @click.self="handleBulkDeleteCancel"
          >
            <div class="bulk-delete-modal-box">
              <div class="bulk-delete-modal-title">
                确定要删除这{{ selectedIds.length }}个内容吗？
              </div>
              <div class="bulk-delete-modal-subtitle">
                删除后无法恢复，请谨慎选择
              </div>
              <div class="bulk-delete-modal-actions">
                <button
                  type="button"
                  class="bulk-delete-confirm-btn"
                  :disabled="bulkDeleting"
                  @click="handleBulkDeleteConfirm"
                >
                  {{ bulkDeleting ? '删除中...' : '确认删除' }}
                </button>
                <button
                  type="button"
                  class="bulk-delete-cancel-btn"
                  :disabled="bulkDeleting"
                  @click="handleBulkDeleteCancel"
                >
                  取消
                </button>
              </div>
            </div>
          </div>
        </Transition>
      </Teleport>

      <div class="myuploads-layout">
        <!-- 左侧控制台 -->
        <aside class="myuploads-sidenav" aria-label="我的上传控制台">
          <button
            type="button"
            class="myuploads-sidenav-item"
            :class="{ active: activePanel === 'list' }"
            @click="switchPanel('list')"
          >
            上传列表
          </button>
          <button
            type="button"
            class="myuploads-sidenav-item"
            :class="{ active: activePanel === 'upload' }"
            @click="switchPanel('upload')"
          >
            上传新内容
          </button>
        </aside>

        <!-- 右侧内容 -->
        <main class="myuploads-main">
          <div v-show="activePanel === 'upload'" class="myuploads-panel-upload">
            <UploadMedia :embedded="true" @upload-success="handleUploadSuccess" />
          </div>

          <div v-show="activePanel !== 'upload'" class="my-uploads-container">
              <div class="my-uploads-header">
                <div class="header-actions-row">
                  <div class="category-selector-inline">
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
                  <div class="header-right-actions">
                    <button
                      class="bulk-delete-btn"
                      @click="handleBulkDelete"
                    >
                      <svg class="bulk-delete-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M3 6h18" stroke-linecap="round" stroke-linejoin="round"/>
                        <path d="M8 6V4h8v2" stroke-linecap="round" stroke-linejoin="round"/>
                        <path d="M6 6l1 16h10l1-16" stroke-linecap="round" stroke-linejoin="round"/>
                        <path d="M10 11v6" stroke-linecap="round" stroke-linejoin="round"/>
                        <path d="M14 11v6" stroke-linecap="round" stroke-linejoin="round"/>
                      </svg>
                      {{ isBulkMode ? '确认删除' : '批量删除' }}
                    </button>
                    <button
                      v-if="isBulkMode"
                      type="button"
                      class="bulk-cancel-btn"
                      :disabled="bulkDeleting"
                      @click="exitBulkMode"
                    >
                      取消
                    </button>
                  </div>
                </div>
              </div>

              <div v-if="loading && mediaList.length === 0" class="loading">
                <div class="loading-spinner"></div>
              </div>

              <div v-if="errorMsg" class="error-message">
                <p>{{ errorMsg }}</p>
              </div>

              <div v-else-if="!loading && mediaList.length === 0" class="empty-state">
                <p>暂无上传内容</p>
              </div>

              <div v-else class="my-uploads-table-wrapper">
                <table class="my-uploads-table">
                  <thead>
                    <tr>
                      <th v-if="isBulkMode" class="col-select">
                        <input
                          class="select-checkbox"
                          type="checkbox"
                          :checked="allSelected"
                          :indeterminate.prop="someSelected && !allSelected"
                          @change="toggleSelectAll"
                          aria-label="全选"
                        />
                      </th>
                      <th class="col-thumbnail"> </th>
                      <th class="col-title">标题</th>
                      <th class="col-category">类型</th>
                      <th class="col-state">状态</th>
                      <th class="col-actions">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="item in mediaList" :key="item.id" class="table-row">
                      <td v-if="isBulkMode" class="col-select">
                        <input
                          class="select-checkbox"
                          type="checkbox"
                          :checked="selectedIds.includes(item.id)"
                          @change="toggleSelectOne(item.id)"
                          aria-label="选择"
                        />
                      </td>
                      <td class="col-thumbnail">
                        <div class="thumbnail-cell">
                          <img
                            v-if="item.coverUrl"
                            :src="item.coverUrl"
                            :alt="item.title || '无标题'"
                            class="thumbnail-image"
                            @error="handleCoverError(item)"
                          />
                          <div v-else class="thumbnail-placeholder">暂无封面</div>
                        </div>
                      </td>
                      <td class="col-title">
                        <span class="title-text">{{ item.title || '无标题' }}</span>
                      </td>
                      <td class="col-category">
                        <span class="category-badge" :class="getCategoryClass(item.category)">
                          {{ getCategoryLabel(item.category) }}
                        </span>
                      </td>
                      <td class="col-state">
                        <span class="state-badge" :class="getStateClass(item.state)">
                          {{ getStateLabel(item.state) }}
                        </span>
                      </td>
                      <td class="col-actions">
                        <button class="action-btn" @click="openDetail(item.id)">查看详情</button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <div v-if="!loading && mediaList.length > 0 && hasMore" class="pagination">
                <button class="load-more-btn" @click="loadMore" :disabled="loading">
                  {{ loading ? '加载中...' : '加载更多' }}
                </button>
              </div>

            </div>
        </main>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from '@/components/NavBar.vue'
import MediaDetailModal from '@/components/MediaDetailModal.vue'
import UploadMedia from '@/views/UploadMedia.vue'
import { useUserStore } from '@/stores/user'
import { getMyUploads, deleteMedia } from '@/api/media'

const router = useRouter()
const userStore = useUserStore()
const showDetailModal = ref(false)
const detailMediaId = ref(null)

const activePanel = ref('list') // 'list' | 'upload'

const currentCategory = ref(null)
const mediaList = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const hasMore = ref(true)
const errorMsg = ref('')

// 批量删除模式（仅UI：勾选）
const isBulkMode = ref(false)
const selectedIds = ref([])
const showBulkDeleteConfirm = ref(false)
const bulkDeleting = ref(false)

const allSelected = computed(() => {
  if (!isBulkMode.value) return false
  if (!mediaList.value || mediaList.value.length === 0) return false
  return mediaList.value.every(item => selectedIds.value.includes(item.id))
})

const someSelected = computed(() => {
  if (!isBulkMode.value) return false
  return selectedIds.value.length > 0
})

watch(mediaList, (list) => {
  if (!isBulkMode.value) return
  const idSet = new Set((list || []).map(i => i.id))
  selectedIds.value = selectedIds.value.filter(id => idSet.has(id))
})

// 导航栏相关函数已移至 NavBar 组件

onMounted(() => {
  loadMyUploads(true)
  // 导航栏相关事件监听已移至 NavBar 组件
})

async function loadMyUploads(reset = false) {
  if (loading.value || (!hasMore.value && !reset)) return
  loading.value = true
  try {
    if (reset) {
      page.value = 1
      mediaList.value = []
      hasMore.value = true
    }
    const response = await getMyUploads(page.value, size.value, currentCategory.value)
    if (response && response.code !== 200) {
      errorMsg.value = response.message || '加载失败，请重试'
      return
    }
    if (response?.data) {
      errorMsg.value = ''
      const newList = response.data.list || []
      if (reset) {
        mediaList.value = newList
      } else {
        mediaList.value.push(...newList)
      }
      const total = response.data.total || 0
      hasMore.value = mediaList.value.length < total
      if (hasMore.value) page.value += 1
    }
  } catch (error) {
    errorMsg.value = error?.response?.data?.message || error?.message || '加载失败，请重试'
    console.error('加载我的上传列表失败:', error)
  } finally {
    loading.value = false
  }
}

function switchCategory(category) {
  if (currentCategory.value === category) return
  currentCategory.value = category
  loadMyUploads(true)
}

function loadMore() {
  if (hasMore.value && !loading.value) loadMyUploads(false)
}

function handleCoverError(item) {
  // 图片加载失败则回退为占位符
  if (item) item.coverUrl = ''
}

function getCategoryLabel(category) {
  return category === 0 ? '图片' : category === 1 ? '视频' : '未知'
}

function getCategoryClass(category) {
  return category === 0 ? 'category-image' : 'category-video'
}

function getStateLabel(state) {
  if (state === 0) return '正常'
  if (state === 1) return '上传中'
  if (state === 2) return '上传成功'
  if (state === 3) return '上传失败'
  if (state === 4) return '正在删除'
  if (state === 5) return '已删除'
  if (state === 6) return '待审核'
  if (state === 7) return '审核未通过'
  return '未知'
}

function getStateClass(state) {
  if (state === 0) return 'state-normal'
  if (state === 1) return 'state-uploading'
  if (state === 2) return 'state-upload-success'
  if (state === 3) return 'state-upload-failed'
  if (state === 4) return 'state-deleting'
  if (state === 5) return 'state-deleted'
  if (state === 6) return 'state-pending'
  if (state === 7) return 'state-rejected'
  return ''
}

function openDetail(mediaId) {
  detailMediaId.value = mediaId
  showDetailModal.value = true
}

function toggleSelectAll(event) {
  const checked = !!event?.target?.checked
  if (!checked) {
    selectedIds.value = []
    return
  }
  selectedIds.value = (mediaList.value || []).map(i => i.id)
}

function toggleSelectOne(mediaId) {
  const idx = selectedIds.value.indexOf(mediaId)
  if (idx >= 0) {
    selectedIds.value.splice(idx, 1)
  } else {
    selectedIds.value.push(mediaId)
  }
}

function handleCoverUpdated(payload) {
  const mediaId = payload?.mediaId
  if (!mediaId) return
  const item = mediaList.value.find(m => m.id === mediaId)
  if (!item) return
  if (payload.coverUrl !== undefined) {
    item.coverUrl = payload.coverUrl || ''
  }
  if (payload.coverPath !== undefined) {
    item.coverPath = payload.coverPath || item.coverPath
  }
  if (payload.state !== undefined) {
    item.state = payload.state
  } else {
    // 兜底：后端规则是更新封面后回到待审核
    item.state = 6
  }
}

function handleBaseUpdated(payload) {
  const mediaId = payload?.mediaId
  if (!mediaId) return
  const item = mediaList.value.find(m => m.id === mediaId)
  if (!item) return
  if (payload.title !== undefined) item.title = payload.title
  if (payload.description !== undefined) item.description = payload.description
  if (payload.state !== undefined) item.state = payload.state
  else item.state = 6
}

function handleUploadNew() {
  switchPanel('upload')
}

async function handleUploadSuccess() {
  switchPanel('list', true)
  // 上传成功后拉取一次列表并等待完成，确保界面显示最新状态
  await loadMyUploads(true)
}

function switchPanel(panel, skipLoadList = false) {
  if (activePanel.value === panel) return

  // 切换面板时，清理批量删除状态与弹窗，避免“残留 UI”
  showBulkDeleteConfirm.value = false
  isBulkMode.value = false
  selectedIds.value = []
  showDetailModal.value = false
  detailMediaId.value = null

  activePanel.value = panel

  // 切回上传列表时刷新列表，使刚上传的内容立即显示（由调用方 skipLoadList 时可自行 await loadMyUploads）
  if (panel === 'list' && !skipLoadList) {
    loadMyUploads(true)
  }
}

function handleBulkDelete() {
  // 进入批量模式
  if (!isBulkMode.value) {
    isBulkMode.value = true
    selectedIds.value = []
    return
  }

  // 批量模式下：有选择则弹窗确认；没有选择则退出批量模式
  if (selectedIds.value.length === 0) {
    // 没有选择时不弹窗（保持在批量模式，等待用户勾选）
    return
  }

  showBulkDeleteConfirm.value = true
}

function exitBulkMode() {
  if (bulkDeleting.value) return
  showBulkDeleteConfirm.value = false
  isBulkMode.value = false
  selectedIds.value = []
}

function handleBulkDeleteCancel() {
  if (bulkDeleting.value) return
  showBulkDeleteConfirm.value = false
  // 取消：清空复选框，但保留“批量模式”（仍显示复选框）
  selectedIds.value = []
}

async function handleBulkDeleteConfirm() {
  if (bulkDeleting.value) return
  const ids = [...selectedIds.value]
  if (ids.length === 0) return

  bulkDeleting.value = true
  try {
    const results = await Promise.allSettled(ids.map(id => deleteMedia(id)))
    const successIds = []
    for (let i = 0; i < results.length; i++) {
      if (results[i].status === 'fulfilled') successIds.push(ids[i])
    }

    // 先本地移除成功删除的项
    if (successIds.length > 0) {
      const successSet = new Set(successIds)
      mediaList.value = mediaList.value.filter(item => !successSet.has(item.id))
    }

    // 关闭弹窗，清空选择，并退出批量模式（回到正常列表）
    showBulkDeleteConfirm.value = false
    selectedIds.value = []
    isBulkMode.value = false

    // 为避免 total/page 不一致，轻量刷新一次列表（保持当前筛选条件）
    await loadMyUploads(true)
  } catch (err) {
    console.error('批量删除失败:', err)
  } finally {
    bulkDeleting.value = false
  }
}
</script>

<style scoped>
/* 样式在 media-browse.css 中定义 */

.myuploads-layout {
  display: flex;
  gap: 16px;
  align-items: stretch;
}

.myuploads-sidenav {
  width: 200px;
  flex: 0 0 200px;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(12px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3), 0 0 0 1px rgba(255, 255, 255, 0.06) inset;
  height: fit-content;
  position: sticky;
  top: 88px;
}

.myuploads-sidenav-item {
  width: 100%;
  display: block;
  text-align: left;
  padding: 10px 10px;
  border-radius: 10px;
  border: 1px solid transparent;
  background: transparent;
  color: #fff;
  font-size: 1.1rem;
  font-weight: 600;
  letter-spacing: 0.12em;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.myuploads-sidenav-item + .myuploads-sidenav-item {
  margin-top: 8px;
}

.myuploads-sidenav-item:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}

.myuploads-sidenav-item.active {
  background: rgba(74, 144, 226, 0.35);
  border-color: rgba(74, 144, 226, 0.5);
  color: #fff;
  box-shadow: 0 2px 8px rgba(74, 144, 226, 0.25);
}

.myuploads-main {
  flex: 1 1 auto;
  min-width: 0;
}
</style>
