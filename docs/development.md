# 开发文档 - Dragons Zone

## 项目简介

这是一款面向粉丝的对象存储系统，旨在为粉丝提供一个安全、便捷的图片/视频存储和互动平台。

## 技术栈

### 后端
- **框架**: Spring Boot 3.3.1
- **语言**: Java 17
- **数据库**: MySQL
- **ORM**: MyBatis-Plus 3.5.15
- **安全**: Spring Security + JWT
- **对象存储**: MinIO（本地开发，MVP阶段），预留接口便于未来迁移到阿里云OSS
- **构建工具**: Maven

### 前端
- **框架**: Vue
- 待开发

## 项目结构

```
dragons-zone/
├── dragons-core-server/          # 后端服务
│   ├── src/main/java/com/dragons/core/
│   │   ├── config/              # 配置类（Security、JWT等）
│   │   ├── controller/          # 控制器层（API接口）
│   │   ├── service/             # 服务接口层
│   │   ├── serviceImpl/         # 服务实现层
│   │   ├── dao/                 # 数据访问层（Mapper）
│   │   ├── entity/              # 实体类
│   │   ├── dto/                 # 数据传输对象（待创建）
│   │   └── util/                # 工具类（待创建）
│   └── src/main/resources/
│       ├── application.yml      # 应用配置
│       └── mapper/              # MyBatis XML映射文件
└── development.md               # 开发文档
```

## 核心功能

### MVP版本功能

#### 1. 用户认证系统
- **JWT登录**: 使用JWT（JSON Web Token）实现无状态的用户认证
- **密码加密**: 使用BCrypt等安全算法对用户密码进行加密存储
- **用户角色**: 支持作者、管理员、普通用户、游客四种角色

#### 2. 媒体文件管理
- **文件上传**: 支持图片和视频文件的上传到阿里云OSS
- **文件查看**: 支持公共区/成员专区的媒体浏览（专区用于筛选展示）
- **文件下载**: 支持媒体文件的下载功能
- **文件删除**: 支持文件删除（逻辑删除）

#### 3. 粉丝留言树洞
- **树洞创建**: 用户可以创建自己的树洞
- **留言功能**: 粉丝可以向树洞投递留言
- **权限控制**: 支持树洞的可见性控制

### 后续优化功能（非MVP）
- 缓存系统（Redis）
- Go语言实现大文件分片上传
- 断点续传功能
- 更多用户体验优化

## 数据库设计

### 核心数据表

1. **user** - 用户表
   - 存储用户基本信息、登录凭证、角色权限等

2. **media** - 媒体资源表
   - 存储图片/视频的元数据信息
   - storage_path字段存储阿里云OSS的资源地址

3. **media_visible** - 媒体可见性表
   - 控制媒体文件的访问权限

4. **tree_hole** - 树洞表
   - 存储树洞的基本信息

5. **tree_hole_message** - 树洞留言表
   - 存储树洞中的留言内容
   - 核心字段：`tree_hole_id`、`tree_hole_owner_id`、`sender_id`、`content`、`state`、`sender_deleted`
   - 回复关联：`root_message_id`（当前消息回复哪条，空表示用户投递）、`reply_message_id`（回复当前消息的那条消息 id，仅支持一条回复）
   - 时间：`update_time`（最近更新时间，用于排序与未读提醒）

6. **tree_hole_visible** - 树洞可见性表
   - 控制树洞的访问权限

## 文件存储方案

### 对象存储（MVP阶段：MinIO，未来：阿里云OSS）
- **MVP阶段**: 使用MinIO本地对象存储（便于开发调试，节约成本）
- **未来迁移**: 预留StorageService抽象接口，迁移到阿里云OSS只需替换实现类
- **存储方式**: 对象存储服务存储实际文件
- **数据库存储**: MySQL中仅存储对象存储路径（storage_path字段）
- **文件格式**: 
  - 图片：jpg, jpeg, png, gif, webp, bmp等常见格式
  - 视频：mp4, avi, mov, wmv, flv, mkv等常见格式
