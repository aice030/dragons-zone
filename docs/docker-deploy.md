# Docker 部署说明（macOS 构建 → 阿里云 Linux 运行）

## 前置

- 本机：Docker Desktop（macOS），构建时会为 `linux/amd64` 打镜像，可直接在阿里云 ECS（x86）上跑。
- 若服务器是 ARM（如 Graviton），在 `docker-compose.yml` 里把 `platform: linux/amd64` 改为 `platform: linux/arm64` 后重新构建。

## 1. 配置环境变量

在项目根目录复制示例并编辑（**勿提交 .env**）：

```bash
cp .env.example .env
# 编辑 .env，填写数据库、JWT、OSS 等
```

- **用阿里云 RDS**：填 `SPRING_DATASOURCE_*` 为 RDS 地址/用户名/密码，部署时**不**带 MySQL 容器。
- **用同机 MySQL 容器**：用 `.env.example` 里「使用同机 MySQL 容器」那组，并加上下面 `--profile with-db`。

## 2. 构建镜像（可在 macOS 上执行）

```bash
docker compose build
```

构建出的镜像是 Linux 镜像，可在本机做基本验证，也可直接推送到阿里云镜像仓库再在 ECS 上拉取运行。

## 3. 启动方式

**使用阿里云 RDS（推荐）：**

```bash
docker compose up -d
```

只启动 `backend` 和 `frontend`，后端通过 `.env` 里的 `SPRING_DATASOURCE_*` 连 RDS。

**使用同机 MySQL 容器：**

```bash
docker compose --profile with-db up -d
```

会启动 `mysql`、`backend`、`frontend`，数据落在 `mysql_data` volume。

## 4. 访问与端口

- 浏览器访问：`http://服务器IP`（前端 nginx 占 80，并把 `/api` 代理到后端 8080）。
- 仅需对外暴露 80；8080 仅在容器内网使用。

## 5. 常用命令

```bash
# 查看日志
docker compose logs -f

# 仅重启后端
docker compose restart backend

# 停止并删除容器（保留 volume）
docker compose down

# 停止并删除容器与 MySQL 数据 volume
docker compose --profile with-db down -v
```

## 6. 首次使用 MySQL 容器时

若使用 `--profile with-db`，需先在 MySQL 里建库表。可用项目里的建表脚本在 MySQL 容器内执行，或从本机 `mysql` 客户端连到服务器 3306（若已映射）执行。
