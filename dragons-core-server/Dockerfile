FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# 使用已在宿主机通过 Maven 打包好的 JAR
# 需要先在 dragons-core-server 目录执行：
#   mvn -DskipTests package
COPY target/dragons-core-server-0.0.1-SNAPSHOT.jar ./app.jar

# 可选 JVM 参数（例如内存、GC 设置），部署时通过环境变量覆盖
ENV JAVA_OPTS=""

# Spring Boot 默认端口
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