- **文件大小**: MVP阶段暂不限制（后续优化时添加）
- **专区展示**: 项目采用“永远全部公开”的产品定义：
  - 公共区列表直接查询 `media`（`state=0`），不依赖 `media_visible`
  - `media_visible` 仅用于成员专区筛选：需要展示到某成员专区时，写入 `user_id=成员ID`

## 开发路线

### 第一阶段：后端接口开发（当前阶段）

#### 步骤1：JWT认证系统 ✅
- [x] 添加JWT依赖（jjwt）
- [x] 创建JWT工具类（生成、解析、验证Token）
- [x] 实现密码加密工具类（BCrypt）
- [x] 创建统一响应格式类（Result）
- [x] 创建全局异常处理器
- [x] 创建登录接口（/api/user/login）
- [x] 创建注册接口（/api/user/register）
- [x] 创建注销接口（/api/user/deregister）
- [x] 创建重置密码接口（/api/user/resetPasswordByPhone）- 已登录用户通过手机号重置本人密码（需 JWT）
- [x] 创建未登录找回密码接口（/api/user/forgotPassword）- 通过登录名+手机号校验身份后修改密码，无需验证码（permitAll）
- [x] 实现JWT过滤器（拦截请求，验证Token）
- [x] 配置Spring Security集成JWT

#### 步骤2：对象存储集成 ✅
- [x] 添加MinIO SDK依赖（MVP阶段使用MinIO本地开发）
- [x] 配置MinIO连接信息（Endpoint、AccessKey等）
- [x] 创建存储抽象接口（StorageService），便于未来迁移到阿里云OSS
- [x] 实现MinIO存储服务（MinioStorageService）
- [x] 实现文件类型验证工具
- [ ] 未来：迁移到阿里云OSS（替换实现类即可）

#### 步骤3：媒体文件管理接口（部分完成）
- [x] 文件上传接口（/api/media/upload）- 上传到MinIO，保存路径到数据库，支持可见权限控制；上传完成后状态为 `state=6`（待审核）
- [x] 媒体基础信息更新接口（PUT /api/media/{id}）- 仅更新 title/description（不改文件、不改封面、不改可见范围）；允许 `state=0/6/7` 更新；`state=7` 修改后自动重置为 `state=6` 需重新审核
- [x] 视频封面更新接口（PUT /api/media/{id}/cover）- 独立操作：先上传MinIO，再更新DB；DB失败则补偿删除MinIO封面
- [x] 媒体可见范围修复接口（PUT /api/media/{id}/visible）- 独立操作：仅更新 media_visible，方案C差量同步，事务保证原子性（visibleUserIds最多12个）
- [x] 文件列表查询接口（/api/mediaVisible/list）- 支持按专区 `currentUserId` 筛选（0=公共区，成员ID=成员专区）；仅显示 `state=0`（已审核通过）的媒体；**游客模式**，无需登录/请求头
- [x] 我的上传列表接口（/api/mediaVisible/my/list）- 上传者本人管理自己的上传内容（直接查 media.uploader_id）；显示所有状态（排除 `state=5` 已删除），包括待审核和审核未通过状态
- [x] 文件详情查询接口（/api/media/{id}）- 详情不依赖专区参数；仅显示 `state=0`（已审核通过）的媒体；**游客模式**，无需登录/请求头
- [x] 文件下载接口（/api/media/{id}/download）- 返回MinIO预签名URL（2小时有效）；仅允许下载 `state=0`（已审核通过）的媒体；**游客模式**，无需登录/请求头
- [x] 文件删除接口（/api/media/{id}/delete）- 仅上传者可删除；软删除：media.state→4，删 media_visible，删 MinIO，media.state→5
- [x] 媒体审核通过接口（POST /api/media/audit/approve）- 批量审核通过，将 `state=6`（待审核）改为 `state=0`（正常）；仅管理员或作者可操作；非事务性，返回失败项列表
- [x] 媒体审核驳回接口（POST /api/media/audit/reject）- 批量审核驳回，将 `state=6`（待审核）改为 `state=7`（审核未通过）；仅管理员或作者可操作；非事务性，返回失败项列表
- [x] 待审核媒体列表接口（GET /api/media/audit/pending）- 分页查询 `state=6`（待审核）的媒体列表；仅管理员或作者可访问
- [x] 查询媒体所属成员专区接口（GET /api/mediaVisible/{mediaId}/zones）- 根据媒体ID查询该媒体属于哪些成员专区；返回成员专区ID列表；**游客模式**，无需登录/请求头

