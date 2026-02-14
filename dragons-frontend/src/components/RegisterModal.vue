<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay" @click.self="close">
        <div class="modal-box auth-modal">
          <div class="modal-header">
            <h3>注册</h3>
            <button type="button" class="modal-close" aria-label="关闭" @click="close">×</button>
          </div>
          <form class="auth-form" @submit.prevent="handleSubmit">
            <div class="form-group">
              <label for="reg-loginName">登录名 <span class="required">*</span></label>
              <input
                id="reg-loginName"
                v-model="form.loginName"
                type="text"
                placeholder="请输入登录名"
                required
                autocomplete="username"
              />
            </div>
            <div class="form-group">
              <label for="reg-password">密码 <span class="required">*</span></label>
              <div class="input-with-icon">
                <input
                  id="reg-password"
                  v-model="form.password"
                  :type="showPassword ? 'text' : 'password'"
                  placeholder="请输入密码"
                  required
                  autocomplete="new-password"
                />
                <button
                  type="button"
                  class="toggle-password"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                  @click="showPassword = !showPassword"
                >
                  <svg v-if="showPassword" class="icon-eye" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
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
              <label for="reg-nickName">昵称 <span class="required">*</span></label>
              <input
                id="reg-nickName"
                v-model="form.nickName"
                type="text"
                placeholder="请输入昵称"
                required
              />
            </div>
            <div class="form-group">
              <label for="reg-phoneNumber">手机号 <span class="required">*</span></label>
              <input
                id="reg-phoneNumber"
                v-model="form.phoneNumber"
                type="tel"
                placeholder="11位手机号"
                required
                autocomplete="tel"
              />
            </div>
            <p v-if="errorMsg" class="form-error">{{ errorMsg }}</p>
            <button type="submit" class="btn-submit" :disabled="loading">注册</button>
          </form>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  visible: { type: Boolean, default: false }
})

const emit = defineEmits(['update:visible', 'success'])

const userStore = useUserStore()
const form = ref({
  loginName: '',
  password: '',
  nickName: '',
  phoneNumber: ''
})
const errorMsg = ref('')
const loading = ref(false)
const showPassword = ref(false)

watch(() => props.visible, (v) => {
  if (v) {
    form.value = { loginName: '', password: '', nickName: '', phoneNumber: '' }
    errorMsg.value = ''
    showPassword.value = false
  }
})

function close() {
  emit('update:visible', false)
}

async function handleSubmit() {
  errorMsg.value = ''
  const { loginName, password, nickName, phoneNumber } = form.value
  if (!loginName?.trim()) {
    errorMsg.value = '请输入登录名'
    return
  }
  if (!password) {
    errorMsg.value = '请输入密码'
    return
  }
  if (!nickName?.trim()) {
    errorMsg.value = '请输入昵称'
    return
  }
  if (!phoneNumber?.trim()) {
    errorMsg.value = '请输入手机号'
    return
  }
  const phone = phoneNumber.replace(/\D/g, '')
  loading.value = true
  try {
    await userStore.register({
      loginName: loginName.trim(),
      password,
      nickName: nickName.trim(),
      phoneNumber: phone
    })
    close()
    emit('success')
  } catch (e) {
    errorMsg.value = e?.response?.data?.message || e?.message || '注册失败，请重试'
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

.form-group input {
  width: 100%;
  padding: 0.5rem 0.75rem;
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
