<template>
  <div class="media-browse-container">
    <div class="media-browse-background"></div>
    <div class="media-browse-content">
      <!-- 导航栏 -->
      <NavBar>
        <template #left>
          <router-link to="/browse" class="nav-back-btn">
            <svg class="back-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M19 12H5M12 19l-7-7 7-7" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            <span>返回浏览</span>
          </router-link>
        </template>
      </NavBar>
      <MediaDetailModal v-model:visible="showDetailModal" :media-id="detailMediaId" @close="detailMediaId = null" />
      <MediaDetail
        v-if="showDetail"
        :media-id="selectedMediaId"
        :media-list="detailMediaList"
        @close="handleCloseDetail"
        @switch-media="handleSwitchMedia"
      />

      <!-- 删除确认弹窗 -->
      <Teleport to="body">
        <Transition name="modal-fade">
          <div
            v-if="showDeleteConfirm"
            class="bulk-delete-modal-overlay"
            @click.self="handleDeleteCancel"
          >
            <div class="bulk-delete-modal-box">
              <div class="bulk-delete-modal-title">
                确认删除该资源？
              </div>
              <div class="bulk-delete-modal-actions">
                <button
                  type="button"
                  class="bulk-delete-confirm-btn"
                  :disabled="processingIds.has(deleteMediaId)"
                  @click="handleDeleteConfirm"
                >
                  {{ processingIds.has(deleteMediaId) ? '处理中...' : '确认删除' }}
                </button>
                <button
                  type="button"
                  class="bulk-delete-cancel-btn"
                  :disabled="processingIds.has(deleteMediaId)"
                  @click="handleDeleteCancel"
                >
                  取消
                </button>
              </div>
            </div>
          </div>
        </Transition>
      </Teleport>

      <!-- 修改用户等级确认弹窗 -->
      <Teleport to="body">
        <Transition name="modal-fade">
          <div
            v-if="showChangeLevelConfirm"
            class="bulk-delete-modal-overlay"
            @click.self="handleChangeLevelCancel"
          >
            <div class="bulk-delete-modal-box">
              <div class="bulk-delete-modal-title">
                确定要把{{ changeLevelUserNickName }}设置为{{ changeLevelTargetLabel }}？
              </div>
              <div class="bulk-delete-modal-actions">
                <button
                  type="button"
                  class="bulk-delete-confirm-btn change-level-confirm-btn"
                  :disabled="userProcessingIds.has(changeLevelUserId)"
                  @click="handleChangeLevelConfirm"
                >
                  {{ userProcessingIds.has(changeLevelUserId) ? '处理中...' : '确认' }}
                </button>
                <button
                  type="button"
                  class="bulk-delete-cancel-btn"
                  :disabled="userProcessingIds.has(changeLevelUserId)"
                  @click="handleChangeLevelCancel"
                >
                  取消
                </button>
              </div>
            </div>
          </div>
        </Transition>
      </Teleport>

      <!-- 拉黑用户确认弹窗 -->
      <Teleport to="body">
        <Transition name="modal-fade">
          <div
            v-if="showBlacklistConfirm"
            class="bulk-delete-modal-overlay"
            @click.self="handleBlacklistCancel"
          >
            <div class="bulk-delete-modal-box">
              <div class="bulk-delete-modal-title">
                确定拉黑{{ blacklistUserNickName }}？
              </div>
              <div class="bulk-delete-modal-actions">
                <button
                  type="button"
                  class="bulk-delete-confirm-btn"
                  :disabled="userProcessingIds.has(blacklistUserId)"
                  @click="handleBlacklistConfirm"
                >
                  {{ userProcessingIds.has(blacklistUserId) ? '处理中...' : '确认拉黑' }}
                </button>
                <button
                  type="button"
                  class="bulk-delete-cancel-btn"
                  :disabled="userProcessingIds.has(blacklistUserId)"
                  @click="handleBlacklistCancel"
                >
                  取消
                </button>
              </div>
            </div>
          </div>
        </Transition>
      </Teleport>

      <div class="rm-layout">
        <!-- 左侧导航 -->
        <aside class="rm-sidenav" aria-label="资源管理导航">
          <button type="button" class="rm-sidenav-item" :class="{ active: activeModule === 'audit' }" @click="switchModule('audit')">
            资源审核
          </button>
          <button type="button" class="rm-sidenav-item" :class="{ active: activeModule === 'manage' }" @click="switchModule('manage')">
            资源管理
          </button>
          <button
            v-if="userStore.userInfo?.level === 0"
            type="button"
            class="rm-sidenav-item"
            :class="{ active: activeModule === 'users' }"
            @click="switchModule('users')"
          >
            用户管理
          </button>
        </aside>

        <!-- 主内容 -->
        <main class="rm-main">
          <div v-if="activeModule !== 'audit'" class="my-uploads-header" style="margin-bottom: 12px;">
            <h1 v-if="activeModule === 'users'" class="page-title" style="font-size: 1.8rem;">
              用户管理
            </h1>

            <div v-if="activeModule === 'manage'" class="header-actions-row">
              <div class="category-selector-inline">
                <button class="category-option" :class="{ active: activeCategory === null }" @click="switchCategory(null)">全部</button>
                <span class="category-separator">|</span>
                <button class="category-option" :class="{ active: activeCategory === 0 }" @click="switchCategory(0)">图片</button>
                <span class="category-separator">|</span>
                <button class="category-option" :class="{ active: activeCategory === 1 }" @click="switchCategory(1)">视频</button>
              </div>
            </div>
          </div>

          <!-- 用户管理 -->
          <section v-if="activeModule === 'users'" class="rm-card-lite">
            <div v-if="userListLoading && userList.length === 0" class="loading">
              <div class="loading-spinner"></div>
            </div>

            <div v-if="userListErrorMsg" class="error-message">
              <p>{{ userListErrorMsg }}</p>
            </div>

            <div v-else-if="!userListLoading && userList.length === 0" class="empty-state">
              <p>暂无用户数据</p>
            </div>

            <div v-else class="my-uploads-table-wrapper">
              <table class="my-uploads-table">
                <thead>
                  <tr>
                    <th class="col-id">ID</th>
                    <th class="col-title">昵称</th>
                    <th class="col-category col-level">等级</th>
                    <th class="col-state">状态</th>
                    <th class="col-actions">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in userList" :key="item.id" class="table-row">
                    <td class="col-id">{{ item.id }}</td>
                    <td class="col-title">
                      <span class="title-text">{{ item.nickName || '无昵称' }}</span>
                    </td>
                    <td class="col-category col-level">
                      <span class="category-badge" :class="getLevelClass(item.level)">
                        {{ getLevelLabel(item.level) }}
                      </span>
                    </td>
                    <td class="col-state">
                      <span class="state-badge" :class="getStateClass(item.state)">
                        {{ getStateLabel(item.state) }}
                      </span>
                    </td>
                    <td class="col-actions col-actions-wide">
                      <button
                        class="action-btn action-btn-warning"
                        :disabled="userProcessingIds.has(item.id)"
                        @click="handleChangeUserLevel(item)"
                      >
                        {{ userProcessingIds.has(item.id) ? '处理中...' : (item.level === 2 ? '设为管理员' : '设为用户') }}
                      </button>
                      <button
                        class="action-btn action-btn-blacklist"
                        style="margin-left: 8px;"
                        :disabled="userProcessingIds.has(item.id)"
                        @click="handleBlacklistUser(item)"
                      >
                        {{ userProcessingIds.has(item.id) ? '处理中...' : '拉黑' }}
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div v-if="!userListLoading && userList.length > 0 && userListHasMore" class="pagination">
              <button class="load-more-btn" @click="loadUserList(false)" :disabled="userListLoading">
                {{ userListLoading ? '加载中...' : '加载更多' }}
              </button>
              <div class="pagination-hint" v-if="userListTotal > 0">
                已加载 {{ userList.length }} / {{ userListTotal }}
              </div>
            </div>
          </section>

          <!-- 审核列表 -->
          <section v-else-if="activeModule === 'audit'" class="rm-card-lite">
            <!-- 筛选：全部 / 视频 / 图片 -->
            <div class="header-actions-row" style="margin: 0 0 12px; justify-content: space-between;">
              <div class="category-selector-inline">
                <button
                  class="category-option"
                  :class="{ active: auditCategoryFilter === null }"
                  :disabled="bulkProcessing"
                  @click="setAuditCategoryFilter(null)"
                >
                  全部
                </button>
                <span class="category-separator">|</span>
                <button
                  class="category-option"
                  :class="{ active: auditCategoryFilter === 1 }"
                  :disabled="bulkProcessing"
                  @click="setAuditCategoryFilter(1)"
                >
                  视频
                </button>
                <span class="category-separator">|</span>
                <button
                  class="category-option"
                  :class="{ active: auditCategoryFilter === 0 }"
                  :disabled="bulkProcessing"
                  @click="setAuditCategoryFilter(0)"
                >
                  图片
                </button>
              </div>

              <div class="rm-audit-toolbar">
                <template v-if="!isBulkMode">
                  <button type="button" class="rm-audit-toolbar-btn" @click="enterBulkMode">
                    批量处理
                  </button>
                </template>
                <template v-else>
                  <button
                    type="button"
                    class="rm-audit-toolbar-btn rm-audit-toolbar-btn-ok"
                    :disabled="bulkProcessing || selectedIds.length === 0"
                    @click="handleBulkApprove"
                  >
                    {{ bulkProcessing && bulkAction === 'approve' ? '通过中...' : '通过' }}
                  </button>
                  <button
                    type="button"
                    class="rm-audit-toolbar-btn rm-audit-toolbar-btn-bad"
                    :disabled="bulkProcessing || selectedIds.length === 0"
                    @click="handleBulkReject"
                  >
                    {{ bulkProcessing && bulkAction === 'reject' ? '驳回中...' : '驳回' }}
                  </button>
                  <button
                    type="button"
                    class="rm-audit-toolbar-btn rm-audit-toolbar-btn-ghost"
                    :disabled="bulkProcessing"
                    @click="exitBulkMode"
                  >
                    取消
                  </button>
                </template>
              </div>
            </div>

            <div v-if="pendingErrorMsg" class="error-message">
              <p>{{ pendingErrorMsg }}</p>
            </div>

            <div v-else-if="!pendingLoading && pendingList.length === 0" class="empty-state">
              <p>暂无待审核内容</p>
            </div>

            <div v-else-if="!pendingLoading && pendingList.length === 0" class="empty-state">
              <p>当前筛选下暂无待审核内容</p>
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
                        :disabled="bulkProcessing || pendingList.length === 0"
                        @change="toggleSelectAll"
                        aria-label="全选"
                      />
                    </th>
                    <th class="col-thumbnail"> </th>
                    <th class="col-title">标题</th>
                    <th class="col-category">类型</th>
                    <th class="col-state col-update-time">更新时间</th>
                    <th class="col-actions col-actions-wide">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in pendingList" :key="item.id" class="table-row">
                    <td v-if="isBulkMode" class="col-select">
                      <input
                        class="select-checkbox"
                        type="checkbox"
                        :checked="selectedIds.includes(item.id)"
                        :disabled="bulkProcessing"
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
                      <div v-if="item.description" style="opacity: 0.8; font-size: 12px; margin-top: 4px;">
                        {{ item.description }}
                      </div>
                    </td>
                    <td class="col-category">
                      <span class="category-badge" :class="getCategoryClass(item.category)">
                        {{ getCategoryLabel(item.category) }}
                      </span>
                    </td>
                    <td class="col-state col-update-time">
                      <span class="state-badge state-pending">{{ formatTime(item.updateTime) }}</span>
                    </td>
                    <td class="col-actions col-actions-wide">
                      <button class="action-btn" style="margin-right: 8px;" @click="openDetail(item.id)">查看详情</button>
                      <template v-if="!isBulkMode">
                        <button class="action-btn" :disabled="auditingIds.has(item.id)" @click="handleApprove(item.id)">
                          {{ auditingIds.has(item.id) ? '处理中...' : '通过' }}
                        </button>
                        <button class="action-btn" style="margin-left: 8px;" :disabled="auditingIds.has(item.id)" @click="handleReject(item.id)">
                          {{ auditingIds.has(item.id) ? '处理中...' : '驳回' }}
                        </button>
                      </template>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div v-if="!pendingLoading && pendingList.length > 0 && pendingHasMore" class="pagination">
              <button class="load-more-btn" @click="loadPending(false)" :disabled="pendingLoading">
                {{ pendingLoading ? '加载中...' : '加载更多' }}
              </button>
              <div class="pagination-hint" v-if="pendingTotal > 0">
                已加载 {{ pendingList.length }} / {{ pendingTotal }}
              </div>
            </div>
          </section>

          <!-- 已通过管理 -->
          <section v-else class="rm-card-lite">
            <div v-if="uploadedErrorMsg" class="error-message">
              <p>{{ uploadedErrorMsg }}</p>
            </div>

            <div v-else-if="!uploadedLoading && uploadedList.length === 0" class="empty-state">
              <p>暂无已上传内容</p>
            </div>

            <div v-else class="my-uploads-table-wrapper">
              <table class="my-uploads-table">
                <thead>
                  <tr>
                    <th class="col-thumbnail"> </th>
                    <th class="col-title">标题</th>
                    <th class="col-category">类型</th>
                    <th class="col-actions col-actions-wide">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in uploadedList" :key="item.id" class="table-row">
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
                    <td class="col-actions">
                      <button class="action-btn" @click="openDetail(item.id)">查看详情</button>
                      <button class="action-btn action-btn-danger" style="margin-left: 8px;" :disabled="processingIds.has(item.id)" @click="handleDelete(item.id)">
                        {{ processingIds.has(item.id) ? '处理中...' : '删除' }}
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div v-if="!uploadedLoading && uploadedList.length > 0 && uploadedHasMore" class="pagination">
              <button class="load-more-btn" @click="loadUploaded(false)" :disabled="uploadedLoading">
                {{ uploadedLoading ? '加载中...' : '加载更多' }}
              </button>
            </div>
          </section>
        </main>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from '@/components/NavBar.vue'
