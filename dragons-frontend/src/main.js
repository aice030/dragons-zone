import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import './assets/styles/home.css'
import './assets/styles/media-detail.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

// 从 localStorage 恢复登录状态（刷新页面后保持已登录）
import { useUserStore } from '@/stores/user'
const userStore = useUserStore()
userStore.initFromStorage()

app.mount('#app')
