# 开发记录与反思

## 2026-01-18

### 当前进度
- ✅ 后端框架搭建完成（Spring Boot 3.3.1 + MyBatis-Plus）
- ✅ 数据库表创建完成
- ✅ 数据库连接配置完成
- ✅ 基础代码结构生成完成（Controller、Service、Mapper等）
- ✅ 统一响应格式类创建完成（Result、Response、ResponseCode）

### 下一步规划
根据项目需求，制定了详细的开发路线：
1. **先完成后端接口开发**，再开发前端
2. 按照功能模块优先级：认证系统 → 媒体管理 → 树洞功能

### 已完成工作
1. **创建统一响应格式类**
   - `Result<T>`: 主要的统一响应格式类，包含code、message、data字段
   - `Response<T>`: 通用响应类，包含时间戳字段，可作为Result的扩展
   - `ResponseCode`: 响应状态码枚举，定义了所有可能的响应状态码
   - 提供了丰富的静态方法，方便创建成功/失败的响应
   - 所有类都包含详细的中文注释，便于理解和使用

## 2026-01-21

### 当前进度
- ✅ 用户服务：登录 / 注册 / 注销 已用 Postman 测试通过
- ✅ Spring Security 标准化：引入 JWT 过滤器统一鉴权（不再在 Controller 手动解析 token）
- ✅ 适配数据库变更：`tree_hole_visible` 重命名为 `tree_hole_message_visible`，后端实体/Mapper/Service/Controller 全量同步
- ✅ 媒体上传（MVP第一步）：新增 `/api/media/upload`，上传到 MinIO 并写入 `media` + `media_visible`（按“本人 + 公共(可选) + 管理员列表”规则）

### 决策与原因
- 之前为排障临时将 `/api/user/deregister` 放行（permitAll），但这会留下安全隐患
- 现在改为：仅 `login/register` 放行，其他接口必须携带 `Authorization: Bearer <token>` 才能通过

### 反思与改进
- 依赖版本尽量保持同一套组件版本一致（例如 MyBatis-Plus 模块版本对齐），减少 IDE 假红与潜在冲突
- 鉴权建议集中在过滤器/中间件层做，业务层只关心“当前用户是谁”

## 2026-01-21（媒体下载/删除：一致性策略补全）

### 新增能力
- ✅ 媒体下载：`GET /api/media/{id}/download` 返回 MinIO 预签名 URL（2小时有效）
- ✅ 下载前校验：同时检查数据库 `media` 记录是否存在、MinIO 对象是否存在（为未来引入 Redis 缓存的不一致场景预留）
- ✅ 媒体删除：`DELETE /api/media/{id}/delete`
  - 仅允许上传者本人删除（JWT 的 `userId` 必须匹配 `media.user_id`）
  - 数据库内 `media_visible` 与 `media` 删除在同一个事务里，确保原子性
  - 事务提交成功后再删除 MinIO 对象（保证“先清库后删对象存储”）

### 反思与改进
- 数据库事务无法天然覆盖对象存储（MinIO）操作：需要采用“事务内改库 + 事务提交后回调删对象”的方式，保证顺序与可恢复性
- MinIO 删除失败时（网络抖动/手动操作等）会遗留“垃圾对象”，但不影响业务正确性；后续可用定时任务做清理补偿（MVP暂不引入）
- **事务机制理解**：`@Transactional` 只管理数据库操作（通过绑定数据库连接到当前线程），方法内的非数据库操作（如 MinIO HTTP 调用）天然不在事务范围内；使用 `TransactionSynchronization.afterCommit()` 确保数据库提交成功后再执行 MinIO 删除，若事务回滚则回调不执行
- **文件名处理优化**：用户上传的文件名可能包含中文，为避免路径兼容性问题，优化 `buildObjectName()` 方法：只提取扩展名，文件名使用 UUID（纯英文数字），确保存储路径只包含英文、数字和日期分隔符，兼容 MinIO/OSS 等对象存储

## 2026-01-22（文件上传幂等性实现）

### 新增能力
- ✅ 文件上传幂等性：通过文件内容MD5哈希值实现，相同用户上传相同文件内容时直接返回已有记录，避免重复上传和存储浪费
- ✅ 数据库扩展：`media` 表新增 `file_hash` 字段（VARCHAR(64), NOT NULL, UNIQUE），用于存储文件MD5哈希值

