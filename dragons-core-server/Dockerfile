# Dragons Zone 后端 - 多阶段构建，目标平台 Linux
# 在 macOS 上构建时可用: docker build --platform linux/amd64 -t dragons-backend .
# 1. 构建阶段：用阿里云镜像源的 maven 镜像
FROM --platform=$TARGETPLATFORM registry.cn-hangzhou.aliyuncs.com/acs/maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# 先只复制 pom，利用 Docker 层缓存
COPY pom.xml .
RUN mvn dependency:go-offline -B -q || true

COPY src ./src
RUN mvn package -DskipTests -B -q -q

# 运行阶段：仅 JRE，减小镜像
FROM --platform=$TARGETPLATFORM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN apk add --no-cache tzdata wget && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
