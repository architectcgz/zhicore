# ===========================================
# ZhiCore Admin Service Dockerfile
# ===========================================

FROM eclipse-temurin:17-jre

LABEL maintainer="ZhiCore Team"
LABEL service="ZhiCore-admin"

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 安装必要工具
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# 创建应用目录
RUN mkdir -p /app /app/logs

WORKDIR /app

# 复制 JAR 文件
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

# 环境变量
ENV SERVER_PORT=8090
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
ENV SPRING_PROFILES_ACTIVE=prod
ENV SW_AGENT_NAME=ZhiCore-admin
ENV SW_AGENT_COLLECTOR_BACKEND_SERVICES=skywalking-oap:11800

# 暴露端口
EXPOSE 8090

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD sh -c 'curl -sf "http://localhost:${SERVER_PORT}/actuator/health" || exit 1'

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=$SERVER_PORT -jar app.jar"]
