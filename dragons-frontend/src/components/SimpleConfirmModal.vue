<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay" @click.self="close">
        <div class="modal-box confirm-modal">
          <div class="modal-header">
            <h3>{{ title }}</h3>
            <button type="button" class="modal-close" aria-label="关闭" @click="close">×</button>
          </div>
          <div class="modal-body">
            <p class="confirm-desc">{{ message }}</p>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn-cancel" @click="close">取消</button>
            <button type="button" class="btn-confirm" :disabled="loading" @click="handleConfirm">
              {{ confirmText }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { watch } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '确认操作' },
  message: { type: String, default: '确定要执行此操作吗？' },
  confirmText: { type: String, default: '确认' },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:visible', 'confirm', 'cancel'])

watch(() => props.visible, (v) => {
  // 弹窗打开时的初始化逻辑（如有需要）
})

function close() {
  emit('update:visible', false)
  emit('cancel')
}

function handleConfirm() {
  emit('confirm')
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
  margin: 0;
  font-size: 0.95rem;
  color: #555;
  line-height: 1.5;
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
  background: #4a90e2;
  color: #fff;
  border-radius: 8px;
  font-size: 0.95rem;
  cursor: pointer;
}

.btn-confirm:hover:not(:disabled) {
  background: #357abd;
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