### 实现方案
- 上传时计算文件MD5哈希值，查询数据库（`file_hash + user_id` 联合索引），如果存在则返回已有记录，不存在则正常上传并保存哈希值
- 当前实现将文件读入内存计算哈希，适合小文件；未来大文件可优化为流式处理（Go服务）

### 反思与改进
- 幂等性方案选择：文件内容哈希方案优于上传令牌、时间窗口限制等方案，真正实现幂等性且节省存储空间
- 批量上传设计：MVP阶段保持单文件上传，后续可新增批量上传接口（限制文件数量/大小，或仅支持图片），批量接口内部复用单文件上传逻辑

## 2026-01-31（媒体列表/详情：按“专区”进行可见性过滤）

### 新增/调整能力
- ✅ 媒体列表：`GET /api/media/list` 支持 `currentUserId` 作为“专区ID”
  - `currentUserId=0`：公共区
  - `currentUserId=成员ID`：成员专区
- ✅ 媒体详情：`GET /api/media/{id}` 同样支持 `currentUserId` 作为“专区ID”，不可见返回404（不泄露资源存在性）
- ✅ 分页支持：补充 MyBatis-Plus 分页拦截器配置（`MybatisPlusConfig`），确保分页 SQL 正常生成

### 反思与改进
- “当前登录用户ID”与“当前所在专区ID”是两个概念：为了便于前端实现专区切换，接口使用 `currentUserId` 表达“专区ID”（历史命名可后续再收敛为 `zoneUserId`）
- 下载接口目前仍沿用“展示层已过滤”的假设：后续若担心通过猜测ID绕过专区过滤，可在下载接口也引入 `currentUserId(专区ID)` 校验

## 2026-01-31（专区仅用于筛选：详情不做专区校验）

### 调整原因
- 产品定义为“全部资源默认公共可见（media_visible 必写 user_id=0）”，专区只是帮助用户筛选喜欢的成员内容，不作为权限系统。

### 调整内容
- ✅ 媒体详情接口不再依赖 `currentUserId(专区ID)` 做可见性校验：只要资源存在且 `state=0` 就返回详情。
- ✅ 列表接口仍按 `currentUserId(专区ID)` 做筛选：`0=公共区（全量）`；成员专区仅返回写了该成员ID记录的资源。

## 2026-01-31（上传后默认公共区：不再写入上传者本人到 media_visible）

### 调整原因
- `media_visible` 在当前产品设计中只用于“专区展示筛选”，而“上传者本人管理自己的上传内容”可以直接通过 `media.uploader_id` 查询完成。

### 调整内容
- ✅ 上传成功后 `media_visible` 只做两类写入：
  - 额外写入 `visibleUserIds` 里的成员专区ID（需要展示到哪个成员专区就写哪个）
- ✅ 不再写入 `user_id=0`：公共区展示直接查 `media`（永远全部公开）
- ✅ 不再默认写入“上传者本人 user_id=上传者ID”的记录，避免把“专区”语义与“归属/权限”混在一起

## 2026-01-31（新增：我的上传列表接口）

### 新增能力
- ✅ 我的上传列表：`GET /api/mediaVisible/my/list`
  - 用途：上传者本人管理自己的上传内容
  - 查询方式：直接查 `media.uploader_id=当前登录用户`（不依赖 `media_visible`）

## 2026-01-31（索引建议文档沉淀）

### 新增文档
- 📝 新增 MySQL 索引建议清单：`MYSQL_INDEXES.md`
  - 说明：基于当前 `entity` 字段与已实现/规划的查询方式整理，不包含主键索引，按表分组，记录单列与联合索引建议。

## 2026-01-31（树洞MVP接口设计定稿：sent/messages + 防刷）

### 关键决策
- 树洞主人固定为 12 位后浪成员：树洞数据提前写入数据库，普通用户不创建树洞，因此不做“创建树洞/主人列表”对外接口
- 留言为单向写：MVP 不做树洞主人回复
- 可见性：主人看全部；投递者只能看自己投递的留言；投递者不看到已删除留言
- 防刷：同一投递者对同一树洞，上一条未读前禁止再次投递；“读”定义为主人显式标记已读/删除

