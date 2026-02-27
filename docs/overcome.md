# 问题与解决方案记录

本文记录项目实现过程中遇到的问题、设计取舍与具体方案，便于日后回顾，也可为类似场景（DB 与对象存储一致性、缓存击穿/穿透、事务与自调用等）提供参考。

---

## 1. 文件上传方案（DB 与 OSS 职责）

### 功能与场景

媒体上传涉及数据库（media 表、state）与对象存储（OSS/MinIO）。需明确：
- 谁为“资源真实来源”、谁为“状态记录”；
- 上传/更新时 DB 与 OSS 的先后顺序；
- DB 更新失败时是否回滚 OSS。

### 设计原则：数据库是状态，OSS 才是资源

- **数据库**：仅记录 `media` 元数据与 `state`、`cover_status`，表示“这条记录当前处于什么阶段”。
- **OSS**：真实存储主文件与封面，是资源的唯一真实来源。
- **一致性策略**：**只要 OSS 上传成功，不因数据库更新 state 失败而回滚删除 OSS**。若“通知上传结果”时 DB 更新失败（如 state 未从 1 改为 2），不删 OSS 对象；通过**定时任务**或**数据库崩溃恢复后的补偿**，扫描 `media` 表与 OSS，按对象存在性/缺失性同步 state（如：OSS 有对象但 state 仍为 1 → 补偿为 2。避免“为保一致删 OSS”导致资源丢失。

### 上传与更新的顺序差异（media 与 cover 统一原则）

无论是 **主文件（media）** 还是 **封面（cover）**，都遵循同一套“DB 与 OSS 谁先谁后”的约定：

- **上传**  
  - **先落库、再传 OSS、再改库**：先在数据库中创建记录（主文件对应 `media` 的 state=1，封面对应 `cover_status=1` 等），再上传到 OSS，上传成功后再次更新数据库（state/cover_status 等），通过 **state、cover_status** 感知资源在 OSS 中的状态。  
  - 这样“有记录再上传”，便于幂等、重试和补偿：记录先存在，OSS 成功后只做状态更新；若 OSS 失败，记录仍在，可重试或标记失败。

- **更新**  
  - **先传 OSS、再改库**：必须先上传到 OSS 成功，再修改数据库（如 `updateCover` 先上传新封面到 OSS，再更新 `media.cover_path`、`cover_status`）。  
  - 若先改库再传 OSS，一旦 OSS 失败，库中已指向新路径但 OSS 上无对象，会变成脏数据；先 OSS 后 DB，保证“库中指向的路径在 OSS 上一定存在”。

- **小结**：上传是“从无到有”，先建记录再上传再改状态；更新是“从有到有”，先上传新资源再改库，避免库与 OSS 不一致。

### 当前实现：两阶段上传 + 前端直连 OSS

**阶段一：准备上传**

- **路径**：`POST /api/media/upload`
- **参数**：`file_hash`（前端计算）、`category`、`title`、`description`、`filename`；**不传主文件与封面**。
- **后端**：幂等校验（同用户+同 hash 则返回已有 media）；否则落库 `media`（state=1），生成 `storagePath`、预签名 PUT `uploadUrl`，可选返回 `stsCredentials`（STS 临时凭证）。返回 `mediaId`、`storagePath`、`uploadUrl`、`uploadUrlExpireSeconds`、`stsCredentials`。

**阶段二：前端直传 OSS**

- 小文件或未配置 STS：对 `uploadUrl` 做 **PUT**，body 为文件。
- 大文件（≥5MB）且存在 `stsCredentials`：用 OSS SDK **分片上传**（`multipartUpload`），支持进度回调。

**阶段三：通知上传结果**

- **路径**：`POST /api/media/upload/complete`
- **参数**：`mediaId`、`success`、`visibleUserIds`、`cover`（success 时）。
- **后端**：若 `success=true`，校验 OSS 对象存在后更新 state=2、写 `media_visible`、上传封面，最后可更新 state=0；若 `success=false`，更新 state=3。**不因 DB 更新失败而删 OSS**。

### 状态与失败处理