import MediaDetailModal from '@/components/MediaDetailModal.vue'
import MediaDetail from '@/views/MediaDetail.vue'
import { useUserStore } from '@/stores/user'
import { getAuditPendingList, auditApprove, auditReject, getMediaList, deleteMedia } from '@/api/media'
import { getUserList, updateUserLevel, updateUserState } from '@/api/user'

const router = useRouter()
const userStore = useUserStore()

const showDetailModal = ref(false)
const detailMediaId = ref(null)

// 浏览式详情（与 browse 页面一致）
const showDetail = ref(false)
const selectedMediaId = ref(null)
const detailMediaList = computed(() => {
  // 在审核列表里需要“完整播放视频”，使用 browse 同款详情视图
  if (activeModule.value === 'audit') return pendingList.value || []
  // 资源管理页也可以复用（目前保守：仅审核列表会打开该详情）
  return uploadedList.value || []
})

const activeModule = ref('audit') // 'audit' | 'manage' | 'users'

// 当前管理类型：null=全部，0=图片，1=视频
const activeCategory = ref(null)

// 待审核列表
const pendingList = ref([])
const pendingLoading = ref(false)
const pendingPage = ref(1)
const pendingSize = ref(20)
const pendingHasMore = ref(true)
const pendingErrorMsg = ref('')
const pendingTotal = ref(0)

