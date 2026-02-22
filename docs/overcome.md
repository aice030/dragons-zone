# 问题与解决方案记录

## 文件上传方案

### 接口设计
- **路径**：`POST /api/media/upload`
- **Content-Type**：`multipart/form-data`
- **参数**：
  - `file`: MultipartFile（文件，必填）
  - `category`: Byte（0=图片，1=视频，必填）
  - `visibleUserIds`: String（JSON数组字符串，必填但可为空数组[]，表示上传的内容在哪些用户的专区可见）
  - `title`: String（标题，可选，最多32个字符）
  - `description`: String（描述，可选，最多128个字符）
  - `cover`: MultipartFile（封面图片，可选，如果提供则使用用户上传的封面，否则按默认规则处理）
- **说明**：
  - `uploaderUserId` 从 JWT Token 中自动获取，不需要在请求参数中传递
  - `visibleUserIds` 格式：JSON数组字符串，例如 `[1,2,3]` 或 `[]`

### 当前实现逻辑（无事务版本）

**完整流程**：
1. 验证文件扩展名（图片/视频格式）
2. 读取文件到字节数组
3. 计算MD5哈希值（用于幂等性）
4. 幂等性检查：查询是否已存在相同文件（相同用户+相同哈希）
5. 如果存在：直接返回已有记录（幂等）
6. 如果不存在，则执行上传流程：
   - 生成MinIO路径
   - 确定封面路径（图片复用原图，视频：用户上传封面则生成路径，否则使用默认封面）
   - 保存 `media`（state=1，正在上传）
   - 上传主文件到MinIO
   - 上传成功：更新 state=2（上传成功），**重试3次**
     - 如果更新失败：删除MinIO文件，删除数据库记录，返回上传失败
     - 这里是保证数据一致性的关键，只要确认上传成功，后续media_visible和封面上传都可以通过重试接口重试。**就怕MinIO上传成功了，但mysql中记录的状态仍是未成功**，所以要重试多次确认没法让数据库及时同步后，删除MinIO中的数据
   - 上传失败：更新 state=3（上传失败），抛出异常
   - 保存 `media_visible`
   - 保存成功：更新 state=0（正常可查看）
   - 保存失败：state 保持 2（上传成功但不可见），不回滚（文件已上传）
   - 上传封面文件到MinIO（如果用户提供了封面，失败不影响主流程）

### 存储顺序（无事务）
1. MySQL: 保存 `media`（state=1，正在上传，包含封面路径）
2. MinIO: 上传主文件
3. MySQL: 更新 `media`（state=2，上传成功，重试3次）
   - 如果更新失败：删除MinIO文件 + 删除数据库记录 + 返回失败
4. MySQL: 保存 `media_visible`
5. MySQL: 更新 `media`（state=0，正常可查看）或保持 state=2（上传成功但不可见）
6. MinIO: 上传封面文件（如果用户提供了封面）

**状态控制机制**：
- **不使用事务**：通过 `state` 字段控制上传过程，简化代码逻辑
- **state 状态流转**：
  - `0=正常`：文件上传成功且可见权限已设置，可以正常查看
  - `1=正在上传`：已保存 media 记录，准备上传文件
  - `2=上传成功`：文件已上传到 MinIO，但可能未设置可见权限
  - `3=上传失败`：MinIO 上传失败
  - `4=正在删除`：删除操作进行中
  - `5=已删除`：删除操作已完成
- **失败处理**：
  - MinIO 上传失败：更新 state=3，抛出异常
  - **更新 state=2 失败**：重试3次，全部失败则删除MinIO文件和数据库记录，返回上传失败
  - `media_visible` 保存失败：state 保持 2，不回滚（文件已上传，可后续重试或手动修复）
  - 封面上传失败：不影响主流程，可记录日志或异步重试

**封面处理**：
- 封面路径在保存 media 时确定并保存到数据库
- 封面文件在主文件上传成功后上传（避免阻塞数据库操作，且不影响主流程）
- 如果用户上传了封面：使用用户上传的封面
- 如果用户未上传封面：图片复用原图路径，视频使用默认封面路径

### 后续优化方向
- **批量上传**：保持单文件接口，新增批量上传接口（限制文件数量/大小，或仅支持图片）
- **大文件流式处理**：引入Go服务处理大文件（分片上传、断点续传），Java端仅负责业务逻辑和调用Go服务
- **内存优化**：当前实现将文件读入内存，大文件可优化为流式处理

