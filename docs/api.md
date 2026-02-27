# API 接口说明

## 约定

- **统一响应格式**：`{ "code", "message", "data", "timestamp" }`。成功时 `code` 为 200，失败时 `data` 多为 null，错误信息见 `message` 及文末状态码说明。
- **接口前缀**：用户 `/api/user/`；媒体 `/api/media/`、`/api/media/visible/`；树洞 `/api/treehole/`、`/api/treehole/message/visible/`、`/api/treehole/blacklist/`。
- **鉴权**：需登录的接口请在请求头携带 `Authorization: Bearer <JWT_TOKEN>`。

---

## 一、用户

### 1. 用户登录

**POST** `/api/user/login`

用户登录，验证用户名和密码，返回 JWT 与用户基本信息。

**请求参数**（JSON Body）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| loginName | String | 是 | 登录名 |
| password | String | 是 | 密码 |

**成功响应（200）**

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

**失败响应**：4002 用户名或密码错误；4007 用户已注销；4008 用户已拉黑。

---

### 2. 用户注册

**POST** `/api/user/register`

新用户注册，创建账号。

**请求参数**（JSON Body）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| loginName | String | 是 | 登录名（唯一） |
| password | String | 是 | 密码 |
| nickName | String | 是 | 昵称 |
| phoneNumber | String | 是 | 手机号（唯一，11 位数字） |

**成功响应（200）**

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

**失败响应**：4001 用户名已存在；4009 手机号已注册；4010 手机号格式错误；400 请求参数错误。

---

### 3. 用户注销

**POST** `/api/user/deregister`

用户注销账号（逻辑删除），需密码二次确认。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`

**请求参数**（JSON Body）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| password | String | 是 | 当前密码 |

**成功响应（200）**

```json
{
  "code": 200,
  "message": "注销成功",
  "data": null,
  "timestamp": 1705564800000
}
```

**失败响应**：401 未授权；4003 Token 无效或已过期；4002 密码错误；4007 用户已注销。

---

### 4. 通过手机号修改密码（已登录）

**POST** `/api/user/resetPasswordByPhone`

已登录用户通过手机号修改本人密码；手机号须与当前用户一致。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`

**请求参数**（JSON Body）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| phoneNumber | String | 是 | 手机号（须为当前用户绑定） |
| newPassword | String | 是 | 新密码 |

**成功响应（200）**

```json
{
  "code": 200,
  "message": "修改成功",
  "data": null,
  "timestamp": 1705564800000
}
```

**失败响应**：401 未授权；403 手机号与当前用户不匹配。

---

### 5. 未登录找回密码

**POST** `/api/user/forgotPassword`

未登录状态下通过登录名与手机号校验身份后重置密码（无需验证码）。

**请求参数**（JSON Body）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| loginName | String | 是 | 登录名 |
| phoneNumber | String | 是 | 注册时绑定的手机号 |
| newPassword | String | 是 | 新密码（6～64 位） |

**成功响应（200）**

```json
{
  "code": 200,
  "message": "密码已重置，请使用新密码登录",
  "data": null,
  "timestamp": 1705564800000
}
```

**失败响应**：400 参数错误；4010 手机号格式错误；4017 登录名与手机号不匹配；4007/4008 用户已注销/已拉黑。

---

### 6. 修改用户等级

**PUT** `/api/user/{targetUserId}/level`

修改指定用户的等级，仅作者或管理员可操作。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`

**路径参数**：`targetUserId`（Long，目标用户 ID）

**请求参数**（JSON Body）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| level | Byte | 是 | 0=作者，1=管理员，2=普通用户，3=游客 |

**成功响应（200）**

```json
{
  "code": 200,
  "message": "用户等级修改成功",
  "data": null,
  "timestamp": 1705564800000
}
```

**失败响应**：401 未授权；403 无权限；400 参数错误；404 用户不存在。

---

### 7. 修改用户状态

**PUT** `/api/user/{targetUserId}/state`

修改指定用户的状态，仅作者或管理员可操作。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`

**路径参数**：`targetUserId`（Long）

**请求参数**（JSON Body）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| state | Byte | 是 | 0=正常，1=逻辑删除，2=黑名单 |

**成功响应（200）**