// 审核列表筛选：null=全部，0=图片，1=视频（后端筛选）
const auditCategoryFilter = ref(null)

// 批量处理（仅审核列表）
const isBulkMode = ref(false)
const selectedIds = ref([])
const bulkProcessing = ref(false)
const bulkAction = ref('') // 'approve' | 'reject' | ''

const allSelected = computed(() => {
  if (!isBulkMode.value) return false
  if (!pendingList.value || pendingList.value.length === 0) return false
  return pendingList.value.every((x) => selectedIds.value.includes(x.id))
})

const someSelected = computed(() => {
  if (!isBulkMode.value) return false
  return selectedIds.value.length > 0
})

// 已上传（公共区，state=0）
const uploadedList = ref([])
const uploadedLoading = ref(false)
const uploadedPage = ref(1)
const uploadedSize = ref(20)
const uploadedHasMore = ref(true)
const uploadedErrorMsg = ref('')

// 审核中（禁用按钮）
const auditingIds = ref(new Set())
// 资源管理中正在处理的ID（删除）
const processingIds = ref(new Set())

// 删除确认弹窗
const showDeleteConfirm = ref(false)
const deleteMediaId = ref(null)

// 用户列表
const userList = ref([])
const userListLoading = ref(false)
const userListPage = ref(1)
const userListSize = ref(20)
const userListHasMore = ref(true)
const userListTotal = ref(0)
const userListErrorMsg = ref('')