---

## 文件上传幂等性

### 问题场景
- 用户可能因网络重试、误操作等原因重复上传相同文件
- 数据库和对象存储无法直接判断文件内容是否相同
- 需要保证相同文件内容多次上传，结果一致

### 解决方案
**采用文件内容哈希（MD5）方案**

1. **数据库设计**：`media` 表新增 `file_hash` 字段（VARCHAR(64), NOT NULL, UNIQUE）
2. **实现逻辑**：
   - 上传时计算文件MD5哈希值
   - 查询数据库：`WHERE file_hash = ? AND user_id = ?`（已创建联合索引）
   - 如果存在：直接返回已有记录（幂等，不重复上传）
   - 如果不存在：正常上传并保存哈希值
3. **优势**：
   - 真正实现幂等性（相同内容=相同哈希）
   - 节省存储空间（相同文件只存一份）
   - 用户体验好（重复上传不报错，直接返回已有资源）

### 注意事项
- 当前实现将文件读入内存计算哈希，大文件可能占用内存
- 未来可优化为流式计算哈希（Go服务实现）

---

## 文件删除方案

### 接口设计
- **路径**：`DELETE /api/media/{mediaId}`
- **说明**：
  - `currentUserId` 从 JWT Token 中自动获取，不需要在请求参数中传递
  - 仅允许上传者本人删除（通过 JWT 中的 userId 校验）

### 当前实现逻辑（无事务版本）

**完整流程**：
1. 查询 media 并校验所有权（排除 state=4 和 state=5 的记录）
2. 保存原始状态，用于回滚
3. **第一步**：将 media 的 state 改为 4（正在删除），**重试3次**
   - 如果更新失败：返回删除失败
4. **第二步**：删除所有 media_visible 数据，**重试3次**
   - 如果删除失败：回滚 media 的 state 状态，返回删除失败
5. **第三步**：删除 MinIO 中的数据（主文件和封面文件）
   - 删除失败不影响主流程（数据库已标记为正在删除，用户已感知删除）
6. **第四步**：将 media 的 state 改为 5（已删除），**重试3次**
   - 即使更新失败也不影响（数据已标记为正在删除，可后续清理）

### 删除顺序（无事务）
1. MySQL: 更新 `media`（state=4，正在删除，重试3次）
   - 如果失败：返回删除失败
2. MySQL: 删除所有 `media_visible` 数据（重试3次）
   - 如果失败：回滚 state，返回删除失败
3. MinIO: 删除主文件和封面文件
   - 删除失败不影响主流程（记录日志）
4. MySQL: 更新 `media`（state=5，已删除，重试3次）
   - 更新失败不影响（已标记为正在删除，可后续清理）

**为什么不使用事务？**
1. **外部服务不在事务范围内**：MinIO 是外部服务，不在 MySQL 事务范围内，即使使用事务也无法保证数据库和 MinIO 的原子性
2. **需要细粒度重试控制**：每步需要独立重试3次，使用事务会让重试逻辑变复杂
3. **状态机设计提供容错**：通过 state 字段控制流程，即使某步失败，状态机也能提供容错能力
4. **性能考虑**：MinIO 删除可能耗时较长，放在事务中会长时间占用数据库连接
5. **用户体验优先**：一旦 media_visible 删除成功，用户就看不到该资源，MinIO 删除失败不影响用户体验

**失败处理机制**：
- **第一步失败**：直接返回删除失败，不修改任何数据
- **第二步失败**：回滚第一步的 state 状态，保证数据一致性
- **第三步失败**：记录日志，不影响主流程（数据库已标记为正在删除）
- **第四步失败**：记录日志，不影响主流程（数据已标记为正在删除，可后续清理）

**状态流转**：
- 正常状态（0/1/2/3） → `4=正在删除` → `5=已删除`
- 如果第二步失败：`4=正在删除` → 回滚到原始状态

---

## 树洞消息：抽取独立方法后为何用 TransactionTemplate 而非 @Transactional

### 背景
- `sendMessage` 拆成两个内部方法：`doDeliverNewMessage`（投递新留言）、`doReplyMessage`（主人回复）。
- 希望**仅回复分支**使用事务（插入回复 + 更新根消息原子性），投递分支不加事务。

