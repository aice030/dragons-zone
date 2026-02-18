# Dragons Zone 前端 - 多阶段构建，目标平台 Linux
# 构建时使用空 API baseURL，由同机 nginx 将 /api 代理到后端
# 在 macOS 上构建时可用: docker build --platform linux/amd64 -t dragons-frontend .
# 1. 构建阶段：用阿里云镜像源的 maven 镜像
FROM --platform=$TARGETPLATFORM registry.cn-hangzhou.aliyuncs.com/acs/node:20-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci

COPY . .
# 上云同机部署：使用相对路径，由 nginx 将 /api 代理到后端
ENV VITE_API_BASE_URL=
RUN npm run build

# 运行阶段：nginx 提供静态资源并代理 /api
FROM --platform=$TARGETPLATFORM nginx:alpine
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
