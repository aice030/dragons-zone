# API接口设计文档 - Dragons Zone

## 用户服务接口设计（最终版）

### 接口路径规范
所有用户相关接口统一使用前缀：`/api/user/`

---

## 1. 用户登录接口

### POST /api/user/login

**功能说明**：用户登录，验证用户名和密码，返回JWT Token和用户基本信息

**请求参数**：
```json
{
  "loginName": "用户名",
  "password": "密码"
}
```

**参数说明**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| loginName | String | 是 | 登录名 |
| password | String | 是 | 密码（明文，后端会加密验证） |

**成功响应**（200）：
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

**失败响应**：
- 用户名或密码错误（4002）：
```json
{
  "code": 4002,
  "message": "用户名或密码错误",
  "data": null,
  "timestamp": 1705564800000
}
```

- 用户已被注销（4007）：
```json
{
  "code": 4007,
  "message": "用户已被注销",
  "data": null,
  "timestamp": 1705564800000
}
```

- 用户已被拉黑（4008）：
```json
{
  "code": 4008,
  "message": "用户已被拉黑",
  "data": null,
  "timestamp": 1705564800000
}
```

**业务逻辑**：
1. 验证用户名和密码
2. 检查用户状态（state）：
   - state=0：正常，允许登录
   - state=1：已注销，不允许登录
   - state=2：已拉黑，不允许登录
3. 密码验证通过后，生成JWT Token
4. 返回Token和用户基本信息（不含密码、手机号）

---

## 2. 用户注册接口

### POST /api/user/register

**功能说明**：新用户注册，创建用户账号

**请求参数**：
```json
{
  "loginName": "登录名",
  "password": "密码",
  "nickName": "昵称",
  "phoneNumber": "手机号"
}
```

**参数说明**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| loginName | String | 是 | 登录名（需唯一） |
| password | String | 是 | 密码（明文，后端会加密存储） |
| nickName | String | 是 | 昵称 |
| phoneNumber | String | 是 | 手机号（需唯一，11位数字） |

**成功响应**（200）：
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

**失败响应**：
- 用户名已存在（4001）：
```json
{
  "code": 4001,
  "message": "用户名已存在",
  "data": null,
  "timestamp": 1705564800000
}
```

- 手机号已存在（4009）：
```json
{
  "code": 4009,
  "message": "手机号已被注册",
  "data": null,
  "timestamp": 1705564800000
}
```

- 手机号格式错误（4010）：
```json
{
  "code": 4010,
  "message": "手机号格式不正确",
  "data": null,
  "timestamp": 1705564800000
}
```

- 请求参数错误（400）：
```json
{
  "code": 400,
  "message": "请求参数错误",
  "data": null,
  "timestamp": 1705564800000
}
```

**业务逻辑**：
1. 验证请求参数（必填字段、格式等）
2. 检查用户名是否已存在
3. 检查手机号是否已存在
4. 验证手机号格式（11位数字）
5. 密码使用BCrypt加密存储
6. 自动设置默认值：
   - `level`: 2（普通用户）
   - `state`: 0（正常状态）
7. 保存用户信息到数据库
8. 返回用户ID和登录名

---

## 3. 用户注销接口

### POST /api/user/deregister

**功能说明**：用户注销账号（逻辑删除），需要密码二次确认

**请求头**：
```
Authorization: Bearer <JWT_TOKEN>
```

**请求参数**：
```json
{
  "password": "密码"
}
```

**参数说明**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| password | String | 是 | 密码（用于二次确认） |

**成功响应**（200）：
```json
{
  "code": 200,
  "message": "注销成功",
  "data": null,
  "timestamp": 1705564800000
}
```

**失败响应**：
- 未授权（401）：
```json
{
  "code": 401,
  "message": "未授权，请先登录",
  "data": null,
  "timestamp": 1705564800000
}
```

- Token无效或已过期（4003）：
```json
{
  "code": 4003,
  "message": "Token无效或已过期",
  "data": null,
  "timestamp": 1705564800000
}
```

- 密码错误（4002）：
```json
{
  "code": 4002,
  "message": "密码错误",
  "data": null,
  "timestamp": 1705564800000
}
```

- 用户已被注销（4007）：
```json
{
  "code": 4007,
  "message": "用户已被注销",
  "data": null,
  "timestamp": 1705564800000
}
```

**业务逻辑**：
1. 从请求头获取JWT Token并验证
2. 从Token中解析用户ID
3. 验证密码（二次确认）
4. 检查用户状态（如果已被注销，不允许重复注销）
5. 执行逻辑删除：将 `state` 字段设置为 `1`（已注销）
6. **注意**：用户数据（媒体、树洞等）保留，其他用户仍可查看

---

## 4. 通过手机号修改密码接口（MVP简化版）

### POST /api/user/resetPasswordByPhone

**功能说明**：通过手机号修改密码（仅允许修改自己的密码，MVP简化方案）。

**请求头**：
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

**请求参数**：
```json
{
  "phoneNumber": "手机号",
  "newPassword": "新密码"
}
```

**参数说明**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| phoneNumber | String | 是 | 手机号（11位数字） |
| newPassword | String | 是 | 新密码（明文，后端会加密存储） |

**成功响应**（200）：
```json
{
  "code": 200,
  "message": "修改成功",
  "data": null,
  "timestamp": 1705564800000
}
```

**失败响应**：
- 未授权（401）：未登录或Token无效
- 无权限（403）：手机号与当前登录用户不匹配

