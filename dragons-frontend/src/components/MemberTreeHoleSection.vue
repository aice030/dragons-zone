<template>
  <section class="member-treehole-section" aria-label="树洞留言">
    <div class="member-treehole-container">
      <h2 class="section-title">
        树洞留言
        <span v-if="isLoggedIn" class="treehole-subtitle">
          {{ currentUserId === ownerId ? '看看拽根们对你说了什么' : `对${ownerNickName || 'TA'}说些什么` }}
        </span>
      </h2>
      <p
        v-if="isLoggedIn && currentUserId !== ownerId"
        class="treehole-hint"
      >
        为了减轻{{ ownerNickName || 'TA' }}的压力，防止刷屏，在上条消息被标记为已读前只能发送一条消息哟
      </p>

      <!-- 未登录提示 -->
      <div v-if="!isLoggedIn" class="login-prompt">
        <p>请先登录后查看树洞留言</p>
      </div>

      <!-- 留言列表 -->
      <div v-else class="treehole-messages">
        <!-- 投递消息按钮 - 右侧，和关闭树洞按钮同一高度 -->
        <div v-if="canSendMessage" class="send-message-controls">
          <button
            type="button"
            class="send-message-btn"
            @click="showSendModal = true"
          >
            <svg class="send-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="22" y1="2" x2="11" y2="13"></line>
              <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
            </svg>
            投递消息
          </button>
        </div>

        <!-- 加载状态 -->
        <div v-if="loading" class="loading">
          <div class="loading-spinner"></div>
          <p>加载中...</p>
        </div>

        <!-- 筛选按钮 - 左侧，单独定位；非主人视角多「已回复」选项 -->
        <div v-if="!loading && messageList.length > 0" class="treehole-filter-controls">
          <div class="category-selector filter-selector">
            <button
              class="category-option"
              :class="{ active: messageFilter === 'all' }"
              @click="messageFilter = 'all'"
            >
              全部
            </button>
            <span class="category-separator">|</span>
            <button
              class="category-option"
              :class="{ active: messageFilter === 'unread' }"
              @click="messageFilter = 'unread'"
            >
              未读
            </button>
            <span class="category-separator">|</span>
            <button
              class="category-option"
              :class="{ active: messageFilter === 'read' }"
              @click="messageFilter = 'read'"
            >
              已读
            </button>
            <template v-if="currentUserId !== ownerId">
              <span class="category-separator">|</span>
              <button
                class="category-option"
                :class="{ active: messageFilter === 'replied' }"
                @click="messageFilter = 'replied'"
              >
                已回复
              </button>
            </template>
          </div>
        </div>

        <!-- 关闭/开放树洞按钮 - 右侧，单独定位。状态 0 或 1 显示「关闭树洞」，状态 2 显示「开放树洞」 -->
        <div v-if="!loading && canManageTreeHole" class="treehole-state-controls">
          <button
            type="button"
            class="send-message-btn"
            :class="{ 'btn-danger': treeHoleState === 0 || treeHoleState === 1, 'btn-success': treeHoleState === 2 }"
            @click="handleToggleTreeHoleState"
            :disabled="updatingTreeHoleState"
          >
            <svg v-if="treeHoleState === 0 || treeHoleState === 1" class="send-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"></circle>
              <line x1="12" y1="8" x2="12" y2="16"></line>
              <line x1="8" y1="12" x2="16" y2="12"></line>
            </svg>
            <svg v-else class="send-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="20 6 9 17 4 12"></polyline>
            </svg>
            {{ treeHoleState === 2 ? '开放树洞' : '关闭树洞' }}
          </button>
        </div>

        <!-- 留言列表 -->
        <div v-if="!loading && displayMessageGroups.length > 0" class="message-list">
          <div
            v-for="group in displayMessageGroups"
            :key="group.root.id"
            class="message-group"
          >
            <div
              class="message-item root-message-item"
              :class="{ 'unread': group.root.state === 0, 'read': group.root.state === 1, 'replied': group.root.state === 3 }"
              @click="showMessageDetail(group.root)"
            >
              <div class="message-header">
                <span class="message-sender">{{ group.root.senderNickName || `用户${group.root.senderId}` }}</span>
                <span class="message-state">
                  <span v-if="group.root.state === 0" class="state-badge state-unread">未读</span>
                  <span v-else-if="group.root.state === 1" class="state-badge state-read">已读</span>
                  <span v-else-if="group.root.state === 3" class="state-badge state-replied">已回复</span>
                </span>
              </div>
              <div class="message-content">{{ group.root.content }}</div>
              <!-- 非主人视角且有回复：右下角下拉箭头 -->
              <div
                v-if="currentUserId !== ownerId && group.replies.length > 0"
                class="message-footer-replies"
              >
                <button
                  type="button"
                  class="replies-toggle-btn"
                  :class="{ expanded: expandedReplyRootIds.has(group.root.id) }"
                  :aria-expanded="expandedReplyRootIds.has(group.root.id)"
                  @click.stop="toggleReplyExpand(group.root.id)"
                >
                  <svg class="toggle-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="6 9 12 15 18 9"></polyline>
                  </svg>
                </button>
              </div>
            </div>
            <!-- 回复列表（展开时显示） -->
            <div
              v-if="currentUserId !== ownerId && group.replies.length > 0 && expandedReplyRootIds.has(group.root.id)"
              class="message-replies-list"
            >
              <div
                v-for="reply in group.replies"
                :key="reply.id"
                class="message-item message-reply-item"
                @click.stop="showMessageDetail(reply)"
              >
                <div class="reply-card-label">{{ ownerNickName || 'TA' }}的回复</div>
                <div class="message-content">{{ reply.content }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else-if="!loading && messageList.length === 0" class="empty-state">
          <p>暂无留言</p>
        </div>
        <!-- 筛选后无结果 -->
        <div v-else-if="!loading && messageList.length > 0 && filteredMessageList.length === 0" class="empty-state">
          <p>没有{{ filterEmptyLabel }}消息</p>
        </div>

        <!-- 分页 -->
        <div v-if="!loading && messageList.length > 0" class="pagination">
          <button
            type="button"
            class="page-btn"
            :disabled="currentPage <= 1"
            @click="goToPage(currentPage - 1)"
          >
            上一页
          </button>
          <span class="page-info">
            第 {{ currentPage }} / {{ totalPages }} 页（共 {{ total }} 条）
          </span>
          <button
            type="button"
            class="page-btn"
            :disabled="currentPage >= totalPages"
            @click="goToPage(currentPage + 1)"
          >
            下一页
          </button>
        </div>
      </div>

      <!-- 投递消息弹窗 -->
      <Teleport to="body">
        <Transition name="modal">
          <div v-if="showSendModal" class="modal-overlay" @click.self="closeSendModal">
            <div class="modal-box send-message-modal">
              <div class="modal-header">
                <h3>写下你想对{{ ownerNickName || 'TA' }}说的话</h3>
                <button type="button" class="modal-close" aria-label="关闭" @click="closeSendModal">×</button>
              </div>
              <form class="send-message-form" @submit.prevent="handleSendMessage">
                <div class="form-group">
                  <p class="form-description">
                    为了减轻{{ ownerNickName || 'TA' }}的压力，如果上一条消息未读，是不允许投递新消息的哟
                  </p>
                  <label for="message-content">留言内容 <span class="required">*</span></label>
                  <div class="textarea-wrapper">
                    <textarea
                      id="message-content"
                      v-model="messageContent"
                      placeholder="请输入留言内容..."
                      required
                      rows="8"
                      maxlength="800"
                      class="message-textarea"
                    ></textarea>
                    <div class="char-count">最多输入800字</div>
                  </div>
                </div>
                <p v-if="sendErrorMsg" class="form-error">{{ sendErrorMsg }}</p>
                <div class="form-actions">
                  <button type="submit" class="btn-submit" :disabled="sending || !messageContent.trim()">
                    {{ sending ? '投递中...' : '确认投递' }}
                  </button>
                  <button type="button" class="btn-cancel" @click="closeSendModal">取消</button>
                </div>
              </form>
            </div>
          </div>
        </Transition>
      </Teleport>

      <!-- 错误提示弹窗 -->
      <Teleport to="body">
        <Transition name="modal">
          <div v-if="showErrorAlertModal" class="modal-overlay" @click.self="closeErrorAlert">
            <div class="modal-box error-alert-modal">
              <div class="modal-header">
                <h3>{{ errorAlertTitle }}</h3>
              </div>
              <div class="error-alert-content">
                <p class="error-alert-message">{{ errorAlertMessage }}</p>
              </div>
              <div class="error-alert-actions">
                <button type="button" class="btn-confirm" @click="closeErrorAlert">确认</button>
              </div>
            </div>
          </div>
        </Transition>
      </Teleport>

      <!-- 消息详情弹窗 -->
      <Teleport to="body">
        <Transition name="modal">
          <div v-if="showMessageDetailModal && selectedMessage" class="modal-overlay" @click.self="closeMessageDetail">
            <div class="modal-box message-detail-modal">
              <div class="modal-header">
                <h3>消息详情</h3>
                <button type="button" class="modal-close" aria-label="关闭" @click="closeMessageDetail">×</button>
              </div>
              <div class="message-detail-content">
                <div class="detail-field">
                  <label>发送者</label>
                  <div class="detail-value">{{ selectedMessage.senderNickName || `用户${selectedMessage.senderId}` }}</div>
                </div>
                <div class="detail-field">
                  <label>内容</label>
                  <div class="detail-value message-detail-text">{{ selectedMessage.content }}</div>
                </div>
              </div>
              <!-- 非树洞主人：仅根留言（自己投递的）可删除，主人回复的消息不允许删除 -->
              <div class="message-detail-actions" v-if="currentUserId !== props.ownerId && selectedMessage.rootMessageId == null">
                <button type="button" class="btn-delete-detail" @click="showDeleteConfirm">删除</button>
              </div>
              <!-- 树洞主人：显示多个操作按钮 -->
              <div class="message-detail-actions-owner" v-if="currentUserId === props.ownerId">
                <button 
                  type="button" 
                  class="btn-action-owner"
                  :class="{ 'btn-read': selectedMessage.state === 0, 'btn-read-disabled': selectedMessage.state === 1 || selectedMessage.state === 3 }"
                  :disabled="selectedMessage.state === 1 || selectedMessage.state === 3"
                  @click="handleMarkReadFromDetail"
                >
                  已读
                </button>
                <button 
                  type="button" 
                  class="btn-action-owner btn-reply"
                  :disabled="selectedMessage.replyMessageId != null"
                  @click="showReplyModal = true"
                >
                  回复
                </button>
                <button type="button" class="btn-action-owner btn-delete-owner" @click="showDeleteConfirmOwner">删除</button>
                <button
                  v-if="selectedMessage.senderId !== ownerId"
                  type="button"
                  class="btn-action-owner btn-block"
                  @click="isSenderBlocked ? handleUnblock() : (showBlockConfirmModal = true)"
                >
                  {{ isSenderBlocked ? '解除拉黑' : '拉黑该用户' }}
                </button>
              </div>
            </div>
          </div>
        </Transition>
      </Teleport>

      <!-- 确认删除弹窗（发送者删除） -->
      <Teleport to="body">
        <Transition name="modal">
          <div v-if="showDeleteConfirmModal && currentUserId !== props.ownerId" class="modal-overlay" @click.self="closeDeleteConfirm">
            <div class="modal-box delete-confirm-modal">
              <div class="modal-header">
                <h3>确认删除</h3>
              </div>
              <div class="delete-confirm-content">
                <p class="delete-confirm-message">
                  确认删除？删除消息并不能让你投递新消息，还是要等主人阅读完上一条消息的哦
                </p>
              </div>
              <div class="delete-confirm-actions">
                <button type="button" class="btn-confirm-delete" @click="handleConfirmDelete">确认删除</button>
                <button type="button" class="btn-cancel-delete" @click="closeDeleteConfirm">取消</button>
              </div>
            </div>
          </div>
        </Transition>
      </Teleport>

      <!-- 确认删除弹窗（树洞主人删除） -->
      <Teleport to="body">
        <Transition name="modal">
          <div v-if="showDeleteConfirmModal && currentUserId === props.ownerId" class="modal-overlay" @click.self="closeDeleteConfirm">
            <div class="modal-box delete-confirm-modal">
              <div class="modal-header">
                <h3>确认删除</h3>
              </div>
              <div class="delete-confirm-content">
                <p class="delete-confirm-message">
                  确认删除这条消息？删除后将无法恢复。
                </p>
              </div>
              <div class="delete-confirm-actions">
                <button type="button" class="btn-confirm-delete" @click="handleConfirmDeleteOwner">确认删除</button>
                <button type="button" class="btn-cancel-delete" @click="closeDeleteConfirm">取消</button>
              </div>
            </div>
          </div>
        </Transition>
      </Teleport>

      <!-- 回复消息弹窗 -->
      <Teleport to="body">
        <Transition name="modal">
          <div v-if="showReplyModal" class="modal-overlay" @click.self="closeReplyModal">
            <div class="modal-box reply-modal">
              <div class="modal-header">
                <h3>回复消息</h3>
                <button type="button" class="modal-close" aria-label="关闭" @click="closeReplyModal">×</button>
              </div>
              <div class="reply-form">
                <div class="form-group">
                  <div class="textarea-wrapper">
                    <textarea
                      v-model="replyContent"
                      placeholder="请输入回复内容..."
                      required
                      rows="6"
                      maxlength="800"
                      class="message-textarea"
                    ></textarea>
                    <div class="char-count">最多输入800字</div>
                  </div>
                </div>
                <div class="form-actions">
                  <button type="button" class="btn-submit" :disabled="replying || !replyContent.trim()" @click="handleSendReply">
                    {{ replying ? '回复中...' : '确认回复' }}
                  </button>
                  <button type="button" class="btn-cancel" @click="closeReplyModal">取消</button>
                </div>
              </div>
            </div>
          </div>
        </Transition>
      </Teleport>

      <!-- 确认拉黑弹窗 -->
      <Teleport to="body">
        <Transition name="modal">
          <div v-if="showBlockConfirmModal" class="modal-overlay" @click.self="closeBlockConfirm">
            <div class="modal-box block-confirm-modal">
              <div class="modal-header">
                <h3>确认拉黑</h3>
              </div>
              <div class="block-confirm-content">
                <p class="block-confirm-message">
                  确认拉黑用户"{{ selectedMessage?.senderNickName || `用户${selectedMessage?.senderId}` }}"？拉黑后该用户将无法向您的树洞投递消息。
                </p>
                <div class="block-reason-wrap">
                  <input
                    v-model="blockReason"
                    type="text"
                    class="block-reason-input"
                    placeholder="请填写拉黑原因，也可不填"
                    maxlength="200"
                  />
                </div>
              </div>
              <div class="block-confirm-actions">
                <button type="button" class="btn-confirm-block" @click="handleConfirmBlock">确认拉黑</button>
                <button type="button" class="btn-cancel-block" @click="closeBlockConfirm">取消</button>
              </div>
            </div>
          </div>
        </Transition>
      </Teleport>

      <!-- 关闭树洞确认弹窗 -->
      <Teleport to="body">
        <Transition name="modal">
          <div v-if="showCloseTreeHoleConfirmModal" class="modal-overlay" @click.self="closeCloseTreeHoleConfirm">
            <div class="modal-box close-treehole-confirm-modal">
              <div class="modal-header">
                <h3>确认关闭树洞</h3>
              </div>
              <div class="close-treehole-confirm-content">
                <p class="close-treehole-confirm-message">
                  确认关闭树洞？拽根们无法再投递消息
                </p>
              </div>
              <div class="close-treehole-confirm-actions">
                <button type="button" class="btn-confirm-close-treehole" @click="handleConfirmCloseTreeHole">确认关闭</button>
                <button type="button" class="btn-cancel-close-treehole" @click="closeCloseTreeHoleConfirm">取消</button>
              </div>
            </div>
          </div>
        </Transition>
      </Teleport>

      <!-- 开放树洞确认弹窗 -->
      <Teleport to="body">
        <Transition name="modal">
          <div v-if="showOpenTreeHoleConfirmModal" class="modal-overlay" @click.self="closeOpenTreeHoleConfirm">
            <div class="modal-box close-treehole-confirm-modal">
              <div class="modal-header">
                <h3>确认开放树洞</h3>
              </div>
              <div class="close-treehole-confirm-content">
                <p class="close-treehole-confirm-message">
                  确认开放树洞？拽根们可以继续投递消息
                </p>
              </div>
              <div class="close-treehole-confirm-actions">
                <button type="button" class="btn-confirm-close-treehole" @click="handleConfirmOpenTreeHole">确认开放</button>
                <button type="button" class="btn-cancel-close-treehole" @click="closeOpenTreeHoleConfirm">取消</button>
              </div>
            </div>
          </div>
        </Transition>
      </Teleport>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { getTreeHoleMessages, sendTreeHoleMessage, markMessageRead, deleteMessageByOwner, deleteMessageBySender, updateTreeHoleState, getTreeHoleInfo, checkBlockStatus, blockUser, unblockUser } from '@/api/treehole'
import { getNickNameById } from '@/api/user'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  ownerId: {
    type: Number,
    required: true
  }
})