- **state 流转**：1=正在上传 → 2=上传成功（OSS 已有）→ 0=正常（可见已设置）；失败为 3；删除为 4→5。
- **OSS 成功、DB 更新失败**：不回滚 OSS；依赖定时任务或补偿任务扫描 media 与 OSS 做状态同步。
- **封面 / media_visible 失败**：主文件已在 OSS，state 保持 2，可重试或补偿。

### 后续优化方向

- **定时/补偿任务**：扫描 state=1 且创建时间过久的 media，或 DB 恢复后全表+OSS 对账，补齐 state 与可见/封面。
- **大文件**：前端已支持分片上传与进度展示；如需断点续传可在此基础上扩展。

---

## 2. 文件上传幂等性

### 问题场景

- 用户可能因网络重试、误操作等原因重复上传相同文件。
- 数据库和对象存储无法直接判断文件内容是否相同。
- 需要保证相同文件内容多次上传，结果一致。

### 解决方案：文件内容哈希（前端计算 file_hash）

- **数据库设计**：`media` 表新增 `file_hash` 字段（VARCHAR(64), NOT NULL），与 user 联合唯一或联合索引，用于幂等查询。
- **实现逻辑**：
  - 上传时由前端计算文件哈希并传入 `file_hash`（或后端计算，大文件可考虑流式）。
  - 查询数据库：`WHERE file_hash = ? AND user_id = ?`（已创建联合唯一索引）。
  - 如果存在：直接返回已有记录（幂等，不重复上传）。
  - 如果不存在：正常上传并保存哈希值。
- **优势**：相同内容=相同哈希，真正幂等；节省存储；重复上传不报错，直接返回已有资源。

### 注意事项

- 当前实现由前端计算 hash 并传入，后端不做内容计算；大文件可考虑流式或独立服务计算哈希。

---

## 3. 文件删除方案

### 接口与权限

- **路径**：`DELETE /api/media/{id}/delete`（当前实现路径以代码为准）。
- **说明**：`currentUserId` 从 JWT 获取；仅允许上传者本人删除（或作者/管理员）。

### 问题与选型：为何不用事务

- **外部服务不在事务范围内**：对象存储（MinIO/OSS）不在 MySQL 事务内，无法与 DB 同事务提交/回滚。
- **需要细粒度重试**：每步独立重试 3 次，事务内重试逻辑复杂。
- **状态机容错**：通过 state（4=正在删除、5=已删除）控制流程，某步失败可再次调用接口收尾。
- **性能与体验**：对象存储删除可能较慢，不宜长时间占 DB 连接；一旦 media_visible 删除成功，用户侧已不可见，对象存储删除失败仅记日志，等后续上传者再次删除即可。

### 当前实现逻辑（无事务、分步执行）

**执行顺序（四步）**

1. **第一步：标记正在删除**  
   - MySQL：将 `media.state` 改为 4（正在删除），重试 3 次。  
   - 失败则直接返回删除失败，不修改其他数据。

2. **第二步：删除可见关系**  
   - MySQL：删除该 media 的所有 `media_visible` 记录，重试 3 次。  
   - 失败则回滚第一步的 state，返回删除失败。

3. **第三步：删除对象存储**  
   - 删除 OSS/MinIO 中的主文件与封面（若路径不同）。  
   - 失败仅记录日志，不影响主流程（用户已不可见该资源）。

4. **第四步：标记已删除**  
   - MySQL：将 `media.state` 改为 5（已删除），重试 3 次；随后物理删除 media 表记录（当前实现为物理 DELETE）。  
   - 失败仅记录日志，数据已处于“正在删除”状态，可后续清理。

**失败处理小结**

- 第一步失败：不修改任何数据，返回失败。
- 第二步失败：回滚 state，保证一致性。
- 第三步失败：记日志，不阻塞。
- 第四步失败：记日志，可再次调用删除接口从 state=4 继续收尾（幂等）。

**状态流转**

- 正常状态（0/1/2/3/6/7）→ `4=正在删除` →（对象存储与 media_visible 清理后）→ `5=已删除` 或物理删除。
- 第二步失败时：`4` → 回滚到原始 state。

---

## 4. 树洞消息：为何用 TransactionTemplate 而非 @Transactional

### 背景