**业务逻辑**：
1. 从 JWT 获取当前用户ID
2. 查询当前用户信息并校验状态（已注销/拉黑不允许修改）
3. 校验请求中的手机号必须与该用户绑定手机号一致
4. 使用 BCrypt 加密新密码并更新到数据库

---

## 5. 修改用户等级接口

### PUT /api/user/{targetUserId}/level

**功能说明**：修改指定用户的等级（仅作者 `level=0` 或管理员 `level=1` 可操作）

**请求头**：
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

**路径参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| targetUserId | Long | 是 | 目标用户ID |

**请求参数**（JSON）：
```json
{
  "level": 1
}
```

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| level | Byte | 是 | 新等级（0=作者，1=管理员，2=普通用户，3=游客） |

**成功响应**（200）：
```json
{
  "code": 200,
  "message": "用户等级修改成功",
  "data": null,
  "timestamp": 1705564800000
}
```

**失败响应**：
- 未授权（401）：未登录或Token无效
- 无权限（403）：当前用户不是作者或管理员
- 请求参数错误（400）：level 参数为空或无效
- 资源不存在（404）：targetUserId 对应的用户不存在

**业务逻辑**：
1. 从 JWT 获取当前用户ID
2. 校验当前用户权限（`level=0` 作者 或 `level=1` 管理员）
3. 查询目标用户是否存在
4. 更新目标用户的 `level` 字段
5. 更新 `updateTime` 字段

---

## 6. 修改用户状态接口

### PUT /api/user/{targetUserId}/state

**功能说明**：修改指定用户的状态（仅作者 `level=0` 或管理员 `level=1` 可操作）

**请求头**：
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

**路径参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| targetUserId | Long | 是 | 目标用户ID |

**请求参数**（JSON）：
```json
{
  "state": 2
}
```

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| state | Byte | 是 | 新状态（0=正常，1=逻辑删除，2=黑名单） |

**成功响应**（200）：
```json
{
  "code": 200,
  "message": "用户状态修改成功",
  "data": null,
  "timestamp": 1705564800000
}
```

**失败响应**：
- 未授权（401）：未登录或Token无效
- 无权限（403）：当前用户不是作者或管理员
- 请求参数错误（400）：state 参数为空或无效
- 资源不存在（404）：targetUserId 对应的用户不存在

**业务逻辑**：
1. 从 JWT 获取当前用户ID
2. 校验当前用户权限（`level=0` 作者 或 `level=1` 管理员）
3. 查询目标用户是否存在
4. 更新目标用户的 `state` 字段
5. 更新 `updateTime` 字段

---

## 7. 获取用户列表接口

### GET /api/user/list

**功能说明**：分页获取用户列表（仅作者 `level=0` 可操作）。返回的用户列表不包含作者（`level=0`）用户。

**请求头**：
```
Authorization: Bearer <JWT_TOKEN>
```

**查询参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page | Integer | 否 | 页码（默认1） |
| size | Integer | 否 | 每页数量（默认20，最大100） |

**成功响应**（200）：
```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "total": 100,
    "list": [
      {
        "id": 1,
        "nickName": "用户昵称",
        "level": 1,
        "state": 0
      }
    ]
  },
  "timestamp": 1705564800000
}
```

**字段说明**：
| 字段名 | 类型 | 说明 |
|--------|------|------|
| total | Long | 总记录数 |
| list | List | 用户列表 |
| list[].id | Long | 用户ID |
| list[].nickName | String | 用户昵称 |
| list[].level | Byte | 用户等级（0=作者，1=管理员，2=普通用户，3=游客） |
| list[].state | Byte | 用户状态（0=正常，1=逻辑删除，2=黑名单） |

**失败响应**：
- 未授权（401）：未登录或Token无效
- 无权限（403）：当前用户不是作者

**业务逻辑**：
1. 从 JWT 获取当前用户ID
2. 校验当前用户权限（必须是 `level=0` 作者）
3. 查询用户列表（排除 `level=0` 的作者用户）
4. 仅返回 `id`、`nickName`、`level`、`state` 字段
5. 按 `id` 升序排序
6. 分页返回结果

---

## 响应状态码说明

### 通用状态码
- `200`: 成功
- `400`: 请求参数错误
- `401`: 未授权（未登录或Token过期）
- `403`: 无权限
- `404`: 资源不存在
- `500`: 服务器内部错误

### 业务状态码
- `4001`: 用户名已存在
- `4002`: 用户名或密码错误 / 密码错误
- `4003`: Token无效或已过期
- `4007`: 用户已被注销
- `4008`: 用户已被拉黑
- `4009`: 手机号已被注册
- `4010`: 手机号格式不正确

---

## 数据状态说明

### 用户状态（state字段）
- `0`: 正常状态
- `1`: 已注销（逻辑删除）
- `2`: 已拉黑

### 用户等级（level字段）
- `0`: 作者
- `1`: 管理员
- `2`: 普通用户（注册时默认）
- `3`: 游客

---

## 数据保留策略

### 注销用户的数据
- **保留策略**：用户注销后（state=1），其数据（媒体、树洞等）**保留**，其他用户仍可查看
- **显示标识**：前端可显示"已注销用户"标识

### 拉黑用户的数据
- **清理策略**：用户被拉黑后（state=2），其数据需要**清理**（具体清理策略待后续确定）

---

## 安全说明

1. **密码加密**：所有密码使用BCrypt算法加密存储，不可逆
2. **JWT Token**：登录成功后返回JWT Token，后续请求需在Header中携带
3. **Token格式**：`Authorization: Bearer <token>`
4. **密码验证**：注销时需要密码二次确认，确保操作安全
5. **手机号唯一性**：手机号不允许重复，用于防止恶意用户重复注册

