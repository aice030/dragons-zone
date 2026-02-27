# 开发文档

## 项目简介

面向粉丝的图片/视频存储与互动平台，支持公共区与成员专区浏览、树洞留言、媒体审核与点赞排行。

## 技术栈

- **后端**：Spring Boot 3.3.1，Java 17，MySQL，MyBatis-Plus 3.5.15，Spring Security + JWT，Maven。对象存储默认阿里云 OSS（`OssStorageService` @Primary），MinIO 保留可切换。
- **前端**：Vue 3.5.27，Vite 7.3.1，Pinia 3.0.4，Vue Router 5.0.1，Axios 1.13.5。

## 项目结构

```
dragons-zone/
├── dragons-core-server/     # 后端
│   └── src/main/java/com/dragons/core/
│       ├── config/          # Security、JWT、OSS/MinIO
│       ├── controller/     # API 入口
│       ├── service/         # 接口
│       ├── serviceImpl/     # 实现
│       ├── dao/             # Mapper
│       ├── entity/, dto/, util/
│       └── resources/mapper/
├── dragons-frontend/        # 前端
│   └── src/
│       ├── api/             # user.js、media.js、treehole.js
│       ├── components/, views/, router/, stores/, config/
│       └── main.js, App.vue
└── docs/
```

## 核心数据表

- **user**：用户与登录凭证、level/state。
- **media**：媒体元数据，storage_path/cover_path 存对象路径。
- **media_visible**：成员专区可见（user_id=成员 ID）。
- **tree_hole**：树洞，owner_id 为树洞主人。
- **tree_hole_message**：留言，含 root_message_id、reply_message_id、state、update_time。
- **tree_hole_visible** / **tree_hole_message_visible**：树洞与分享可见。
- **user_like_record**：用户点赞记录；点赞数在 media.like_count 与 Redis 排行榜同步。

## 已实现功能

### 用户与认证

- JWT 登录/注册/注销、重置密码（已登录）、未登录找回密码（登录名+手机号）；实现于 `UserController`、`UserServiceImpl`，JWT 工具与 Security 配置在 `config/`。
- 用户等级/状态修改、用户列表（仅作者）、按 ID 取昵称；实现于 `UserController`、`UserServiceImpl`。

### 对象存储与上传

- 存储抽象 `StorageService`，实现 `MinioStorageService`、`OssStorageService`（@Primary）；配置 `oss.*` / `minio.*`。
- 两阶段上传：准备上传（`POST /api/media/upload`，file_hash 幂等、返回 uploadUrl/stsCredentials）、通知结果（`POST /api/media/upload/complete`）；实现于 `MediaController`、`MediaServiceImpl`。
- 媒体基础信息更新、视频封面更新、可见范围差量更新；实现于 `MediaServiceImpl`（含封面更新失败补偿删 OSS）。

### 媒体查询与列表

- 媒体详情、列表（公共区/成员专区）、我的上传列表、媒体所属专区、下载预签名 URL；实现于 `MediaController`、`MediaVisibleController`、`MediaServiceImpl`、`MediaVisibleServiceImpl`。
- 媒体详情与列表使用 Redis 缓存（media:core、media:list、media:my），分布式锁防击穿、空值缓存防穿透；实现于 `MediaVisibleServiceImpl`（含 `loadMissingMediaWithLock`、`queryMediaListFromDB`、`writeMediaListCache` 等内部工具方法）。

### 审核与删除

- 审核通过/驳回（批量）、待审核列表；实现于 `MediaController`、`MediaServiceImpl`。
- 媒体物理删除（先修改数据库中状态state→4，再删 media_visible 与对象存储，最后 DELETE media）；实现于 `MediaServiceImpl`，删除幂等可重试收尾。

### 点赞与排行榜

- 点赞/取消点赞（当前方案：先 DB 再 Redis ZSET，Redis+MQ 落库方案已实现未启用。）；
- 查询用户对当前图片 / 视频的点赞状态，是否已赞查 Redis bitmap；
- 热门排行榜，读 Redis ZSET Top N；实现于 `MediaController`、`MediaServiceImpl`、`RedisCacheMediaLikeService`。

### 树洞

- 投递留言与主人回复（同一接口 rootMessageId 区分）、防刷（上一条未读禁止再投）、留言列表（主人全量/投递者本人+回复）、已读、主人删除/发送者删除、树洞状态、分享与分享收件箱；实现于 `TreeHoleController`、`TreeHoleMessageController`、`TreeHoleMessageVisibleController`、`TreeHoleMessageServiceImpl` 等，回复分支使用 `TransactionTemplate` 保证原子性。
- 树洞黑名单：查询是否拉黑、拉黑、解除拉黑；实现于 `TreeHoleBlacklistController`、`TreeHoleBlacklistServiceImpl`。

### 前端

- 登录/注册/找回密码/修改密码/注销弹窗；上传（UploadMedia.vue）、浏览（MediaBrowse.vue）、详情（MediaDetail.vue）、我的上传（MyUploads.vue）、资源管理/审核（ResourceManage.vue）、成员专区与树洞（MemberZonePage.vue、MemberTreeHoleSection.vue）、导航与用户菜单（NavBar.vue）；API 封装与路由守卫；热门排行榜入口。

## 后续扩展与优化

- 接口与性能测试、安全加固、体验优化。
- 媒体上传/状态补偿：定时或恢复后扫描 media 与 OSS 对账 state。
- 点赞方案增强：对于高并发量场景，可启用 Redis+MQ 落库方案并做 Redis 宕机后利用 user_like_record 表恢复 缓存中的 ZSET 和 bitmap。
- 批量上传的前端队列模式适合作为 MVP；若未来文件量很大或需要更强的并发/断点续传能力，可考虑新增后端批量接口
- 当前实现由前端计算 hash 并传入，后端不做内容计算；大文件可考虑流式或独立服务计算哈希。
- 树洞功能尚未实现缓存，可在后续引入

## 开发环境

- 依赖：JDK 17+，Maven 3.6+，MySQL 8.0+，阿里云 OSS（或 MinIO，本地）。
- 配置：
    - `application.yml` 中配置数据源与 `oss.*`（或 `minio.*`）；密钥勿提交，可参考 `application-example.yml`。
    - 用docker创建镜像，构建容器时时需创建 `.env` 文件配置环境变量，配置`docker-compose.yml` 中的数据源和前后端连接的基础url，会直接覆盖 `application.yml` 中的对应配置，可参考 `env-example`
- 启动：`cd dragons-core-server && mvn spring-boot:run`。

## 注意事项

- 密码 BCrypt 存储；JWT 需在 Header 中携带；上传校验文件类型；OSS 密钥勿入库。
- 媒体删除同时删对象存储；接口统一 Result 与全局异常处理；按角色做权限校验。