#### 步骤4：树洞功能接口 ✅
- [x] 投递留言接口（/api/treehole/{ownerId}/sent/messages）- 带防刷：上一条未读前禁止重复投递；同一接口支持可选 rootMessageId 做主人回复，回复时根消息自动标已读
- [x] 留言列表接口（/api/treehole/{ownerId}/messages）- 主人看全部；投递者只看自己；不返回已删除留言
- [x] 留言已读接口（/api/treehole/messages/{messageId}/read）- 仅主人可改（标记已读）
- [x] 树洞主人删除留言（DELETE /api/treehole/messages/{messageId}）- 全局删除
- [x] 发送者删除留言（DELETE /api/treehole/messages/{messageId}/sender）- 仅对发送者不可见
- [x] 树洞开关接口（/api/treehole/{ownerId}/state）- 仅主人可改（允许/禁止投递）
- [x] 分享留言接口（POST /api/treehole/{ownerId}/messages/{messageId}/share）- 树洞主人将一条留言分享给其他树洞主人（可多人）；写入 tree_hole_message_visible，幂等；部分失败返回「分享给xxx失败」，写操作重试 3 次
- [x] 分享收件箱列表接口（GET /api/treeholeMessageVisible/shared/list）- 树洞主人查看别人分享给自己的留言，分页（page、size）

### 后端闭环检查确认（可进入前端开发）

- **接口覆盖**：用户（登录/注册/注销/重置密码/找回密码）、媒体（上传/更新/封面/可见范围/列表/详情/下载/删除、我的上传、审核通过/驳回/待审核列表、查询所属成员专区）、树洞（投递与回复/留言列表/已读/主人删除/发送者删除/开关/分享/分享收件箱）、树洞黑名单（拉黑）均已实现，与 API_DESIGN.md、development 清单一致。
- **鉴权**：permitAll 仅开放登录/注册/找回密码及游客模式（GET 媒体列表、媒体详情、下载链接）；其余接口需 JWT，Controller 层对 principal 做 null 校验并返回 401。
- **异常与响应**：BusinessException 由 GlobalExceptionHandler 统一转为 Result.error；Result 统一带 code/message/data/timestamp。
- **业务逻辑**：媒体软删除与 MinIO 顺序、上传 state=2 写库失败回滚 MinIO、封面更新补偿、可见范围差量同步与事务、树洞防刷与回复原子性、分享部分失败动态文案等已按文档实现，未发现逻辑错误。
- **结论**：后端接口与业务逻辑已闭环，可进入第二阶段前端开发。

### 第二阶段：前端开发
- [ ] Vue项目搭建
- [ ] 登录/注册页面
- [ ] 文件上传页面
- [ ] 文件浏览页面
- [ ] 树洞页面
- [ ] 前后端联调

### 第三阶段：优化与测试
- [ ] 接口测试
- [ ] 性能优化
- [ ] 安全性测试
- [ ] 用户体验优化

## API接口设计

### 统一响应格式