### 接口命名
- `POST /api/treehole/{ownerId}/sent/messages`
- `GET /api/treehole/{ownerId}/messages`
- `PUT /api/treehole/messages/{messageId}/state`
- `PUT /api/treehole/{ownerId}/state`（可选）

## 2026-02-02（新增：手机号重置密码接口）

### 新增能力
- ✅ 通过手机号重置密码：`POST /api/user/resetPasswordByPhone`
  - MVP 简化：只要手机号存在即可重置密码（不需要登录）
  - 安全配置：该接口需要在 `SecurityConfig` 放行

### 决策说明
**为什么先开发后端接口？**
- 后端接口是前端的基础，接口设计好了前端开发会更顺畅
- 可以使用Postman等工具先测试接口功能
- 前后端可以并行开发，但需要先定义好接口规范
- 后端逻辑相对复杂，先完成后端可以确保核心功能稳定

### 待解决问题
1. JWT依赖需要添加到pom.xml
2. 阿里云OSS SDK依赖需要添加
3. 统一响应格式需要设计（Result/Response类）
4. 异常处理机制需要建立

### 已确定方案
1. ✅ 文件存储：使用阿里云OSS，MySQL存储资源地址
2. ✅ 接口响应格式：统一使用Result格式（code, message, data）
3. ✅ 前端技术栈：Vue
4. ✅ 文件格式：支持常见图片和视频格式，MVP阶段不限制大小

### 改进建议
1. 考虑使用统一响应格式，方便前端处理（已确定）
2. 建议添加全局异常处理器
3. 建议添加接口文档（Swagger/OpenAPI）
4. 文件上传需要验证文件类型（图片和视频常见格式）
5. OSS配置信息需要妥善保管，建议使用环境变量或配置中心

## 2026-02-02（结构调整：树洞留言列表归入 TreeHoleMessageVisible 系列）

### 调整原因
- 为了与媒体模块保持一致：**展示型列表接口统一放到 Visible 系列**，核心写入/状态修改留在原系列。

### 具体改动
- 将“留言列表”能力从 `TreeHoleMessage` 系列迁移到 `TreeHoleMessageVisible` 系列：
  - Controller：`GET /api/treehole/{ownerId}/messages` 从 `TreeHoleController` 移到 `TreeHoleMessageVisibleController`
  - Service：`listMessages(...)` 从 `ITreeHoleMessageService` 移到 `ITreeHoleMessageVisibleService`
- 对外接口路径不变，仅做代码职责归类调整。

## 2026-02-02（接口调整：留言已读接口语义化）

### 调整内容
- 将“标记已读”接口调整为无请求体的语义化接口：
  - `PUT /api/treehole/messages/{messageId}/read`
- 删除旧的兼容接口与参数结构：
  - 移除 `PUT /api/treehole/messages/{messageId}/state`
  - 移除 DTO：`TreeHoleUpdateMessageStateRequest`

## 2026-02-02（结构调整：正常留言列表归回 TreeHoleMessage，Visible 系列用于分享区）

### 调整原因
- 正常留言展示属于树洞核心消息能力（TreeHoleMessage 系列）。
- `tree_hole_message_visible` 的语义更适合承接“分享给其他树洞主人可见”的关系数据，因此单独作为分享区（收件箱）展示。

### 调整内容
- `GET /api/treehole/{ownerId}/messages`：回归 TreeHoleMessage 系列（树洞正常消息展示）。
- 新增分享收件箱接口：
  - `GET /api/treeholeMessageVisible/shared/list`

## 2026-02-03（新增：树洞黑名单表代码框架）

### 新增内容
- 新增数据表：`tree_hole_blacklist`
- 通过代码生成器生成框架代码：
  - Entity：`TreeHoleBlacklist`
  - Mapper：`TreeHoleBlacklistMapper`
  - Service：`ITreeHoleBlacklistService`
  - ServiceImpl：`TreeHoleBlacklistServiceImpl`
  - Controller：`TreeHoleBlacklistController`

### 结构规范调整
- 将 `TreeHoleBlacklistController` 的路径前缀统一为：`/api/treeholeBlacklist`