### 为何不能在 `doReplyMessage` 上直接加 @Transactional？
**原因：自调用导致事务不生效（self-invocation）**

1. **@Transactional 的生效方式**：Spring 通过 **AOP 代理** 实现事务。外部调用 `treeHoleMessageService.sendMessage(...)` 时，实际先进入的是**代理对象**，代理在调用真实方法前开启事务、调用后提交或回滚。
2. **同类内部调用不经过代理**：`sendMessage` 内部执行 `return doReplyMessage(...)` 时，相当于 `this.doReplyMessage(...)`，这是**当前对象自己调自己的方法**，没有经过代理。
3. **结果**：贴在 `doReplyMessage` 上的 `@Transactional` 不会被代理处理，事务**不会开启**，等于没加。

### 可选做法对比
| 做法 | 说明 |
|------|------|
| 在 `doReplyMessage` 上加 @Transactional | 无效：同类自调用不经过代理，事务不生效。 |
| 把“回复”逻辑抽到**另一个 Bean**，在该 Bean 的方法上加 @Transactional，本类注入并调用 | 有效：调用的是另一个 Bean 的代理，事务会开启；但需要多一个类。 |
| 在 `doReplyMessage` 内用 **TransactionTemplate.execute(...)** 包住写库逻辑 | 有效：编程式事务，不依赖代理，谁调用都会在 `execute` 内开启事务；不需新增类。 |

### 本项目选择
- 采用 **TransactionTemplate**：在 `doReplyMessage` 内用 `transactionTemplate.execute(status -> { ... })` 包住“插入回复 + 更新根消息”，保证原子性。
- 不新增类，事务边界清晰，且不依赖“必须通过代理调用”这一前提。

---

## MediaServiceImpl 与 MediaVisibleServiceImpl 循环依赖

### 问题
- 启动报错：`mediaServiceImpl` ↔ `mediaVisibleServiceImpl` 构造器循环引用，Spring 禁止。

### 原因
- `MediaServiceImpl` 构造函数注入 `IMediaVisibleService`；`MediaVisibleServiceImpl` 构造函数注入 `IMediaService`，形成环。

### 解决方案
- **MediaVisibleServiceImpl** 中对 `IMediaService` 的依赖仅用于两处 `getById(mediaId)`（查库写 media:core 缓存）。
- 该类已注入 `MediaMapper`，将两处 `mediaService.getById(mediaId)` 改为 `mediaMapper.selectById(mediaId)`，并移除构造函数中的 `IMediaService` 参数。
- 打破循环的同时保持职责清晰：列表/缓存逻辑只需按 ID 查 Media，用 Mapper 即可，无需经 Media 业务服务。

---

## 联表查询流程（MyBatis 手写 SQL 方式）

与 MyBatis-Plus 的 `LambdaQueryWrapper` 单表查询不同，联表需要多张表 JOIN，一般用 **Mapper 接口 + XML 手写 SQL**，不依赖 Wrapper。

### 调用链（谁调谁、参数怎么传）

1. **Controller** 调 Service 的 `listSharedMessages(ownerId, page, size)`，`ownerId` 来自 JWT，`page/size` 来自请求参数。
2. **Service** 做校验和分页换算：`offset = (pageNum - 1) * pageSize`，然后调 **Mapper 的两个方法**：
   - `baseMapper.countSharedMessages(ownerId)` → 得到总条数；
   - `baseMapper.selectSharedMessageItems(ownerId, offset, limit)` → 得到本页数据列表。
3. **Mapper 接口**（Java）只做声明：方法名、参数用 `@Param("xxx")` 命名，返回值是 `List<DTO>` 或 `long`。**不写实现**，实现在 XML 里通过 **方法名 = SQL 的 id** 绑定。
4. **Mapper XML** 里：
   - `namespace` 必须是该 Mapper 接口的全限定名，这样 MyBatis 才能把 `<select id="selectSharedMessageItems">` 对应到接口里的 `selectSharedMessageItems`；
   - SQL 里用 `#{ownerId}`、`#{offset}`、`#{limit}` 占位，**名字必须和 @Param 一致**，MyBatis 会把接口方法入参按名字注入；
   - 多表 JOIN 写在一条 SQL 里；结果用 `resultMap`（或 `resultType`）映射成 Java 对象，列名与 DTO 属性对应（别名或 result 的 column→property）。