- `sendMessage` 拆成两个内部方法：`doDeliverNewMessage`（投递新留言）、`doReplyMessage`（主人回复）。
- 希望**仅回复分支**使用事务（插入回复 + 更新根消息原子性），投递分支不加事务。

### 问题：自调用导致 @Transactional 不生效

- **@Transactional 的生效方式**：Spring 通过 **AOP 代理** 实现事务。外部调用 `treeHoleMessageService.sendMessage(...)` 时，先进入代理对象，代理在调用真实方法前开启事务、调用后提交或回滚。
- **同类内部调用不经过代理**：`sendMessage` 内部执行 `return doReplyMessage(...)` 时，相当于 `this.doReplyMessage(...)`，没有经过代理，因此贴在 `doReplyMessage` 上的 `@Transactional` 不会被处理，事务不会开启。

### 可选做法对比

| 做法 | 说明 |
|------|------|
| 在 `doReplyMessage` 上加 @Transactional | 无效：同类自调用不经过代理，事务不生效。 |
| 把“回复”逻辑抽到**另一个 Bean**，在该 Bean 的方法上加 @Transactional，本类注入并调用 | 有效：调用的是另一个 Bean 的代理；但需多一个类。 |
| 在 `doReplyMessage` 内用 **TransactionTemplate.execute(...)** 包住写库逻辑 | 有效：编程式事务，不依赖代理；不需新增类。 |

### 本项目选择

- 采用 **TransactionTemplate**：在 `doReplyMessage` 内用 `transactionTemplate.execute(status -> { ... })` 包住“插入回复 + 更新根消息”，保证原子性。
- 不新增类，事务边界清晰，且不依赖“必须通过代理调用”这一前提。

---

## 5. MediaServiceImpl 与 MediaVisibleServiceImpl 循环依赖

### 问题

- 启动报错：`mediaServiceImpl` ↔ `mediaVisibleServiceImpl` 构造器循环引用，Spring 禁止。

### 原因

- `MediaServiceImpl` 构造函数注入 `IMediaVisibleService`；`MediaVisibleServiceImpl` 构造函数注入 `IMediaService`，形成环。

### 解决方案

- **MediaVisibleServiceImpl** 中对 `IMediaService` 的依赖仅用于两处 `getById(mediaId)`（查库写 media:core 缓存）。
- 该类已注入 `MediaMapper`，将两处 `mediaService.getById(mediaId)` 改为 `mediaMapper.selectById(mediaId)`，并移除构造函数中的 `IMediaService` 参数。
- 打破循环的同时保持职责清晰：列表/缓存逻辑只需按 ID 查 Media，用 Mapper 即可，无需经 Media 业务服务。

---

## 6. 联表查询流程（MyBatis 手写 SQL）

### 场景

与 MyBatis-Plus 的 `LambdaQueryWrapper` 单表查询不同，联表需要多张表 JOIN，一般用 **Mapper 接口 + XML 手写 SQL**，不依赖 Wrapper。

### 调用链与参数传递

1. **Controller** 调 Service 的 `listSharedMessages(ownerId, page, size)`，`ownerId` 来自 JWT，`page/size` 来自请求参数。
2. **Service** 做校验和分页换算：`offset = (pageNum - 1) * pageSize`，然后调 **Mapper**：
   - `baseMapper.countSharedMessages(ownerId)` → 总条数；
   - `baseMapper.selectSharedMessageItems(ownerId, offset, limit)` → 本页数据列表。
3. **Mapper 接口**（Java）：只做声明，方法名、参数用 `@Param("xxx")` 命名，返回值 `List<DTO>` 或 `long`；实现在 XML 里通过 **方法名 = SQL 的 id** 绑定。
4. **Mapper XML**：
   - `namespace` 为 Mapper 接口全限定名；
   - SQL 中用 `#{ownerId}`、`#{offset}`、`#{limit}`，名字与 @Param 一致；
   - 多表 JOIN 写在一句 SQL；结果用 `resultMap` 或 `resultType` 映射为 DTO。

### 与 Wrapper 方式的区别

- **Wrapper**：单表、条件在 Java 里链式拼装，不需要写 XML。
- **联表**：多表、JOIN 与复杂条件写在 XML 的一条 SQL 里；参数通过 @Param 传到 `#{}`，返回类型由 resultMap/resultType 决定。

