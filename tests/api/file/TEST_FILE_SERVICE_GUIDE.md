# File Service 外部调用测试指南

## 测试目的

验证 file-service 能否被外部成功调用（通过 REST API）。

## 当前状态

根据测试结果，file-service 目前**未运行**。

## File Service 架构说明

### 1. File Service 是什么？

File Service 是一个**独立的平台级文件服务**，特点：

- **独立部署**: 拥有独立的代码库、数据库和存储
- **多租户隔离**: 通过 `X-App-Id` 请求头实现不同应用的文件隔离
- **通用服务**: 可被多个应用（blog、im 等）共享使用

### 2. 与 blog-upload 的关系

- **blog-upload**: Blog 系统的上传服务，作为 **Feign Client** 调用 file-service
- **file-service**: 独立的文件服务，提供实际的文件存储和管理功能

```
┌─────────────┐
│ blog-post   │
│ blog-admin  │──┐
│ blog-upload │  │  通过 Feign Client 调用
└─────────────┘  │
                 ▼
         ┌──────────────┐
         │ file-service │ (独立服务)
         └──────────────┘
                 │
                 ▼
         ┌──────────────┐
         │ MinIO/S3     │ (对象存储)
         └──────────────┘
```

## 启动 File Service

### 方式 1: 使用 Docker (推荐用于开发测试)

```powershell
# 进入 file-service docker 目录
cd file-service/docker

# 启动所有依赖服务（PostgreSQL + MinIO + file-service）
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f file-service
```

### 方式 2: 本地 Maven 启动

如果 file-service 源代码在其他位置：

```powershell
# 进入 file-service 源代码目录
cd /path/to/file-service

# 启动依赖服务（PostgreSQL + MinIO）
cd docker
docker-compose up -d postgres minio

# 返回项目根目录启动服务
cd ..
mvn spring-boot:run
```

### 方式 3: 使用已部署的 File Service

如果 file-service 已在其他环境部署，修改测试配置：

```powershell
# 使用自定义 URL 运行测试
.\test-file-service-external-call.ps1 -FileServiceUrl "http://your-file-service-host:8089"
```

## 运行测试

### 1. 基础外部调用测试

测试 file-service 是否可访问：

```powershell
cd tests/api/file
.\test-file-service-external-call.ps1
```

**测试内容**:
- `EXTERNAL-001`: Health check 端点
- `EXTERNAL-002`: API info 端点
- `EXTERNAL-003`: Upload API 可访问性（预期返回 400/401）

### 2. 完整 API 测试

需要先启动 blog-user 服务（用于认证）：

```powershell
# 启动 blog-user 服务
cd blog-user
mvn spring-boot:run

# 运行完整测试
cd tests/api/file
.\test-file-service-api.ps1
```

**测试内容**:
- X-App-Id 验证
- 跨应用访问隔离
- 文件去重
- 文件操作（上传、获取、删除）

### 3. Blog 集成测试

测试 blog 服务与 file-service 的集成：

```powershell
cd tests/api/file
.\test-blog-to-file-service-integration.ps1
```

## 预期测试结果

### 成功场景

```
========================================
File Service External Call Test
Service URL: http://localhost:8089
========================================

[EXTERNAL-001] Testing health check endpoint...
  [PASS] Health check successful (45ms)

[EXTERNAL-002] Testing API info endpoint...
  [PASS] API info endpoint accessible (32ms)

[EXTERNAL-003] Testing upload API without authentication...
  [PASS] Upload API is accessible - Status: 400 (28ms)
  Note: API correctly rejected request without proper auth/headers

========================================
Test Results Summary
========================================

Total Tests: 3
Passed: 3
Failed: 0

[SUCCESS] File service can be called externally!
```

### 失败场景（服务未启动）

```
[EXTERNAL-001] Testing health check endpoint...
  [FAIL] Health check failed - Status: 0, Error: 无法连接到远程服务器

[FAILED] Some tests failed. Check if file-service is running.
```

## 故障排查

### 问题 1: 无法连接到 file-service

**症状**: `无法连接到远程服务器`

**解决方案**:
1. 检查 file-service 是否启动：
   ```powershell
   # 检查端口 8089 是否被监听
   netstat -ano | findstr :8089
   ```

2. 检查 Docker 容器状态：
   ```powershell
   cd file-service/docker
   docker-compose ps
   ```

3. 查看服务日志：
   ```powershell
   docker-compose logs file-service
   ```

### 问题 2: file-service 源代码不在当前项目

**说明**: file-service 是独立项目，可能在其他代码库

**解决方案**:
1. 使用 Docker 方式启动（推荐）
2. 或者克隆 file-service 独立项目
3. 或者连接到已部署的 file-service 实例

### 问题 3: 依赖服务未启动

**症状**: file-service 启动失败，日志显示数据库或 MinIO 连接错误

**解决方案**:
```powershell
cd file-service/docker

# 启动依赖服务
docker-compose up -d postgres minio

# 等待服务就绪
Start-Sleep -Seconds 10

# 启动 file-service
docker-compose up -d file-service
```

## 配置说明

### 测试环境配置

文件: `tests/config/test-env.json`

```json
{
  "file_service_url": "http://localhost:8089",
  "user_service_url": "http://localhost:8081",
  ...
}
```

### File Service 配置

文件: `file-service/docker/.env`

```env
# 数据库配置
POSTGRES_HOST=postgres
POSTGRES_PORT=5432
POSTGRES_DB=file_service
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# MinIO 配置
MINIO_ENDPOINT=http://minio:9000
MINIO_ACCESS_KEY=fileservice
MINIO_SECRET_KEY=fileservice123
MINIO_BUCKET=platform-files
```

## 下一步

测试通过后，可以进行以下工作：

1. **集成到 blog-upload**: 
   - 修改 blog-upload 使用 FileServiceClient
   - 测试文件上传功能

2. **集成到其他服务**:
   - blog-post: 文章封面图片
   - blog-admin: 用户头像管理
   - blog-message: 文件消息发送

3. **性能测试**:
   - 并发上传测试
   - 大文件上传测试
   - 文件去重效果验证

## 参考文档

- [File Service README](../../../file-service/README.md)
- [File Service API 文档](../../../file-service/API.md)
- [集成指南](../../../file-service/INTEGRATION.md)
- [Blog 集成设置](../../../file-service/BLOG_INTEGRATION_SETUP.md)

## 总结

File Service 是一个**独立的平台级服务**，需要单独启动。Blog 系统通过 Feign Client 调用其 API。

**关键点**:
- File Service 有独立的代码库、数据库和存储
- 使用 Docker 是最简单的启动方式
- 所有 API 调用必须携带 `X-App-Id` 请求头
- 不同应用的文件数据完全隔离
