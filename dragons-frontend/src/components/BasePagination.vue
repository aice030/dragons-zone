<template>
  <div class="media-grid-pagination">
    <button
      type="button"
      class="media-grid-page-btn"
      :disabled="isFirstPage || disabled"
      @click="handlePrev"
    >
      上一页
    </button>

    <template v-for="item in pageItems" :key="item.key">
      <button
        v-if="item.type === 'page'"
        type="button"
        class="media-grid-page-btn media-grid-page-number-btn"
        :class="{ 'is-active': item.page === currentPage }"
        :disabled="disabled || item.page === currentPage"
        @click="handlePageClick(item.page)"
      >
        {{ item.page }}
      </button>
      <span
        v-else
        class="media-grid-page-ellipsis"
      >
        …
      </span>
    </template>

    <button
      type="button"
      class="media-grid-page-btn"
      :disabled="isLastPage || disabled"
      @click="handleNext"
    >
      下一页
    </button>

    <span class="media-grid-page-info">
      第 {{ currentPage }} / {{ totalPages }} 页（共 {{ totalItems }} 条）
    </span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  currentPage: {
    type: Number,
    required: true
  },
  totalPages: {
    type: Number,
    required: true
  },
  totalItems: {
    type: Number,
    required: true
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:page'])

const isFirstPage = computed(() => props.currentPage <= 1)
const isLastPage = computed(() => props.currentPage >= props.totalPages)

const pageItems = computed(() => {
  const total = props.totalPages
  const current = props.currentPage
  const items = []

  if (total <= 1) return items

  const pushPage = (page) => {
    items.push({ type: 'page', page, key: `p-${page}` })
  }

  const pushEllipsis = (id) => {
    items.push({ type: 'ellipsis', key: `e-${id}` })
  }

  if (total <= 7) {
    for (let i = 1; i <= total; i++) {
      pushPage(i)
    }
    return items
  }

  pushPage(1)

  let left = current - 1
  let right = current + 1

  if (left <= 2) {
    left = 2
    right = 4
  }

  if (right >= total - 1) {
    right = total - 1
    left = total - 3
  }

  if (left > 2) {
    pushEllipsis('left')
  }

  for (let i = left; i <= right; i++) {
    if (i > 1 && i < total) {
      pushPage(i)
    }
  }

  if (right < total - 1) {
    pushEllipsis('right')
  }

  pushPage(total)

  return items
})

function handlePrev() {
  if (isFirstPage.value || props.disabled) return
  emit('update:page', props.currentPage - 1)
}

function handleNext() {
  if (isLastPage.value || props.disabled) return
  emit('update:page', props.currentPage + 1)
}

function handlePageClick(page) {
  if (props.disabled || page === props.currentPage) return
  emit('update:page', page)
}
</script>

