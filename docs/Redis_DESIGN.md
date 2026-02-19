# Redis 设计方案

## 缓存

### 缓存媒体详情

#### 描述
缓存媒体资源的详细信息，包括标题、描述、存储路径、封面等信息。
采用**二级缓存架构**：
- **设计理由**：媒体资源详细信息查询请求远多于修改，使用二级缓存能大大缓解redis和mysql压力，扬长避短
- **一级缓存（Redis）**：分布式缓存，存储序列化后的完整数据
- **二级缓存（Caffeine）**：本地缓存，减少Redis访问压力，提升响应速度
先查Caffeine本地缓存，未命中再查Redis，都未命中则查数据库。

#### 缓存Key
- **Redis Key格式**：`media:detail:{mediaId}`
  - 示例：`media:detail:1`
- **Caffeine Key格式**：`media:detail:{mediaId}`（与Redis保持一致）

#### 缓存Value
JSON序列化的 `MediaDetailResult` 对象，包含以下字段：
```json
{
  "id": 123,
  "category": 0,
  "title": "标题",
  "description": "描述",
  "storagePath": "images/2026/01/21/xxx.jpg",
  "coverPath": "images/2026/01/21/xxx.jpg",
  "coverUrl": "https://...",
  "uploaderId": 1,
  "updateTime": "2026-01-31T12:34:56"
}
```

#### 过期时间
- **Redis TTL**：600秒（10分钟）
- **Caffeine TTL**：300秒（5分钟）
- **Caffeine最大条目数**：200条

#### 缓存更新策略
- **写时删除（Write-Through）**：当媒体信息发生变更时（更新、删除、审核），主动删除对应的缓存
- **删除操作**：同时删除Redis和Caffeine中的缓存
- **更新操作**：先删除缓存，再更新数据库，下次查询时重新加载

#### 实现说明（当前仅 Redis，未接 Caffeine）

**查（getMediaDetail）**
- 先查 Redis → 命中则直接返回
- 未命中则查 DB，仅 `state=0`（已审核通过）时写入缓存

**增（upload）**
- 无需缓存操作：新上传媒体为 `state=6` 待审核，不参与缓存

**改（update / updateCover / rebuildVisible）**
- 写库成功后删除对应 mediaId 的缓存

**删（delete）**
- 删除成功后删除对应 mediaId 的缓存

**审核（approveMedia / rejectMedia）**
- 每成功处理一条，删除该 mediaId 的缓存

**说明**：`MediaDetailResult` 需添加 `@JsonCreator`、`@JsonProperty` 以支持 Jackson 反序列化。

### 缓存media临时下载链接

#### 描述
缓存媒体资源的临时下载链接，无需每次都访问 OSS 获取。仅对 **state=0（已审核通过、公开）** 的媒体缓存；state=6/7 不缓存，每次请求在权限校验通过后直接向 OSS 获取临时链接。
先查 Redis 缓存，未命中则调用 StorageService 从 OSS 获取临时下载链接并写入缓存。

#### 缓存Key
- **Redis Key格式**：`media:downloadUrl:{mediaId}`
  - 示例：`media:downloadUrl:1`

#### 缓存Value
- **Redis Value格式**：字符串 String，临时下载链接

#### 过期时间
- **Redis TTL**：OSS 临时下载链接的过期时间 - 60 秒（示例：OSS 过期 7200 秒时，缓存 TTL=7140 秒）
  - 缓存早过期 60 秒，避免网络波动导致链接已过期但缓存仍在

#### 缓存更新策略
- **写时删除（Write-Through）**：当媒体信息发生变更时（更新、删除、审核），主动删除对应的缓存
- **删除操作**：删除 Redis 中该 key。实现时在调用 evictMediaDetail(mediaId) 的同一处同时调用 evictDownloadUrl(mediaId)

#### 实现说明

**查（getDownloadUrl）**
- 先查 DB、做 state/权限与 storage 存在性校验；仅 **state=0** 时查 Redis，命中即返
- 未命中则调 StorageService.getPresignedUrl，仅 state=0 时写入缓存（TTL = 预设过期秒数 - 60），state=6/7 不写缓存直接返回 URL

**增（upload）**
- 无需缓存操作：新上传媒体为 state=6 待审核，不参与缓存

**改（update / updateCover / rebuildVisible）**
- update、updateCover：写库成功后与 evictMediaDetail 同一处调用 evictDownloadUrl（建议 try-catch 仅打日志，不抛异常）
- rebuildVisible：只改可见性，未改 media 核心字段，不删下载链接缓存

**删（delete）**
- 第一次删缓存（evictDownloadUrl + evictMediaDetail）→ removeById 物理删 media → 延迟 500ms 后再删一次缓存（延迟双删）

**审核（approveMedia / rejectMedia）**
- 每成功处理一条，与 evictMediaDetail 同一处调用 evictDownloadUrl

### 缓存media列表（仅缓存media_id）

### 缓存穿透预防/解决方案

## 排行榜
