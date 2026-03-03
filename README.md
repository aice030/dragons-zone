# 后浪粉丝资源分享平台

面向发癫吧，后浪粉丝的图片 / 视频资源分享与互动平台，支持公共区与成员专区浏览、树洞留言、媒体上传与审核、点赞排行榜等功能，前后端分离，可通过 Docker 快速本地运行。

## **抖音关注发癫吧，后浪！**

---

## 功能概览

- **游客浏览**
  - 访问公共区媒体列表、媒体详情与热门排行榜。
  - 支持按类型（图片 / 视频）筛选、分页浏览。

- **成员专区**
  - 为固定成员提供独立专区页面，展示该成员相关的图片 / 视频。
  - 支持公共区与各成员专区之间的切换。

- **媒体上传与管理**
  - 登录用户可上传图片 / 视频，采用两阶段上传 + 前端直传对象存储。
  - 支持查看「我的上传」列表、更新标题与描述、更新视频封面、删除媒体。

- **审核与资源管理**
  - 作者 / 管理员可在资源管理页查看待审核列表，对媒体批量审核通过或驳回。
  - 已审核媒体在公共区或成员专区对外展示。

- **点赞与热门榜单**
  - 登录用户可对媒体点赞 / 取消点赞。
  - 通过 Redis ZSET 维护点赞排行榜，前端在导航栏提供「热门内容」入口。

- **树洞留言**
  - 固定成员拥有树洞，粉丝可匿名或半匿名投递留言。
  - 树洞主人可标记已读、回复留言、删除留言、拉黑 / 解除拉黑投递者。
  - 支持树洞状态开关（允许 / 禁止投递）以及留言分享给其他树洞。

- **用户与权限（简要）**
  - **作者（level=0）**：拥有最高权限，可管理用户、审核媒体、查看所有数据。
  - **管理员（level=1）**：协助作者进行审核与管理操作。
  - **普通用户（level=2）**：可上传媒体、点赞、浏览、参与树洞。
  - **游客（level=3）**：可浏览公开媒体与热门内容，不能上传或点赞。

---

## 技术栈

- **后端**
  - Java 17、Spring Boot 3.3.x
  - Spring Security + JWT 认证
  - MyBatis-Plus 3.5.x
  - MySQL 8.0、Redis 7.0
  - 可选：RocketMQ（用于点赞异步落库等优化场景）
  - 对象存储：阿里云 OSS（默认）或 MinIO（可切换）

- **前端**
  - Vue 3、Vite
  - Pinia、Vue Router
  - Axios
  - ali-oss（前端直传 OSS）

---

## 环境要求

- **JDK**：17
- **Node.js**：20.19.0 或 22.12.0 及以上（参考 `dragons-frontend/package.json` 的 `engines` 字段）
- **MySQL**：8.0（开发环境实际使用）
- **Redis**：7.0（开发环境实际使用）
- **Maven**：3.6+（本地开发时需要）
- **Docker / Docker Compose**：用于快速本地启动前后端

---

## 快速开始（推荐：Docker Compose）

此方式使用根目录的 `docker-compose.yml` 在本机启动前后端服务，数据库、Redis 等由外部提供。

### 1. 准备工作

- 安装 Docker 与 Docker Compose。
- 准备好可访问的 MySQL 8.0 与 Redis 8.0 实例，并创建用于本项目的数据库。
- 克隆本仓库代码：

```bash
git clone <your-repo-url>
cd dragons-zone
```

### 2. 配置环境变量

- 根目录提供了示例环境变量文件：

```bash
cp .env.example .env
```

- 根据本地环境编辑 `.env`，至少需要关注以下变量（实际以 `.env.example` 为准）：
  - **数据库**
    - `DB_URL`：形如 `jdbc:mysql://<host>:<port>/<db>?useSSL=false&serverTimezone=UTC&characterEncoding=utf8mb4`
    - `DB_USER`：数据库用户名
    - `DB_PASS`：数据库密码
  - **Redis**
    - `REDIS_HOST`、`REDIS_PORT`
    - `REDIS_USER`（如无可留空）、`REDIS_PASS`
  - **前端访问后端的基础地址**
    - `VITE_API_BASE_URL`：例如 `http://localhost:8080`

`docker-compose.yml` 会将上述变量注入后端与前端容器，覆盖 `application.yml` 中相应配置。

### 3. 配置后端应用

- 后端配置示例位于：
  - `dragons-core-server/src/main/resources/application-example.yml`

可以按需复制为正式配置文件：

```bash
cd dragons-core-server/src/main/resources
cp application-example.yml application.yml
```

然后在 `application.yml` 中配置：

- **数据源**：MySQL 连接信息（URL / 用户名 / 密码）
- **Redis**：主机、端口、认证信息
- **对象存储（必选其一）**
  - **OSS**：在 `oss.*` 小节中配置 `accessKey`、`secretKey`、`endpoint`、`bucket`、STS 开关等。
  - **MinIO**：在 `minio.*` 小节中配置 `accessKey`、`secretKey`、`endpoint`、`bucket` 等。

RocketMQ 相关配置为可选，如不启用 RocketMQ，可保持默认关闭或留空。