---

## 7. 对象存储抽象：策略模式与实现

### 设计思路

- **策略模式**：上传、删除、存在性检查、预签名 URL 等同一类操作，多种实现可替换；调用方只依赖接口，不关心具体存储（MinIO/OSS）。
- **依赖倒置**：业务层依赖 `StorageService` 接口，不依赖具体实现类；扩展新存储只需新增实现类并配置，业务代码无需改动。
- **运行时切换**：通过 Spring 依赖注入（如 `@Primary`）在运行时绑定具体实现，切换存储仅改配置或 Bean 条件即可。

### 实现要点

- **接口**：`StorageService`（upload 两种重载、delete、exists、getPresignedUrl）。
- **实现**：`MinioStorageService`、`OssStorageService`；当前默认 OSS（`OssStorageService` 标注 `@Primary`）。
- **配置**：`oss.*` / `minio.*` 在 application.yml；`OssConfig`、`MinioConfig` 分别创建 OSS 客户端或 MinioClient Bean。
- **业务层**：仅注入 `StorageService`，所有读写通过接口完成，与具体存储解耦。

---

## 8. 媒体缓存设计（media:core）

### 缓存方案：media:core 单一数据源缓存 media 除 like_count 和 like_count_update_time 外的全部字段，不同列表展示均根据 media_id 查询 media:core 再获取各自所需的字段。

- **缓存 Key**：`media:core:{mediaId}`
- **缓存内容**：`Media` 实体完整字段（含 `coverUrl` 预签名 URL，约 12 分钟有效）
- **TTL**：600 秒（10 分钟）
- **设计理念**：作为单一数据源，供 `MediaDetailResult`、`MediaListItem`、`UploadResult` 等不同返回结构选择性填充使用。

### 为何只缓存 state=0

- **media:core** 仅缓存 state=0（已审核通过、公开）的媒体。
- **原因**：state=0 对所有人可见，单 key 即可；state=6/7 仅上传者或审核者可访问，若按 mediaId 单 key 缓存会越权，故不缓存，每次校验权限后直接查 DB。

### coverUrl 与写时删除

- **写入缓存时**：动态生成预签名 URL（12 分钟有效），设置到 `Media.coverUrl`。
- **读取时**：优先使用缓存中的 `coverUrl`，不存在则重新生成。
- **写时删除**：`update`、`updateCover`、`delete`、`approveMedia`、`rejectMedia` 在变更后调用 `evictMediaCore`；`rebuildVisible` 不删缓存。

### 延迟双删

- **使用位置**：`delete` 方法。
- **流程**：第一次删缓存 → 物理删 media → 延迟 500ms 后再删一次缓存。
- **目的**：避免“删库后、删缓存前”并发请求读 DB 并回写缓存。

### 缓存删除失败不回滚数据库

- **设计原则**：数据库是主数据源，缓存失败不应影响数据库事务。
- **实现**：所有缓存删除用 try-catch 包裹，失败只记录日志，不抛出异常。
- **原因**：避免 Redis 故障导致业务失败，接受最终一致性（缓存 TTL 10 分钟）。

---

## 9. 缓存击穿防护：分布式锁

### 问题场景

- 热点数据过期时，大量并发请求同时穿透到数据库。
- 缓存未命中，多个线程同时查 DB，造成压力骤增。

### 解决方案：分布式锁 + 双重检测 + 重试

- 分布式锁保证跨 JVM/跨进程同步，防止多实例同时查 DB。
- 双重检测：获取锁后再次查缓存，减少重复查库。
- 重试：获取锁失败时等待 100ms 后重试查缓存，最多 3 次。

### 实现步骤（分步）

1. **第一次检测缓存**  
   命中则直接返回；未命中进入锁竞争。

2. **获取分布式锁**  
   - Redis `SETNX`，Key：`lock:media:core:{mediaId}`，TTL 5 秒。  
   - 使用 `requestId` 标识锁持有者，释放时用 Lua 检查并删除，防止误释。

3. **重试机制**  
   获取锁失败则等待 100ms 后重试查询缓存，最多 3 次。

4. **第二次检测缓存**  
   获取锁后再次查缓存，命中则释放锁并返回。