---

## 接口测试建议

### 测试工具
- Postman
- Apifox
- curl命令

### 测试顺序
1. 先测试注册接口
2. 使用注册的用户测试登录接口
3. 使用登录返回的Token测试注销接口

---

---

## 媒体服务接口设计

### 接口路径规范
所有媒体相关接口统一使用前缀：`/api/media/`（列表为 `/api/mediaVisible/list`）。

### 游客模式
系统支持游客模式：**未登录**用户可访问以下接口，**无需**携带请求头 `Authorization`：
- `GET /api/mediaVisible/list`：查看公共区（currentUserId=0）或专区媒体列表
- `GET /api/media/{id}`：查看媒体详情
- `GET /api/media/{id}/download`：获取下载预签名 URL

上传、更新、删除、我的上传列表等操作仍需登录。

### 媒体状态说明
媒体（media）的状态（state）字段定义如下：
- `state=0`：正常（已审核通过，可公开查看）
- `state=1`：正在上传
- `state=2`：上传成功
- `state=3`：上传失败
- `state=4`：正在删除
- `state=5`：已删除
- `state=6`：待审核（上传完成后进入此状态，等待管理员/作者审核）
- `state=7`：审核未通过（审核被驳回）

---

## 1. 上传媒体资源接口

### POST /api/media/upload

**功能说明**：上传图片或视频文件到对象存储（MinIO），并保存媒体记录和可见权限

**请求头**：
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: multipart/form-data
```

**请求参数**（multipart/form-data）：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| file | MultipartFile | 是 | 文件（图片或视频） |
| category | Byte | 是 | 0=图片，1=视频 |
| visibleUserIds | String | 是 | JSON数组字符串，例如 `[1,2,3]` 或 `[]`（必填，但可以为空数组，表示要展示到哪些成员专区） |
| title | String | 否 | 标题（可选） |
| description | String | 否 | 描述（可选） |
| cover | MultipartFile | 否 | 封面图片（可选，仅视频建议传；不传则后端自动生成/使用默认封面策略） |

**支持的图片格式**：jpg, jpeg, png, gif, webp, bmp

**支持的视频格式**：mp4, mov, avi, mkv, flv, wmv

**成功响应**（200）：
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

**失败响应**：
- 未授权（401）：
```json
{
  "code": 401,
  "message": "未授权，请先登录",
  "data": null,
  "timestamp": 1705564800000
}
```

- 请求参数错误（400）：
```json
{
  "code": 400,
  "message": "请求参数错误",
  "data": null,
  "timestamp": 1705564800000
}
```

- 文件格式不支持（4005）：
```json
{
  "code": 4005,
  "message": "文件格式不支持",
  "data": null,
  "timestamp": 1705564800000
}
```

- 文件上传失败（4004）：
```json
{
  "code": 4004,
  "message": "文件上传失败",
  "data": null,
  "timestamp": 1705564800000
}
```

**业务逻辑**：
1. 验证JWT Token并获取当前用户ID
2. 验证文件格式（根据category检查扩展名）
3. 生成对象存储路径（格式：`{category}/yyyy/MM/dd/{uuid}-{filename}`）
4. 先保存 media 记录（state=1 正在上传），再上传主文件到 MinIO
5. MinIO 上传成功后，将 media 的 state 更新为 2（上传成功）并写库；**仅当这一步写库失败时**：删除已上传的 MinIO 对象、删除该 media 记录，并返回上传失败
6. 保存 media_visible 记录（用于“成员专区筛选”，不是权限系统）：
   - **不再写入公共区 `user_id=0`**：公共区展示直接查询 `media` 表（永远全部公开）
   - 遍历 visibleUserIds：写入成员专区ID列表
   - 若 media_visible 保存成功，将 media 的 state 更新为 6（待审核）并写库；若 media_visible 写库失败，不删 MinIO，media 保持 state=2，可后续通过「可见范围修复」接口重试
7. （可选）上传封面到 MinIO（如果传了 `cover`）

**注意**：上传完成后，媒体状态为 `state=6`（待审核），需要管理员或作者审核通过后才能公开显示（`state=0`）。

---

## 2. 更新媒体基础信息接口（不改文件/不改封面/不改可见范围）

### PUT /api/media/{id}

**功能说明**：更新媒体的标题与描述（仅上传者本人；允许 `state=0`、`state=6`、`state=7` 更新）

**请求头**：
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/x-www-form-urlencoded
```

**路径参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 媒体ID |

**请求参数**（form-data 或 x-www-form-urlencoded）：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| title | String | 否 | 标题（可选） |
| description | String | 否 | 描述（可选） |

**成功响应**（200）：
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

**业务逻辑**：
1. 校验JWT并获取当前用户ID
2. 查询media并校验所有权（上传者本人）
3. 校验 media.state 为 0（正常）、6（待审核）或 7（审核未通过）才允许修改基础信息
4. 更新 `title/description/updateTime` 写库
5. 修改成功后自动将状态重置为 `state=6`（待审核），需要重新审核（包括原 `state=0/6/7`）

---

## 3. 更新视频封面接口（独立操作）

### PUT /api/media/{id}/cover

**功能说明**：上传新封面并更新数据库（仅上传者本人；仅 `state=0`；仅视频允许）