### 和 Wrapper 方式的区别

- **Wrapper**：单表、条件在 Java 里链式拼装，MyBatis-Plus 根据实体表名生成 SQL，**不需要写 XML**。
- **联表**：多表、JOIN 和复杂条件写在 **XML 的一条 SQL** 里；Mapper 接口只声明方法签名，**参数通过 @Param 传到 XML 的 #{}**，返回类型由 XML 的 resultMap/resultType 决定。

---

## 对象存储抽象：策略模式与实现

### 设计思路
- **策略模式**：上传、删除、存在性检查、预签名 URL 等同一类操作，多种实现可替换；调用方只依赖接口，不关心具体存储（MinIO/OSS）。
- **依赖倒置**：业务层（MediaServiceImpl、MediaVisibleServiceImpl）依赖 `StorageService` 接口，不依赖具体实现类；扩展新存储只需新增实现类并配置，业务代码无需改动。
- **运行时切换**：通过 Spring 依赖注入在运行时绑定具体实现（如 `@Primary` 指定默认实现），切换存储仅改配置或 Bean 条件即可。

### 实现方式
- **接口**：`StorageService`（upload 两种重载、delete、exists、getPresignedUrl）。
- **实现**：`MinioStorageService`、`OssStorageService` 实现该接口；当前默认 OSS（`OssStorageService` 标注 `@Primary`）。
- **配置**：`oss.*` / `minio.*` 在 application.yml 中配置；`OssConfig`、`MinioConfig` 分别创建 OSS 客户端或 MinioClient Bean。
- **业务层**：仅注入 `StorageService`，所有读写通过接口完成，与具体存储解耦。

---

## 媒体缓存设计

### 缓存方案：media:core 单一数据源
- **缓存 Key**：`media:core:{mediaId}`
- **缓存内容**：`Media` 实体完整字段（包括 `coverUrl` 预签名URL，12分钟有效）
- **TTL**：600秒（10分钟）
- **设计理念**：作为单一数据源，供 `MediaDetailResult`、`MediaListItem`、`UploadResult` 等不同返回结构选择性填充使用

### 设计理念：为何只缓存 state=0
- **media:core 缓存**仅缓存 state=0（已审核通过、公开）的媒体。
- **原因**：state=0 对所有人可见，单 key 即可；state=6/7 仅上传者或审核者可访问，若按 mediaId 单 key 缓存会越权，故不缓存，每次校验权限后直接查 DB。

### coverUrl 处理
- **写入缓存时**：动态生成预签名URL（12分钟有效），设置到 `Media.coverUrl` 字段
- **读取缓存时**：优先使用缓存中的 `coverUrl`，不存在则重新生成

### 使用缓存的方法
- **读**：`getMediaDetail` 先查 `media:core`，命中则从 `Media` 转换为 `MediaDetailResult`；未命中查 DB，仅 state=0 时写入缓存
- **删（写时删除）**：`update`、`updateCover`、`delete`、`approveMedia`、`rejectMedia` 在变更后调用 `evictMediaCore`；`rebuildVisible` 不删缓存

### 延迟双删
- **使用位置**：`delete` 方法。流程：第一次删缓存 → 物理删 media → 延迟 500ms 后再删一次缓存。
- **目的**：避免“删库后、删缓存前”并发请求读 DB 并回写缓存。

### 缓存删除失败不回滚数据库
- **设计原则**：数据库是主数据源，缓存失败不应影响数据库事务。
- **实现方式**：所有缓存删除操作用 try-catch 包裹，失败只记录日志，不抛出异常。
- **原因**：避免级联故障（Redis 故障不应导致业务失败），接受最终一致性（缓存 TTL 10分钟）。

---

## 缓存击穿防护：分布式锁

### 问题场景
- 热点数据过期时，大量并发请求同时访问数据库
- 缓存未命中，多个线程同时查询数据库，造成数据库压力骤增

### 解决方案
**分布式锁（Redis SETNX）+ 双重检测 + 重试机制**

### 设计理由
- 分布式锁保证跨 JVM/跨进程同步，防止多实例同时访问数据库
- 双重检测减少不必要的数据库访问（获取锁后再次检测缓存）
- 重试机制提升响应速度（等待期间其他线程可能已写入缓存）

