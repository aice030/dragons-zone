# 大文件分片上传方案

## 1. 概述与流程

- **协议**：HTTP（Java ↔ Go 转发/响应）+ gRPC（Go → Java 回调）。
- **入口**：前端仅调用 Java 上传接口；Java 按文件大小分流（如 ≥20MB 走大文件，否则走小文件）。

| 类型   | 流程 |
|--------|------|
| **小文件** | Java 执行完整上传（校验 → 哈希/幂等 → 落库 state=1 → 上传存储 → 更新 state → media_visible/封面/缓存），**全部完成后**返回前端「上传成功」。 |
| **大文件** | Java 校验参数、计算哈希校验幂等、落库 Media（state=1）、转发 Go；**收到 Go 的 202 后立即**返回前端「正在上传」；Go 后台上传，结束后通过 **gRPC 调用 Java** 做后续处理；state 落库即可，**不主动通知前端**，用户**刷新界面**即见最新状态。 |

---

## 2. 与现有实现的衔接

小文件逻辑与当前 `MediaServiceImpl.upload` 一致：入参校验 → 扩展名校验 → 整文件读入内存 → 计算哈希、幂等 → 落库（state=1）→ 上传存储 → 更新 state → media_visible、封面上传、缓存失效 → 返回 `UploadResult`。

大文件差异：文件写入存储由 Go 异步完成；Java 在收到 202 后即返回「正在上传」；实际上传结果由 gRPC 回调触发 Java 后续处理并落库，前端通过刷新获取状态。

---

## 3. 设计结论（精炼）

- **记录创建**：小文件、大文件均**先落库再上传**，用 `media.state` 表示进度（1=上传中、3=失败、6=待审核等）。
- **前端结果**：小文件 = 接口返回上传成功；大文件 = 接口先返回正在上传（可带 mediaId、state=1），最终状态**刷新即得**。
- **幂等与哈希**：hash 在 Java 内计算，前端不参与；大文件不在落库前做幂等；Go 上传完成后可经 gRPC 回传 `fileHash`，Java 仅回写库表，不参与幂等判断。
- **封面**：由 Java 处理，按库中 `coverPath` 上传；成功与否不影响主流程。
- **失败与回滚**：Go 侧清理临时数据并 gRPC 通知 Java；Java 将 `media.state` 置为 3，必要时清理对象存储（若 Go 未写入则不删）。

---

## 4. Java → Go：HTTP 接口

**职责**：Java 落库 Media（state=1）并生成 `objectName` 后，将大文件请求**流式转发**给 Go；Go 接收文件流并**异步**写入 OSS。

### 4.1 请求

- **Method / URL**：`POST /upload/stream`
- **Content-Type**：`multipart/form-data`

| part   | 说明 |
|--------|------|
| `meta` | `application/json`，见下表。 |
| `file` | 媒体文件二进制流（与前端一致），由 Java 流式转发，不落 Java 内存。 |

**`meta` 字段**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `mediaId` | number | 是 | 已落库 Media 主键，Go 回调时据此定位。 |
| `objectName` | string | 是 | 对象存储路径，即 Java `buildObjectName(originalFilename, category)`。 |
| `contentType` | string | 是 | 主文件 MIME（如 `video/mp4`）。 |
| `originalFilename` | string | 否 | 前端文件名，便于日志。 |

其余（category、title、visibleUserIds 等）由 Java 在 gRPC 回调后从库/上下文取，不在此传。

### 4.2 响应

仅用 **HTTP 状态码** 表示是否受理：

| 状态 | Body | 含义 |
|------|------|------|
| **202** | 无 | 已受理；Go 已接管文件流，将异步写 OSS；实际上传结果经 gRPC 通知 Java。 |
| **4xx / 5xx** | 必须 JSON：`{ "code": "<错误码>", "message": "<描述>" }` | 未受理；Java 将对应 `media.state` 置为 3，并打日志排查。 |

---

## 5. Go → Java：gRPC 接口

**职责**：Go 异步上传结束后（成功或失败），调用 Java 的 gRPC。Java 根据**成功/失败**执行：成功 → 更新 state、写 media_visible、按库中 `coverPath` 上传封面、失效缓存；失败 → `media.state` 置为 3、若 Go 已成功写入OSS，则清理存储。无需通知前端，用户刷新即见。

### 5.1 请求（Go → Java）

- **必带**：`mediaId`、**成功/失败标识**（否则 Java 无法分支处理）。
- **成功时可选**：`fileHash`（Java 回写 `Media.fileHash`）。
- **失败时必带**：`code`、`message`（便于日志与排查）。

Media 在创建时已落库 `storagePath`、`coverPath`、category、title 等，Java 按 `mediaId` 查库即可完成后续逻辑（含封面上传）。

### 5.2 响应（Java → Go）

- **成功**：不返回业务 body，Go 能感知调用成功即可（如 unary 无错误即成功）。
- **失败**：在响应或 error 中带 `code`、`message`，便于 Go 记录日志。

### 5.3 接口形式

成功与失败**共用同一 RPC**（如 `NotifyUploadResult`），请求中通过成功/失败标识区分；接口名由实现时确定。

### 5.4 OSS 校验

Go 上报上传成功后，Java **必须先校验 OSS 上该对象是否真实存在**，再执行后续操作（更新 state、media_visible、封面上传等），**不能完全信任 Go 服务**，避免状态与存储不一致。

### 5.5 幂等设计

Java 侧**必须做幂等**：若根据 `mediaId` 查库发现当前 **`state != 1`**（即已处理过），则**直接返回成功**，不再执行后续写 media_visible、封面、缓存等逻辑，防止因网络抖动导致 Go 重复调用而造成重复写入 media_visible 等表。

---

## 6. 后续可选

前端可后续通过 Canal 等监听 MySQL 变化主动推送上传结果，当前方案不依赖该能力，刷新即满足需求。
