# Redis 设计方案

## 缓存

### 缓存媒体详情

#### 描述
缓存媒体资源的详细信息，包括标题、描述、存储路径、封面等信息。
采用**二级缓存架构**：
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


## 排行榜
