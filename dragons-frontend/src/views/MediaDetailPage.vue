<template>
  <MediaDetail
    v-if="mediaId"
    :media-id="mediaId"
    :media-list="[]"
    @close="handleClose"
  />
  <div v-else class="media-detail-page-invalid">
    <p>媒体不存在或链接无效</p>
    <router-link to="/browse" class="back-link">返回浏览</router-link>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MediaDetail from '@/views/MediaDetail.vue'

const route = useRoute()
const router = useRouter()

const mediaId = computed(() => {
  const id = route.params.id
  if (id == null || id === '') return null
  const n = Number(id)
  return Number.isNaN(n) ? null : n
})

function handleClose() {
  router.back()
}
</script>

<style scoped>
.media-detail-page-invalid {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding: 2rem;
}
.media-detail-page-invalid p {
  color: #7f8c8d;
}
.back-link {
  color: #4a90e2;
  text-decoration: none;
}
.back-link:hover {
  text-decoration: underline;
}
</style>