### 实现步骤
1. **第一次检测缓存**：命中则返回，未命中进入锁竞争
2. **获取分布式锁**：使用 Redis `SETNX` 原子操作，Key: `lock:media:core:{mediaId}`，TTL: 5秒
3. **重试机制**：获取锁失败则等待100ms后重试查询缓存，最多重试3次
4. **第二次检测缓存**：获取锁后再次检测，命中则释放锁并返回
5. **查询数据库**：缓存仍未命中则查询数据库并写入缓存
6. **释放锁**：使用 `requestId` 标识锁持有者，Lua脚本原子性地检查并删除，防止误释放其他线程的锁
7. **锁续期**：获取锁成功后启动后台线程，每2秒自动续期，防止业务执行时间超过锁TTL导致锁过期

### 结果
- 保证同一时间只有一个请求访问数据库
- 重试过程中如果缓存命中，立即返回，减少等待时间
- 锁续期机制确保长时间业务不会导致锁过期
- 使用 `requestId` 防止误释放其他线程的锁

---

## 缓存穿透防护：空值缓存

### 问题场景
- 查询不存在的数据，缓存和数据库都没有
- 每次请求都访问数据库，造成数据库压力

### 解决方案
**空值缓存（Null Object Pattern）**

### 设计理由
- 将"数据不存在"这一结果也缓存，避免重复查询数据库
- 设置较短TTL（60秒），避免永久阻止新数据查询

### 实现步骤
1. **空值标识**：使用特殊字符串 `"__NULL__"` 作为空值标记
2. **写入时机**：查询数据库发现数据不存在时，在抛出 `NOT_FOUND` 前写入空值缓存
3. **空值识别**：`getMediaCore()` 中识别 `"__NULL__"` 并返回 `null`
4. **TTL设置**：空值缓存TTL为60秒（避免永久阻止新数据）
5. **空值清理**：数据创建时（`upload`、`approveMedia`）调用 `evictMediaCore()` 删除空值缓存

### 结果
- 60秒内重复查询不存在的数据，直接命中空值缓存，避免访问数据库
- 新数据创建后，空值缓存被清理，可正常查询

---

## 列表查询：双重分布式锁 + 空值缓存

### 设计理念
- **双重锁保护**：列表锁（`lock:media:list`）保护列表查询，media:core 锁（`lock:media:core`）保护单个媒体查询
- **二级缓存结构**：列表缓存（`media:list`）存储 total 和 mediaIds，核心数据缓存（`media:core`）存储完整 Media 实体
- **空值缓存**：列表为空时缓存 `{"total": 0, "mediaIds": []}`，media:core 不存在时缓存 `"__NULL__"`

### listMedia 实现流程

**第一步：查询列表缓存**
- 查询 `media:list:{zoneUserId}:{category}:{page}:{size}`
- 命中则进入"列表缓存命中"流程，未命中则进入"列表缓存未命中"流程

**第二步A：列表缓存命中**
1. 批量查询 `media:core`：`batchGetMediaCore(cachedMediaIds)`
2. 处理未命中的 media:core（加分布式锁）：
   - 对每个 `missingId` 获取 `lock:media:core:{mediaId}` 锁
   - 双重检测：获取锁后再次查询 `media:core` 缓存
   - 仍未命中则查询数据库并写入缓存（不存在则写入空值 `putNullValue`）
   - 锁续期：每2秒自动续期，确保锁不过期
3. 构建结果：按 `cachedMediaIds` 顺序，只保留 `state=0` 的媒体

**第二步B：列表缓存未命中**
1. 获取列表分布式锁：`lock:media:list:{zoneUserId}:{category}:{page}:{size}`
   - 最多重试3次，每次等待100ms后重试查询缓存
   - 获取锁成功后启动锁续期线程（每2秒续期）
2. 双重检测：获取锁后再次查询列表缓存，命中则释放锁并返回
3. 查询数据库：调用 `queryMediaListFromDB()` 查询 total 和 records
4. 写入缓存：调用 `writeMediaListCache()` 写入列表缓存和 media:core 缓存
   - 列表为空时写入空列表缓存：`{"total": 0, "mediaIds": []}`（TTL 300秒）
5. 释放锁：finally 块中停止续期线程并释放锁

### listMyUpload 实现流程

