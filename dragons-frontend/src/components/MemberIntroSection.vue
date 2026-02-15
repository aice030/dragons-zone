<template>
  <section class="member-intro-section" aria-label="成员简介">
    <article class="member-intro-container">
      <!-- 左侧：文字信息 -->
      <div class="member-intro-left">
        <h1 class="member-name">{{ memberInfo.name }}</h1>
        <div v-if="introLines.length" class="member-intro-lines">
          <p v-for="(line, i) in introLines" :key="i" class="member-intro-line">
            <span class="intro-title">{{ line.title }}</span>：<span class="intro-content">{{ line.content }}</span>
          </p>
        </div>
        <div v-if="memberInfo.intro" class="member-intro-block">
          <p class="intro-title">简介</p>
          <p class="intro-block-content">{{ memberInfo.intro }}</p>
        </div>
      </div>

      <!-- 右侧：成员照片 -->
      <div class="member-intro-right">
        <div v-if="memberInfo.avatar" class="member-avatar-wrapper">
          <img 
            :src="memberInfo.avatar" 
            :alt="`${memberInfo.name}${memberInfo.fullName || memberInfo.waihao ? ` (${memberInfo.fullName || memberInfo.waihao})` : ''}的照片`" 
            class="member-avatar"
            loading="lazy"
          />
        </div>
        <div v-else class="member-avatar-placeholder">
          <span>暂无照片</span>
        </div>
      </div>
    </article>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  memberInfo: {
    type: Object,
    required: true
  }
})

// 每行一个字段：{ title, content }
const introLines = computed(() => {
  const m = props.memberInfo
  const lines = []
  if (m.fullName) lines.push({ title: '姓名', content: m.fullName })
  if (m.waihao) lines.push({ title: '外号', content: m.waihao })
  if (m.specialties) lines.push({ title: '特长', content: m.specialties })
  if (m.hobbies) lines.push({ title: '爱好', content: m.hobbies })
  if (m.dream) lines.push({ title: '梦想', content: m.dream })
  return lines
})
</script>

<style scoped>
.member-intro-section {
  padding: 3rem 2rem;
  position: relative;
  z-index: 1;
}

.member-intro-container {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  gap: 3rem;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  padding: 2.5rem;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.member-intro-left {
  flex: 1;
  min-width: 0;
}

.member-name {
  margin: 0 0 0.5rem 0;
  font-size: 2rem;
  font-weight: 700;
  color: var(--text-primary, #2c3e50);
}

.member-intro-lines {
  margin: 0;
}

.member-intro-line {
  margin: 0 0 0.25em 0;
  font-size: 0.95rem;
  line-height: 1.8;
}

.intro-title {
  font-weight: 600;
  color: var(--text-primary, #2c3e50);
}

.intro-content {
  color: var(--text-primary, #2c3e50);
}

.member-intro-line:last-child {
  margin-bottom: 0;
}

.member-intro-block {
  margin-top: 1.5rem;
  max-width: 100%;
}

.member-intro-block .intro-title {
  margin: 0 0 0.5rem 0;
}

.intro-block-content {
  margin: 0;
  font-size: 0.95rem;
  line-height: 1.8;
  color: var(--text-primary, #2c3e50);
  max-width: 100%;
  word-wrap: break-word;
}

.member-intro-right {
  flex: 0 0 300px;
  display: flex;
  align-items: flex-start;
  justify-content: center;
}

.member-avatar-wrapper {
  width: 100%;
  aspect-ratio: 3 / 4;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
}

.member-avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.member-avatar-placeholder {
  width: 100%;
  aspect-ratio: 3 / 4;
  border-radius: 12px;
  background: rgba(74, 144, 226, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary, rgba(44, 62, 80, 0.5));
  font-size: 1rem;
  border: 2px dashed rgba(74, 144, 226, 0.3);
}
</style>
