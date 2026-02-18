<template>
  <div class="welcome-page">
    <div class="welcome-backdrop"></div>
    <div class="welcome-content">
      <h1 class="welcome-title">Dragons Zone</h1>
      <h1 class="welcome-desc""> </h1>
      <p class="welcome-desc">热烈欢迎所有喜欢发癫吧后浪的拽根！</p>
      <p class="welcome-desc">这里有后浪们的图片/视频集合，同时也希望有资源的拽根们可以登录后分享更多后浪的内容哟</p>
      <router-link to="/browse" class="welcome-btn">进入</router-link>
    </div>
  </div>
</template>

<script setup>
</script>

<style scoped>
.welcome-page {
  min-height: 100vh;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.welcome-backdrop {
  position: absolute;
  inset: 0;
  z-index: 0;
  background-image: url('/images/home-background.jpg');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

.welcome-backdrop::after {
  content: '';
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
}

.welcome-content {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 2rem;
}

.welcome-title {
  margin: 0 0 0.5rem;
  font-size: clamp(2rem, 6vw, 3.5rem);
  font-weight: 700;
  color: #fff;
  text-shadow: 0 2px 20px rgba(0, 0, 0, 0.4);
  letter-spacing: 0.05em;
}

.welcome-desc {
  margin: 0 0 2rem;
  font-size: 1.1rem;
  color: rgba(255, 255, 255, 0.9);
  text-shadow: 0 1px 8px rgba(0, 0, 0, 0.3);
}

.welcome-btn {
  display: inline-block;
  padding: 0.75rem 2.5rem;
  background: rgba(255, 255, 255, 0.95);
  color: #2c3e50;
  font-size: 1.1rem;
  font-weight: 600;
  text-decoration: none;
  border-radius: 28px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.welcome-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.25);
}
</style>