**第一步：查询列表缓存**
- 查询 `media:my:{uploaderId}:{category}:{page}:{size}`
- 命中则进入"列表缓存命中"流程，未命中则进入"列表缓存未命中"流程

**第二步A：列表缓存命中**
1. 批量查询 `media:core`：`batchGetMediaCore(cachedMediaIds)`
2. 处理未命中的 media:core（加分布式锁）：
   - 对每个 `missingId` 获取 `lock:media:core:{mediaId}` 锁
   - 双重检测：获取锁后再次查询 `media:core` 缓存
   - 仍未命中则查询数据库并写入缓存（不存在则写入空值 `putNullValue`）
   - 锁续期：每2秒自动续期，确保锁不过期
3. 构建结果：按 `cachedMediaIds` 顺序，排除 `state=5` 的媒体（允许显示 state=6/7）

**第二步B：列表缓存未命中**
1. 获取列表分布式锁：`lock:media:my:{uploaderId}:{category}:{page}:{size}`
   - 最多重试3次，每次等待100ms后重试查询缓存
   - 获取锁成功后启动锁续期线程（每2秒续期）
2. 双重检测：获取锁后再次查询列表缓存，命中则释放锁并返回
3. 查询数据库：调用 `queryMyUploadListFromDB()` 查询 total 和 records（排除 state=5）
4. 写入缓存：调用 `writeMyUploadListCache()` 写入列表缓存和 media:core 缓存
   - 列表为空时写入空列表缓存：`{"total": 0, "mediaIds": []}`（TTL 300秒）
5. 释放锁：finally 块中停止续期线程并释放锁

### 空值缓存设置

**列表空值缓存**
- 触发条件：查询结果为空（`records == null || records.isEmpty()`）
- 缓存内容：`{"total": 0, "mediaIds": []}`
- TTL：300秒（与正常列表缓存一致）
- 设置位置：`writeMediaListCache()` 和 `writeMyUploadListCache()` 方法中

**media:core 空值缓存**
- 触发条件：数据库查询不到数据（`media == null || media.getState() == null`）
- 缓存内容：`"__NULL__"`
- TTL：60秒
- 设置位置：`queryAndWriteMediaCore()` 方法中

### 锁续期机制

**WatchDog 机制**
- 获取锁成功后启动后台线程，每2秒自动续期
- 锁TTL是5秒，每2秒续期一次，确保锁不会过期
- 在 finally 块中优雅关闭续期线程（等待最多1秒）

---

## 点赞与查询已赞设计

### 设计概要（Redis + RocketMQ 方案）
- **排行榜**：Redis ZSET（`media:rank:all` / `media:rank:0` / `media:rank:1`），member=mediaId，score=点赞数。写路径：**先改 Redis（Lua 位图+ZSET）→ 发 RocketMQ → 同应用内消费者事务落库**；任意环节失败则回滚 Redis，保证与 DB 一致（见 Redis_DESIGN.md）。
- **是否已赞**：Redis bitmap `media:liked:{mediaId}`，offset=userId，bit=1 表示已赞；每人每媒体一把 bit，每媒体一个 key。
- **查询流程**：只查 Redis GETBIT，为 1 返回已赞；为 0 或 key 不存在视为未赞返回 false。点赞/取消点赞已先写 Redis 再 MQ 同步 DB，以 Redis 为准，不查 DB、不写回。
- **接口归属**：查询是否已赞归属用户点赞记录，路径 `GET /api/userLikeRecord/media/{mediaId}/status`。

### 点赞/取消点赞实现流程（Redis → RocketMQ → 落库，失败回滚 Redis）

**整体顺序**：接口层校验 → Redis（Lua 原子更新位图+双 ZSET）→ 发 RocketMQ（topic：media-like-topic，消息体 MediaLikeEvent）→ 消费者事务落库。两处回滚：① MQ 发送失败时接口层回滚 Redis；② 消费者落库失败时消费者回滚 Redis。

**点赞**
- **接口层（MediaServiceImpl.like）**：校验 media 存在且 state=0、category → 调 `RedisCacheMediaLikeService.like`（位图置 1 + 双 ZSET ZINCRBY 1；已赞过则返回 false 幂等）→ 若 true 则 `MediaLikeMqProducer.send(MediaLikeEvent.LIKE)`；发送失败则 `rollbackLike` 并抛 500。
- **消费者（MediaLikeMqConsumer）**：收到事件后 `MediaLikePersistService.persist`：事务内插入 user_like_record（已存在则跳过）、`MediaMapper.incrementLikeCount`；失败则 `rollbackLike` 后 rethrow，由 RocketMQ 重试或进死信。