所有接口统一使用以下响应格式：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    // 具体数据
  }
}
```

**状态码说明**:
- 200: 成功
- 400: 请求参数错误
- 401: 未授权（未登录或Token过期）
- 403: 无权限
- 404: 资源不存在
- 500: 服务器内部错误

### 认证相关接口

#### POST /api/user/register
用户注册接口 ✅

**请求参数**:
```json
{
  "loginName": "用户名",
  "password": "密码",
  "nickName": "昵称",
  "phoneNumber": "手机号（必填，需唯一）"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "userId": 1,
    "loginName": "用户名"
  },
  "timestamp": 1705564800000
}
```

#### POST /api/user/login
用户登录接口 ✅

**请求参数**:
```json
{
  "loginName": "用户名",
  "password": "密码"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "JWT_TOKEN_STRING",
    "userInfo": {
      "id": 1,
      "loginName": "用户名",
      "nickName": "昵称",
      "level": 2
    }
  },
  "timestamp": 1705564800000
}
```

#### POST /api/user/deregister
用户注销接口 ✅

**请求头**: `Authorization: Bearer <JWT_TOKEN>`

**请求参数**:
```json
{
  "password": "密码（二次确认）"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "注销成功",
  "data": null,
  "timestamp": 1705564800000
}
```

#### POST /api/user/resetPasswordByPhone
已登录用户通过手机号重置本人密码 ✅

**请求头**: `Authorization: Bearer <JWT_TOKEN>`

**请求参数**:
```json
{
  "phoneNumber": "手机号",
  "newPassword": "新密码"
}
```
说明：手机号须与当前登录用户一致，用于二次确认；新密码 6～64 位。

#### POST /api/user/forgotPassword
未登录找回密码（忘记密码）✅

**说明**：无需登录，路径已配置为 permitAll。通过登录名+手机号校验身份后修改密码；不依赖短信验证码（短信需申请签名/模板等，故采用“登录名+手机号”方案）。

**请求参数**:
```json
{
  "loginName": "登录名",
  "phoneNumber": "注册时绑定的手机号",
  "newPassword": "新密码（6～64位）"
}
```

**成功响应**（200）:
```json
{
  "code": 200,
  "message": "密码已重置，请使用新密码登录",
  "data": null,
  "timestamp": 1705564800000
}
```

**失败响应**：
- 400：参数缺失或新密码长度不符
- 4010：手机号格式不正确
- 4017：登录名与手机号不匹配（用户不存在或手机号错误时统一返回，避免泄露信息）
- 4007/4008：用户已注销或已拉黑

### 媒体文件相关接口

#### POST /api/media/upload
上传媒体文件到MinIO ✅

**请求头**: `Authorization: Bearer <JWT_TOKEN>`

**请求**: multipart/form-data
- file: 文件（图片或视频）
- category: 0=图片, 1=视频
- visibleUserIds: JSON数组字符串，例如 `[1,2,3]` 或 `[]`（成员专区ID列表，必填但可为空）
- title: 标题（可选）
- description: 描述（可选）
- cover: 封面图片（可选，仅视频建议传；也可以后续用封面更新接口单独上传）

**响应示例**:
```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "mediaId": 1,
    "storagePath": "images/2026/01/21/abc123-test.jpg",
    "category": 0,
    "visibleUserIds": [1, 2, 3]
  },
  "timestamp": 1705564800000
}
```

#### PUT /api/media/{id}
更新媒体基础信息（仅标题/描述，不改文件/封面/可见范围）✅

**请求头**: `Authorization: Bearer <JWT_TOKEN>`

**请求**: form-data 或 x-www-form-urlencoded
- title: 标题（可选）
- description: 描述（可选）

**响应示例**:
```json
{
  "code": 200,
  "message": "更新成功",
  "data": {
    "mediaId": 1,
    "storagePath": "images/2026/01/21/abc123-test.jpg",
    "category": 0,
    "visibleUserIds": null
  },
  "timestamp": 1705564800000
}
```

#### PUT /api/media/{id}/cover
更新视频封面（独立操作：先MinIO后DB，DB失败补偿删MinIO）✅

**请求头**: `Authorization: Bearer <JWT_TOKEN>`

**请求**: multipart/form-data
- cover: 封面图片（必填）

**响应示例**:
```json
{
  "code": 200,
  "message": "封面更新成功",
  "data": {
    "mediaId": 1,
    "coverPath": "covers/2026/01/31/abc123-cover.jpg"
  },
  "timestamp": 1705564800000
}
```

#### PUT /api/media/{id}/visible
修复/重建媒体可见范围（独立操作：仅DB；方案C差量同步；事务保证原子性）✅

**请求头**: `Authorization: Bearer <JWT_TOKEN>`

**请求**: form-data 或 x-www-form-urlencoded
- visibleUserIds: JSON数组字符串，例如 `[1,2,3]` 或 `[]`（必填但可为空；最多12个）

**响应示例**:
```json
{
  "code": 200,
  "message": "可见范围修复成功",
  "data": {
    "mediaId": 1,
    "storagePath": "images/2026/01/21/abc123-test.jpg",
    "category": 0,
    "visibleUserIds": [1, 2, 3]
  },
  "timestamp": 1705564800000
}
```

#### GET /api/mediaVisible/list
获取媒体文件列表（**游客模式**：无需登录，无需请求头）

**查询参数**:
- page: 页码（默认1）
- size: 每页数量（默认10）
- category: 类型筛选（可选，0=图片, 1=视频）
- currentUserId: 专区ID（可选，默认0；0=公共区，成员ID=成员专区）

**响应示例**:
```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "total": 100,
    "list": [
      {
        "id": 1,
        "category": 0,
        "title": "标题（可为空）",
        "coverPath": "images/2026/01/21/xxx.jpg"
      }
    ]
  }
}
```

#### GET /api/media/{id}
获取媒体文件详情（**游客模式**：无需登录，无需请求头）

**响应示例**:
```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "id": 1,
    "category": 0,
    "title": "标题（可为空）",
    "description": "描述（可为空）",
    "storagePath": "images/2026/01/21/xxx.jpg",
    "coverPath": "images/2026/01/21/xxx.jpg",
    "uploaderId": 1,
    "updateTime": "2026-01-31T12:34:56"
  }
}
```

#### GET /api/media/{id}/download
获取媒体文件下载URL（MinIO预签名URL，2小时有效）（**游客模式**：无需登录，无需请求头）✅

**响应示例**:
```json
{
  "code": 200,
  "message": "获取成功",
  "data": {
    "downloadUrl": "http://localhost:9000/dragons-media/images/2026/01/21/abc123.jpg?X-Amz-Algorithm=...&X-Amz-Expires=7200&..."
  },
  "timestamp": 1705564800000
}
```

**注意**: 下载前会检查数据库记录和MinIO文件是否存在，防止缓存不一致问题

#### DELETE /api/media/{id}/delete
删除媒体文件（仅上传者本人可删除）✅

**请求头**: `Authorization: Bearer <JWT_TOKEN>`

**响应示例**:
```json
{
  "code": 200,
  "message": "删除成功",
  "data": null,
  "timestamp": 1705564800000
}
```

**业务逻辑**（软删除）:
1. 校验所有权（media.uploader_id 与当前用户一致）
2. 将 media.state 置为 4（正在删除）并写库
3. 删除 media_visible 记录（若本就不存在记录，视为幂等成功）
4. 删除 MinIO 主文件与封面（若路径不同）
5. 将 media.state 置为 5（已删除）并写库
说明：若某次删除执行到一半失败导致 state=4，可再次调用删除接口继续清理 media_visible/MinIO 并将 state 置为 5（接口幂等收尾）。

### 树洞相关接口

#### 树洞主人说明（产品设定）
- 只有固定的 **12 位后浪成员**是树洞主人（其 `tree_hole` 数据由脚本/人工提前写入数据库）
- 普通用户 **不拥有树洞**，因此不提供“创建树洞/树洞主人列表”的对外接口

#### POST /api/treehole/{ownerId}/sent/messages
向树洞投递留言或树洞主人回复（同一接口，通过 rootMessageId 区分）

**请求参数**:
```json
{
  "content": "留言内容",
  "rootMessageId": null
}
```
- `content` 必填；`rootMessageId` 可选，为空表示投递新留言，非空表示主人回复该条留言（仅支持一次回复）
防刷并发说明：为防止同一投递者“快速连点”绕过防刷，投递新留言分支在事务内对 `tree_hole(owner_id)` 行执行 `SELECT ... FOR UPDATE` 加锁，保证 `count + insert` 原子性。

#### GET /api/treehole/{ownerId}/messages
获取树洞留言列表

说明：
- 如果当前用户是树洞主人：返回全部留言（不含已删除）
- 如果当前用户不是树洞主人：仅返回自己投递的留言（不含已删除）

#### PUT /api/treehole/messages/{messageId}/read
树洞主人将留言标记为已读

#### DELETE /api/treehole/messages/{messageId}
树洞主人删除留言（全局删除）

#### DELETE /api/treehole/messages/{messageId}/sender
发送者删除留言（仅对发送者不可见）

#### PUT /api/treehole/{ownerId}/state
树洞主人设置树洞状态（允许/禁止投递）

#### POST /api/treehole/{ownerId}/messages/{messageId}/share
树洞主人将一条留言分享给其他树洞主人（可多人）✅

**请求体**：`{ "ownerIds": [2, 3, 5] }`（接收方树洞主人用户 ID 列表，必填非空）

**说明**：只能分享自己树洞下的、未删除的留言；目标须为有树洞的用户且不能为自己；同一 (留言, 目标) 已存在则静默跳过（幂等）。全部成功返回「分享成功！」；部分失败返回「分享给昵称1、昵称2失败」。

#### GET /api/treeholeMessageVisible/shared/list
树洞主人查看「分享收件箱」：别人分享给自己的留言列表 ✅

**查询参数**：page（默认1）、size（默认10）

**响应**：与留言列表结构一致（total、list，每项含 id、senderId、content、state 等）

## 开发环境配置

### 前置要求
- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- IDE（推荐IntelliJ IDEA）
- 阿里云OSS账号（需要AccessKey和Bucket配置）

### 数据库配置
修改 `dragons-core-server/src/main/resources/application.yml` 中的数据库连接信息：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3307/dragons?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8
    username: root
    password: 123456
```