5. **查询数据库并写缓存**  
   仍未命中则查 DB、写入缓存，然后释放锁。

6. **锁续期（WatchDog）**  
   获取锁成功后启动后台线程，每 2 秒续期，防止业务执行超过锁 TTL；finally 中优雅关闭。

### 结果

- 同一时间只有一个请求访问 DB；重试过程中若缓存已写入则立即返回；锁续期避免长业务导致锁过期；requestId 避免误释他人锁。

---

## 10. 缓存穿透防护：空值缓存

### 问题场景

- 查询不存在的数据，缓存和 DB 都没有，每次请求都打 DB，造成压力。

### 解决方案：空值缓存（Null Object Pattern）

- 将“数据不存在”这一结果也缓存，避免重复查 DB。
- 设置较短 TTL（60 秒），避免永久挡住新数据。

### 实现要点（分步）

1. **空值标识**：使用特殊字符串 `"__NULL__"` 作为空值标记。  
2. **写入时机**：查询 DB 发现数据不存在时，在抛出 NOT_FOUND 前写入空值缓存。  
3. **空值识别**：`getMediaCore()` 中识别 `"__NULL__"` 并返回 `null`。  
4. **TTL**：空值缓存 TTL 60 秒。  
5. **空值清理**：数据创建时（如 `upload`、`approveMedia`）调用 `evictMediaCore()` 删除对应空值缓存。

### 结果

- 60 秒内重复查询不存在的数据命中空值缓存，不访问 DB；新数据创建后空值被清理，可正常查询。

---

## 11. 列表查询：双重分布式锁 + 空值缓存

### 设计理念

- **双重锁**：列表锁保护“查 DB + 写列表缓存”，media:core 锁保护单条媒体查 DB + 写 media:core。
- **二级缓存**：列表缓存（`media:list` / `media:my`）存 total 和 mediaIds；media:core 存完整 Media。
- **空值缓存**：列表为空时缓存 `{"total": 0, "mediaIds": []}`；media 不存在时缓存 `"__NULL__"`。

### listMedia 实现流程（分步）

**第一步：查列表缓存**

- 查询 `media:list:{zoneUserId}:{category}:{page}:{size}`。
- 命中 → 进入「列表缓存命中」；未命中 → 进入「列表缓存未命中」。

**第二步 A：列表缓存命中**

1. 批量查 `media:core`：`batchGetMediaCore(cachedMediaIds)`。  
2. 对未命中的 mediaId，逐个加锁处理：  
   - 获取 `lock:media:core:{mediaId}`；  
   - 双重检测：获锁后再查 media:core；  
   - 仍未命中则查 DB 并写缓存（不存在则写空值）；  
   - 锁续期每 2 秒。  
3. 按 `cachedMediaIds` 顺序构建结果，只保留 state=0。

**第二步 B：列表缓存未命中**

1. 获取列表锁：`lock:media:list:{zoneUserId}:{category}:{page}:{size}`，重试 3 次、每次等 100ms 后先试查缓存，再重新尝试获取锁，获锁后启动续期。  
2. 双重检测：获锁后再查列表缓存，命中则释放锁并返回。  
3. 查 DB：`queryMediaListFromDB()` 得到 total 和 records。  
4. 写缓存：`writeMediaListCache()` 写列表缓存与 media:core；列表为空时写 `{"total": 0, "mediaIds": []}`（TTL 300 秒）。  
5. 在 finally 中停止续期并释放锁。

### listMyUpload 实现流程（分步）

**第一步：查列表缓存**

- 查询 `media:my:{uploaderId}:{category}:{page}:{size}`。
- 命中 → 进入「列表缓存命中」；未命中 → 进入「列表缓存未命中」。

**第二步 A：列表缓存命中**

1. 批量查 `media:core`。  
2. 对未命中 id 加 `lock:media:core:{mediaId}`，双重检测、查 DB、写缓存（含空值）、锁续期。  
3. 按顺序构建结果，排除 state=5（允许 state=6/7）。

**第二步 B：列表缓存未命中**

1. 获取列表锁：`lock:media:my:{uploaderId}:{category}:{page}:{size}`，重试与续期同 listMedia。  
2. 双重检测后查 DB：`queryMyUploadListFromDB()`（排除 state=5）。  
3. 写缓存：`writeMyUploadListCache()`，空列表同样写 TTL 300 秒。  
4. finally 中释放锁。