**请求头**：
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: multipart/form-data
```

**路径参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 媒体ID |

**请求参数**（multipart/form-data）：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| cover | MultipartFile | 是 | 封面图片 |

**成功响应**（200）：
```json
{
  "code": 200,
  "message": "封面更新成功",
  "data": {
    "mediaId": 1,
    "coverPath": "covers/2026/01/31/abc123-cover.jpg",
    "coverUrl": "http://localhost:9000/dragons-media/covers/2026/01/31/abc123-cover.jpg?X-Amz-Algorithm=...&X-Amz-Expires=7200&..."
  },
  "timestamp": 1705564800000
}
```

**字段说明**：
- `coverPath`：封面对象存储路径
- `coverUrl`：封面预签名 URL（2 小时有效），用于前端更新成功后立即刷新预览；若无法生成则可能为空（例如对象不存在）

**业务逻辑**：
1. 校验JWT并获取当前用户ID
2. 查询media并校验所有权（上传者本人）
3. 校验 media.category=1（视频）、media.state=0
4. 先上传封面到MinIO
5. 再更新DB中的 `media.coverPath`，并将 `media.state` 重置为 `state=6`（待审核）
6. 若DB更新失败，补偿删除MinIO中新上传的封面对象，并返回失败

---

## 4. 修复/重建媒体可见范围接口（独立操作，方案C：差量同步）

### PUT /api/media/{id}/visible

**功能说明**：仅更新 `media_visible` 表，用于“成员专区筛选”（仅上传者本人；事务保证删除与新增的原子性）

**请求头**：
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/x-www-form-urlencoded
```

**路径参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 媒体ID |

**请求参数**（form-data 或 x-www-form-urlencoded）：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| visibleUserIds | String | 是 | JSON数组字符串，例如 `[1,2,3]` 或 `[]`（必填，但可为空数组；最多12个元素） |

**成功响应**（200）：
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

**业务逻辑（差量同步）**：
1. 校验JWT并获取当前用户ID
2. 查询media并校验所有权（上传者本人）
3. 查询当前 `media_visible` 的旧集合 oldSet
4. 将入参 `visibleUserIds` 去重得到 newSet
5. 计算：
   - toAdd = newSet - oldSet
   - toRemove = oldSet - newSet
6. 事务内：删除 toRemove、插入 toAdd（任何一步失败则整体回滚）
7. **补救逻辑**：若媒体当前为 `state=2`（上传成功但不可见，通常是 upload 阶段写入 `media_visible` 失败导致），则在本次修复成功后将 `media.state` 修正为 `state=0`（正常可查看），并更新 `updateTime`
8. 除 `state=2 → 0` 的补救场景外，**不修改 `media` 的其他字段**；标签仅用于“专区筛选”，不影响审核状态

---

## 5. 获取媒体下载URL接口

### GET /api/media/{id}/download

**功能说明**：获取媒体资源的预签名下载URL（2小时有效）。支持游客模式，无需请求头。

**访问规则（补充：审核预览）**：
- **游客 / 未登录**：仅允许获取 `state=0`（已审核通过）的媒体下载/播放 URL
- **已登录上传者本人**：允许获取 `state=0/6/7`
- **已登录作者/管理员（level=0/1）**：允许获取 `state=0/6/7`（用于资源审核流程中的视频/图片预览）

**路径参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 媒体ID |

**成功响应**（200）：
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

**失败响应**：
- 资源不存在（404）：
```json
{
  "code": 404,
  "message": "资源不存在",
  "data": null,
  "timestamp": 1705564800000
}
```

**业务逻辑**：
1. 查询数据库中的media记录
2. 按“访问规则”校验资源 state 与身份：
   - 游客：仅 `state=0`
   - 上传者本人：`state=0/6/7`
   - 作者/管理员：`state=0/6/7`（审核预览）
3. 检查MinIO中文件是否实际存在（防止缓存不一致）
4. 生成预签名URL（有效期2小时，7200秒）
5. 返回URL给前端，前端可重定向下载

**注意**：
- 游客模式下的“可下载”仍以 `state=0` 为准
- 审核预览场景下（作者/管理员已登录），待审核/驳回资源也允许获取预签名 URL，用于前端在审核列表中播放完整视频内容

---

## 6. 删除媒体资源接口

### DELETE /api/media/{id}/delete

**功能说明**：删除媒体资源（仅允许上传者本人删除）

**请求头**：
```
Authorization: Bearer <JWT_TOKEN>
```

**路径参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 媒体ID |

**成功响应**（200）：
```json
{
  "code": 200,
  "message": "删除成功",
  "data": null,
  "timestamp": 1705564800000
}
```

**失败响应**：
- 未授权（401）：
```json
{
  "code": 401,
  "message": "未授权，请先登录",
  "data": null,
  "timestamp": 1705564800000
}
```

- 资源不存在（404）：
```json
{
  "code": 404,
  "message": "资源不存在",
  "data": null,
  "timestamp": 1705564800000
}
```

**业务逻辑**（软删除，不物理删除 media 表行）：
1. 从JWT Token获取当前用户ID
2. 查询 media 记录并校验所有权（media.uploader_id 与当前用户ID一致）
3. 如果无权限或资源不存在，返回404（不暴露资源是否存在）
4. 将 media.state 置为 4（正在删除）并写库（若已为 state=4，允许重复调用该接口继续收尾）
5. 删除 media_visible 记录（where media_id = ?；若本就不存在记录，视为幂等成功）
6. 删除 MinIO 中的主文件与封面（若与主文件路径不同）
7. 将 media.state 置为 5（已删除）并写库