### MinIO配置（MVP阶段）
需要在 `application.yml` 中配置MinIO相关信息：
```yaml
minio:
  endpoint: http://localhost:9000
  access-key: root
  secret-key: 123456789
  bucket: dragons-media
```

**注意**: 未来迁移到阿里云OSS时，只需替换StorageService实现类，无需修改业务代码

### 运行项目
```bash
cd dragons-core-server
mvn spring-boot:run
```

## 注意事项

1. **安全性**: 
   - 密码必须加密存储，使用BCrypt算法
   - JWT Token需要设置合理的过期时间
   - 文件上传需要验证文件类型
   - OSS AccessKey需要妥善保管，不要提交到代码仓库

2. **文件存储**:
   - 使用阿里云OSS存储实际文件
   - MySQL仅存储OSS资源的URL地址
   - 删除文件时需要同时删除OSS中的文件

3. **权限控制**:
   - 所有接口需要根据用户角色进行权限验证
   - 文件访问需要检查可见性设置
   - OSS访问链接需要设置合理的过期时间

4. **接口规范**:
   - 所有接口统一使用Result格式返回
   - 需要添加全局异常处理器
   - 建议添加Swagger接口文档

## 更新日志

### 2026-01-18
- 项目初始化
- 完成基础框架搭建
- 完成数据库表创建
- 完成数据库连接配置
- 确定使用阿里云OSS存储方案（后调整为MinIO本地开发）
- 确定统一响应格式
- 确定前端技术栈为Vue