// 用户操作处理状态
const userProcessingIds = ref(new Set())

// 修改用户等级确认弹窗
const showChangeLevelConfirm = ref(false)
const changeLevelUserId = ref(null)
const changeLevelUserNickName = ref('')
const changeLevelTargetLabel = ref('')
const changeLevelTargetLevel = ref(null)

// 拉黑用户确认弹窗
const showBlacklistConfirm = ref(false)
const blacklistUserId = ref(null)
const blacklistUserNickName = ref('')

// 导航栏相关函数已移至 NavBar 组件

function openDetail(mediaId) {
  if (activeModule.value === 'audit') {
    selectedMediaId.value = mediaId
    showDetail.value = true
    return
  }
  detailMediaId.value = mediaId
  showDetailModal.value = true
}

function handleCloseDetail() {
  showDetail.value = false
  selectedMediaId.value = null
}

function handleSwitchMedia(newMediaId) {
  selectedMediaId.value = newMediaId
}

function handleCoverError(item) {
  if (item) item.coverUrl = ''
}

function getCategoryLabel(category) {
  return category === 0 ? '图片' : category === 1 ? '视频' : '未知'
}

function getCategoryClass(category) {
  return category === 0 ? 'category-image' : 'category-video'
}

function getLevelLabel(level) {
  if (level === 0) return '作者'
  if (level === 1) return '管理员'
  if (level === 2) return '普通用户'
  if (level === 3) return '游客'
  return '未知'
}

function getLevelClass(level) {
  if (level === 0) return 'category-image'
  if (level === 1) return 'category-video'
  return ''
}

function getStateLabel(state) {
  if (state === 0) return '正常'
  if (state === 1) return '已注销'
  if (state === 2) return '黑名单'
  return '未知'
}

function getStateClass(state) {
  if (state === 0) return 'state-normal'
  if (state === 1) return 'state-rejected'
  if (state === 2) return 'state-blacklist'
  return ''
}

function setAuditCategoryFilter(value) {
  if (auditCategoryFilter.value === value) return
  auditCategoryFilter.value = value
  exitBulkMode()
  loadPending(true)
}

function enterBulkMode() {
  isBulkMode.value = true
  selectedIds.value = []
}

function exitBulkMode() {
  if (bulkProcessing.value) return
  isBulkMode.value = false
  selectedIds.value = []
}

function toggleSelectAll(event) {
  const checked = !!event?.target?.checked
  if (!checked) {
    selectedIds.value = []
    return
  }
  selectedIds.value = (pendingList.value || []).map((x) => x.id)
}

function toggleSelectOne(mediaId) {
  const idx = selectedIds.value.indexOf(mediaId)
  if (idx >= 0) selectedIds.value.splice(idx, 1)
  else selectedIds.value.push(mediaId)
}

async function handleBulkApprove() {
  if (bulkProcessing.value) return
  const ids = [...selectedIds.value]
  if (ids.length === 0) return
  bulkProcessing.value = true
  bulkAction.value = 'approve'
  try {
    const res = await auditApprove(ids)
    if (res && res.code !== 200) throw new Error(res.message || '批量通过失败')
    // 失败项不为空时，提示 message，但仍刷新列表
    if (res?.data?.failedItems?.length) {
      pendingErrorMsg.value = res.message || '部分批量通过失败'
    }
    await loadPending(true)
    if (uploadedList.value.length > 0) await loadUploaded(true)
    exitBulkMode()
  } catch (err) {
    pendingErrorMsg.value = err?.message || '批量通过失败'
  } finally {
    bulkProcessing.value = false
    bulkAction.value = ''
  }
}