```json
{
  "code": 200,
  "message": "用户状态修改成功",
  "data": null,
  "timestamp": 1705564800000
}
```

**失败响应**：401/403/400/404。

---

### 8. 根据用户 ID 获取昵称

**GET** `/api/user/{userId}/nickname`

根据用户 ID 查询昵称。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`

**路径参数**：`userId`（Long）

**成功响应（200）**

```json
{
  "code": 200,
  "message": "查询成功",
  "data": "用户昵称",
  "timestamp": 1705564800000
}
```

**失败响应**：401 未授权；404 用户不存在；400 参数错误。

---

### 9. 获取用户列表

**GET** `/api/user/list`

分页获取用户列表，仅作者可操作；列表不包含作者账号。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`

**查询参数**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 20，最大 100 |

**成功响应（200）**

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

**失败响应**：401 未授权；403 无权限（非作者）。

---

### 10. 记录上传前承诺

**POST** `/api/user/{currentUserId}/promise`

在用户首次或再次上传前，记录一条「我承诺不上传违规/侵权内容」的操作日志；仅允许当前登录用户为自己记录。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`

**路径参数**：`currentUserId`（Long，当前登录用户 ID，必须与 JWT 中用户 ID 一致）

**请求参数**：无（Body 为空）

**成功响应（200）**

```json
{
  "code": 200,
  "message": "承诺记录成功",
  "data": null,
  "timestamp": 1705564800000
}
```

**失败响应**：401 未授权；403 当前用户与路径 ID 不一致；500 服务器内部错误。

---

## 二、媒体

**游客模式**：未登录可访问以下接口，无需携带 `Authorization`：  
`GET /api/media/visible/list`、`GET /api/media/{id}`、`GET /api/media/{id}/download`、`GET /api/media/visible/{mediaId}/zones`、`GET /api/media/visible/rank`。其余写操作及「我的上传」、审核等需登录。

**媒体 state**：0=正常，1=正在上传，2=上传成功，3=上传失败，4=正在删除，5=已删除，6=待审核，7=审核未通过。

---

### 1. 准备上传

**POST** `/api/media/upload`

两阶段上传之第一阶段：校验参数与 file_hash 幂等，落库 media（state=1），返回 mediaId、storagePath、uploadUrl 及可选 stsCredentials；不接收主文件与封面，主文件由前端直传 OSS。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`，`Content-Type: multipart/form-data`

**请求参数**（multipart/form-data）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| file_hash | String | 是 | 前端计算的文件 hash，用于幂等 |
| category | Byte | 是 | 0=图片，1=视频 |
| title | String | 否 | 标题 |
| description | String | 否 | 描述 |
| filename | String | 否 | 原始文件名（缺省按 category 用 .jpg/.mp4） |

**成功响应（200）**

```json
{
  "code": 200,
  "message": "可以开始上传",
  "data": {
    "mediaId": 1,
    "storagePath": "images/2026/01/21/abc123-test.jpg",
    "uploadUrl": "https://...?Expires=...&OSSAccessKeyId=...&Signature=...",
    "uploadUrlExpireSeconds": 3600,
    "stsCredentials": {
      "accessKeyId": "...",
      "accessKeySecret": "...",
      "securityToken": "...",
      "expiration": 1771866228,
      "region": "oss-cn-beijing",
      "bucket": "media"
    }
  },
  "timestamp": 1705564800000
}
```

**失败响应**：401 未授权；400 参数错误；4005 文件格式不支持；幂等命中时可能返回 200（已有 media）或 4xx。

---

### 2. 通知上传结果

**POST** `/api/media/upload/complete`

