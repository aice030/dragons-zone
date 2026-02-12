<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay" @click.self="close">
        <div class="modal-box confirm-modal">
          <div class="modal-header">
            <h3>确认注销？</h3>
            <button type="button" class="modal-close" aria-label="关闭" @click="close">×</button>
          </div>
          <div class="modal-body">
            <p class="confirm-desc">您上传的图片、视频以及树洞消息仍会保留。</p>
            <div class="form-group">
              <label for="deregister-password">请输入当前密码以确认</label>
              <input
                id="deregister-password"
                v-model="password"
                type="password"
                placeholder="当前密码"
                autocomplete="current-password"
              />
            </div>
            <p v-if="errorMsg" class="form-error">{{ errorMsg }}</p>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn-cancel" @click="close">取消</button>
            <button type="button" class="btn-confirm" :disabled="loading" @click="handleConfirm">确认注销</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { deregister } from '@/api/user'

const props = defineProps({
  visible: { type: Boolean, default: false }
})

const emit = defineEmits(['update:visible', 'success'])

const userStore = useUserStore()
const password = ref('')
const errorMsg = ref('')
const loading = ref(false)

watch(() => props.visible, (v) => {
  if (v) {
    password.value = ''
    errorMsg.value = ''
  }
})

function close() {
  emit('update:visible', false)
}

async function handleConfirm() {
  errorMsg.value = ''
  if (!password.value) {
    errorMsg.value = '请输入当前密码'
    return
  }
  loading.value = true
  try {
    const res = await deregister(password.value)
    if (res && res.code !== 200) {
      errorMsg.value = res.message || '注销失败，请重试'
      return
    }
    userStore.logout()
    close()
    emit('success')
  } catch (e) {
    errorMsg.value = e?.response?.data?.message || e?.message || '注销失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}

.modal-box {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  min-width: 320px;
  max-width: 90vw;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #eee;
}

.modal-header h3 {
  margin: 0;
  font-size: 1.1rem;
  color: #2c3e50;
}

.modal-close {
  background: none;
  border: none;
  font-size: 1.5rem;
  line-height: 1;
  color: #999;
  cursor: pointer;
  padding: 0 0.25rem;
}

.modal-close:hover {
  color: #333;
}

.modal-body {
  padding: 1.25rem;
}

.confirm-desc {
  margin: 0 0 1rem;
  font-size: 0.95rem;
  color: #555;
  line-height: 1.5;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.35rem;
  font-size: 0.9rem;
  color: #555;
}

.form-group input {
  width: 100%;
  padding: 0.5rem 0.75rem;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 1rem;
  box-sizing: border-box;
}

.form-group input:focus {
  outline: none;
  border-color: #4a90e2;
}

.form-error {
  color: #e74c3c;
  font-size: 0.9rem;
  margin: 0 0 0.75rem;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding: 1rem 1.25rem;
  border-top: 1px solid #eee;
}

.btn-cancel {
  padding: 0.5rem 1.25rem;
  border: 1px solid #ddd;
  background: #fff;
  color: #555;
  border-radius: 8px;
  font-size: 0.95rem;
  cursor: pointer;
}

.btn-cancel:hover {
  background: #f5f5f5;
}

.btn-confirm {
  padding: 0.5rem 1.25rem;
  border: none;
  background: #e74c3c;
  color: #fff;
  border-radius: 8px;
  font-size: 0.95rem;
  cursor: pointer;
}

.btn-confirm:hover:not(:disabled) {
  background: #c0392b;
}

.btn-confirm:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-active .modal-box,
.modal-leave-active .modal-box {
  transition: transform 0.2s ease;
}

.modal-enter-from .modal-box,
.modal-leave-to .modal-box {
  transform: scale(0.95);
}
</style>