## 2026-02-03（新增：树洞留言删除接口框架）

### 新增接口
- 树洞主人删除留言（全局删除）：`DELETE /api/treehole/messages/{messageId}`
- 发送者删除留言（仅对发送者不可见）：`DELETE /api/treehole/messages/{messageId}/sender`

### 代码结构
- Controller：`TreeHoleMessageController`
- Service：`ITreeHoleMessageService`
- ServiceImpl：`TreeHoleMessageServiceImpl`（方法体保留 TODO）

## 2026-02-XX（实现：创建树洞消息功能，含投递与回复）

### 实现内容
- ✅ **新建树洞消息接口**：`POST /api/treehole/{ownerId}/sent/messages` 统一处理投递新留言与树洞主人回复
  - 请求体：`content`（必填）、`rootMessageId`（可选；为空=投递新留言，非空=主人回复该条留言）
  - 投递新留言：校验树洞存在且未关闭、发送者未被拉黑、防刷（该发送者对该树洞无未读留言）；写入 `tree_hole_message`（`root_message_id`、`reply_message_id` 为空，`update_time` 设当前时间）；必要时更新树洞 state 为“有未读”
  - 树洞主人回复：校验当前用户为树洞主人、根消息存在且未删除且未回复；在事务内插入回复消息（`root_message_id=rootMessageId`，`update_time` 等）、更新根消息的 `reply_message_id`、`state=1`（已读）、`update_time`，保证原子性
- ✅ **实体与表字段**：`tree_hole_message` 使用 `root_message_id`、`reply_message_id`（Long）、`update_time`；回复时根消息自动标已读并填写 `reply_message_id`
- ✅ **事务与结构**：投递分支无事务；回复分支使用 `TransactionTemplate` 包裹“插入回复 + 更新根消息”，仅回复分支具备事务原子性。`sendMessage` 拆分为 `doDeliverNewMessage`（投递）与 `doReplyMessage`（回复），主方法只做校验与分支分发

### 文档同步
- 开发文档（development.md）：树洞留言表补充 `root_message_id`、`reply_message_id`、`update_time` 说明
- API 设计（API_DESIGN.md）：投递接口请求体增加可选 `rootMessageId`，业务逻辑区分投递与回复，并写明 `root_message_id`/`reply_message_id`/`update_time` 的写入与更新
- 索引建议（MYSQL_INDEXES.md）：`tree_hole_message` 字段列表补充 `root_message_id`、`reply_message_id`、`content`、`update_time`

## 2026-01-31（文档同步：媒体更新接口拆分）

### 同步内容
- 将媒体模块“更新能力”在文档中拆分并补齐为 3 个独立接口：
  - `PUT /api/media/{id}`：仅更新标题/描述（不改文件、不改封面、不改可见范围）
  - `PUT /api/media/{id}/cover`：独立封面更新（先MinIO后DB，DB失败补偿删MinIO）
  - `PUT /api/media/{id}/visible`：独立可见范围修复（仅DB；方案C差量同步；事务保证原子性；visibleUserIds最多12个）
- 同步上传接口参数：补充 `title`、`description`、`cover` 的说明。
- 对齐下载接口说明：下载接口在实现上不强制要求JWT（如网关策略要求，可继续携带）。

## 2026-02-10（并发与幂等优化：媒体删除收尾 + 树洞投递防刷）

### 媒体删除（幂等收尾）
- 删除接口允许 **state=4（正在删除）** 时重复调用，用于在上一次删除中途失败后继续清理 `media_visible` / MinIO 并最终置为 **state=5**。
- `media_visible` 删除在“0 行受影响”时视为幂等成功，避免重复删除导致误判失败。

### 树洞投递防刷（并发）
- 为防止同一投递者快速连点绕过“同一树洞仅允许一条未读”，在投递新留言分支引入事务内 **`SELECT ... FOR UPDATE`** 行锁（锁 `tree_hole(owner_id)`），保证 `count + insert` 原子性。

### 文档同步
- `API_DESIGN.md`：更新媒体删除幂等说明；投递留言业务逻辑补充事务加锁步骤，并移除未启用的“更新 tree_hole.state=1”描述。
- `development.md`：补充媒体删除幂等收尾说明；补充投递留言的并发防刷加锁说明。