两阶段上传之第二阶段：前端直传 OSS 成功或失败后调用；success=true 时后端校验 OSS 对象存在后写 media_visible、处理封面并将 state 置为 6（待审核）。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`，`Content-Type: multipart/form-data`

**请求参数**（multipart/form-data）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| mediaId | Long | 是 | 准备上传返回的 mediaId |
| success | Boolean | 是 | true=上传成功，false=上传失败 |
| visibleUserIds | String | 是 | JSON 数组字符串，如 `[1,2,3]` 或 `[]` |
| cover | File | 否 | 封面图；视频可不传（由前端抽第一帧或默认封面） |
| code | String | 否 | 失败时错误码 |
| message | String | 否 | 失败时描述 |

**成功响应（200）**

```json
{
  "code": 200,
  "message": "已处理",
  "data": null,
  "timestamp": 1705564800000
}
```

**失败响应**：401/404/400/4005。

---

### 3. 更新媒体基础信息

**PUT** `/api/media/{id}`

更新媒体标题与描述（不改文件、封面、可见范围）；仅上传者本人，且 state 为 0/6/7 时可更新；修改后状态重置为待审核（6）。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`，`Content-Type: application/x-www-form-urlencoded`

**路径参数**：`id`（Long，媒体 ID）

**请求参数**（form 或 x-www-form-urlencoded）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| title | String | 否 | 标题 |
| description | String | 否 | 描述 |

**成功响应（200）**

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

---

### 4. 更新视频封面

**PUT** `/api/media/{id}/cover`

上传新封面并更新数据库；仅上传者本人、仅视频文件且 state=0。先上传对象存储再更新 DB，DB 失败则补偿删除对象存储中的新封面。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`，`Content-Type: multipart/form-data`

**路径参数**：`id`（Long）

**请求参数**（multipart/form-data）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| cover | File | 是 | 封面图片 |

**成功响应（200）**

```json
{
  "code": 200,
  "message": "封面更新成功",
  "data": {
    "mediaId": 1,
    "coverPath": "covers/2026/01/31/abc123-cover.jpg",
    "coverUrl": "http://...?X-Amz-Algorithm=...&X-Amz-Expires=7200&..."
  },
  "timestamp": 1705564800000
}
```

---

### 5. 修复/重建媒体可见范围

**PUT** `/api/media/{id}/visible`

仅更新 media_visible 表，用于成员专区筛选；仅上传者本人；差量同步，利用事务保证原子性；visibleUserIds 最多 12 个，对应 12 名后浪成员。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`，`Content-Type: application/x-www-form-urlencoded`

**路径参数**：`id`（Long）

**请求参数**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| visibleUserIds | String | 是 | JSON 数组字符串，如 `[1,2,3]` 或 `[]`，最多 12 个 |

**成功响应（200）**

```json
{
  "code": 200,
  "message": "标签更新成功",
  "data": {
    "mediaId": 1,
    "storagePath": "images/2026/01/21/abc123-test.jpg",
    "category": 0,
    "visibleUserIds": [1, 2, 3]
  },
  "timestamp": 1705564800000
}
```

---

### 6. 获取媒体下载 URL

**GET** `/api/media/{id}/download`

获取媒体预签名下载 URL（有效期 5 分钟）。游客仅可获取 state=0（公开）的媒体资源；登录后上传者本人或作者/管理员可获取 state=0/6/7（审核预览）。

**路径参数**：`id`（Long）

**成功响应（200）**

```json
{
  "code": 200,
  "message": "获取成功",
  "data": {
    "downloadUrl": "http://...?X-Amz-Algorithm=...&X-Amz-Expires=300&..."
  },
  "timestamp": 1705564800000
}
```

**失败响应**：404 资源不存在。

---

### 7. 删除媒体资源

**DELETE** `/api/media/{id}/delete`

删除媒体（仅上传者本人或作者/管理员）。流程：state→4，删 media_visible 与对象存储，再物理删除 media 表记录；除修改 state 失败会报错外，其他操作均不会报错，会保留 state=4 供用户感知，以再次调用，同时保证了幂等。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`

**路径参数**：`id`（Long）

**成功响应（200）**

```json
{
  "code": 200,
  "message": "删除成功",
  "data": null,
  "timestamp": 1705564800000
}
```

**失败响应**：401 未授权；404 资源不存在。

---

### 8. 获取媒体列表（标签筛选）

**GET** `/api/media/visible/list`

分页获取媒体列表，支持公共区与成员专区；仅返回 state=0（已审核通过）。游客可访问。

**查询参数**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 10，最大 100 |
| category | Byte | 否 | 0=图片，1=视频 |
| currentUserId | Long | 否 | 0=公共区，成员 ID=该成员专区，默认 0 |

**成功响应（200）**

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
        "coverUrl": "http://...?X-Amz-Expires=7200&..."
      }
    ]
  },
  "timestamp": 1705564800000
}
```

