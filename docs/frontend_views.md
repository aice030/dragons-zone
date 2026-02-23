# dragons-frontend 速查（核心目录 & 界面说明）

## 核心目录（`dragons-frontend/src`）

- **`api/`**：后端接口封装（axios 实例 + 媒体/用户 API），请求自动带 `token`，401 时清理本地登录信息。
- **`assets/styles/`**：全局/页面级样式（如媒体浏览、媒体详情），在 `main.js` 中统一引入。
- **`components/`**：可复用组件与业务弹窗（统一导航栏组件 `NavBar`、登录/注册/找回/改密/注销、媒体卡片/条带、详情弹窗等）。
- **`config/`**：前端静态配置（成员专区列表 `members`、联系方式 `contact`、用户菜单 `userMenu`）。
- **`router/`**：路由定义与路由守卫（如 `my-uploads`、`upload` 的登录校验；`resource-manage` 的登录+角色校验）。
- **`stores/`**：Pinia 状态管理（用户登录态、`token/userInfo` 持久化与恢复）。
- **`views/`**：页面级组件（与路由一一对应的界面；如 `MediaDetailPage.vue` 为 `/media/:id` 的包装页，将路由参数传入 `MediaDetail.vue`）。
- **`main.js`**：应用入口（创建 app、注册 Pinia/Router、从 localStorage 恢复登录态、引入全局样式）。
- **`App.vue`**：根组件（承载 `router-view` + 全局样式 reset）。

## 界面一览（按路由）

| 路由 | 页面文件 | 一句话描述 |
| --- | --- | --- |
| `/` | `src/views/Welcome.vue` | 欢迎入口页，展示背景与“进入”按钮跳转到浏览页。 |
| `/browse` | `src/views/MediaBrowse.vue` | 主浏览页：切换成员专区与类型筛选，滚动加载媒体，并提供登录/注册与用户菜单。 |
| `/media/:id` | `src/views/MediaDetailPage.vue` → `MediaDetail.vue` | 媒体详情页：由 MediaDetailPage 从路由取 id 传入 MediaDetail，支持点赞、下载与上一/下一切换；关闭时 `router.back()`。 |
| `/my-uploads` | `src/views/MyUploads.vue` | 我的上传页：按类型筛选查看个人上传列表/状态，并可查看详情或继续上传。 |
| `/upload` | `src/views/UploadMedia.vue` | 上传页：图片支持批量/文件夹队列上传（每张图片的封面即该图片本身）；视频支持单文件上传且封面必填。主文件由前端直连 OSS（小文件预签名 PUT，大文件分片上传），详见下方「上传页与 OSS 直传」。 |
| `/resource-manage` | `src/views/ResourceManage.vue` | 资源管理页（仅作者/管理员）：分“待审核列表”和“已上传列表”，支持审核通过/驳回与查看详情。 |

## 上传页与 OSS 直传

### 流程概览

1. **准备上传**：调用 `prepareUpload()`（`POST /api/media/upload`），传 `file_hash`、`category`、`title`、`description`、`filename`；不传主文件与封面。后端落库 Media（state=1）并返回 `mediaId`、`storagePath`、`uploadUrl`（预签名 PUT）、`uploadUrlExpireSeconds`，以及可选 `stsCredentials`（STS 临时凭证，后端配置了 oss.sts 时返回）。
2. **直传 OSS**：前端根据文件大小与是否拿到 `stsCredentials` 二选一：
   - **小文件或未配置 STS**：用 `uploadUrl` 做一次 **PUT** 请求，body 为文件内容（`src/utils/ossUpload.js` 的 `uploadFileToOss`）。
   - **大文件（≥5MB）且存在 `stsCredentials`**：用 `createOssClient(credentials)` 创建 OSS 客户端，调用 **分片上传** `multipartUpload(storagePath, file, { parallel, partSize, progress })`（`uploadFileToOssMultipart`）；进度通过 `onProgress(0～1)` 回传，前端展示百分比与进度条。
3. **通知结果**：直传成功后调用 `uploadComplete()`（`POST /api/media/upload/complete`），传 `mediaId`、`success: true`、`visibleUserIds`、`cover`；失败时在 catch 中调用 `uploadComplete(..., success: false)`，避免列表一直显示「正在上传」。

### 前端职责与文件

