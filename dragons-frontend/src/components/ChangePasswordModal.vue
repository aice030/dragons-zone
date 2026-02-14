<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay" @click.self="close">
        <div class="modal-box auth-modal">
          <div class="modal-header">
            <h3>修改密码</h3>
            <button type="button" class="modal-close" aria-label="关闭" @click="close">×</button>
          </div>
          <form class="auth-form" @submit.prevent="handleSubmit">
            <p class="form-hint">请输入账号绑定的手机号和新密码</p>
            <div class="form-group">
              <label for="cp-phone">手机号 <span class="required">*</span></label>
              <input
                id="cp-phone"
                v-model="form.phoneNumber"
                type="tel"
                placeholder="手机号"
                required
                autocomplete="tel"
              />
            </div>
            <div class="form-group">
              <label for="cp-new">新密码 <span class="required">*</span></label>
              <div class="input-with-icon">
                <input
                  id="cp-new"
                  v-model="form.newPassword"
                  :type="showNew ? 'text' : 'password'"
                  placeholder="请输入新密码"
                  required
                  autocomplete="new-password"
                />
                <button
                  type="button"
                  class="toggle-password"
                  :aria-label="showNew ? '隐藏密码' : '显示密码'"
                  @click="showNew = !showNew"
                >
                  <svg v-if="showNew" class="icon-eye" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                    <circle cx="12" cy="12" r="3"/>
                  </svg>
                  <svg v-else class="icon-eye" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                    <line x1="1" y1="1" x2="23" y2="23" stroke-linecap="round"/>
                  </svg>
                </button>
              </div>
            </div>
            <div class="form-group">
              <label for="cp-confirm">确认新密码 <span class="required">*</span></label>
              <div class="input-with-icon">
                <input
                  id="cp-confirm"
                  v-model="form.confirmPassword"
                  :type="showConfirm ? 'text' : 'password'"
                  placeholder="请再次输入新密码"
                  required
                  autocomplete="new-password"
                />
                <button
                  type="button"
                  class="toggle-password"
                  :aria-label="showConfirm ? '隐藏密码' : '显示密码'"
                  @click="showConfirm = !showConfirm"
                >
                  <svg v-if="showConfirm" class="icon-eye" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                    <circle cx="12" cy="12" r="3"/>
                  </svg>
                  <svg v-else class="icon-eye" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                    <line x1="1" y1="1" x2="23" y2="23" stroke-linecap="round"/>
                  </svg>
                </button>
              </div>
            </div>
            <p v-if="passwordMismatch" class="form-error">两次输入的新密码不一致</p>
            <p v-else-if="errorMsg" class="form-error">{{ errorMsg }}</p>
            <p v-if="successMsg" class="form-success">{{ successMsg }}</p>
            <button type="submit" class="btn-submit" :disabled="loading || passwordMismatch">确认修改</button>
          </form>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { resetPasswordByPhone } from '@/api/user'

const props = defineProps({
  visible: { type: Boolean, default: false }
})

const emit = defineEmits(['update:visible', 'success'])

const form = ref({
  phoneNumber: '',
  newPassword: '',
  confirmPassword: ''
})
const errorMsg = ref('')
const successMsg = ref('')
const loading = ref(false)
const showNew = ref(false)
const showConfirm = ref(false)

// 新密码与确认密码一致性校验
const passwordMismatch = computed(() => {
  const { newPassword, confirmPassword } = form.value
  return newPassword && confirmPassword && newPassword !== confirmPassword
})

watch(() => props.visible, (v) => {
  if (v) {
    form.value = { phoneNumber: '', newPassword: '', confirmPassword: '' }
    errorMsg.value = ''
    successMsg.value = ''
    showNew.value = false
    showConfirm.value = false
  }
})

function close() {
  emit('update:visible', false)
}

async function handleSubmit() {
  errorMsg.value = ''
  successMsg.value = ''
  if (!form.value.phoneNumber?.trim()) {
    errorMsg.value = '请输入手机号'
    return
  }
  if (!form.value.newPassword) {
    errorMsg.value = '请输入新密码'
    return
  }
  if (!form.value.confirmPassword) {
    errorMsg.value = '请再次输入新密码'
    return
  }
  if (form.value.newPassword !== form.value.confirmPassword) {
    errorMsg.value = '两次输入的新密码不一致'
    return
  }
  const phone = form.value.phoneNumber.replace(/\D/g, '')
  loading.value = true
  try {
    const res = await resetPasswordByPhone(phone, form.value.newPassword)
    if (res && res.code !== 200) {
      errorMsg.value = res.message || '修改失败，请重试'
      return
    }
    successMsg.value = '密码已修改成功，请使用新密码登录。'
    form.value = { phoneNumber: '', newPassword: '', confirmPassword: '' }
    emit('success')
    setTimeout(() => close(), 1500)
  } catch (e) {
    errorMsg.value = e?.response?.data?.message || e?.message || '修改失败，请重试'
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

.auth-form {
  padding: 1.25rem;
}

.form-hint {
  font-size: 0.85rem;
  color: #666;
  margin: 0 0 1rem;
  line-height: 1.4;
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

.form-group label .required {
  color: #e74c3c;
  font-size: 0.8rem;
}

.form-group input {
  width: 100%;
  padding: 0.5rem 0.75rem;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 1rem;
  box-sizing: border-box;
}

.input-with-icon {
  position: relative;
  display: flex;
  align-items: center;
}

.input-with-icon input {
  width: 100%;
  padding: 0.5rem 2.5rem 0.5rem 0.75rem;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 1rem;
  box-sizing: border-box;
}

.input-with-icon input:focus,
.form-group input:focus {
  outline: none;
  border-color: #4a90e2;
}

.toggle-password {
  position: absolute;
  right: 0.5rem;
  top: 50%;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  background: none;
  color: #888;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
}

.toggle-password:hover {
  color: #555;
  background: rgba(0, 0, 0, 0.05);
}

.icon-eye {
  width: 18px;
  height: 18px;
}

.form-error {
  color: #e74c3c;
  font-size: 0.9rem;
  margin: 0 0 0.75rem;
}

.form-success {
  color: #27ae60;
  font-size: 0.9rem;
  margin: 0 0 0.75rem;
}

.btn-submit {
  width: 100%;
  padding: 0.6rem;
  background: #4a90e2;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
}

.btn-submit:hover:not(:disabled) {
  background: #3a7bc8;
}

.btn-submit:disabled {
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