---

### 9. 获取媒体详情

**GET** `/api/media/{id}`

获取媒体详情。游客仅可查看 state=0；登录后上传者本人或作者/管理员可查看 state=0/6/7。

**路径参数**：`id`（Long）

**成功响应（200）**

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
    "coverUrl": "http://...?X-Amz-Expires=7200&...",
    "uploaderId": 1,
    "updateTime": "2026-01-31T12:34:56"
  },
  "timestamp": 1705564800000
}
```

**失败响应**：404 资源不存在。

---

### 10. 获取「我的上传」列表

**GET** `/api/media/visible/my/list`

上传者本人查看自己上传的媒体列表（含待审核、审核未通过，排除 state=5 已删除，）。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`

**查询参数**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page | Integer | 否 | 默认 1 |
| size | Integer | 否 | 默认 10，最大 100 |
| category | Byte | 否 | 0=图片，1=视频 |

**成功响应（200）**

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
        "coverUrl": "http://...?X-Amz-Expires=7200&..."
      }
    ]
  },
  "timestamp": 1705564800000
}
```

---

### 11. 查询媒体所属成员专区

**GET** `/api/media/visible/{mediaId}/zones`

根据媒体 ID 返回该媒体所在的成员专区 ID 列表；仅公共区可见时返回空数组。游客可访问。

**路径参数**：`mediaId`（Long）

**成功响应（200）**

```json
{
  "code": 200,
  "message": "查询成功",
  "data": [1, 2, 3],
  "timestamp": 1705564800000
}
```

**失败响应**：400 mediaId 无效。

---

### 12. 审核通过

**POST** `/api/media/audit/approve`

批量将媒体从待审核（state=6）改为正常（state=0）；仅作者或管理员；非事务，返回失败项列表。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`，`Content-Type: application/json`

**请求参数**（JSON Body）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| mediaIds | List&lt;Long&gt; | 是 | 媒体 ID 列表 |

**成功响应（200）**

全部成功示例：

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

部分失败时 `data.failedItems` 为 `[{ "mediaId": 2, "title": "媒体标题" }, { "mediaId": 999, "title": "媒体id999不存在" }]`，message 为「部分审核通过失败」。

**失败响应**：401 未授权；4022 无审核权限。

---

### 13. 审核驳回

**POST** `/api/media/audit/reject`

批量将媒体从待审核（state=6）改为审核未通过（state=7）；仅作者或管理员；非事务，返回失败项列表。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`，`Content-Type: application/json`

**请求参数**（JSON Body）：`mediaIds`（List&lt;Long&gt;，必填）

**成功响应（200）**

全部成功：

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

部分失败时结构同审核通过，`data.failedItems` 含失败项。

**失败响应**：401 未授权；4022 无审核权限。

---

### 14. 待审核媒体列表

**GET** `/api/media/audit/pending`

分页查询待审核（state=6）媒体列表；仅作者或管理员。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`

**查询参数**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page | Integer | 否 | 默认 1 |
| size | Integer | 否 | 默认 10 |
| category | Byte | 否 | 0=图片，1=视频 |

**成功响应（200）**

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

**失败响应**：401/4022。

---

### 15. 点赞

**POST** `/api/media/{id}/like`

当前用户对指定媒体点赞；需登录；仅 state=0 可点赞；同一用户同一媒体仅可点赞一次（重复请求幂等成功）。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`

**路径参数**：`id`（Long，媒体 ID）

**成功响应（200）**

```json
{
  "code": 200,
  "message": "点赞成功",
  "data": null,
  "timestamp": 1705564800000
}
```

**失败响应**：401 未授权；404 媒体不存在或非 state=0。

---

### 16. 取消点赞

**POST** `/api/media/{id}/unlike`

当前用户取消对指定媒体的点赞；需登录；未点赞过则幂等成功。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`

**路径参数**：`id`（Long）

**成功响应（200）**

```json
{
  "code": 200,
  "message": "已取消点赞",
  "data": null,
  "timestamp": 1705564800000
}
```

**失败响应**：401/404。

---

### 17. 查询当前用户是否已赞