async function handleBulkReject() {
  if (bulkProcessing.value) return
  const ids = [...selectedIds.value]
  if (ids.length === 0) return
  bulkProcessing.value = true
  bulkAction.value = 'reject'
  try {
    const res = await auditReject(ids)
    if (res && res.code !== 200) throw new Error(res.message || '批量驳回失败')
    if (res?.data?.failedItems?.length) {
      pendingErrorMsg.value = res.message || '部分批量驳回失败'
    }
    await loadPending(true)
    exitBulkMode()
  } catch (err) {
    pendingErrorMsg.value = err?.message || '批量驳回失败'
  } finally {
    bulkProcessing.value = false
    bulkAction.value = ''
  }
}

function formatTime(value) {
  if (!value) return '-'
  // 后端可能返回 ISO 字符串，直接展示即可（保持时区信息）
  return String(value).replace('T', ' ')
}

function switchModule(module) {
  if (activeModule.value === module) return
  activeModule.value = module
  if (module === 'audit' && pendingList.value.length === 0) loadPending(true)
  if (module === 'manage' && uploadedList.value.length === 0) loadUploaded(true)
  if (module === 'users' && userList.value.length === 0) loadUserList(true)
}

function switchCategory(category) {
  if (activeCategory.value === category) return
  activeCategory.value = category
  // 仅用于“资源管理”（已通过资源的管理列表）
  if (activeModule.value === 'manage') loadUploaded(true)
}

async function loadPending(reset = false) {
  if (pendingLoading.value || (!pendingHasMore.value && !reset)) return
  pendingLoading.value = true
  try {
    if (reset) {
      pendingPage.value = 1
      pendingList.value = []
      pendingHasMore.value = true
      pendingTotal.value = 0
    }
    const res = await getAuditPendingList(pendingPage.value, pendingSize.value, auditCategoryFilter.value)
    if (res && res.code !== 200) {
      pendingErrorMsg.value = res.message || '加载失败，请重试'
      return
    }
    if (!res || !res.data) {
      pendingErrorMsg.value = '待审核接口返回为空或结构不符合预期（缺少 data）'
      return
    }
    pendingErrorMsg.value = ''
    const list = res?.data?.list || []
    const total = Number(res?.data?.total || 0)
    pendingTotal.value = total
    if (reset) pendingList.value = list
    else pendingList.value.push(...list)
    pendingHasMore.value = pendingList.value.length < total
    if (pendingHasMore.value) pendingPage.value += 1
  } catch (err) {
    pendingErrorMsg.value = err?.response?.data?.message || err?.message || '加载失败，请重试'
  } finally {
    pendingLoading.value = false
  }
}

async function loadUploaded(reset = false) {
  if (uploadedLoading.value || (!uploadedHasMore.value && !reset)) return
  uploadedLoading.value = true
  try {
    if (reset) {
      uploadedPage.value = 1
      uploadedList.value = []
      uploadedHasMore.value = true
    }
    const res = await getMediaList(uploadedPage.value, uploadedSize.value, activeCategory.value, 0)
    if (res && res.code !== 200) {
      uploadedErrorMsg.value = res.message || '加载失败，请重试'
      return
    }
    uploadedErrorMsg.value = ''
    const list = res?.data?.list || []
    const total = res?.data?.total || 0
    if (reset) uploadedList.value = list
    else uploadedList.value.push(...list)
    uploadedHasMore.value = uploadedList.value.length < total
    if (uploadedHasMore.value) uploadedPage.value += 1
  } catch (err) {
    uploadedErrorMsg.value = err?.response?.data?.message || err?.message || '加载失败，请重试'
  } finally {
    uploadedLoading.value = false
  }
}

async function handleApprove(mediaId) {
  if (!mediaId) return
  const set = auditingIds.value
  if (set.has(mediaId)) return
  set.add(mediaId)
  auditingIds.value = new Set(set)
  try {
    const res = await auditApprove([mediaId])
    if (res && res.code !== 200) throw new Error(res.message || '审核失败')
    const failed = res?.data?.failedItems || []
    if (Array.isArray(failed) && failed.length > 0) {
      const hit = failed.find((x) => x?.mediaId === mediaId)
      if (hit) throw new Error(res?.message || '审核失败')
    }
    // 审核后重刷待审核列表：避免分页/total 因为审核变化导致“加载更多”错位
    await loadPending(true)
    // 若已上传 tab 曾经打开过，轻量刷新一次，保证能看到新内容
    if (uploadedList.value.length > 0) await loadUploaded(true)
  } catch (err) {
    pendingErrorMsg.value = err?.message || '审核失败，请重试'
  } finally {
    const next = new Set(auditingIds.value)
    next.delete(mediaId)
    auditingIds.value = next
  }
}

async function handleReject(mediaId) {
  if (!mediaId) return
  const set = auditingIds.value
  if (set.has(mediaId)) return
  set.add(mediaId)
  auditingIds.value = new Set(set)
  try {
    const res = await auditReject([mediaId])
    if (res && res.code !== 200) throw new Error(res.message || '审核失败')
    const failed = res?.data?.failedItems || []
    if (Array.isArray(failed) && failed.length > 0) {
      const hit = failed.find((x) => x?.mediaId === mediaId)
      if (hit) throw new Error(res?.message || '审核失败')
    }
    // 审核后重刷待审核列表：避免分页/total 因为审核变化导致“加载更多”错位
    await loadPending(true)
  } catch (err) {
    pendingErrorMsg.value = err?.message || '审核失败，请重试'
  } finally {
    const next = new Set(auditingIds.value)
    next.delete(mediaId)
    auditingIds.value = next
  }
}