- **接口**：`src/api/media.js` — `prepareUpload`、`uploadComplete`；无单独 STS 接口，凭证仅来自准备上传响应。
- **OSS 客户端**：`src/utils/ossClient.js` — `createOssClient(credentials)`，参数为准备上传返回的 `stsCredentials`；内部对 `region` 做规范化（去掉 `.aliyuncs.com` 后缀以符合 ali-oss 要求）。
- **上传实现**：`src/utils/ossUpload.js` — `uploadFileToOss(uploadUrl, file)`（PUT）；`uploadFileToOssMultipart(credentials, storagePath, file, { onProgress })`（分片，可选进度回调）。
- **页面逻辑**：`src/views/UploadMedia.vue` — 单文件（视频）与批量（图片）均先准备上传，再按阈值（5MB）与 `stsCredentials` 选择 PUT 或分片；分片时传入 `onProgress`，单文件用 `uploadProgress`（0～100）展示进度条与文案，批量项用 `item.progress` 在状态列展示「上传中 xx%」与进度条。上传失败时若有 `mediaId` 则调用 `uploadComplete(mediaId, success: false)`。

### 我的上传页与列表刷新

- **`src/views/MyUploads.vue`**：上传入口与上传列表用 **`v-show`** 切换（非 `v-if`），保证切到「上传列表」后 `UploadMedia` 仍挂载，后台分片上传完成后能正常 `emit('upload-success')`。
- **上传成功**：父组件监听 `@upload-success`，执行 `handleUploadSuccess()` → `switchPanel('list', true)` 再 `await loadMyUploads(true)`，列表拉取最新数据，无需手动刷新。

## 新增：资源管理（Resource Manage）

### 入口与可见性

- **入口位置**：导航栏昵称下拉菜单
  - 配置文件：`src/config/userMenu.js`
  - 菜单项：`资源管理`（`/resource-manage`）
- **显示规则**：仅当 `userInfo.level in (0, 1)`（作者/管理员）时显示

### 路由链路与权限守卫

- **路由**：`/resource-manage`
- **路由定义**：`src/router/index.js`
- **进入条件**：
  - 必须已登录（localStorage 中存在 `token` + `userInfo`）
  - 且 `userInfo.level === 0 || userInfo.level === 1`
  - 否则重定向到 `/browse`（带 query：`needLogin` 或 `noPermission`）

### 页面结构（两个列表 Tab）

- **待审核列表**（仅作者/管理员）
  - 调用：`GET /api/media/audit/pending`
  - 操作：单条 **通过** / **驳回**
    - `POST /api/media/audit/approve`
    - `POST /api/media/audit/reject`
- **已上传列表**（公共区已审核通过内容，state=0）
  - 调用：`GET /api/mediaVisible/list`（`currentUserId=0`）
  - 支持按类型筛选：全部 / 图片 / 视频
  - 支持打开 `MediaDetailModal` 查看详情

### 相关前端 API 封装

- 文件：`src/api/media.js`
  - `getAuditPendingList(page, size)`
  - `auditApprove(mediaIds)`
  - `auditReject(mediaIds)`

## 成员专区页面（MemberZonePage）

