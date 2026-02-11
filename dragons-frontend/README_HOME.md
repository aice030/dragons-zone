# 主页实现说明

## 已完成的功能

1. **主页组件** (`src/views/Home.vue`)
   - 背景图展示（`public/home-background.jpg`）
   - 筛选 Tab（全部/图片/视频）
   - 横向滚动卡片带（桌面端）
   - 网格布局（移动端）

2. **媒体卡片组件** (`src/components/MediaCard.vue`)
   - 封面图展示
   - 标题显示
   - 类型标识（图片/视频）
   - 点击跳转详情页

3. **横向滚动带组件** (`src/components/MediaStrip.vue`)
   - 横向滚动效果（桌面端）
   - 网格布局（移动端）
   - 自动加载更多

4. **API 服务** (`src/api/media.js`)
   - 媒体列表接口
   - 媒体详情接口
   - 下载/预览 URL 接口
   - JWT Token 自动添加

5. **样式文件** (`src/assets/styles/home.css`)
   - 参考 html5up-parallelism 的核心样式
   - 调整为更青春阳光的配色方案
   - 响应式设计

## 配置说明

### 1. API 基础 URL 配置

创建 `.env` 文件（参考 `.env.example`）：

```env
VITE_API_BASE_URL=http://localhost:8080
```

根据实际后端地址修改。

### 2. 背景图

背景图已移动到 `public/home-background.jpg`，可以直接通过 `/home-background.jpg` 访问。

## 使用说明

1. **启动开发服务器**：
   ```bash
   npm run dev
   ```

2. **访问主页**：
   打开浏览器访问 `http://localhost:5173/`

3. **功能说明**：
   - 默认显示混合列表（图片+视频）
   - 点击 Tab 可以筛选图片或视频
   - 桌面端：横向滚动浏览
   - 移动端：网格布局，上下滚动

## 后续开发

- [ ] 媒体详情页
- [ ] 登录/注册功能
- [ ] 上传功能
- [ ] 树洞功能