function handleDelete(mediaId) {
  if (!mediaId || processingIds.value.has(mediaId)) return
  deleteMediaId.value = mediaId
  showDeleteConfirm.value = true
}

function handleDeleteCancel() {
  if (processingIds.value.has(deleteMediaId.value)) return
  showDeleteConfirm.value = false
  deleteMediaId.value = null
}

async function handleDeleteConfirm() {
  const mediaId = deleteMediaId.value
  if (!mediaId || processingIds.value.has(mediaId)) return
  const set = processingIds.value
  set.add(mediaId)
  processingIds.value = new Set(set)
  try {
    const res = await deleteMedia(mediaId)
    if (res && res.code !== 200) throw new Error(res.message || '删除失败')
    // 删除成功后关闭弹窗并刷新列表
    showDeleteConfirm.value = false
    deleteMediaId.value = null
    await loadUploaded(true)
  } catch (err) {
    uploadedErrorMsg.value = err?.message || '删除失败，请重试'
  } finally {
    const next = new Set(processingIds.value)
    next.delete(mediaId)
    processingIds.value = next
  }
}

async function loadUserList(reset = false) {
  if (userListLoading.value || (!userListHasMore.value && !reset)) return
  userListLoading.value = true
  try {
    if (reset) {
      userListPage.value = 1
      userList.value = []
      userListHasMore.value = true
      userListTotal.value = 0
    }
    const res = await getUserList(userListPage.value, userListSize.value)
    if (res && res.code !== 200) {
      userListErrorMsg.value = res.message || '加载失败，请重试'
      return
    }
    userListErrorMsg.value = ''
    const list = res?.data?.list || []
    const total = Number(res?.data?.total || 0)
    userListTotal.value = total
    if (reset) userList.value = list
    else userList.value.push(...list)
    userListHasMore.value = userList.value.length < total
    if (userListHasMore.value) userListPage.value += 1
  } catch (err) {
    userListErrorMsg.value = err?.response?.data?.message || err?.message || '加载失败，请重试'
  } finally {
    userListLoading.value = false
  }
}

function handleChangeUserLevel(user) {
  if (!user || userProcessingIds.value.has(user.id)) return
  changeLevelUserId.value = user.id
  changeLevelUserNickName.value = user.nickName || '该用户'
  // 如果当前是普通用户（level=2），则设为管理员（level=1）；否则设为普通用户（level=2）
  const targetLevel = user.level === 2 ? 1 : 2
  changeLevelTargetLevel.value = targetLevel
  changeLevelTargetLabel.value = targetLevel === 1 ? '管理员' : '普通用户'
  showChangeLevelConfirm.value = true
}

function handleChangeLevelCancel() {
  if (userProcessingIds.value.has(changeLevelUserId.value)) return
  showChangeLevelConfirm.value = false
  changeLevelUserId.value = null
  changeLevelUserNickName.value = ''
  changeLevelTargetLabel.value = ''
  changeLevelTargetLevel.value = null
}

async function handleChangeLevelConfirm() {
  const userId = changeLevelUserId.value
  const targetLevel = changeLevelTargetLevel.value
  if (!userId || targetLevel === null || userProcessingIds.value.has(userId)) return
  const set = userProcessingIds.value
  set.add(userId)
  userProcessingIds.value = new Set(set)
  try {
    const res = await updateUserLevel(userId, targetLevel)
    if (res && res.code !== 200) throw new Error(res.message || '修改失败')
    // 修改成功后关闭弹窗并刷新列表
    showChangeLevelConfirm.value = false
    changeLevelUserId.value = null
    changeLevelUserNickName.value = ''
    changeLevelTargetLabel.value = ''
    changeLevelTargetLevel.value = null
    await loadUserList(true)
  } catch (err) {
    userListErrorMsg.value = err?.message || '修改失败，请重试'
  } finally {
    const next = new Set(userProcessingIds.value)
    next.delete(userId)
    userProcessingIds.value = next
  }
}

function handleBlacklistUser(user) {
  if (!user || userProcessingIds.value.has(user.id)) return
  blacklistUserId.value = user.id
  blacklistUserNickName.value = user.nickName || '该用户'
  showBlacklistConfirm.value = true
}

function handleBlacklistCancel() {
  if (userProcessingIds.value.has(blacklistUserId.value)) return
  showBlacklistConfirm.value = false
  blacklistUserId.value = null
  blacklistUserNickName.value = ''
}

async function handleBlacklistConfirm() {
  const userId = blacklistUserId.value
  if (!userId || userProcessingIds.value.has(userId)) return
  const set = userProcessingIds.value
  set.add(userId)
  userProcessingIds.value = new Set(set)
  try {
    const res = await updateUserState(userId, 2) // 2=黑名单
    if (res && res.code !== 200) throw new Error(res.message || '拉黑失败')
    // 拉黑成功后关闭弹窗并刷新列表
    showBlacklistConfirm.value = false
    blacklistUserId.value = null
    blacklistUserNickName.value = ''
    await loadUserList(true)
  } catch (err) {
    userListErrorMsg.value = err?.message || '拉黑失败，请重试'
  } finally {
    const next = new Set(userProcessingIds.value)
    next.delete(userId)
    userProcessingIds.value = next
  }
}

