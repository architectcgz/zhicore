FROM eclipse-temurin:17-jre

WORKDIR /app

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# 复制 jar 文件
COPY target/zhicore-upload-1.0.0-SNAPSHOT.jar app.jar

# 暴露端口
EXPOSE 8089

ENV SERVER_PORT=8089

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD sh -c 'curl -sf "http://localhost:${SERVER_PORT}/actuator/health" || exit 1'

# 启动应用
ENTRYPOINT ["sh", "-c", "java -Dserver.port=$SERVER_PORT -jar app.jar"]
