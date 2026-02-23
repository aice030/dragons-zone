# 大文件分片上传方案

## 1. 概述与流程

- **原则**：**不由后端上传文件到 OSS**，由**前端**直连 OSS 上传；后端只负责落库、生成路径、校验幂等，以及在上传完成后校验 OSS 并执行后续业务。
- **协议**：HTTP（前端 ↔ Java）。前端先调「准备上传」接口拿到 media 与 OSS 存储路径，再使用 OSS 分块上传 SDK 直传，最后调「通知上传结果」接口由后端收尾。

| 步骤 | 说明 |
|------|------|
| 1 | 前端计算文件 hash，携带 `file_hash` 及现有上传参数（category、visibleUserIds、title、description、cover 等）调用**准备上传**接口。 |
| 2 | 后端做参数校验、**幂等校验（基于前端传来的 file_hash）**，落库 Media（state=1），生成 OSS 存储路径（现有逻辑），可选上传封面并写入 coverPath；返回 mediaId、storagePath 等，告知前端「可以开始上传」。 |
| 3 | 前端收到响应后，使用 **OSS 分块上传 SDK** 直传文件到 OSS（目标路径为后端返回的 storagePath）。 |
| 4 | 前端上传完成后，调用**通知上传结果**接口，携带 mediaId 与成功/失败标识。 |
| 5 | 后端收到通知后：**先校验 OSS 上该对象是否真实存在**，再按成功/失败执行后续逻辑（与现有一致）；成功则更新 state、写 media_visible、封面上传等，失败则 state=3 并视情况清理。 |

---

## 2. 与现有实现的衔接

- **准备上传接口**：在现有上传接口参数基础上增加必填项 `file_hash`（前端计算）；**不再由后端接收主文件流**，也不由后端计算或判断 hash，仅用前端传来的 `file_hash` 做幂等校验。主文件写入 OSS 完全由前端完成。
- **落库与路径**：后端仍先落库 Media（state=1），再生成 OSS 存储路径（与现有 `buildObjectName` 等逻辑一致），并通过响应把该路径告知前端。
- **封面**：可在准备阶段由后端接收 cover 并上传到 OSS，写入 `coverPath`；也可在「通知上传结果」之后由后端按库中 `coverPath` 处理，具体与现有封面逻辑保持一致即可。
- **通知上传结果后的处理**：与现有「上传成功/失败」逻辑一致：成功则更新 state、写 media_visible、封面上传、缓存失效等；失败则 state=3，必要时清理 OSS。

---

## 3. 设计结论（精炼）

- **记录创建**：先落库再上传（state=1），用 `media.state` 表示进度（1=上传中、3=失败、6=待审核等）。
- **前端结果**：准备接口返回 mediaId、storagePath 后，前端即开始直传 OSS；最终成功/失败通过「通知上传结果」接口回传，用户刷新即可见最新状态。
- **幂等与 hash**：**hash 由前端计算**，通过 `file_hash` 传给后端；后端**仅用该值做幂等校验**，不自行计算、不替代前端判断。
- **封面**：由后端处理（准备阶段上传或通知成功后按 coverPath 上传）；成功与否不影响主流程。
- **失败与回滚**：通知失败时后端将 `media.state` 置为 3，若 OSS 上已有残留对象则视情况清理。

---

## 4. 准备上传接口（后端）

**职责**：参数校验、基于 `file_hash` 的幂等校验、落库 Media（state=1）、生成 OSS 存储路径、可选上传封面；返回 mediaId、storagePath 等，供前端直传 OSS。

### 4.1 请求（前端 → 后端）

- **Method / URL**：与现有上传接口一致，例如 `POST /api/media/upload`。
- **Content-Type**：`multipart/form-data`。
- **在现有上传参数基础上增加**：
  - `file_hash`（必填）：前端计算的文件 hash，用于后端幂等校验；后端不计算、不覆盖该值。