在创建后端容器 / 镜像前，请先进入 `dragons-core-server` 目录并执行 `mvn clean package` 完成后端项目打包。

### 4. 启动前后端容器

在仓库根目录执行：

```bash
docker-compose up -d
```

首次执行会根据 `dragons-core-server/Dockerfile` 与 `dragons-frontend/Dockerfile` 构建镜像，然后启动：

- 后端容器：监听 `127.0.0.1:8080`
- 前端容器：通过 Nginx 暴露到 `127.0.0.1:8081`（容器内部 80 端口）

### 5. 访问应用

- 在浏览器中访问前端：
  - `http://localhost:8081`
- 后端接口调试（如需）：
  - `http://localhost:8080/api/...`

> 说明：根目录下还提供了 `nginx/dragons-zone.conf`，可用于在宿主机 Nginx 中配置反向代理，将 80 端口流量分发到前端与后端本地端口。该配置主要用于本地或自建环境示例，不作为生产部署规范。

---

## 手动启动（开发模式，可选）

在不使用 Docker 的情况下，也可以分别启动后端与前端，便于本地调试与开发。

### 后端服务

1. 导入项目  
   使用 IntelliJ IDEA 等 IDE 打开 `dragons-core-server` 目录，作为 Maven 项目加载依赖。

2. 准备配置  
   在 `src/main/resources` 下复制并编辑配置：

   ```bash
   cd dragons-core-server/src/main/resources
   cp application-example.yml application.yml
   ```

   按照前文说明，填入 MySQL、Redis、OSS/MinIO 等连接信息。

3. 启动服务  
   在 IDE 中运行 Spring Boot 主类，或在命令行中执行：

   ```bash
   cd dragons-core-server
   mvn spring-boot:run
   ```

   默认监听端口为 `8080`。

### 前端应用

1. 安装依赖

   ```bash
   cd dragons-frontend
   npm install
   ```

2. 配置开发环境变量  
   对本地开发场景，可直接在 `vite.config.js` 或前端代码中使用默认的 API 基础地址，也可以自定义环境变量文件（如 `.env.development`）指定：

   ```bash
   VITE_API_BASE_URL=http://localhost:8080
   ```

3. 启动开发服务器

   ```bash
   npm run dev
   ```

   Vite 默认会在 `http://localhost:5173`（或提示的端口）提供前端应用。

---

## 配置说明（概览）

- **数据库 & Redis**
  - 所有连接信息由后端 `application.yml` 与根目录 `.env` 共同决定。
  - 通常情况下，`application.yml` 负责默认配置（如本地开发），`.env` 通过环境变量覆盖容器中的连接信息。

- **对象存储（OSS / MinIO）**
  - 系统通过统一的 `StorageService` 接口访问对象存储，具体实现可在配置中切换为阿里云 OSS 或 MinIO。
  - 需要在 `application.yml` 中配置好对应的 `accessKey`、`secretKey`、`endpoint`、`bucket` 以及是否启用 STS 等参数。
  - 上传与下载均依赖对象存储，请确保相应账户具有读写权限。

- **可选组件：RocketMQ**
  - 项目内实现了基于 Redis + RocketMQ 的点赞异步落库方案，用于高并发场景下的性能优化。
  - 默认情况下可以仅依赖「先 DB 再 Redis」的方案，不配置 RocketMQ 也能正常使用点赞功能。
  - 如需启用 RocketMQ，请在 `application.yml` 中补充对应的连接与 Topic 配置。

---

## 文档与进一步阅读

仓库内提供了较为详细的设计与接口文档，可按需阅读：

- **API 接口说明**：`docs/api.md`
- **前端结构与页面说明**：`docs/frontend.md`
- **Redis 缓存与点赞设计**：`docs/redis_cache.md`
- **开发说明与已实现功能清单**：`docs/development.md`
- **问题与解决方案记录（设计取舍、踩坑与方案）**：`docs/overcome.md`

这些文档对缓存策略、文件上传与删除的一致性处理、树洞实现、对象存储抽象等关键设计都有较详细的说明。

---

## 安全与使用限制

- 本项目 **仅供学习、分享与实践使用**，**严禁任何形式的商业化使用**。
- 代码与文档保持开源免费，不收取任何费用。
- 对象存储中的媒体访问链接和下载链接采用 **短期预签名 URL**：
  - 有效期有限，过期后需要通过接口重新获取；
  - 不建议将对象存储 Bucket 设置为公开读写，以免造成资源泄露。
- 示例配置文件（如 `application-example.yml`、`.env.example`）中的密钥字段仅为占位，请务必使用自己的真实凭证，并避免将有效密钥提交到公共仓库。

---

## 规划与后续方向

项目当前已实现基础的媒体管理与互动功能，后续有以下规划方向：

- **媒体评论系统**：为图片 / 视频增加评论与回复功能。
- **粉丝交流讨论区**：提供独立的主题讨论版块，支持话题发帖与互动。
- **消息通知**：对审核结果、树洞回复、点赞等事件向用户推送站内通知。
- **彩蛋系统**：用户浏览网站过程中达成某些特定条件可以解锁隐藏内容。


