FROM openjdk:21-jdk-slim

WORKDIR /app

# 复制 jar 文件
COPY target/zhicore-upload-1.0.0-SNAPSHOT.jar app.jar

# 暴露端口
EXPOSE 8088

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