### 2026-01-21
- ✅ 完成JWT认证系统
  - 实现JWT工具类（生成、解析、验证）
  - 实现密码加密工具类（BCrypt）
  - 创建统一响应格式（Result类）
  - 创建全局异常处理器
  - 实现JWT过滤器集成Spring Security
- ✅ 完成用户服务接口
  - POST /api/user/login（登录）
  - POST /api/user/register（注册）
  - POST /api/user/deregister（注销，逻辑删除）
- ✅ 完成对象存储集成
  - 创建StorageService抽象接口（便于未来迁移到OSS）
  - 实现MinioStorageService（MinIO本地存储）
  - 配置MinIO连接（本地开发环境）
- ✅ 完成媒体服务核心接口
  - POST /api/media/upload（上传，支持可见权限控制）
  - GET /api/media/{id}/download（下载，预签名URL，2小时有效）
  - DELETE /api/media/{id}/delete（删除，仅上传者可删；软删除：state→4，删 media_visible，删 MinIO，state→5）
- ✅ 实现媒体可见权限控制
  - 通过 `media_visible` 表控制“展示在哪个专区”
  - 公共区直接查 media（state=0）；成员专区按 media_visible 筛选
- ✅ 媒体删除为软删除
  - media 表不物理删除，state 置 4（正在删除）后删 media_visible 与 MinIO，再置 state=5（已删除）
  - MinIO 删除失败不影响业务正确性