- **路由**：`/member/:memberId`
- **路由定义**：`src/router/index.js`
- **页面结构**（从上到下）：
  1. **导航栏**（`NavBar` 组件）
  2. **简介区**（`MemberIntroSection`）
     - 数据来源：前端配置文件 `src/config/members.js`
     - 布局：左侧文字描述（姓名、昵称、介绍、特长、名场面），右侧成员照片
     - 组件：`src/components/MemberIntroSection.vue`
  3. **图片/视频区**（`MemberMediaSection`）
     - 功能：展示带该成员标签的图片/视频
     - 显示模式：条带模式（两行横向滚动）或网格模式（2行×4列）
     - 筛选：全部/图片/视频切换
     - 组件：`src/components/MemberMediaSection.vue`
     - API调用：`GET /api/mediaVisible/list`（传入 `currentUserId=memberId` 参数）
  4. **树洞区**（`MemberTreeHoleSection`）
     - 功能：展示树洞消息列表；树洞主人可管理树洞状态（关闭/开放）、拉黑/解除拉黑留言发送者
     - 显示条件：已登录用户（level=0/1/2）
     - 组件：`src/components/MemberTreeHoleSection.vue`
     - API 调用：`GET /api/treehole/{ownerId}/messages`、`GET /api/treehole/{ownerId}`（仅主人加载状态）、树洞黑名单 check/block/unblock
     - **非主人视角**：根留言与主人回复分组展示；根留言卡片右下角有下拉箭头，点击展开/收起回复；回复卡片内显示「{nickName}的回复」标签；主人回复的消息详情页不显示删除按钮
     - **留言状态**：0=未读，1=已读，3=已回复（绿色徽章）；回复消息不显示状态
     - **筛选**：主人 3 项（全部/未读/已读）；非主人 4 项（全部/未读/已读/已回复）
     - **树洞状态按钮**（仅当前用户=树洞主人时显示）：树洞状态 0 或 1 时显示「关闭树洞」，点击后弹出确认弹窗，确认后调用 `updateTreeHoleState(ownerId, 2)`；状态 2 时显示「开放树洞」，点击后弹出确认弹窗，确认后调用 `updateTreeHoleState(ownerId, 0)`。树洞状态通过 `getTreeHoleInfo(ownerId)` 加载，前端使用 `response.data.state`（因 axios 拦截器已返回 Result）。
     - **拉黑/解除拉黑**（仅树洞主人在消息详情中可见）：打开详情时根据发送者 ID 调用 `checkBlockStatus(senderId)`；若已拉黑（data=true）显示「解除拉黑」，点击调用 `unblockUser(senderId)`；若未拉黑显示「拉黑该用户」，点击弹出确认弹窗，弹窗内带输入框「请填写拉黑原因，也可不填」，确认后调用 `blockUser(senderId, reason)`。自己给自己的留言不显示该按钮。

### 页面跳转逻辑

- **图片&视频集** → `/browse`（MediaBrowse页面）
- **成员专区下拉菜单** → `/member/:memberId`（MemberZonePage页面）
- **成员专区页面内切换成员**：通过导航栏下拉菜单选择其他成员，路由更新为 `/member/:newMemberId`

### 相关组件

- `src/components/NavBar.vue`：统一导航栏组件
- `src/components/MemberIntroSection.vue`：成员简介展示组件
- `src/components/MemberMediaSection.vue`：成员媒体展示组件（复用媒体浏览的条带/网格逻辑）
- `src/components/MemberTreeHoleSection.vue`：树洞消息展示组件

### 树洞消息展示说明（MemberTreeHoleSection）

- **树洞主人**：列表仅显示根留言（`rootMessageId` 为 null），每条显示发送者、内容、状态（未读/已读/已回复）；筛选 3 项：全部 / 未读（state=0）/ 已读（state=1 或 3）；可点击「关闭树洞」/「开放树洞」切换树洞状态（均需二次确认）；在消息详情中可对发送者「拉黑该用户」或「解除拉黑」（拉黑确认弹窗含可选原因输入框）
- **非主人（投递者）**：
  - 按根留言分组，回复挂到对应根留言下
  - 根留言卡片：显示发送者、内容、状态；右下角有下拉箭头（有回复时显示）
  - 点击箭头展开/收起回复列表
  - 回复卡片：显示「{树洞主人昵称}的回复」标签 + 回复内容；不显示状态
  - 详情页：仅根留言（自己投递的）显示删除按钮，主人回复的消息不显示删除按钮
  - 筛选 4 项：全部 / 未读（state=0）/ 已读（state=1）/ 已回复（state=3）

### 相关前端 API 封装

- 文件：`src/api/media.js`
  - `getMediaList(page, size, category, currentUserId)`：获取媒体列表（支持按成员ID筛选）
- 文件：`src/api/treehole.js`
  - `getTreeHoleMessages(ownerId, page, size)`：获取树洞消息列表
  - `sendTreeHoleMessage(ownerId, content, rootMessageId)`：投递树洞消息（rootMessageId 为空为投递新留言，非空为主人回复）
  - `getTreeHoleInfo(ownerId)`：获取树洞信息（含 state），用于展示关闭/开放树洞按钮；**注意**：axios 响应拦截器返回 `response.data`，故接口得到的是 Result，树洞实体在 `response.data`，状态为 `response.data.state`
  - `updateTreeHoleState(ownerId, state)`：更新树洞状态（0=正常，2=禁止投递）
  - `checkBlockStatus(blockedUserId)`：查询当前用户（树洞主人）是否已拉黑某用户，返回 `data: true/false`
  - `blockUser(blockedUserId, reason)`：拉黑用户，`reason` 可选（拉黑原因）
  - `unblockUser(blockedUserId)`：解除拉黑
  - 分享功能已暂不实现，前端未接入

