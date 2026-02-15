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