- 📝 更新API设计文档（API_DESIGN.md）
- 📝 更新开发记录（chatRecord.md）

### 2026-02-xx
- ✅ 开发进度与代码对齐
  - 步骤4 树洞功能接口：投递/回复、留言列表、已读、主人删除、发送者删除、树洞开关均已实现，文档中勾选完成并补充删除接口说明
  - 树洞相关接口描述与实现一致：POST sent/messages（支持 rootMessageId 回复）、PUT message read、DELETE by owner、DELETE by sender、PUT treehole state
- ✅ 树洞留言分享功能
  - POST /api/treehole/{ownerId}/messages/{messageId}/share：树洞主人将一条留言分享给多个树洞主人，实现位于 TreeHoleMessageVisibleServiceImpl.shareMessage，写库重试 3 次，部分失败返回「分享给xxx失败」
  - GET /api/treeholeMessageVisible/shared/list：分享收件箱列表，联表查询 tree_hole_message_visible + tree_hole_message，分页返回
  - 数据库表 tree_hole_message_visible：message_id、owner_id（接收方）、shared_by_user_id（分享者）
- ✅ 未登录找回密码（忘记密码）
  - POST /api/user/forgotPassword：permitAll，无需 JWT；请求体 loginName、phoneNumber、newPassword；通过登录名+手机号校验身份后修改密码，不依赖验证码；登录名或手机号不匹配时统一返回 4017「登录名与手机号不匹配」；SecurityConfig 已加入该路径 permitAll。

### 后端逻辑检查摘要（当前已完成功能）

- **鉴权与权限**
  - 除登录、注册、未登录找回密码（/api/user/forgotPassword）外，**游客模式**下 GET 媒体列表（/api/mediaVisible/list）、媒体详情（/api/media/{id}）、下载链接（/api/media/{id}/download）无需 JWT；其余接口均需 JWT，树洞/分享/媒体写操作等均校验 principal。
  - 树洞：投递用 senderUserId、主人操作用 ownerId/ownerUserId，分享要求 ownerId.equals(currentUserId)。
  - 重置密码：resetPasswordByPhone 须登录且手机号与当前用户一致；forgotPassword 为 permitAll，仅凭登录名+手机号校验身份。

- **参数与边界**
  - 树洞/留言/分享的 ownerId、messageId 等在 Service 层做 null 与 ≤0 校验，并统一抛 BusinessException，由 GlobalExceptionHandler 转成 Result。
  - 分享：ownerIds 空列表在 Controller 层拦截；已分享过（同 messageId+targetOwnerId）静默跳过，写库失败（含重试 3 次后）计入「分享给xxx失败」。

- **建议与可选优化**
  - **唯一索引**：建议在 `tree_hole_message_visible` 上添加 `(owner_id, message_id)` 唯一索引（见 MYSQL_INDEXES.md），防止并发或异常重试导致重复记录；若已加可忽略。
  - **分享去重**：已对 targetOwnerIds 在 Service 内做 distinct 去重后再循环。