## 用户菜单与退出登录

### 用户菜单配置

- **配置文件**：`src/config/userMenu.js`
- **菜单项**：
  - 修改密码（弹窗：`ChangePasswordModal`）
  - 我的上传（路由：`/my-uploads`）
  - 资源管理（路由：`/resource-manage`，仅作者/管理员可见）
  - 注销账号（弹窗：`DeregisterConfirmModal`，需输入密码确认）
  - 退出登录（弹窗：`SimpleConfirmModal`，简单确认）

### 退出登录流程

1. **触发**：用户点击导航栏用户菜单中的"退出登录"
2. **确认弹窗**：显示 `SimpleConfirmModal` 确认弹窗
   - 标题："退出登录"
   - 消息："确定要退出登录吗？"
   - 按钮：取消 / 退出
3. **执行退出**：
   - 清除 `userStore` 中的 `token` 和 `userInfo`
   - 清除 `localStorage` 中的登录信息
   - 关闭确认弹窗和用户菜单
4. **页面跳转**：统一跳转到欢迎页（`/`）

### 注销账号流程

1. **触发**：用户点击导航栏用户菜单中的"注销账号"
2. **确认弹窗**：显示 `DeregisterConfirmModal` 确认弹窗
   - 需要输入当前密码进行二次确认
   - 提示：用户上传的图片、视频以及树洞消息仍会保留
3. **执行注销**：
   - 调用 `POST /api/user/deregister` 接口
   - 成功后清除本地登录信息
   - 自动执行退出登录流程

## 统一导航栏组件（NavBar）

### 组件位置
- **文件**：`src/components/NavBar.vue`
- **用途**：所有页面统一使用的导航栏组件，避免代码重复

### 功能特性

1. **统一管理**：
   - 所有页面的导航栏逻辑集中在一个组件中
   - 包含登录/注册、用户菜单、成员专区选择等功能
   - 统一管理所有相关弹窗（登录、注册、找回密码、修改密码、注销、退出登录）

2. **布局结构**：
   - **左侧**：支持自定义内容（通过 `#left` slot），默认显示占位符
   - **中间偏左**（独立定位）：**热门内容** 按钮（下拉弹窗，不跳转页面）、**图片&视频集** 按钮（`router-link to="/browse"`），两者独立，均在 logo 左侧（热门内容 `-24rem`，图片&视频集 `-10rem`）
   - **中间**：Logo（`/`）
   - **中间偏右**：成员专区下拉菜单
   - **右侧**：登录/注册按钮或用户昵称下拉菜单

3. **成员专区下拉菜单**：
   - 包含"公共区"选项和12个成员选项
   - 点击"公共区"跳转到 `/browse`
   - 点击成员跳转到 `/member/:memberId`
   - 当前访问的成员专区会高亮显示

4. **热门内容下拉**（见下方「热门内容（NavBar）」小节）：
   - 点击「热门内容」展开下拉弹窗，不跳转页面
   - 标题行左侧「热门内容」，右侧筛选：全部 / 图片 / 视频
   - 列表来自 `GET /api/mediaVisible/rank`，最多 20 条，每次展开都会重新请求以反映最新点赞
   - 列表区域最大高度 50vh，可纵向滚动；点击某项跳转 `/media/:id` 并关闭下拉

5. **用户菜单下拉菜单**：
   - 修改密码、我的上传、资源管理（仅作者/管理员可见）、注销账号、退出登录
   - 所有菜单项统一管理，通过 `src/config/userMenu.js` 配置

6. **退出登录确认**：
   - 点击"退出登录"时弹出确认弹窗（`SimpleConfirmModal`）
   - 确认后执行退出登录并跳转到欢迎页（`/`）
   - 防止误触操作

### 使用方式

**默认使用**（左侧占位）：
```vue
<NavBar />
```

**自定义左侧内容**（如返回按钮）：
```vue
<NavBar>
  <template #left>
    <router-link to="/browse" class="nav-back-btn">
      <svg class="back-icon">...</svg>
      <span>返回浏览</span>
    </router-link>
  </template>
</NavBar>
```

### 已使用该组件的页面