其余参数与现有一致（如 category、visibleUserIds、title、description、cover 等）；**不传主文件**，主文件由前端收到响应后直传 OSS。

### 4.2 响应

- **成功（200）**：表示 media 已落库、可开始上传；Body 中至少包含：
  - `mediaId`：已落库的 Media 主键。
  - `storagePath`：OSS 中的存储路径（对象 key），前端直传 OSS 时使用该路径。
- 可选：若前端直传 OSS 需要临时凭证或预签名策略，可在此响应或单独接口中返回。
- **4xx / 5xx**：未受理（如参数错误、幂等命中等）；Body 为统一错误格式，如 `{ "code": "...", "message": "..." }`。

---

## 5. 前端直传 OSS（分块上传）

**职责**：前端在收到准备接口响应后，使用 **OSS 分块上传 SDK**（如阿里云 OSS Browser SDK）将文件上传到 OSS，目标路径为后端返回的 `storagePath`。

- 前端需自行持有或向后端申请 OSS 上传所需配置（endpoint、bucket、STS 临时凭证或预签名策略等）。
- 上传过程、分片大小、重试等由前端与 OSS SDK 完成，后端不参与文件传输。
- 上传成功或失败后，前端调用「通知上传结果」接口告知后端。

---

## 5.1 阿里云 OSS Bucket 跨域配置（CORS）— 必配

前端直传时，浏览器会向 **OSS 域名**（如 `dragons-media.oss-cn-beijing.aliyuncs.com`）发起请求，CORS 响应头由 **OSS 返回**，与后端 `CorsConfig.java` / `SecurityConfig` 无关（后端 CORS 只对发往本机的请求生效）。

因此必须在 **阿里云 OSS 控制台** 为使用的 Bucket 配置 CORS 规则，否则浏览器会报错：`No 'Access-Control-Allow-Origin' header is present on the requested resource`。

**配置步骤**（阿里云控制台 → 对象存储 OSS → 选择 Bucket → 数据安全 → 跨域设置 → 创建规则）：

| 配置项 | 建议值 |
|--------|--------|
| 来源 Origins | `http://localhost:5173`（开发）、线上前端域名如 `https://your-domain.com` |
| 允许 Methods | `GET`、`PUT`、`POST`、`DELETE`、`HEAD`（至少需包含 `PUT`） |
| 允许 Headers | `*` |
| 暴露 Headers | 可留空或填 `ETag` |
| 缓存时间（秒） | `600` 或按需 |

保存后即可支持前端从上述来源对 Bucket 发起直传 PUT 请求。

---

## 6. 通知上传结果接口（后端）

**职责**：前端在上传成功或失败后调用此接口；后端**先校验 OSS 上该对象是否真实存在**（成功时），再按成功/失败执行后续逻辑（与现有一致）。

### 6.1 请求

- **Method / URL**：例如 `POST /api/media/upload/complete` 或 `POST /api/media/{mediaId}/upload/complete`。
- **必带**：`mediaId`、**成功/失败标识**（如 `success: true/false`）。
- **成功时可选**：可回传 `file_hash` 等供后端回写 `Media.fileHash`。
- **失败时建议**：`code`、`message`，便于日志与排查。

### 6.2 后端逻辑

- **成功**：
  1. **先校验 OSS**：根据 media 的 `storagePath` 检查 OSS 上该对象是否存在（如 HEAD 或 GET 元数据），**未通过则视为失败**，不执行后续写库。
  2. 校验通过后：更新 media state、写 media_visible、按库中 coverPath 处理封面、失效缓存等（与现有成功逻辑一致）。
- **失败**：将 `media.state` 置为 3；若 OSS 上已有该对象（异常情况），可择机清理。
- **幂等**：若根据 `mediaId` 查库发现当前 **state != 1**（已处理过），则**直接返回成功**，不再重复写 media_visible、封面、缓存等。

---

## 7. 后续可选

前端可后续通过 Canal 等监听 MySQL 变化主动推送上传结果；当前方案不依赖该能力，刷新即满足需求。