**注意**：
- 仅允许上传者本人删除（通过 JWT 中的 userId 校验）
- media 表行为软删除（state 4→5），不执行物理 DELETE
- MinIO 删除在删除 media_visible 之后、state 置 5 之前执行；若 MinIO 删除失败，不影响业务正确性（最多遗留垃圾文件）
- **删除接口幂等**：若某次删除执行到一半失败导致 state=4，可再次调用删除接口继续清理 media_visible/MinIO 并将 state 置为 5

---

## 7. 获取媒体列表接口（专区筛选）

### GET /api/mediaVisible/list

**功能说明**：获取媒体列表，用于首页/公共区/成员专区的媒体浏览。支持游客模式，无需请求头。

**请求头**：无需（游客可访问）；若已登录也可携带 JWT。

**查询参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page | Integer | 否 | 页码（默认1） |
| size | Integer | 否 | 每页数量（默认10，最大100） |
| category | Byte | 否 | 类型筛选（0=图片，1=视频） |
| currentUserId | Long | 否 | **专区ID**：0=公共区；成员ID=成员专区（默认0） |

**成功响应**（200）：
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
        "coverPath": "images/2026/01/21/xxx.jpg",
        "updateTime": "2026-01-31T12:34:56",
        "coverUrl": "http://localhost:9000/dragons-media/images/2026/01/21/xxx.jpg?X-Amz-Algorithm=...&X-Amz-Expires=7200&..."
      }
    ]
  },
  "timestamp": 1705564800000
}
```

**字段说明**：
- `coverUrl`：封面预签名 URL（2 小时有效），用于公共区/成员专区列表直接展示缩略图；若无法生成则可能为空（例如对象不存在或 coverPath 为空）
- `updateTime`：最近更新时间（用于前端按时间排序/展示）

**业务逻辑**：
1. 仅返回 `state=0`（正常可查看，已审核通过）的媒体；不显示 `state=6`（待审核）和 `state=7`（审核未通过）的媒体；列表项不包含 state 字段（无需登录即可调用）
2. 根据 `currentUserId`（专区ID）做筛选：
   - `currentUserId=0`：公共区，直接查询 `media`（不依赖 `media_visible`）
   - `currentUserId=成员ID`：成员专区，通过 `media_visible.user_id = currentUserId` 做筛选
3. 按更新时间倒序、id 倒序分页返回

**注意**：
- `currentUserId` 在此处表达“专区筛选”，不是会员权限控制

---

## 8. 获取媒体详情接口

### GET /api/media/{id}

**功能说明**：获取媒体详情（用于点击列表项进入详情页）。支持游客模式，无需请求头。

**请求头**：无需（游客可访问）；若已登录也可携带 JWT。

**路径参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 媒体ID |

**成功响应**（200）：
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
    "coverUrl": "http://localhost:9000/dragons-media/images/2026/01/21/xxx.jpg?X-Amz-Algorithm=...&X-Amz-Expires=7200&...",
    "uploaderId": 1,
    "updateTime": "2026-01-31T12:34:56"
  },
  "timestamp": 1705564800000
}
```

**字段说明**：
- `coverUrl`：封面预签名 URL（2 小时有效），用于详情页/弹窗直接展示封面；若无法生成则可能为空（例如对象不存在或 coverPath 为空）

**失败响应**：
- 资源不存在（404）：
```json
{
  "code": 404,
  "message": "资源不存在",
  "data": null,
  "timestamp": 1705564800000
}
```

**业务逻辑**：
1. 查询 `media` 记录
2. 若未登录（游客）：仅允许查看 `state=0`（正常可查看，已审核通过）的媒体；`state=6/7` 返回 404
3. 若已登录且为上传者本人：允许查看 `state=0/6/7`（用于查看违规原因并做修改）
4. 若已登录且为作者/管理员（level=0/1）：允许查看 `state=0/6/7`（用于审核流程中的预览与核对）
4. 若 `coverPath` 存在且对象存储中对象存在，则生成 `coverUrl`（预签名 URL，2 小时有效）
5. 返回媒体详情字段（供前端展示与后续下载/删除操作）

---

## 9. 获取“我的上传”列表接口

### GET /api/mediaVisible/my/list

**功能说明**：上传者本人管理自己上传内容的列表（不依赖 `media_visible`）。

**请求头**：
```
Authorization: Bearer <JWT_TOKEN>
```

**查询参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page | Integer | 否 | 页码（默认1） |
| size | Integer | 否 | 每页数量（默认10，最大100） |
| category | Byte | 否 | 类型筛选（0=图片，1=视频） |