const emit = defineEmits(['message-updated'])

const userStore = useUserStore()
const isLoggedIn = computed(() => userStore.isLoggedIn)
const currentUserId = computed(() => userStore.userInfo?.id ?? null)
const userLevel = computed(() => userStore.userInfo?.level ?? null)

// 判断是否为管理员（level=1）或树洞主人（可以管理消息）
const canManage = computed(() => {
  // level=1（管理员）或当前用户是树洞主人，可以管理消息
  return userLevel.value === 1 || currentUserId.value === props.ownerId
})

// 判断是否可以管理树洞状态（仅树洞主人）
const canManageTreeHole = computed(() => {
  return currentUserId.value === props.ownerId
})

// 判断是否可以投递消息（只要当前用户和树洞主人ID不同即可）
const canSendMessage = computed(() => {
  // 如果未登录，不能投递
  if (!isLoggedIn.value || currentUserId.value == null) {
    return false
  }
  // 只要当前用户ID和树洞主人ID不同，就可以投递消息
  return currentUserId.value !== props.ownerId
})

const messageList = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

// 消息筛选：all=全部, unread=未读, read=已读, replied=已回复（仅非主人视角）
const messageFilter = ref('all')

// 根据筛选条件过滤消息列表
const filteredMessageList = computed(() => {
  if (messageFilter.value === 'all') {
    return messageList.value
  } else if (messageFilter.value === 'unread') {
    return messageList.value.filter(msg => msg.state === 0)
  } else if (messageFilter.value === 'read') {
    // 非主人：已读=state1；主人：已读=state1或3（无「已回复」单独筛选项）
    if (currentUserId.value === props.ownerId) {
      return messageList.value.filter(msg => msg.state === 1 || msg.state === 3)
    }
    return messageList.value.filter(msg => msg.state === 1)
  } else if (messageFilter.value === 'replied') {
    return messageList.value.filter(msg => msg.state === 3)
  }
  return messageList.value
})