onMounted(() => {
  loadPending(true)
  loadUploaded(true)
  // 导航栏相关事件监听已移至 NavBar 组件
})
</script>

<style scoped>
.rm-layout {
  display: flex;
  gap: 16px;
  padding: 18px 16px 28px;
}

.rm-sidenav {
  width: 200px;
  flex: 0 0 200px;
  padding: 12px;
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(12px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3), 0 0 0 1px rgba(255, 255, 255, 0.06) inset;
  height: fit-content;
  position: sticky;
  top: 88px;
}

.rm-sidenav-item {
  width: 100%;
  display: block;
  text-align: left;
  padding: 10px 10px;
  border-radius: 12px;
  border: 1px solid transparent;
  background: transparent;
  color: #fff;
  font-size: 1.1rem;
  font-weight: 600;
  font-family: 'ZCOOL KuaiLe', 'ZCOOL XiaoWei', serif;
  letter-spacing: 0.12em;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.rm-sidenav-item + .rm-sidenav-item {
  margin-top: 8px;
}

.rm-sidenav-item:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}

.rm-sidenav-item.active {
  background: rgba(74, 144, 226, 0.35);
  border-color: rgba(74, 144, 226, 0.5);
  color: #fff;
  box-shadow: 0 2px 8px rgba(74, 144, 226, 0.25);
}

.rm-main {
  flex: 1 1 auto;
  min-width: 0;
  /* 让表格风格对齐 MyUploads（使用全局 media-browse.css 的 table 样式） */
  --card-bg: rgba(255, 255, 255, 0.92);
  --shadow: rgba(0, 0, 0, 0.08);
  --text-primary: #2c3e50;
}

.rm-card {
  padding: 14px 14px 16px;
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(0, 0, 0, 0.18);
  backdrop-filter: blur(10px);
}

/* 内容块：保留容器感，但不强行改变表格样式 */
.rm-card-lite {
  padding: 0;
}

.rm-main .col-thumbnail {
  width: 160px; /* 拉宽封面列 */
}

.rm-main .thumbnail-cell {
  width: 110px;
  height: 82px;
}

/* 缩短“标题 ↔ 类型”的视觉间距（约为默认的一半），并减少标题列宽度 */
.rm-main .my-uploads-table th.col-title,
.rm-main .my-uploads-table td.col-title {
  padding-right: 0.5rem;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rm-main .my-uploads-table th.col-category,
.rm-main .my-uploads-table td.col-category {
  padding-left: 0.5rem;
}

/* 用户管理表格：等级列宽度 */
.rm-main .my-uploads-table th.col-level,
.rm-main .my-uploads-table td.col-level {
  min-width: 120px;
  width: 120px;
}

.rm-audit-toolbar {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.rm-audit-toolbar-btn {
  /* 对齐 MyUploads 按钮风格：实心 + 阴影 + 上浮 */
  --btn-bg: var(--accent-color, #4a90e2);
  --btn-hover-bg: #3a7bc8;
  --btn-shadow: rgba(74, 144, 226, 0.3);
  --btn-shadow-hover: rgba(74, 144, 226, 0.4);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.85rem 1.1rem;
  border-radius: 10px;
  background: var(--btn-bg);
  color: #fff;
  border: none;
  font-size: 0.95rem;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 4px 12px var(--btn-shadow);
  transition: all 0.2s ease;
}

.rm-audit-toolbar-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  background: var(--btn-hover-bg);
  box-shadow: 0 6px 16px var(--btn-shadow-hover);
}

.rm-audit-toolbar-btn:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: 0 2px 8px var(--btn-shadow);
}

.rm-audit-toolbar-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.rm-audit-toolbar-btn-ok {
  --btn-bg: rgba(46, 204, 113, 0.95);
  --btn-hover-bg: rgba(46, 204, 113, 1);
  --btn-shadow: rgba(46, 204, 113, 0.28);
  --btn-shadow-hover: rgba(46, 204, 113, 0.36);
}

.rm-audit-toolbar-btn-bad {
  --btn-bg: rgba(220, 38, 38, 0.95);
  --btn-hover-bg: rgba(220, 38, 38, 1);
  --btn-shadow: rgba(220, 38, 38, 0.28);
  --btn-shadow-hover: rgba(220, 38, 38, 0.36);
}

.rm-audit-toolbar-btn-ghost {
  --btn-bg: rgba(210, 210, 210, 0.92);
  --btn-hover-bg: #fff;
  --btn-shadow: rgba(0, 0, 0, 0.08);
  --btn-shadow-hover: rgba(0, 0, 0, 0.12);
  color: rgba(44, 62, 80, 0.85);
  border: 1px solid rgba(0, 0, 0, 0.18);
}

.col-update-time {
  width: 220px;
  white-space: nowrap;
}

.col-actions-wide {
  width: 360px;
  white-space: nowrap;
}

.resource-manage-layout {
  display: flex;
  gap: 16px;
  align-items: stretch;
}

.resource-side-nav {
  width: 200px;
  flex: 0 0 200px;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(0, 0, 0, 0.18);
  backdrop-filter: blur(10px);
  height: fit-content;
  position: sticky;
  top: 88px;
}

.resource-side-nav-title {
  font-size: 12px;
  opacity: 0.75;
  margin-bottom: 10px;
  letter-spacing: 1px;
}

.resource-side-nav-item {
  width: 100%;
  display: block;
  text-align: left;
  padding: 10px 10px;
  border-radius: 10px;
  border: 1px solid transparent;
  background: transparent;
  color: rgba(255, 255, 255, 0.9);
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.resource-side-nav-item + .resource-side-nav-item {
  margin-top: 8px;
}

.resource-side-nav-item:hover {
  background: rgba(255, 255, 255, 0.06);
}

.resource-side-nav-item.active {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.18);
}

.resource-main {
  flex: 1 1 auto;
  min-width: 0;
}

.resource-debug-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 8px 0 12px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(0, 0, 0, 0.14);
  backdrop-filter: blur(10px);
}