**成功响应**（200）：
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
        "coverPath": "images/2026/01/21/xxx.jpg",
        "updateTime": "2026-01-31T12:34:56",
        "coverUrl": "http://localhost:9000/dragons-media/images/2026/01/21/xxx.jpg?X-Amz-Algorithm=...&X-Amz-Expires=7200&..."
      }
    ]
  },
  "timestamp": 1705564800000
}
```

**字段说明**：
- `coverUrl`：封面预签名 URL（2 小时有效），用于“我的上传”列表直接展示缩略图；若无法生成则可能为空（例如对象不存在或 coverPath 为空）
- `updateTime`：最近更新时间（用于前端按时间排序/展示）

**业务逻辑**：
1. 从 JWT 获取当前用户ID（上传者ID）
2. 查询 `media`：`uploader_id = 当前用户ID` 且 `state != 5`（排除已删除状态，显示包括 `state=0`正常、`state=6`待审核、`state=7`审核未通过等所有状态）
3. 按创建时间倒序分页返回

---

## 10. 查询媒体所属成员专区接口

### GET /api/mediaVisible/{mediaId}/zones

**功能说明**：根据媒体ID查询该媒体属于哪些成员专区。支持游客模式，无需请求头。

**请求头**：无需（游客可访问）；若已登录也可携带 JWT。

**路径参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| mediaId | Long | 是 | 媒体ID |

**成功响应**（200）：
```json
{
  "code": 200,
  "message": "查询成功",
  "data": [1, 2, 3],
  "timestamp": 1705564800000
}
```

**说明**：
- 返回成员专区ID列表（`user_id`）
- 如果媒体只在公共区可见（`media_visible` 表中没有记录），返回空数组 `[]`
- 如果返回 `[1, 2, 3]`，表示该媒体在成员1、成员2、成员3的专区可见

**失败响应**：
- 请求参数错误（400）：mediaId 为空

**业务逻辑**：
1. 根据 `mediaId` 查询 `media_visible` 表
2. 返回所有匹配的 `user_id` 列表（成员专区ID）
3. 如果媒体只在公共区可见（`media_visible` 表中没有记录），返回空列表

---

## 11. 媒体审核接口

### 10.1 审核通过接口

### POST /api/media/audit/approve

**功能说明**：批量审核通过媒体，将媒体状态从 `state=6`（待审核）改为 `state=0`（正常）。仅管理员（`level=1`）或作者（`level=0`）可操作。

**请求头**：
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

**请求参数**（JSON）：
```json
{
  "mediaIds": [1, 2, 3]
}
```

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| mediaIds | List<Long> | 是 | 要审核通过的媒体ID列表 |

**成功响应**（200）：
- 全部成功：
```json
{
  "code": 200,
  "message": "审核通过成功",
  "data": {
    "failedItems": []
  },
  "timestamp": 1705564800000
}
```

- 部分失败：
```json
{
  "code": 200,
  "message": "部分审核通过失败",
  "data": {
    "failedItems": [
      {
        "mediaId": 2,
        "title": "媒体标题"
      },
      {
        "mediaId": 999,
        "title": "媒体id999不存在"
      }
    ]
  },
  "timestamp": 1705564800000
}
```

**失败响应**：
- 未授权（401）
- 无审核权限（4022）：当前用户不是管理员或作者

**业务逻辑**：
1. 校验JWT并获取当前用户ID
2. 校验当前用户权限（`level=0` 作者 或 `level=1` 管理员）
3. 批量处理媒体ID列表：
   - 如果媒体不存在，记录到失败列表（`title: "媒体id{media_id}不存在"`）
   - 如果媒体状态不是 `state=6`（待审核），记录到失败列表
   - 否则，将媒体状态更新为 `state=0`（正常）
   - 如果更新失败，记录到失败列表
4. 返回结果，包含失败项列表（如果有）

**注意**：
- 该方法**非事务性**：部分成功时不会回滚已成功的操作
- 失败项会详细记录媒体ID和标题（或"媒体id{media_id}不存在"），便于管理员查看

---

### 10.2 审核驳回接口

### POST /api/media/audit/reject

**功能说明**：批量审核驳回媒体，将媒体状态从 `state=6`（待审核）改为 `state=7`（审核未通过）。仅管理员（`level=1`）或作者（`level=0`）可操作。

**请求头**：
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

**请求参数**（JSON）：
```json
{
  "mediaIds": [1, 2, 3]
}
```

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| mediaIds | List<Long> | 是 | 要审核驳回的媒体ID列表 |

**成功响应**（200）：
- 全部成功：
```json
{
  "code": 200,
  "message": "审核驳回成功",
  "data": {
    "failedItems": []
  },
  "timestamp": 1705564800000
}
```

- 部分失败：
```json
{
  "code": 200,
  "message": "部分审核驳回失败",
  "data": {
    "failedItems": [
      {
        "mediaId": 2,
        "title": "媒体标题"
      },
      {
        "mediaId": 999,
        "title": "媒体id999不存在"
      }
    ]
  },
  "timestamp": 1705564800000
}
```

**失败响应**：
- 未授权（401）
- 无审核权限（4022）：当前用户不是管理员或作者

**业务逻辑**：
1. 校验JWT并获取当前用户ID
2. 校验当前用户权限（`level=0` 作者 或 `level=1` 管理员）
3. 批量处理媒体ID列表：
   - 如果媒体不存在，记录到失败列表（`title: "媒体id{media_id}不存在"`）
   - 如果媒体状态不是 `state=6`（待审核），记录到失败列表
   - 否则，将媒体状态更新为 `state=7`（审核未通过）
   - 如果更新失败，记录到失败列表
4. 返回结果，包含失败项列表（如果有）

**注意**：
- 该方法**非事务性**：部分成功时不会回滚已成功的操作
- 失败项会详细记录媒体ID和标题（或"媒体id{media_id}不存在"），便于管理员查看

---

### 10.3 待审核媒体列表接口

### GET /api/media/audit/pending

**功能说明**：分页查询待审核的媒体列表（`state=6`）。仅管理员（`level=1`）或作者（`level=0`）可访问。

**请求头**：
```
Authorization: Bearer <JWT_TOKEN>
```

**请求参数**（Query参数）：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page | Integer | 否 | 页码，从1开始，默认1 |
| size | Integer | 否 | 每页数量，默认10 |

**成功响应**（200）：
```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "total": 100,
    "page": 1,
    "size": 10,
    "list": [
      {
        "id": 1,
        "category": 0,
        "title": "标题",
        "description": "描述",
        "storagePath": "images/2026/01/21/xxx.jpg",
        "coverPath": "images/2026/01/21/xxx.jpg",
        "uploaderId": 1,
        "createTime": "2026-01-31T12:34:56",
        "updateTime": "2026-01-31T12:34:56"
      }
    ]
  },
  "timestamp": 1705564800000
}
```

**失败响应**：
- 未授权（401）
- 无审核权限（4022）：当前用户不是管理员或作者

**业务逻辑**：
1. 校验JWT并获取当前用户ID
2. 校验当前用户权限（`level=0` 作者 或 `level=1` 管理员）
3. 查询 `state=6`（待审核）的媒体列表
4. 按创建时间倒序分页返回

---

## 树洞功能接口设计（MVP）

### 树洞主人说明（产品设定）
- 只有固定的 **12 位后浪成员**是树洞主人（其 `tree_hole` 数据由脚本/人工提前写入数据库）
- 普通用户 **不拥有树洞**，因此不提供“创建树洞/树洞主人列表”的对外接口

### 通用说明
- 所有树洞接口都需要登录（携带 `Authorization: Bearer <JWT_TOKEN>`）
- 留言支持投递与回复：同一接口 `POST /api/treehole/{ownerId}/sent/messages`，请求体可选 `rootMessageId`；为空为投递新留言，非空为树洞主人回复该条留言（仅支持一条回复；回复时根消息自动标已读并写入 `reply_message_id`、`update_time`）
- 留言可见性：
  - 树洞主人：可查看该树洞全部留言，并修改留言状态
  - 投递者：仅可查看自己投递的留言
- 防刷规则（核心）：
  - 同一投递者对同一树洞：若上一条留言仍为未读（`state=0`），则禁止再次投递，直到树洞主人将其标记为已读（`state=1`）或删除（`state=2`）
- 并发防刷（实现层）：为防止同一投递者“快速连点”绕过防刷，投递新留言在事务内对 `tree_hole(owner_id)` 行执行 `SELECT ... FOR UPDATE` 加锁，保证 `count + insert` 原子性

---

## 1. 投递留言接口（sent）

### POST /api/treehole/{ownerId}/sent/messages

**功能说明**：向指定树洞主人的树洞投递留言（单向写）。

**请求头**：
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

**路径参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ownerId | Long | 是 | 树洞主人用户ID（固定12位之一） |

**请求参数**：
```json
{
  "content": "留言内容",
  "rootMessageId": null
}
```
- `content`：必填，留言/回复内容
- `rootMessageId`：可选。为空表示**投递新留言**；非空表示**树洞主人回复**该条留言（仅支持一次回复）

**成功响应**（200）：
```json
{
  "code": 200,
  "message": "投递成功",
  "data": {
    "messageId": 1
  },
  "timestamp": 1705564800000
}
```

**失败响应**：
- 未授权（401）
- 树洞不存在（404）：ownerId 对应的 tree_hole 不存在
- 禁止投递（403）：树洞 state=2 或触发防刷规则（上一条未读）；或回复时当前用户非树洞主人/根消息已删除或已被回复

**业务逻辑**：
- **投递新留言**（`rootMessageId == null`）：
  1. 从 JWT 获取当前用户ID（投递者）
  2. 根据 ownerId 查询树洞（tree_hole）
  3. 若 tree_hole.state=2（禁止投递）返回 403
  4. 在事务内对 `tree_hole(owner_id)` 行加锁（`SELECT ... FOR UPDATE`），防止并发绕过防刷
  5. 防刷校验：该投递者在该树洞存在 state=0（未读）留言则返回 403
  6. 写入 tree_hole_message：`tree_hole_id/tree_hole_owner_id/sender_id/content/state=0`，`root_message_id`、`reply_message_id` 为空，`update_time` 设为当前时间
- **树洞主人回复**（`rootMessageId != null`）：
  1. 校验当前用户为树洞主人
  2. 校验 rootMessageId 对应留言存在、属于该树洞、未删除且未回复（`reply_message_id` 为空）
  3. 在事务内：插入回复消息（`root_message_id=rootMessageId`，`update_time` 等）；更新根消息：`reply_message_id=新回复 id`、`state=1`（已读）、`update_time`

---

## 2. 留言列表接口

### GET /api/treehole/{ownerId}/messages

**功能说明**：获取指定树洞的留言列表（按身份返回不同范围）。

**请求头**：
```
Authorization: Bearer <JWT_TOKEN>
```

**路径参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ownerId | Long | 是 | 树洞主人用户ID |

**查询参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page | Integer | 否 | 页码（默认1） |
| size | Integer | 否 | 每页数量（默认10） |

**成功响应**（200）：
```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "total": 100,
    "list": [
      {
        "id": 1,
        "senderId": 10001,
        "content": "留言内容",
        "state": 0
      }
    ]
  },
  "timestamp": 1705564800000
}
```

**返回规则**：
- 如果当前用户是 ownerId：返回该树洞全部留言（过滤 `state=2` 的已删除留言）
- 否则：只返回 `sender_id=当前用户ID` 的留言（同样过滤 `state=2`）

---

## 3. 树洞主人修改留言状态接口

### PUT /api/treehole/messages/{messageId}/read

**功能说明**：树洞主人将留言标记为“已读”，用于管理与防刷解锁。

**请求头**：
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

**路径参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| messageId | Long | 是 | 留言ID |

**请求参数**：
无（请求体为空）

**成功响应**（200）：
```json
{
  "code": 200,
  "message": "更新成功",
  "data": null,
  "timestamp": 1705564800000
}
```

**失败响应**：
- 未授权（401）
- 资源不存在（404）：messageId 不存在
- 无权限（403）：只有该留言所属树洞的主人可修改

---

## 4. 树洞消息分享接口（TreeHoleMessageVisible）

### POST /api/treehole/{ownerId}/messages/{messageId}/share

**功能说明**：树洞主人将一条自己树洞下的留言分享给其他树洞主人（可多人）；对方在「分享收件箱」中可见。与 media 可见列表一致：一个操作、一个列表参数；只分享给一人时传单元素列表。

**请求头**：
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

**路径参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ownerId | Long | 是 | 分享者（当前用户）的树洞主人 ID，即留言所属树洞的 owner |
| messageId | Long | 是 | 被分享的留言 ID |

**请求参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ownerIds | List\<Long\> | 是 | 接收方树洞主人用户 ID 列表；只分享给一人时传单元素列表，如 `[2]` |

**请求体示例**：
```json
{
  "ownerIds": [2, 3, 5]
}
```

**成功响应**（200）：
```json
{
  "code": 200,
  "message": "分享成功",
  "data": null,
  "timestamp": 1705564800000
}
```

**失败响应**：
- 未授权（401）
- 请求参数错误（400）：ownerIds 为空或格式错误
- 无权限（403）：当前用户不等于 ownerId（只能分享自己树洞的留言）
- 资源不存在（404）：messageId 不存在或不属于该树洞；或某 targetOwnerId 在数据库中无对应树洞

**业务约定**（实现时需满足）：
- 留言须存在且 `tree_hole_message.tree_hole_owner_id = ownerId`，且 `state != 2`（未删除）。
- 每个 targetOwnerId 不能为当前用户；须存在 `tree_hole.owner_id = targetOwnerId`（不校验固定名单，便于扩展）。
- 幂等：同一 (messageId, targetOwnerId) 已存在则静默成功，不重复插入。

---

## 5. 分享收件箱接口（TreeHoleMessageVisible）

### GET /api/treeholeMessageVisible/shared/list

**功能说明**：树洞主人查看“其他树洞主人分享给自己的留言”列表（分享区/收件箱）。

**请求头**：
```
Authorization: Bearer <JWT_TOKEN>
```

**查询参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page | Integer | 否 | 页码（默认1） |
| size | Integer | 否 | 每页数量（默认10） |

**成功响应**（200）：返回结构与“留言列表接口”一致。

---

## 6. 树洞主人设置树洞状态接口（可选）

### PUT /api/treehole/{ownerId}/state

**功能说明**：树洞主人开启/关闭“允许投递”开关。

**请求头**：
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

**路径参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ownerId | Long | 是 | 树洞主人用户ID |

**请求参数**：
```json
{
  "state": 2
}
```
说明：`state=0` 正常；`state=2` 禁止他人投递新消息

## 更新日志

### 2026-01-18
- 完成用户服务接口设计（最终版）
- 确定接口路径规范：`/api/user/...`
- 确定数据保留策略：注销用户数据保留，拉黑用户数据清理
- 确定登录返回信息：不包含phoneNumber

### 2026-01-21
- 完成媒体服务接口设计（上传、下载、删除）
- 确定接口路径规范：`/api/media/...`
- 确定文件格式支持：图片（jpg, jpeg, png, gif, webp, bmp）和视频（mp4, mov, avi, mkv, flv, wmv）
- 确定可见权限规则：上传者本人 + 公共区（可选） + 管理员列表
- 确定下载机制：预签名URL（2小时有效）
- 确定删除机制：仅上传者本人可删除；软删除 state=4→5；支持 state=4 时重复调用用于收尾清理（media_visible/MinIO）

### 2026-01-31
- 新增媒体列表接口：`GET /api/mediaVisible/list`（支持按专区 `currentUserId` 筛选）
- 新增媒体详情接口：`GET /api/media/{id}`（用于详情页展示）

### 2026-02-02
- 新增媒体审核流程：
  - 上传完成后媒体状态为 `state=6`（待审核），需审核通过后才能公开显示
  - 新增审核通过接口：`POST /api/media/audit/approve`（批量审核通过，`state=6` → `state=0`）
  - 新增审核驳回接口：`POST /api/media/audit/reject`（批量审核驳回，`state=6` → `state=7`）
  - 新增待审核列表接口：`GET /api/media/audit/pending`（分页查询待审核媒体）
  - 更新接口允许 `state=0/6/7` 修改基础信息；`state=7` 修改后自动重置为 `state=6` 需重新审核
  - 列表/详情/下载接口仅显示 `state=0`（已审核通过）的媒体
  - "我的上传"列表显示所有状态（排除 `state=5` 已删除），包括待审核和审核未通过状态
- 新增查询媒体所属成员专区接口：`GET /api/mediaVisible/{mediaId}/zones`（根据媒体ID查询该媒体属于哪些成员专区）

### 2026-02-13
- 新增用户管理接口（仅作者/管理员可操作）：
  - 新增修改用户等级接口：`PUT /api/user/{targetUserId}/level`（修改指定用户的等级）
  - 新增修改用户状态接口：`PUT /api/user/{targetUserId}/state`（修改指定用户的状态，如拉黑）
  - 新增获取用户列表接口：`GET /api/user/list`（分页获取用户列表，仅作者可操作，不包含作者用户）
