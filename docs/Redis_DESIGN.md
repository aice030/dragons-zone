# Redis 设计方案

## 缓存

### 缓存媒体核心数据（media:core）

#### 描述
缓存媒体资源的完整核心数据，采用 `Media` 实体完整字段。该缓存作为单一数据源，供 `MediaDetailResult`、`MediaListItem`、`UploadResult` 等不同返回结构选择性填充使用。
采用**二级缓存架构**（当前仅 Redis，未接 Caffeine）：
- **设计理由**：媒体资源详细信息查询请求远多于修改，使用二级缓存能大大缓解redis和mysql压力，扬长避短
- **一级缓存（Redis）**：分布式缓存，存储序列化后的完整 `Media` 实体
- **二级缓存（Caffeine）**：本地缓存，减少Redis访问压力，提升响应速度（待实现）
先查Caffeine本地缓存，未命中再查Redis，都未命中则查数据库。

#### 缓存Key
- **Redis Key格式**：`media:core:{mediaId}`
  - 示例：`media:core:1`
- **Caffeine Key格式**：`media:core:{mediaId}`（与Redis保持一致，待实现）

#### 缓存Value
序列化的 `Media` 实体对象，包含以下字段：
```json
{
  "id": 123,
  "uploaderId": 1,
  "fileHash": "abc123...",
  "category": 0,
  "title": "标题",
  "description": "描述",
  "storagePath": "images/2026/01/21/xxx.jpg",
  "coverPath": "images/2026/01/21/xxx.jpg",
  "coverUrl": "https://...", 
  "state": 0,
  "updateTime": "2026-01-31T12:34:56",
  "likeCount": 0,
  "likeCountUpdateTime": "2026-01-31T12:34:56"
}
```

**字段说明**：
- `coverUrl`：封面预签名URL（12分钟有效），在写入缓存时动态生成并存储，查询时优先使用缓存中的值
- 其他字段：与数据库 `media` 表字段一一对应

#### 过期时间
- **Redis TTL**：600秒（10分钟）
- **Caffeine TTL**：300秒（5分钟，待实现）
- **Caffeine最大条目数**：200条（待实现）

#### 缓存更新策略
- **写时删除（Write-Through）**：当媒体信息发生变更时（更新、删除、审核），主动删除对应的缓存
- **删除操作**：同时删除Redis和Caffeine中的缓存（Caffeine待实现）
- **更新操作**：先删除缓存，再更新数据库，下次查询时重新加载

#### 实现说明（当前仅 Redis，未接 Caffeine）

**查（getMediaDetail）**
- 先查 Redis → 命中则从 `Media` 实体转换为 `MediaDetailResult`，优先使用缓存中的 `coverUrl`
- 未命中则查 DB，仅 `state=0`（已审核通过）时写入缓存
- 写入缓存时，动态生成 `coverUrl` 并设置到 `Media` 对象中一起缓存

**增（upload）**
- 无需缓存操作：新上传媒体为 `state=6` 待审核，不参与缓存

**改（update / updateCover / rebuildVisible）**
- update、updateCover：写库成功后删除对应 mediaId 的缓存（`evictMediaCore`）
- rebuildVisible：只改可见性，未改 media 核心字段，不删缓存

**删（delete）**
- 删除成功后删除对应 mediaId 的缓存（`evictMediaCore`）
- 延迟双删：删除缓存 → 物理删除 media → 延迟 500ms 后再删一次缓存

**审核（approveMedia / rejectMedia）**
- 每成功处理一条，删除该 mediaId 的缓存（`evictMediaCore`）

**coverUrl 处理**：
- 写入缓存时：根据 `coverPath` 动态生成预签名URL（有效期12分钟），设置到 `Media.coverUrl` 字段
- 读取缓存时：优先使用缓存中的 `coverUrl`，如果不存在或为空，则重新生成
- 有效期设置为12分钟（720秒），覆盖缓存TTL（10分钟）并额外2分钟应对网络波动，确保缓存的 `coverUrl` 在缓存有效期内都是可用的

**说明**：下载链接（`getDownloadUrl`）不进行缓存，因为生成预签名URL是纯本地计算（HMAC-SHA256签名），CPU占用很小，对于QPS < 1000的项目无需缓存。每次请求直接调用 `StorageService.getPresignedUrl()` 生成即可。

### 缓存media列表（仅缓存media_id）

#### 描述
缓存媒体列表的ID集合，复用 `media:core` 缓存填充列表项。采用**ID列表缓存 + 核心数据缓存**的二级结构：
- **ID列表缓存**：存储符合条件的媒体ID列表（分页结果）
- **核心数据缓存**：复用 `media:core`，通过ID批量获取完整数据
查询时先查ID列表，再批量从 `media:core` 获取数据填充返回结构。

#### 缓存Key

**media展示列表**
- **Redis Key格式**：`media:list:{zoneUserId}:{category}:{page}:{size}`
  - `zoneUserId`：0=公共区，其他=成员专区ID
  - `category`：`all`=全部，`0`=图片，`1`=视频（null 映射为 `all`）
  - 示例：`media:list:0:all:1:20`（公共区全部类型第1页）、`media:list:0:0:1:20`（公共区图片第1页）

**用户管理media列表**
- **Redis Key格式**：`media:my:{uploaderId}:{category}:{page}:{size}`
  - `uploaderId`：上传者用户ID
  - `category`：`all`=全部，`0`=图片，`1`=视频（null 映射为 `all`）
  - 示例：`media:my:1:all:1:20`（用户1的全部类型第1页）、`media:my:1:0:1:20`（用户1的图片第1页）