**取消点赞**
- **接口层（MediaServiceImpl.unlike）**：校验 → `RedisCacheMediaLikeService.unlike`（位图置 0 + 双 ZSET 在 score>0 时 ZINCRBY -1；未赞过则返回 false 幂等）→ 若 true 则发 MQ(UNLIKE)；发送失败则 `rollbackUnlike` 并抛 500。
- **消费者**：事务内删除 user_like_record、`MediaMapper.decrementLikeCount`（SQL 内 GREATEST(0, like_count-1)）；失败则 `rollbackUnlike` 后 rethrow。

### 排行榜查询实现

- **一次 Redis 取数**：使用 ZREVRANGE WITHSCORES 一次取 Top N 的 member 与 score；Spring 返回 `Set<TypedTuple<String>>`，转成 `List<RankEntry>` 后按 score 降序、mediaId 升序排序，保证顺序与 Redis 一致。
- **ZSET 不存在**：不主动创建；直接返回空列表。ZSET 由点赞时的 ZINCRBY 在 key 不存在时自动创建，无需读时回源。
- **媒体信息补全**：按 mediaId 列表先批量查 media:core；未命中的 id 再按 id 加分布式锁、双重检测后查 DB 并写回 media:core，最后按顺序组装 HotListItem（likeCount 取自 ZSET score）。

### 详情展示点赞数（实时 likeCount）

- **约定**（与 Redis_DESIGN.md 一致）：列表/详情展示点赞数时，**先读 Redis ZSET 的 score，不存在再用 media:core/DB 的 likeCount**。
- **MediaDetailResult**：增加 `likeCount` 字段，供前端媒体详情页展示。
- **取值逻辑**（`MediaServiceImpl.resolveLikeCount(mediaId, media)`）：
  1. 调用 `RedisCacheMediaLikeService.getLikeCountFromRank(mediaId)`：对 `media:rank:all` 执行 **ZSCORE**，有值则返回该 score（即实时点赞数）。
  2. 若 ZSET 无该 member（新媒体或从未被点赞），则用当前 **Media** 的 `likeCount`；再缺则视为 0。
- **Media 来源**：当前这条 Media 来自 **media:core 缓存**还是 **数据库**，由 `getMediaDetail` 的「先查缓存、未命中再查 DB」逻辑决定；本逻辑不区分来源，只要在拼装 `MediaDetailResult` 时传入「当前用于详情的 Media」，即可在 ZSET 未命中时用其 `likeCount` 兜底。
- **实现位置**：`getMediaDetail` 所有返回路径（缓存命中三处、持锁查 DB 一处、未持锁降级查 DB 一处）在得到 `MediaDetailResult` 后统一执行 `result.likeCount = resolveLikeCount(mediaId, media)`。

### 缓存恢复策略（Redis 宕机后）
- Redis 宕机导致 ZSET 与 bitmap 丢失时，以 **user_like_record** 为数据源恢复缓存。
- **ZSET 恢复**：按 media_id 聚合统计每条 media 的点赞数，对 `media:rank:all` 与 `media:rank:{category}` 执行 ZADD（mediaId, count）。
- **Bitmap 恢复**：按 media_id 分组，对每个 mediaId 根据其 user_like_record 中的 user_id 列表，对 `media:liked:{mediaId}` 执行 SETBIT userId 1。
- 恢复任务可在 Redis 恢复后由定时任务或运维脚本触发执行。

### 查询已赞为何只读 Redis
- 点赞/取消点赞已采用「先写 Redis → MQ 同步 DB」；查询以 Redis 为准，只读 GETBIT，未命中（key 不存在或该位为 0）统一视为未赞，不查 DB、不写回，无需分布式锁。
- Redis 规定：对不存在的 key 执行 GETBIT 任意 offset 均返回 0，与「该用户位为 0」无法区分；当前策略不依赖区分二者，故无需「EXISTS + 仅 key 不存在时加锁」等方案。锁能力（tryLockLiked/unlockLiked/renewLockLiked）已预留，若后续需强一致读可再接入。