// 筛选无结果时的提示文案
const filterEmptyLabel = computed(() => {
  const m = { unread: '未读', read: '已读', replied: '已回复' }
  return m[messageFilter.value] || '已读'
})

// 非主人视角：将消息按根留言分组，回复挂到对应根留言下。格式：{ root, replies }[]
const displayMessageGroups = computed(() => {
  const list = filteredMessageList.value
  if (!list.length) return []
  // 树洞主人：全部为根留言，无分组（主人视角接口不返回回复）
  if (currentUserId.value === props.ownerId) {
    return list.map(root => ({ root, replies: [] }))
  }
  // 非主人：区分根留言和回复
  const roots = list.filter(msg => msg.rootMessageId == null)
  const replyMap = new Map()
  list.filter(msg => msg.rootMessageId != null).forEach(reply => {
    const rid = reply.rootMessageId
    if (!replyMap.has(rid)) replyMap.set(rid, [])
    replyMap.get(rid).push(reply)
  })
  return roots.map(root => ({
    root,
    replies: replyMap.get(root.id) || []
  }))
})

// 非主人视角：展开的回复根消息ID集合，点击箭头切换
const expandedReplyRootIds = ref(new Set())

const toggleReplyExpand = (rootId) => {
  const next = new Set(expandedReplyRootIds.value)
  if (next.has(rootId)) {
    next.delete(rootId)
  } else {
    next.add(rootId)
  }
  expandedReplyRootIds.value = next
}

