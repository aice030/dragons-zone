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
序列化的 `Media` 实体对象（写入时去掉 likeCount/likeCountUpdateTime，避免与 ZSET 冗余），包含以下字段：
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
  "updateTime": "2026-01-31T12:34:56"
}
```

**字段说明**：
- `coverUrl`：封面预签名URL（12分钟有效），在写入缓存时动态生成并存储，查询时优先使用缓存中的值
- **不存 likeCount/likeCountUpdateTime**：点赞数统一由排行榜 ZSET 提供，列表/详情通过 `getLikeCountFromRank(mediaId)` 解析
- 其他字段：与数据库 `media` 表对应字段一致

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

**用户管理media列表（暂未启用）**
- **Redis Key格式**：`media:my:{uploaderId}:{category}:{page}:{size}`
  - `uploaderId`：上传者用户ID
  - `category`：`all`=全部，`0`=图片，`1`=视频（null 映射为 `all`）
  - 示例：`media:my:1:all:1:20`（用户1的全部类型第1页）、`media:my:1:0:1:20`（用户1的图片第1页）
  - **说明**：当前业务未使用该列表缓存，listMyUpload 直接查 DB 再按 id 从 media:core 或 DB 取数；Redis 中相关方法保留实现，便于后续启用。

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

**用户管理media列表（listMyUpload，暂未启用）**
- **当前实现**：直接查 DB 获取本页 (total, records)，再根据 id 批量从 `media:core` 取数，未命中则使用 DB 结果；不读写 `media:my` 列表缓存。
- **若启用 media:my 时**：先查 Redis ID列表 → 命中则批量从 `media:core` 获取数据，填充 `MyUploadListItem` 返回；未命中则查 DB，排除 `state=5` 的媒体写入ID列表缓存；批量获取 `media:core` 时，未命中的ID从DB加载并写入缓存。

**缓存失效**
- **media:list**：上传/更新/删除/审核时，删除对应 `zoneUserId` 和 `category` 的列表缓存。
- **media:my**：暂未启用，无失效逻辑。

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
- listMyUpload()方法：用户上传列表缓存（media:my）暂未启用，当前无列表缓存击穿；若启用后需在此处对「列表未命中」加锁，获取列表中id对应的media:core 时与 listMedia 一致

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
- **写流程（两种同步方式，由 MediaServiceImpl 内注释切换）**：  
  - **方式一（当前默认）**：先 DB 再 Redis。事务落库 `user_like_record` 与 `media.like_count`，再按 DB 的 likeCount 回写 ZSET 与位图（`setScoreForMedia` + `setLiked`）。无 MQ，轻量。  
  - **方式二（高并发）**：先 Redis（Lua 更新 ZSET + 位图）→ 发 MQ → 消费者事务落库；落库失败则回滚 Redis。

### 点赞/取消点赞写流程

**方式一（先 DB 再 Redis，当前默认）**  
1. 校验媒体存在且 state=0、用户已登录。  
2. 若 Redis 位图已赞/未赞则直接 return（幂等）。  
3. 调用 `MediaLikePersistService.persist` 事务落库（LIKE：插 user_like_record + like_count+1；UNLIKE：删记录 + like_count-1）。  
4. `getById(mediaId)` 取最新 likeCount，再 `setLiked` + `setScoreForMedia` 回写 Redis。  
使用 `MediaServiceImpl.likeSyncDbFirst` / `unlikeSyncDbFirst`；切换为方式二时在 like/unlike 中注释 DbFirst 调用、取消 ViaMq 调用即可。

**方式二（先 Redis 再 MQ，高并发方案）**

1. **用户点赞/取消点赞** → 接口层校验（媒体存在且 state=0、用户身份等）。
2. **改 Redis（Lua 原子）**  
   - 点赞：位图 `media:liked:{mediaId}` 该用户位置 1（未赞过才执行），并对 `media:rank:all`、`media:rank:{category}` 执行 ZINCRBY 1。  
   - 取消点赞：位图该用户位置 0（已赞过才执行），并对两个 ZSET 在 score>0 时 ZINCRBY -1。  
   Lua 脚本内完成「位图 + 双 ZSET」的原子更新。
3. **发 MQ**  
   消息体包含：操作类型（like/unlike）、mediaId、userId、category 等，供消费者落库与回滚使用。
4. **消费者：事务落库**  
   在事务内：  
   - 点赞：插入 `user_like_record`，更新 `media.like_count += 1`、`media.like_count_update_time`。  
   - 取消点赞：删除 `user_like_record`，更新 `media.like_count -= 1`（且不小于 0）、`media.like_count_update_time`。  
   提交成功则流程结束。
5. **落库失败：回滚 Redis**  
   若消费者事务失败或重试耗尽仍失败，则执行回滚 Lua 脚本：  
   - 点赞回滚：位图该用户位置 0，两个 ZSET 对该 mediaId ZINCRBY -1（仅当 score>0）。  
   - 取消点赞回滚：位图该用户位置 1，两个 ZSET 对该 mediaId ZINCRBY 1。  
   保证 Redis 与 DB 最终一致，不依赖定时同步或兜底。


### 需明确与建议

1. **Key 格式**
   - `media:rank:all`：全部媒体按点赞数排序
   - `media:rank:0`：图片
   - `media:rank:1`：视频  
   一次点赞需更新 2 个 ZSET：`media:rank:all` + `media:rank:{category}`（Lua 脚本保证原子性）。

2. **取数范围**
   - 前 N，默认 20，接口用 `size` 参数，实现用 `ZREVRANGE key 0 (size-1)` 即可支持分页/更多。

3. **取消点赞与边界**
   - 取消点赞：先判断当前 score > 0 再执行 `ZINCRBY -1`，保证 score 不为负（Lua 脚本保证原子性）。

4. **与 media 生命周期一致**
   - 媒体删除（state=5）或下架：从 3 个 ZSET（all、0、1）中均 `ZREM` 该 mediaId，并 `DEL media:liked:{mediaId}`（Lua 脚本一次完成）。
   - 审核通过（state 6→0）：若 DB 已有 like_count，需用该值对 `media:rank:all` 与 `media:rank:{category}` 执行 `ZADD`（Lua 脚本保证双 key 同时写入）。

5. **与 media:core 的 likeCount**
   - media:core 不存 likeCount；列表/详情展示点赞数时，先读 Redis ZSET 的 score，未在榜时再用 DB 的 likeCount。

### 查询点赞记录（是否已赞）

用于「查询当前用户是否已赞某媒体」接口（如媒体详情页展示已赞/未赞按钮）：只查 Redis，以 Redis 为准。

#### Redis 结构
- **Key 格式**：`media:liked:{mediaId}`
- **类型**：位图（BITMAP，底层为 string）
- **语义**：offset = userId，bit = 1 表示该用户已赞；适合 userId 连续且范围有界的场景，内存固定约 ceil(maxUserId/8) 字节/媒体。

#### 查询流程
1. **只查 Redis**：对 `media:liked:{mediaId}` 执行 `GETBIT media:liked:{mediaId} {userId}`，若为 1 则已赞返回 true；若为 0 或 key 不存在则视为未赞返回 false。点赞/取消点赞已先写 Redis 再 MQ 同步 DB，故不查 DB、不写回。

#### 与写操作的一致
- **点赞**：对 `media:liked:{mediaId}` 执行 `SETBIT media:liked:{mediaId} {userId} 1`（与 ZSET ZINCRBY 同时进行），保证后续查询命中 Redis。
- **取消点赞**：对 `media:liked:{mediaId}` 执行 `SETBIT media:liked:{mediaId} {userId} 0`（与 ZSET ZINCRBY -1 同时进行）。
- **媒体删除/下架**：从排行榜 ZSET 中 ZREM 该 mediaId 时，同时 `DEL media:liked:{mediaId}`，避免脏读。