- `MediaBrowse.vue` - 媒体浏览页（默认左侧）
- `MemberZonePage.vue` - 成员专区页（默认左侧）
- `ResourceManage.vue` - 资源管理页（自定义左侧：返回浏览按钮）
- `UploadMedia.vue` - 上传页（自定义左侧：返回按钮）
- `MyUploads.vue` - 我的上传页（自定义左侧：返回浏览按钮）

## 热门内容（NavBar）

### 入口与行为

- **入口**：导航栏 logo 左侧第一个按钮「热门内容」（与「图片&视频集」独立，定位 `-24rem`）
- **交互**：点击展开/收起下拉弹窗，不跳转页面；点击页面其他区域关闭下拉

### 下拉内容

- **标题行**：左侧文案「热门内容」，右侧筛选按钮 **全部 | 图片 | 视频**（与标题同一行）
- **数据来源**：`GET /api/mediaVisible/rank`，参数 `category`（null=全部，0=图片，1=视频）、`size=20`
- **加载时机**：每次展开下拉时都会请求，保证点赞等操作后能看到最新排行榜
- **列表**：每条展示排名、封面、标题（空则不显示）、点赞数；列表区域 `max-height: 50vh`，`overflow-y: auto` 可滚动
- **点击列表项**：跳转 `/media/:id`（由 `MediaDetailPage` 承接），并关闭下拉

### 相关 API

- **文件**：`src/api/media.js`
  - `getMediaRank(category, size)`：获取热门排行榜，`category` 为 `null`/`0`/`1`，`size` 默认 20

### 媒体详情路由（/media/:id）

- **路由**：`/media/:id`
- **组件**：`src/views/MediaDetailPage.vue` 包装 `MediaDetail.vue`
  - `MediaDetailPage` 从 `route.params.id` 解析媒体 ID，以 props `media-id`、`media-list=[]` 传入 `MediaDetail`
  - 用户点击关闭时执行 `router.back()` 返回上一页
  - 无效 id（如 `/media/abc`）时展示「媒体不存在或链接无效」与「返回浏览」链接
- **使用场景**：从热门内容下拉、图片&视频集列表、成员专区等点击媒体后均可跳转至该详情页

### 点赞、取消点赞与点赞数（MediaDetail）

- **使用位置**：媒体详情页 `src/views/MediaDetail.vue` 顶部操作栏，点赞图标与点赞数并排展示；按钮根据当前是否已赞切换样式（`liked` class），点击触发点赞/取消点赞。

- **前端 API 封装**（`src/api/media.js`）：
  - **点赞**：`likeMedia(mediaId)` → `POST /api/media/{id}/like`（需登录）
  - **取消点赞**：`unlikeMedia(mediaId)` → `POST /api/media/{id}/unlike`（需登录）
  - **查询是否已赞**：`getLikeStatus(mediaId)` → `GET /api/userLikeRecord/media/{mediaId}/status`（需登录，返回 `data: true/false`）
  - **当前点赞数**：来自详情接口 `getMediaDetail(mediaId)` 的响应字段 `data.likeCount`（后端先读 Redis ZSET，再兜底 media:core/DB，见 overcome.md）

- **状态与展示**：
  - `isLiked`：当前用户是否已赞，用于按钮样式与点击行为（已赞点一下→取消，未赞点一下→点赞）
  - `likeCount`：当前媒体点赞数，展示在点赞图标旁；初始值来自详情接口返回的 `mediaDetail.likeCount`，通过 `watch(mediaDetail?.likeCount)` 同步
  - 防抖：`isThrottling` + 500ms 内禁止重复点击，避免连点

- **加载详情时**：
  - 先请求 `getMediaDetail(mediaId)`，得到详情（含 `likeCount`），`watch` 将 `likeCount` 赋给展示用变量
  - 已登录时再请求 `getLikeStatus(mediaId)`，用返回的 `data` 设置 `isLiked`；未登录或请求失败则 `isLiked = false`

- **点击点赞/取消点赞**（`handleLike`）：
  - 先做**乐观更新**：立即切换 `isLiked` 并增减 `likeCount`
  - 再根据当前是「已赞→取消」或「未赞→点赞」调用 `unlikeMedia(mediaId)` 或 `likeMedia(mediaId)`
  - 请求失败则回滚 `isLiked` 与 `likeCount` 到操作前；401 时在控制台提示「点赞需先登录」

- **切换媒体**：`mediaId` 变化时重新执行 `loadMediaDetail()`，`isLiked` 由新一轮 `getLikeStatus` 结果更新，无需在 watch 里手动重置。