// 投递消息相关
const showSendModal = ref(false)
const messageContent = ref('')
const sending = ref(false)
const sendErrorMsg = ref('')

// 错误提示弹窗相关
const showErrorAlertModal = ref(false)
const errorAlertMessage = ref('')
const errorAlertTitle = ref('提示')

// 消息详情弹窗相关
const showMessageDetailModal = ref(false)
const selectedMessage = ref(null)

// 确认删除弹窗相关
const showDeleteConfirmModal = ref(false)

// 回复消息弹窗相关
const showReplyModal = ref(false)
const replyContent = ref('')
const replying = ref(false)

// 确认拉黑弹窗相关
const showBlockConfirmModal = ref(false)
const blockReason = ref('')
// 当前详情中发送者是否已被拉黑（仅树洞主人视角有效）
const isSenderBlocked = ref(false)

// 关闭/开放树洞确认弹窗（仅树洞主人可见并操作）
const showCloseTreeHoleConfirmModal = ref(false)
const showOpenTreeHoleConfirmModal = ref(false)

// 树洞状态相关
const treeHoleState = ref(0) // 0=正常，2=禁止投递
const updatingTreeHoleState = ref(false)
const ownerNickName = ref('') // 树洞主人的昵称

const loadMessages = async (page = 1) => {
  if (!isLoggedIn.value) return
  
  loading.value = true
  try {
    const response = await getTreeHoleMessages(props.ownerId, page, pageSize.value)
    if (response?.data) {
      messageList.value = response.data.list || []
      total.value = response.data.total || 0
      currentPage.value = page
    }
  } catch (error) {
    console.error('加载树洞留言失败:', error)
    messageList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const goToPage = (page) => {
  if (page >= 1 && page <= totalPages.value) {
    loadMessages(page)
  }
}

const handleMarkRead = async (messageId) => {
  try {
    await markMessageRead(messageId)
    // 重新加载当前页
    loadMessages(currentPage.value)
    emit('message-updated')
  } catch (error) {
    console.error('标记已读失败:', error)
    alert('标记已读失败，请重试')
  }
}

const handleDelete = async (messageId) => {
  if (!confirm('确定要删除这条留言吗？')) return
  
  try {
    // 如果是管理员或树洞主人，使用全局删除；否则使用发送者删除
    if (canManage.value) {
      await deleteMessageByOwner(messageId)
    } else {
      await deleteMessageBySender(messageId)
    }
    // 重新加载当前页
    loadMessages(currentPage.value)
    emit('message-updated')
  } catch (error) {
    console.error('删除留言失败:', error)
    alert('删除失败，请重试')
  }
}

const closeSendModal = () => {
  showSendModal.value = false
  messageContent.value = ''
  sendErrorMsg.value = ''
}

// 显示错误提示弹窗
const showErrorAlert = (errorCode, errorMessage) => {
  // 针对不同错误码设置不同的提示信息
  if (errorCode === 4016 || errorMessage.includes('未读') || errorMessage.includes('等待主人查看')) {
    errorAlertTitle.value = '无法投递'
    errorAlertMessage.value = `为了减轻${ownerNickName.value || 'TA'}的压力，如果上一条消息未读，是不允许投递新消息的哟。\n\n请等待${ownerNickName.value || 'TA'}阅读后再试。`
  } else if (errorCode === 4014 || errorMessage.includes('树洞已关闭')) {
    errorAlertTitle.value = '无法投递'
    errorAlertMessage.value = '该树洞已关闭，暂不接收新消息'
  } else if (errorCode === 4015 || errorMessage.includes('拉黑')) {
    errorAlertTitle.value = '无法投递'
    errorAlertMessage.value = '您已被该树洞拉黑，无法投递消息'
  } else if (errorMessage) {
    errorAlertTitle.value = '投递失败'
    errorAlertMessage.value = errorMessage
  } else {
    errorAlertTitle.value = '投递失败'
    errorAlertMessage.value = '投递失败，请重试'
  }
  showErrorAlertModal.value = true
}

// 关闭错误提示弹窗，同时关闭投递消息弹窗
const closeErrorAlert = () => {
  showErrorAlertModal.value = false
  closeSendModal() // 同时关闭投递消息弹窗
}

// 获取发送者是否已被拉黑（仅树洞主人查看详情时调用）
const fetchBlockStatus = async (senderId) => {
  if (currentUserId.value !== props.ownerId || senderId == null) {
    isSenderBlocked.value = false
    return
  }
  try {
    const res = await checkBlockStatus(senderId)
    isSenderBlocked.value = res?.data === true
  } catch {
    isSenderBlocked.value = false
  }
}

// 显示消息详情
const showMessageDetail = (message) => {
  selectedMessage.value = message
  showMessageDetailModal.value = true
  if (canManage.value && message.senderId != null && message.senderId !== props.ownerId) {
    fetchBlockStatus(message.senderId)
  } else {
    isSenderBlocked.value = false
  }
}

// 关闭消息详情弹窗
const closeMessageDetail = () => {
  showMessageDetailModal.value = false
  selectedMessage.value = null
  isSenderBlocked.value = false
}

// 显示确认删除弹窗
const showDeleteConfirm = () => {
  showDeleteConfirmModal.value = true
}

// 关闭确认删除弹窗
const closeDeleteConfirm = () => {
  showDeleteConfirmModal.value = false
}

// 确认删除消息（调用发送者删除接口，仅非树洞主人使用）
const handleConfirmDelete = async () => {
  if (!selectedMessage.value) return
  
  try {
    await deleteMessageBySender(selectedMessage.value.id)
    // 删除成功，关闭所有弹窗并刷新列表
    closeDeleteConfirm()
    closeMessageDetail()
    loadMessages(currentPage.value)
    emit('message-updated')
  } catch (error) {
    console.error('删除消息失败:', error)
    const errorMessage = error.response?.data?.message || error.message || '删除失败，请重试'
    alert(errorMessage)
  }
}

// 从详情弹窗标记已读
const handleMarkReadFromDetail = async () => {
  if (!selectedMessage.value || selectedMessage.value.state === 1 || selectedMessage.value.state === 3) return
  
  try {
    await markMessageRead(selectedMessage.value.id)
    // 更新消息状态
    selectedMessage.value.state = 1
    // 刷新列表
    loadMessages(currentPage.value)
    emit('message-updated')
  } catch (error) {
    console.error('标记已读失败:', error)
    const errorMessage = error.response?.data?.message || error.message || '标记已读失败，请重试'
    alert(errorMessage)
  }
}

// 显示树洞主人删除确认弹窗
const showDeleteConfirmOwner = () => {
  showDeleteConfirmModal.value = true
}

// 打开回复弹窗时，清空内容
watch(showReplyModal, (newVal) => {
  if (newVal) {
    replyContent.value = ''
  }
})

// 确认删除消息（树洞主人删除，全局删除）
const handleConfirmDeleteOwner = async () => {
  if (!selectedMessage.value) return
  
  try {
    await deleteMessageByOwner(selectedMessage.value.id)
    // 删除成功，关闭所有弹窗并刷新列表
    closeDeleteConfirm()
    closeMessageDetail()
    loadMessages(currentPage.value)
    emit('message-updated')
  } catch (error) {
    console.error('删除消息失败:', error)
    const errorMessage = error.response?.data?.message || error.message || '删除失败，请重试'
    alert(errorMessage)
  }
}

// 确认拉黑用户
const handleConfirmBlock = async () => {
  if (!selectedMessage.value) return
  
  try {
    const reason = (blockReason.value || '').trim()
    await blockUser(selectedMessage.value.senderId, reason)
    isSenderBlocked.value = true
    blockReason.value = ''
    showBlockConfirmModal.value = false
    closeMessageDetail()
    loadMessages(currentPage.value)
    alert('拉黑成功')
  } catch (error) {
    console.error('拉黑用户失败:', error)
    const errorMessage = error.response?.data?.message || error.message || '拉黑失败，请重试'
    alert(errorMessage)
  }
}

// 解除拉黑用户
const handleUnblock = async () => {
  if (!selectedMessage.value) return
  
  try {
    await unblockUser(selectedMessage.value.senderId)
    isSenderBlocked.value = false
    alert('已解除拉黑')
  } catch (error) {
    console.error('解除拉黑失败:', error)
    const errorMessage = error.response?.data?.message || error.message || '解除拉黑失败，请重试'
    alert(errorMessage)
  }
}

// 关闭拉黑确认弹窗
const closeBlockConfirm = () => {
  showBlockConfirmModal.value = false
  blockReason.value = ''
}

// 发送回复消息
const handleSendReply = async () => {
  if (!selectedMessage.value || !replyContent.value.trim()) return
  
  replying.value = true
  try {
    await sendTreeHoleMessage(props.ownerId, replyContent.value.trim(), selectedMessage.value.id)
    // 回复成功，关闭弹窗并刷新列表
    showReplyModal.value = false
    replyContent.value = ''
    closeMessageDetail()
    loadMessages(currentPage.value)
    emit('message-updated')
  } catch (error) {
    console.error('回复消息失败:', error)
    const errorMessage = error.response?.data?.message || error.message || '回复失败，请重试'
    alert(errorMessage)
  } finally {
    replying.value = false
  }
}

// 关闭回复弹窗
const closeReplyModal = () => {
  showReplyModal.value = false
  replyContent.value = ''
}

const handleSendMessage = async () => {
  if (!messageContent.value.trim()) {
    sendErrorMsg.value = '请输入留言内容'
    return
  }

  sending.value = true
  sendErrorMsg.value = ''

  try {
    const result = await sendTreeHoleMessage(props.ownerId, messageContent.value.trim())
    
    // 检查返回的 Result 对象的 code 字段
    // axios 拦截器返回的是 response.data，即 Result 对象
    if (result && result.code !== undefined && result.code !== 200) {
      // 后端返回了错误码，即使 HTTP 状态是 200
      const errorMessage = result.message || ''
      const errorCode = result.code
      
      // 显示错误弹窗
      showErrorAlert(errorCode, errorMessage)
      return // 不继续执行成功逻辑
    }
    
    // 投递成功，关闭弹窗并刷新列表
    closeSendModal()
    // 重新加载第一页（新消息会在最前面）
    loadMessages(1)
    emit('message-updated')
  } catch (error) {
    // 根据错误信息显示弹窗提示
    let errorMessage = ''
    let errorCode = null
    
    // 检查不同的错误对象结构
    if (error.response?.data) {
      errorMessage = error.response.data.message || ''
      errorCode = error.response.data.code
    } else if (error.data) {
      errorMessage = error.data.message || error.message || ''
      errorCode = error.data.code || error.code
    } else if (error.message) {
      errorMessage = error.message
    }
    
    // 显示错误弹窗
    showErrorAlert(errorCode, errorMessage)
  } finally {
    sending.value = false
  }
}

// 显示关闭树洞确认弹窗
const showCloseTreeHoleConfirm = () => {
  showCloseTreeHoleConfirmModal.value = true
}

// 关闭关闭树洞确认弹窗
const closeCloseTreeHoleConfirm = () => {
  showCloseTreeHoleConfirmModal.value = false
}

const showOpenTreeHoleConfirm = () => {
  showOpenTreeHoleConfirmModal.value = true
}

const closeOpenTreeHoleConfirm = () => {
  showOpenTreeHoleConfirmModal.value = false
}

// 确认关闭树洞（从确认弹窗调用）
const handleConfirmCloseTreeHole = async () => {
  closeCloseTreeHoleConfirm()
  updatingTreeHoleState.value = true
  try {
    await updateTreeHoleState(props.ownerId, 2)
    treeHoleState.value = 2
    alert('关闭成功')
  } catch (error) {
    console.error('关闭树洞失败:', error)
    const errorMsg = error.response?.data?.message || '关闭失败，请重试'
    alert(errorMsg)
  } finally {
    updatingTreeHoleState.value = false
  }
}

// 确认开放树洞（从确认弹窗调用）
const handleConfirmOpenTreeHole = async () => {
  closeOpenTreeHoleConfirm()
  updatingTreeHoleState.value = true
  try {
    await updateTreeHoleState(props.ownerId, 0)
    treeHoleState.value = 0
    alert('开放成功')
  } catch (error) {
    console.error('开放树洞失败:', error)
    const errorMsg = error.response?.data?.message || '开放失败，请重试'
    alert(errorMsg)
  } finally {
    updatingTreeHoleState.value = false
  }
}

// 切换树洞状态：状态 0 或 1 显示「关闭树洞」弹窗，状态 2 显示「开放树洞」弹窗
const handleToggleTreeHoleState = () => {
  if (treeHoleState.value === 0 || treeHoleState.value === 1) {
    showCloseTreeHoleConfirm()
  } else {
    // treeHoleState === 2
    showOpenTreeHoleConfirm()
  }
}

// 加载树洞主人的昵称
const loadOwnerNickName = async () => {
  try {
    const response = await getNickNameById(props.ownerId)
    // 后端返回的 data 字段直接就是字符串，不是嵌套对象
    if (response.data) {
      ownerNickName.value = response.data
    }
  } catch (error) {
    console.error('加载树洞主人昵称失败:', error)
  }
}

// 加载树洞状态（仅树洞主人需要）
// 注意：axios 响应拦截器已返回 response.data，故 getTreeHoleInfo 得到的是 Result，树洞实体在 response.data，state 为 response.data.state
const loadTreeHoleState = async () => {
  if (!canManageTreeHole.value) return
  
  try {
    const response = await getTreeHoleInfo(props.ownerId)
    if (response?.data?.state != null) {
      treeHoleState.value = response.data.state
    } else {
      treeHoleState.value = 0 // 默认正常状态
    }
  } catch (error) {
    console.error('加载树洞状态失败:', error)
    treeHoleState.value = 0 // 默认正常状态
  }
}


// 监听登录状态变化
watch(isLoggedIn, (newVal) => {
  if (newVal) {
    loadMessages(1)
    loadOwnerNickName()
    loadTreeHoleState()
  } else {
    messageList.value = []
    total.value = 0
    treeHoleState.value = 0
    ownerNickName.value = ''
  }
})

// 监听 ownerId 变化，重新加载（仅当前用户为树洞主人时加载树洞状态）
watch(() => props.ownerId, () => {
  if (isLoggedIn.value) {
    loadMessages(1)
    loadOwnerNickName()
    loadTreeHoleState()
  }
}, { immediate: true })

// 监听「当前用户是否为树洞主人」变化，加载树洞状态（flush: 'post' 确保 store 就绪后再请求）
watch(canManageTreeHole, (newVal) => {
  if (newVal && isLoggedIn.value) {
    loadTreeHoleState()
  }
}, { immediate: true, flush: 'post' })
</script>

<style scoped>
.member-treehole-section {
  padding: 3rem 2rem;
  position: relative;
  z-index: 1;
}

.member-treehole-container {
  max-width: 1200px;
  margin: 0 auto;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  padding: 2.5rem;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  position: relative;
  z-index: 1;
}

.section-title {
  margin: 0 0 0.5rem 0;
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--text-primary, #2c3e50);
}

.treehole-subtitle {
  margin-left: 2em; /* 空两个字的大小 */
  font-size: 1.5rem; /* 比 1.8rem 小一号 */
  font-weight: 400;
  color: var(--text-primary, #2c3e50);
}

.treehole-hint {
  margin: 0 0 2rem 0;
  font-size: 0.85rem;
  color: var(--text-secondary, rgba(44, 62, 80, 0.7));
}

.login-prompt {
  text-align: center;
  padding: 3rem 2rem;
  color: var(--text-secondary, rgba(44, 62, 80, 0.7));
  font-size: 1rem;
}

.treehole-messages {
  min-height: 200px;
  position: relative;
  z-index: 2;
  padding-top: 1rem; /* 为筛选按钮和关闭树洞按钮留出空间 */
  margin-top: 2rem; /* 消息列表向下挪一些 */
}

/* 筛选按钮 - 左侧，单独定位 */
.treehole-filter-controls {
  position: absolute;
  top: -3rem;
  left: -1.5rem;
  z-index: 10;
}

/* 关闭树洞按钮 - 右侧，单独定位 */
.treehole-state-controls {
  position: absolute;
  top: -2rem;
  right: 0;
  z-index: 10;
}

/* 投递消息按钮 - 右侧，和关闭树洞按钮同一高度 */
.send-message-controls {
  position: absolute;
  top: -2rem;
  right: 0;
  z-index: 10;
}

/* 筛选选择器样式（复用图片/视频区的样式） */
.category-selector {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.35rem 0.8rem;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}


.category-option {
  padding: 0.3rem 0.6rem;
  border: none;
  background: transparent;
  color: var(--text-secondary, rgba(44, 62, 80, 0.7));
  font-size: 0.85rem;
  font-weight: 500;
  cursor: pointer;
  border-radius: 10px;
  transition: all 0.3s ease;
  white-space: nowrap;
  flex-shrink: 0;
}

.category-option:hover {
  color: var(--accent-color, #4a90e2);
  background: rgba(74, 144, 226, 0.1);
}

.category-option.active {
  color: var(--accent-color, #4a90e2);
  background: rgba(74, 144, 226, 0.2);
  font-weight: 600;
}

.category-separator {
  color: rgba(44, 62, 80, 0.3);
  font-size: 0.8rem;
  user-select: none;
}

.treehole-state-control {
  position: relative;
}



.send-message-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.5rem;
  background: var(--accent-color, #4a90e2);
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 4px 12px rgba(74, 144, 226, 0.3);
  position: relative;
  z-index: 10;
}

.send-message-btn:hover {
  background: #3a7bc8;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(74, 144, 226, 0.4);
}

.send-message-btn:active {
  transform: translateY(0);
  box-shadow: 0 2px 8px rgba(74, 144, 226, 0.3);
}

.send-message-btn.btn-danger {
  background: linear-gradient(135deg, #e74c3c 0%, #c0392b 100%);
  box-shadow: 0 4px 12px rgba(231, 76, 60, 0.3);
}

.send-message-btn.btn-danger:hover:not(:disabled) {
  background: linear-gradient(135deg, #c0392b 0%, #a93226 100%);
  box-shadow: 0 6px 16px rgba(231, 76, 60, 0.4);
  transform: translateY(-2px);
}

.send-message-btn.btn-success {
  background: linear-gradient(135deg, #27ae60 0%, #229954 100%);
  box-shadow: 0 4px 12px rgba(39, 174, 96, 0.3);
}

.send-message-btn.btn-success:hover:not(:disabled) {
  background: linear-gradient(135deg, #229954 0%, #1e8449 100%);
  box-shadow: 0 6px 16px rgba(39, 174, 96, 0.4);
  transform: translateY(-2px);
}

.send-message-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.send-icon {
  width: 18px;
  height: 18px;
}

.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem 2rem;
  color: var(--text-secondary, rgba(44, 62, 80, 0.7));
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 4px solid rgba(74, 144, 226, 0.2);
  border-top-color: var(--accent-color, #4a90e2);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 1rem;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.message-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.root-message-item {
  display: flex;
  flex-direction: column;
}

.message-footer-replies {
  margin-top: 0.5rem;
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

.replies-toggle-btn {
  padding: 0.25rem 0.5rem;
  background: none;
  border: none;
  cursor: pointer;
  color: var(--text-secondary, rgba(44, 62, 80, 0.6));
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s, transform 0.2s;
}

.replies-toggle-btn:hover {
  color: var(--accent-color, #4a90e2);
}

.replies-toggle-btn .toggle-arrow {
  width: 1.25rem;
  height: 1.25rem;
  transition: transform 0.2s;
}

.replies-toggle-btn.expanded .toggle-arrow {
  transform: rotate(180deg);
}

.message-replies-list {
  margin-left: 1rem;
  padding-left: 1rem;
  border-left: 3px solid rgba(74, 144, 226, 0.3);
  background: rgba(255, 255, 255, 0.7);
  border-radius: 8px;
}

.reply-card-label {
  font-size: 0.85rem;
  color: var(--text-secondary, rgba(44, 62, 80, 0.7));
  margin-bottom: 0.5rem;
}

.message-reply-item {
  margin-top: 0.5rem;
  padding: 0.75rem 1rem !important;
  font-size: 0.95em;
}

.message-item {
  padding: 1.25rem;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 12px;
  border: 1px solid rgba(0, 0, 0, 0.1);
  transition: all 0.2s ease;
  cursor: pointer;
}

.message-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.message-item.unread {
  border-left: 4px solid var(--accent-color, #4a90e2);
  background: rgba(74, 144, 226, 0.05);
}

.message-item.read {
  border-left: 4px solid rgba(0, 0, 0, 0.1);
}

.message-item.replied {
  border-left: 4px solid rgba(46, 204, 113, 0.6);
  background: rgba(46, 204, 113, 0.05);
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.message-sender {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-primary, #2c3e50);
}

.message-state {
  display: flex;
  align-items: center;
}

.state-badge {
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
  font-size: 0.8rem;
  font-weight: 600;
}

.state-unread {
  background: rgba(74, 144, 226, 0.15);
  color: var(--accent-color, #4a90e2);
}

.state-read {
  background: rgba(0, 0, 0, 0.05);
  color: var(--text-secondary, rgba(44, 62, 80, 0.7));
}

.state-replied {
  background: rgba(46, 204, 113, 0.15);
  color: #2ecc71;
}

.message-content {
  font-size: 1rem;
  line-height: 1.6;
  color: var(--text-primary, #2c3e50);
  margin-bottom: 0.5rem;
  word-break: break-word;
  /* 限制显示3行，超出部分用省略号 */
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

.message-reply {
  margin-top: 0.5rem;
  padding-top: 0.5rem;
  border-top: 1px solid rgba(0, 0, 0, 0.05);
}

.reply-label {
  font-size: 0.85rem;
  color: var(--text-secondary, rgba(44, 62, 80, 0.6));
}

.message-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid rgba(0, 0, 0, 0.05);
}

.action-btn {
  padding: 0.4rem 0.9rem;
  border: 1px solid rgba(0, 0, 0, 0.15);
  background: #fff;
  color: var(--text-primary, #2c3e50);
  border-radius: 8px;
  font-size: 0.85rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.action-btn-read {
  border-color: var(--accent-color, #4a90e2);
  color: var(--accent-color, #4a90e2);
}

.action-btn-read:hover {
  background: rgba(74, 144, 226, 0.1);
}

.action-btn-delete {
  border-color: rgba(231, 76, 60, 0.5);
  color: #e74c3c;
}

.action-btn-delete:hover {
  background: rgba(231, 76, 60, 0.1);
}

.empty-state {
  text-align: center;
  padding: 3rem 2rem;
  color: var(--text-secondary, rgba(44, 62, 80, 0.7));
  font-size: 1rem;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  margin-top: 2rem;
  padding-top: 2rem;
  border-top: 1px solid rgba(0, 0, 0, 0.1);
}

.page-btn {
  padding: 0.55rem 0.9rem;
  border: none;
  background: var(--accent-color, #4a90e2);
  color: #fff;
  border-radius: 10px;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 4px 14px rgba(74, 144, 226, 0.25);
}

.page-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(74, 144, 226, 0.35);
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none;
}

.page-info {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--text-secondary, rgba(44, 62, 80, 0.7));
}

/* 投递消息弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  padding: 1rem;
}

.modal-box {
  width: 100%;
  max-width: 500px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 12px 44px rgba(0, 0, 0, 0.35);
  overflow: hidden;
}

.send-message-modal {
  padding: 0;
}

.error-alert-modal {
  padding: 0;
  max-width: 500px;
  width: 90%;
}

.error-alert-content {
  padding: 1.5rem;
}

.error-alert-message {
  margin: 0;
  font-size: 1rem;
  line-height: 1.6;
  color: var(--text-primary, #2c3e50);
  white-space: pre-line;
}

.error-alert-actions {
  display: flex;
  justify-content: center;
  gap: 0.75rem;
  padding: 0 1.5rem 1.5rem 1.5rem;
}

.btn-confirm {
  padding: 0.6rem 2rem;
  border: none;
  background: var(--accent-color, #4a90e2);
  color: #fff;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-confirm:hover {
  background: #3a7bc8;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(74, 144, 226, 0.3);
}

.btn-confirm:active {
  transform: translateY(0);
}

/* 消息详情弹窗样式 */
.message-detail-modal {
  padding: 0;
  max-width: 600px;
  width: 90%;
}

.message-detail-content {
  padding: 1.5rem;
}

.detail-field {
  margin-bottom: 1.5rem;
}

.detail-field:last-child {
  margin-bottom: 0;
}

.detail-field label {
  display: block;
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-secondary, rgba(44, 62, 80, 0.7));
}

.detail-value {
  font-size: 1rem;
  color: var(--text-primary, #2c3e50);
  word-break: break-word;
}

.message-detail-text {
  line-height: 1.6;
  padding: 0.75rem;
  background: rgba(0, 0, 0, 0.02);
  border-radius: 8px;
  min-height: 60px;
}

.message-detail-actions {
  padding: 0 1.5rem 1.5rem 1.5rem;
  display: flex;
  justify-content: flex-end;
}

.btn-delete-detail {
  padding: 0.6rem 1.25rem;
  border: 1px solid rgba(231, 76, 60, 0.5);
  background: #fff;
  color: #e74c3c;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-delete-detail:hover {
  background: rgba(231, 76, 60, 0.1);
  border-color: #e74c3c;
}

/* 确认删除弹窗样式 */
.delete-confirm-modal {
  padding: 0;
  max-width: 500px;
  width: 90%;
}

.delete-confirm-content {
  padding: 1.5rem;
}

.delete-confirm-message {
  margin: 0;
  font-size: 1rem;
  line-height: 1.6;
  color: var(--text-primary, #2c3e50);
}

.delete-confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding: 0 1.5rem 1.5rem 1.5rem;
}

.btn-confirm-delete {
  padding: 0.6rem 1.25rem;
  border: none;
  background: #e74c3c;
  color: #fff;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-confirm-delete:hover {
  background: #c0392b;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(231, 76, 60, 0.3);
}

.btn-confirm-delete:active {
  transform: translateY(0);
}

.btn-cancel-delete {
  padding: 0.6rem 1.25rem;
  border: 1px solid rgba(0, 0, 0, 0.15);
  background: #fff;
  color: var(--text-primary, #2c3e50);
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-cancel-delete:hover {
  background: rgba(0, 0, 0, 0.05);
  border-color: rgba(0, 0, 0, 0.25);
}

/* 树洞主人操作按钮样式 */
.message-detail-actions-owner {
  padding: 0 1.5rem 1.5rem 1.5rem;
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.btn-action-owner {
  padding: 0.6rem 1rem;
  border: 1px solid rgba(0, 0, 0, 0.15);
  background: #fff;
  color: var(--text-primary, #2c3e50);
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  flex: 1;
  min-width: 80px;
}


.btn-action-owner:hover:not(:disabled) {
  background: rgba(0, 0, 0, 0.05);
  border-color: rgba(0, 0, 0, 0.25);
}

.btn-action-owner:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-read {
  border-color: var(--accent-color, #4a90e2);
  color: var(--accent-color, #4a90e2);
}

.btn-read:hover:not(:disabled) {
  background: rgba(74, 144, 226, 0.1);
}

.btn-read-disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-reply {
  border-color: #f39c12;
  color: #f39c12;
}

.btn-reply:hover:not(:disabled) {
  background: rgba(243, 156, 18, 0.1);
}

.btn-delete-owner {
  border-color: rgba(231, 76, 60, 0.5);
  color: #e74c3c;
}

.btn-delete-owner:hover:not(:disabled) {
  background: rgba(231, 76, 60, 0.1);
  border-color: #e74c3c;
}

.btn-block {
  border-color: #555;
  color: #555;
}

.btn-block:hover:not(:disabled) {
  background: rgba(85, 85, 85, 0.1);
  border-color: #333;
  color: #333;
}

/* 回复弹窗样式 */
.reply-modal {
  padding: 0;
  max-width: 600px;
  width: 90%;
}

.reply-form {
  padding: 1.5rem;
}

/* 确认拉黑弹窗样式 */
.block-confirm-modal {
  padding: 0;
  max-width: 500px;
  width: 90%;
}

/* 关闭树洞确认弹窗样式 */
.close-treehole-confirm-modal {
  padding: 0;
  max-width: 500px;
  width: 90%;
}

.close-treehole-confirm-content {
  padding: 1.5rem;
}

.close-treehole-confirm-message {
  font-size: 1rem;
  line-height: 1.6;
  color: var(--text-primary, #2c3e50);
  text-align: center;
  margin: 0;
}

.close-treehole-confirm-actions {
  display: flex;
  justify-content: center;
  gap: 1rem;
  padding: 0 1.5rem 1.5rem;
}

.btn-confirm-close-treehole {
  padding: 0.6rem 1.5rem;
  background: var(--accent-color, #4a90e2);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-confirm-close-treehole:hover {
  background: #3a7bc8;
  transform: translateY(-1px);
}

.btn-cancel-close-treehole {
  padding: 0.6rem 1.5rem;
  background: #f0f0f0;
  color: var(--text-primary, #2c3e50);
  border: none;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-cancel-close-treehole:hover {
  background: #e0e0e0;
}

.block-confirm-content {
  padding: 1.5rem;
}

.block-confirm-message {
  margin: 0;
  font-size: 1rem;
  line-height: 1.6;
  color: var(--text-primary, #2c3e50);
}

.block-reason-wrap {
  margin-top: 1rem;
}

.block-reason-input {
  width: 100%;
  padding: 0.6rem 0.75rem;
  font-size: 0.95rem;
  border: 1px solid rgba(0, 0, 0, 0.15);
  border-radius: 8px;
  color: var(--text-primary, #2c3e50);
  background: #fff;
  box-sizing: border-box;
}

.block-reason-input::placeholder {
  color: #999;
}

.block-reason-input:focus {
  outline: none;
  border-color: #8e44ad;
  box-shadow: 0 0 0 2px rgba(142, 68, 173, 0.15);
}

.block-confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding: 0 1.5rem 1.5rem 1.5rem;
}

.btn-confirm-block {
  padding: 0.6rem 1.25rem;
  border: none;
  background: #8e44ad;
  color: #fff;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-confirm-block:hover {
  background: #7d3c98;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(142, 68, 173, 0.3);
}

.btn-confirm-block:active {
  transform: translateY(0);
}

.btn-cancel-block {
  padding: 0.6rem 1.25rem;
  border: 1px solid rgba(0, 0, 0, 0.15);
  background: #fff;
  color: var(--text-primary, #2c3e50);
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-cancel-block:hover {
  background: rgba(0, 0, 0, 0.05);
  border-color: rgba(0, 0, 0, 0.25);
}


.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
}

.modal-header h3 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--text-primary, #2c3e50);
}

.modal-close {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: var(--text-secondary, rgba(44, 62, 80, 0.7));
  font-size: 1.5rem;
  line-height: 1;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-close:hover {
  background: rgba(0, 0, 0, 0.05);
  color: var(--text-primary, #2c3e50);
}

.send-message-form {
  padding: 1.5rem;
}

.form-group {
  margin-bottom: 1.25rem;
}

.form-description {
  margin-bottom: 1rem;
  font-size: 0.95rem;
  color: var(--text-secondary, rgba(44, 62, 80, 0.7));
  line-height: 1.6;
}

.send-message-form .form-group label {
  display: block;
  margin-bottom: 0.35rem;
  font-size: 0.9rem;
  color: #555;
}

.send-message-form .form-group label .required {
  color: #e74c3c;
  font-size: 0.8rem;
}

.textarea-wrapper {
  position: relative;
}

.message-textarea {
  width: 100%;
  padding: 0.75rem;
  padding-bottom: 2rem;
  border: 1px solid rgba(0, 0, 0, 0.15);
  border-radius: 8px;
  font-size: 0.95rem;
  font-family: inherit;
  color: var(--text-primary, #2c3e50);
  resize: vertical;
  min-height: 150px;
  transition: border-color 0.2s ease;
}

.message-textarea:focus {
  outline: none;
  border-color: var(--accent-color, #4a90e2);
  box-shadow: 0 0 0 3px rgba(74, 144, 226, 0.1);
}

.char-count {
  position: absolute;
  bottom: 0.5rem;
  right: 0.75rem;
  font-size: 0.85rem;
  color: var(--text-secondary, rgba(44, 62, 80, 0.6));
  pointer-events: none;
}

.form-error {
  margin: 0 0 1rem 0;
  padding: 0.75rem;
  background: rgba(231, 76, 60, 0.1);
  border-radius: 8px;
  color: #e74c3c;
  font-size: 0.9rem;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 1.5rem;
}

.btn-cancel {
  padding: 0.6rem 1.25rem;
  border: 1px solid rgba(0, 0, 0, 0.15);
  background: #fff;
  color: var(--text-primary, #2c3e50);
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-cancel:hover {
  background: rgba(0, 0, 0, 0.05);
  border-color: rgba(0, 0, 0, 0.25);
}

.btn-submit {
  padding: 0.6rem 1.25rem;
  border: none;
  background: var(--accent-color, #4a90e2);
  color: #fff;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 2px 8px rgba(74, 144, 226, 0.3);
}

.btn-submit:hover:not(:disabled) {
  background: #3a7bc8;
  box-shadow: 0 4px 12px rgba(74, 144, 226, 0.4);
}

.btn-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  box-shadow: none;
}

/* 弹窗动画 */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.3s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-active .modal-box,
.modal-leave-active .modal-box {
  transition: transform 0.3s ease, opacity 0.3s ease;
}

.modal-enter-from .modal-box,
.modal-leave-to .modal-box {
  transform: scale(0.95) translateY(-10px);
  opacity: 0;
}
</style>