.resource-debug-left {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
  min-width: 0;
}

.resource-debug-item {
  font-size: 12px;
  opacity: 0.85;
}

.resource-debug-item code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 12px;
}

.resource-debug-refresh {
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.9);
  padding: 8px 10px;
  border-radius: 10px;
  cursor: pointer;
  white-space: nowrap;
}

.resource-debug-refresh:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.resource-debug-raw {
  margin: 8px 0 14px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(0, 0, 0, 0.12);
  backdrop-filter: blur(10px);
}

.resource-debug-raw summary {
  cursor: pointer;
  user-select: none;
  opacity: 0.9;
}

.resource-debug-raw-meta {
  margin-top: 10px;
  font-size: 12px;
  opacity: 0.85;
}

.resource-debug-raw-line + .resource-debug-raw-line {
  margin-top: 6px;
}

.resource-debug-raw-pre {
  margin-top: 10px;
  max-height: 260px;
  overflow: auto;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(0, 0, 0, 0.22);
  font-size: 12px;
  line-height: 1.35;
  color: rgba(255, 255, 255, 0.9);
}

.pagination-hint {
  margin-top: 10px;
  font-size: 12px;
  opacity: 0.75;
}

/* 修复：资源管理页整体为暗色背景时，表格沿用 MyUploads 的“浅色文字变量”会导致内容看不清 */
.resource-main .my-uploads-table-wrapper {
  background: rgba(0, 0, 0, 0.22);
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: none;
}

.resource-main .my-uploads-table thead {
  background: rgba(255, 255, 255, 0.06);
}

.resource-main .my-uploads-table th {
  color: rgba(255, 255, 255, 0.88);
  border-bottom: 1px solid rgba(255, 255, 255, 0.14);
}

.resource-main .my-uploads-table td {
  color: rgba(255, 255, 255, 0.86);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.resource-main .table-row:hover {
  background: rgba(255, 255, 255, 0.04);
}

/* 修改用户等级按钮：黄色警告样式 */
.action-btn-warning {
  background: #f39c12 !important;
  border-color: #f39c12 !important;
  color: #fff !important;
}

.action-btn-warning:hover:not(:disabled) {
  background: #e67e22 !important;
  border-color: #e67e22 !important;
  color: #fff !important;
}

/* 删除按钮：红色危险样式 */
.action-btn-danger {
  background: #e74c3c !important;
  border-color: #e74c3c !important;
  color: #fff !important;
}

.action-btn-danger:hover:not(:disabled) {
  background: #c0392b !important;
  border-color: #c0392b !important;
  color: #fff !important;
}

/* 拉黑按钮：黑灰色样式 */
.action-btn-blacklist {
  background: #5a6c7d !important;
  border-color: #5a6c7d !important;
  color: #fff !important;
}

.action-btn-blacklist:hover:not(:disabled) {
  background: #4a5a6a !important;
  border-color: #4a5a6a !important;
  color: #fff !important;
}

/* 修改用户等级确认按钮：黄色警告样式 */
.change-level-confirm-btn {
  background: rgba(243, 156, 18, 0.95) !important;
  box-shadow: 0 8px 20px rgba(243, 156, 18, 0.26) !important;
}

.change-level-confirm-btn:hover:not(:disabled) {
  background: rgba(230, 126, 34, 1) !important;
}

/* 用户状态标签样式 */
.state-badge.state-normal {
  background: rgba(39, 174, 96, 0.15) !important;
  color: #27ae60 !important;
}

.state-badge.state-pending {
  background: rgba(241, 196, 15, 0.15) !important;
  color: #f1c40f !important;
}

.state-badge.state-rejected {
  background: rgba(231, 76, 60, 0.15) !important;
  color: #e74c3c !important;
}

.state-badge.state-blacklist {
  background: rgba(90, 108, 125, 0.15) !important;
  color: #5a6c7d !important;
}

/* 弹窗过渡效果 */
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.25s ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}

.modal-fade-enter-active .bulk-delete-modal-box,
.modal-fade-leave-active .bulk-delete-modal-box {
  transition: transform 0.25s ease;
}

.modal-fade-enter-from .bulk-delete-modal-box,
.modal-fade-leave-to .bulk-delete-modal-box {
  transform: scale(0.95);
}
</style>