**GET** `/api/userLikeRecord/media/{mediaId}/status`

查询当前登录用户是否已对指定媒体点赞；需登录。data 为 true 表示已赞，false 表示未赞。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`

**路径参数**：`mediaId`（Long）

**成功响应（200）**

```json
{
  "code": 200,
  "message": "查询成功",
  "data": true,
  "timestamp": 1705564800000
}
```

**失败响应**：401/404。

---

### 18. 热门排行榜

**GET** `/api/media/visible/rank`

按点赞数从高到低返回热门媒体 Top N；不做分页与专区；游客可访问。

**查询参数**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| category | String | 否 | 不传=全部，0=图片，1=视频 |
| size | Integer | 否 | 条数，默认 20，最大 100 |

**成功响应（200）**

```json
{
  "code": 200,
  "message": "查询成功",
  "data": [
    {
      "id": 1,
      "category": 0,
      "title": "标题",
      "description": "描述",
      "coverUrl": "https://...",
      "likeCount": 10
    }
  ],
  "timestamp": 1705564800000
}
```

---

## 三、树洞

**树洞主人**：仅固定 12 位后浪成员拥有树洞（tree_hole 预置）；普通用户无创建树洞接口。  
**通用**：所有树洞接口需登录；投递与回复共用 `POST /api/treehole/{ownerId}/sent/messages`，body 中 `rootMessageId` 为空为投递新留言，非空为树洞主人回复该条（仅支持一条回复）；同一投递者在上一条未读前不可再次投递（防刷）。

---

### 1. 投递留言 / 主人回复

**POST** `/api/treehole/{ownerId}/sent/messages`

向指定树洞投递新留言，或树洞主人回复某条留言（通过 rootMessageId 区分）。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`，`Content-Type: application/json`

**路径参数**：`ownerId`（Long，树洞主人用户 ID）

**请求参数**（JSON Body）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| content | String | 是 | 留言或回复内容 |
| rootMessageId | Long | 否 | 为空=投递新留言；非空=主人回复该条留言 |

**成功响应（200）**

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

**失败响应**：401 未授权；404 树洞不存在；403 禁止投递（树洞关闭或防刷）或回复权限不足。

---

### 2. 留言列表

**GET** `/api/treehole/{ownerId}/messages`

获取指定树洞的留言列表；主人看全部根留言，非主人看自己投递的根留言及主人回复自己的那条。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`

**路径参数**：`ownerId`（Long）

**查询参数**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page | Integer | 否 | 默认 1 |
| size | Integer | 否 | 默认 10 |

**成功响应（200）**

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
        "senderNickName": "用户昵称",
        "content": "留言内容",
        "state": 0,
        "rootMessageId": null
      }
    ]
  },
  "timestamp": 1705564800000
}
```

`state`：0=未读，1=已读，2=已删除，3=已回复。`rootMessageId` 为 null 表示根留言，非 null 表示该条为主人对某根留言的回复。

---

### 3. 留言标记已读

**PUT** `/api/treehole/messages/{messageId}/read`

树洞主人将留言标记为已读。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`

**路径参数**：`messageId`（Long）

**成功响应（200）**

```json
{
  "code": 200,
  "message": "更新成功",
  "data": null,
  "timestamp": 1705564800000
}
```

**失败响应**：401/404/403（非该树洞主人）。

---

### 4. 树洞主人删除留言

**DELETE** `/api/treehole/messages/{messageId}`

树洞主人全局删除该条留言。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`

**路径参数**：`messageId`（Long）

**成功响应（200）**：`data` 为 null。

**失败响应**：401/404/403。

---

### 5. 发送者删除留言

**DELETE** `/api/treehole/messages/{messageId}/sender`

发送者删除留言（仅对发送者本人不可见）。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`

**路径参数**：`messageId`（Long）

**成功响应（200）**：`data` 为 null。

---

### 6. 分享留言

**POST** `/api/treehole/{ownerId}/messages/{messageId}/share`

树洞主人将一条自己树洞下的留言分享给其他树洞主人（可多人）；对方在分享收件箱中可见。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`，`Content-Type: application/json`

**路径参数**：`ownerId`（Long，当前用户即分享者），`messageId`（Long）

