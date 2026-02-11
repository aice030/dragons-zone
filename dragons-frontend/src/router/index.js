import { createRouter, createWebHistory } from 'vue-router'
import Home from '@/views/Home.vue'
import MediaDetail from '@/views/MediaDetail.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'Home',
      component: Home
    },
    {
      path: '/media/:id',
      name: 'MediaDetail',
      component: MediaDetail
    }
  ],
})

export default router