#### 缓存Value
包含列表总数和媒体ID列表的结构，序列化为JSON对象：
```json
{
  "total": 100,
  "mediaIds": [1, 2, 3, 4, 5]
}
```

#### 过期时间
- **Redis TTL**：300秒（5分钟）

#### 缓存更新策略
- **写时删除（Write-Through）**：媒体状态变更时，删除相关列表缓存
- **删除范围**：根据变更媒体所属的 `zoneUserId` 和 `category`，删除对应的列表缓存
- **更新操作**：先删除缓存，再更新数据库，下次查询时重新加载

#### 实现说明

**media展示列表（listMedia）**
- 先查 Redis ID列表 → 命中则批量从 `media:core` 获取数据，填充 `MediaListItem` 返回
- 未命中则查 DB，仅 `state=0` 的媒体写入ID列表缓存
- 批量获取 `media:core` 时，未命中的ID从DB加载并写入缓存

**用户管理media列表（listMyUpload）**
- 先查 Redis ID列表 → 命中则批量从 `media:core` 获取数据，填充 `MyUploadListItem` 返回
- 未命中则查 DB，排除 `state=5` 的媒体写入ID列表缓存
- 批量获取 `media:core` 时，未命中的ID从DB加载并写入缓存

**缓存失效**
- 上传：删除对应 `zoneUserId` 和 `category` 的列表缓存
- 更新/删除/审核：删除相关列表缓存（根据媒体所属专区）

### 缓存击穿/穿透的预防&解决方案

#### 缓存击穿防护

**方案**：分布式锁（Redis SETNX）+ 双重检测

**流程**：
1. 第一次检测缓存 → 命中则返回，未命中则获取分布式锁
2. 获取分布式锁（Redis SETNX，Key: `lock:media:core:{mediaId}`，TTL: 5秒）
3. 第二次检测缓存 → 命中则释放锁并返回，未命中则查询数据库并写入缓存
4. 释放锁（try-finally 确保释放）

**锁管理**：
- 分布式锁：使用 Redis `SET lock:media:core:{mediaId} {requestId} EX 5 NX` 实现
- 锁超时：5秒（防止死锁）
- 锁等待：获取失败则等待 100ms 后重试查询缓存，最多重试3次
- 锁粒度：按 `mediaId` 加锁

**实现位置**：
- getMediaDetail()方法，获取媒体详情
- listMedia()方法，获取媒体展示列表，获取列表中id对应的media:core
- listMyUpload()方法，获取用户上传列表列表，获取列表中id对应的media:core

#### 缓存穿透防护

**方案**：空值缓存

- media:core 空值缓存：当单个媒体不存在时，缓存 "__NULL__"，TTL 60秒
- 列表空值缓存：当查询结果为空时，缓存 {"total": 0, "mediaIds": []}，TTL 300秒

**空值缓存**：
- 空值标记：`"__NULL__"`（字符串）
- 空值 TTL：60秒（避免永久阻止新数据）
- 写入时机：查询数据库发现数据不存在时，在抛出 `NOT_FOUND` 前写入
- 空值识别：`getMediaCore()` 中识别 `"__NULL__"` 并返回 `null`
- 空值清理：数据创建时（`upload`、`approveMedia`）调用 `evictMediaCore()` 删除

**列表查询**：空列表也写入缓存（TTL: 300秒），Value: `{"total": 0, "mediaIds": []}`


## 排行榜（点赞数）

### 设计要点
- 使用 Redis ZSET 存储点赞数并排序：member = mediaId，score = likeCount（仅记录 media id，与列表缓存一致）。
- 按分类分桶：`category=null`（全部）、`0`（图片）、`1`（视频）各一个 ZSET，便于「分类 TopN」查询。
- 点赞/取消点赞时实时更新 Redis（ZINCRBY），再异步或定期回写 DB，保证排行榜实时、DB 最终一致。

### 需明确与建议

1. **Key 格式**（建议）
   - `media:rank:all`：全部媒体按点赞数排序
   - `media:rank:0`：图片
   - `media:rank:1`：视频  
   一次点赞需更新 2 个 ZSET：`media:rank:all` + `media:rank:{category}`（Lua 脚本保证原子性）。

2. **取数范围**
   - 文档写「前二十」：建议改为「前 N，默认 20」，接口用 `size` 参数，实现用 `ZREVRANGE key 0 (size-1)` 即可支持分页/更多。

3. **取消点赞与边界**
   - 取消点赞：先判断当前 score > 0 再执行 `ZINCRBY -1`，保证 score 不为负（Lua 脚本保证原子性）。

4. **与 media 生命周期一致**
   - 媒体删除（state=5）或下架：从 3 个 ZSET（all、0、1）中均 `ZREM` 该 mediaId（Lua 脚本一次对 3 个 key 执行 ZREM，避免只删部分）。
   - 审核通过（state 6→0）：若 DB 已有 like_count，需用该值对 `media:rank:all` 与 `media:rank:{category}` 执行 `ZADD`（Lua 脚本保证双 key 同时写入，与点赞一致）。

5. **回写 DB**
   - 每 5 分钟扫 ZSET 变更写回 `media.like_count`、`media.like_count_update_time`。
   - 接受redis宕机数据丢失，后续可能引入MQ

6. **与 media:core 的 likeCount**
   - `media:core` 里缓存的 `likeCount` 来自 DB，在两次同步之间会落后于 Redis 排行榜；因此列表/详情展示点赞数时，先读 Redis ZSET 中的 score，若不存在再用缓存的 likeCount。