**请求参数**（JSON Body）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ownerIds | List&lt;Long&gt; | 是 | 接收方树洞主人用户 ID 列表，如 `[2, 3, 5]` |

**成功响应（200）**

```json
{
  "code": 200,
  "message": "分享成功",
  "data": null,
  "timestamp": 1705564800000
}
```

**失败响应**：401/400/403/404；部分失败时 message 可能为「分享给xxx失败」。

---

### 7. 分享收件箱列表

**GET** `/api/treehole/message/visible/shared/list`

树洞主人查看「别人分享给自己的留言」列表，分页。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`

**查询参数**：`page`（默认 1），`size`（默认 10）

**成功响应（200）**：结构与「留言列表」一致（`data.total`、`data.list`）。

---

### 8. 获取树洞信息

**GET** `/api/treehole/{ownerId}`

根据树洞主人用户 ID 获取树洞信息（含 state），用于前端展示开关状态。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`

**路径参数**：`ownerId`（Long）

**成功响应（200）**

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "id": 1,
    "ownerId": 4,
    "state": 0
  },
  "timestamp": 1705564800000
}
```

`data.state`：0=正常（允许投递），1=保留，2=禁止投递。

**失败响应**：401/404。

---

### 9. 设置树洞状态

**PUT** `/api/treehole/{ownerId}/state`

树洞主人开启/关闭「允许投递」；仅本人可操作。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`，`Content-Type: application/json`

**路径参数**：`ownerId`（Long）

**请求参数**（JSON Body）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| state | Byte | 是 | 0=正常（允许投递），2=禁止投递 |

**成功响应（200）**：`data` 为 null，message 为「更新成功」。

**失败响应**：401/403/400（state 非 0/2）。

---

### 10. 查询是否已拉黑

**GET** `/api/treehole/blacklist/check`

树洞主人查询是否已拉黑某用户。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`

**查询参数**：`blockedUserId`（Long，必填）

**成功响应（200）**

```json
{
  "code": 200,
  "message": "查询成功",
  "data": true,
  "timestamp": 1705564800000
}
```

`data` 为 true 表示已拉黑，false 表示未拉黑或已解除。

**失败响应**：401/400/403。

---

### 11. 拉黑用户

**POST** `/api/treehole/blacklist/block`

树洞主人拉黑某用户；拉黑后该用户无法再向该树洞投递新留言。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`，`Content-Type: application/json`

**请求参数**（JSON Body）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| blockedUserId | Long | 是 | 被拉黑用户 ID |
| reason | String | 否 | 拉黑原因 |

**成功响应（200）**：`data` 为 null，message 为「成功，该用户已被拉黑」。

**失败响应**：401/400/403；已存在且 state=0 时可幂等成功。

---

### 12. 解除拉黑

**POST** `/api/treehole/blacklist/unblock`

树洞主人解除对某用户的拉黑；幂等。

**请求头**：`Authorization: Bearer <JWT_TOKEN>`，`Content-Type: application/json`

**请求参数**（JSON Body）：`blockedUserId`（Long，必填）

**成功响应（200）**：`data` 为 null，message 为「解除拉黑成功」。

**失败响应**：401/403。

---

## 四、状态码与枚举

**通用状态码**  
200 成功；400 请求参数错误；401 未授权（未登录或 Token 过期）；403 无权限；404 资源不存在；500 服务器内部错误。

**业务状态码**  
4001 用户名已存在；4002 用户名或密码错误/密码错误；4003 Token 无效或已过期；4005 文件格式不支持；4007 用户已注销；4008 用户已拉黑；4009 手机号已注册；4010 手机号格式错误；4017 登录名与手机号不匹配；4022 无审核权限。

**用户 state**：0 正常，1 已注销，2 已拉黑。  
**用户 level**：0 作者，1 管理员，2 普通用户，3 游客。  
**树洞 state**：0 正常（允许投递），1 保留，2 禁止投递。  
**留言 state**：0 未读，1 已读，2 已删除，3 已回复。

**失败响应体示例**（格式统一，仅 code/message 不同）：

```json
{
  "code": 4002,
  "message": "用户名或密码错误",
  "data": null,
  "timestamp": 1705564800000
}
```
