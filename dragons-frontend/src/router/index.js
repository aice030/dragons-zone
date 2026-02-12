import { createRouter, createWebHistory } from 'vue-router'
import Welcome from '@/views/Welcome.vue'
import MediaBrowse from '@/views/MediaBrowse.vue'
import MediaDetail from '@/views/MediaDetail.vue'
import MyUploads from '@/views/MyUploads.vue'
import UploadMedia from '@/views/UploadMedia.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'Welcome',
      component: Welcome
    },
    {
      path: '/browse',
      name: 'MediaBrowse',
      component: MediaBrowse
    },
    {
      path: '/media/:id',
      name: 'MediaDetail',
      component: MediaDetail
    },
    {
      path: '/my-uploads',
      name: 'MyUploads',
      component: MyUploads,
      beforeEnter: (to, from, next) => {
        // 检查 localStorage 中的 token 来判断登录状态
        const token = localStorage.getItem('token')
        const userInfo = localStorage.getItem('userInfo')
        if (!token || !userInfo) {
          next({ path: '/browse', query: { needLogin: 'true' } })
        } else {
          next()
        }
      }
    },
    {
      path: '/upload',
      name: 'UploadMedia',
      component: UploadMedia,
      beforeEnter: (to, from, next) => {
        // 检查 localStorage 中的 token 来判断登录状态
        const token = localStorage.getItem('token')
        const userInfo = localStorage.getItem('userInfo')
        if (!token || !userInfo) {
          next({ path: '/browse', query: { needLogin: 'true' } })
        } else {
          next()
        }
      }
    }
  ],
})

export default router