### 空值缓存与锁续期

- **列表空值**：触发条件为查询结果为空；内容 `{"total": 0, "mediaIds": []}`；TTL 300 秒；在 `writeMediaListCache()`、`writeMyUploadListCache()` 中写入。  
- **media:core 空值**：触发条件为 DB 无该媒体或 state 为空；内容 `"__NULL__"`；TTL 60 秒；在 `queryAndWriteMediaCore()` 中写入。  
- **WatchDog**：获锁后每 2 秒续期，锁 TTL 5 秒；finally 中等待最多 1 秒关闭续期线程。

---

## 12. 点赞与查询已赞设计

### 设计概要

- **排行榜**：Redis ZSET（`media:rank:all` / `media:rank:0` / `media:rank:1`），member=mediaId，score=点赞数。写路径可选：当前为「先 DB 再 Redis」；另有「先 Redis → MQ → 事务落库、失败回滚 Redis」已实现未启用。
- **是否已赞**：Redis bitmap `media:liked:{mediaId}`，offset=userId，bit=1 表示已赞。
- **查询**：只查 Redis GETBIT，为 1 返回已赞；为 0 或 key 不存在视为未赞。接口：`GET /api/userLikeRecord/media/{mediaId}/status`。

### 点赞 / 取消点赞实现（当前方案：先 DB 再 Redis）

**点赞**

- 接口层校验 media 存在且 state=0。
- 先落库：插入 user_like_record（已存在则幂等）、更新 media.like_count。
- 再更新 Redis：位图置 1，全量和图片/视频双 ZSET 均更新（`media:rank:all`、`media:rank:{category}`）ZINCRBY 1（Lua 双 key 原子）。

**取消点赞**

- 先落库：删除 user_like_record、更新 media.like_count（SQL 内防止为负）。
- 再更新 Redis：位图置 0；仅当 ZSET 中 score>0 时 ZINCRBY -1（Lua），保证不为负。

### 排行榜查询

- **取 Top N**：ZREVRANGE WITHSCORES 取前 N 条，转成 `List<RankEntry>` 按 score 降序、mediaId 升序。
- **ZSET 不存在**：不主动创建，返回空列表；ZSET 由点赞时 ZINCRBY 自动创建。
- **媒体信息补全**：按 mediaId 批量查 media:core；未命中则加锁查 DB 并回写，再组装 HotListItem（likeCount 取自 ZSET score）。

### 详情展示点赞数（resolveLikeCount）

- **约定**：先读 Redis ZSET 的 score，不存在再用 media:core/DB 的 likeCount。
- **实现**：`MediaServiceImpl.resolveLikeCount(mediaId, media)` 中先调 `getLikeCountFromRank(mediaId)`（ZSCORE `media:rank:all`），有值则返回；无则用传入的 Media 的 likeCount，再缺则 0。
- **调用位置**：`getMediaDetail` 所有返回路径在得到 `MediaDetailResult` 后统一执行 `result.likeCount = resolveLikeCount(mediaId, media)`。

### 缓存恢复（Redis 宕机后）

- 以 **user_like_record** 为数据源恢复。  
- **ZSET 恢复**：按 media_id 聚合点赞数，对 `media:rank:all` 与 `media:rank:{category}` 执行 ZADD。  
- **Bitmap 恢复**：按 media_id 分组，对每个 mediaId 根据其 user_like_record 的 user_id 列表，对 `media:liked:{mediaId}` 执行 SETBIT userId 1。  
- 恢复可由定时任务或运维脚本在 Redis 恢复后触发。

### 查询已赞为何只读 Redis

- 点赞/取消点赞目前是先写 DB 再写 Redis，读以 Redis 为准；GETBIT 未命中（key 不存在或该位为 0）统一视为未赞，不查 DB、不写回。  
- Redis 对不存在的 key 执行 GETBIT 返回 0，与“该用户位为 0”无法区分；当前策略不依赖区分，故无需额外“EXISTS + 加锁”等。锁能力（tryLockLiked/unlockLiked/renewLockLiked）已预留，若需强一致读可再接入。
