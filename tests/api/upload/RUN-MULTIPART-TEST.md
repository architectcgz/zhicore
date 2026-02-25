# 如何运行分片上传集成测试

> **注意**: 本测试文档针对旧的 ZhiCore-upload 服务。RustFS 对象存储已移至独立的 `file-service`。
> 新的文件服务测试请参考 `file-service/docker/README.md`。

## 当前状态

测试脚本已创建但尚未执行。需要先启动必要的服务。

## 前置条件检查

### 1. 检查 Docker 服务状态

```powershell
# 检查 RustFS
docker ps --filter "name=rustfs"

# 检查 PostgreSQL
docker ps --filter "name=postgres"
```

### 2. 启动基础设施服务

如果服务未运行，执行：

```powershell
cd docker
docker-compose up -d rustfs postgres redis
```

### 3. 检查数据库迁移

确保上传相关的数据库表已创建：

```powershell
# 运行迁移
mvn flyway:migrate -pl ZhiCore-migration

# 验证表是否存在
docker-compose exec postgres psql -U ZhiCore_user -d ZhiCore_db -c "\dt upload_*"
```

应该看到以下表：
- `upload_tasks`
- `upload_parts`
- `file_records`
- `storage_objects`

### 4. 启动 User 服务（用于认证）

```powershell
# 在新的终端窗口中
mvn spring-boot:run -pl ZhiCore-user
```

等待服务启动完成，然后验证：

```powershell
curl http://localhost:8081/actuator/health
```

### 5. 启动 Upload 服务（配置 S3 模式）

```powershell
# 在新的终端窗口中
$env:STORAGE_TYPE = 's3'
mvn spring-boot:run -pl ZhiCore-upload
```

等待服务启动完成，然后验证：

```powershell
curl http://localhost:8089/actuator/health
```

## 运行测试

所有服务启动后，运行测试脚本：

```powershell
cd tests/api/upload
.\test-multipart-upload-integration.ps1
```

## 预期测试结果

测试应该执行以下场景：

1. ✅ RustFS 连接检查
2. ✅ Upload 服务连接检查
3. ✅ 用户注册和登录
4. ✅ 创建 15MB 测试文件
5. ✅ 初始化分片上传
6. ✅ 上传所有分片（3个，每个5MB）
7. ✅ 查询上传进度
8. ✅ 完成分片上传
9. ✅ 验证文件存在于 RustFS
10. ✅ 测试断点续传（相同 hash）
11. ✅ 测试部分上传和恢复
12. ✅ 列出上传任务

## 快速启动脚本

为了方便，可以创建一个启动脚本：

```powershell
# start-test-env.ps1
Write-Host "Starting test environment..." -ForegroundColor Cyan

# 1. Start infrastructure
Write-Host "1. Starting Docker services..." -ForegroundColor Yellow
cd docker
docker-compose up -d rustfs postgres redis
cd ..

# 2. Wait for services
Write-Host "2. Waiting for services to be ready..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# 3. Run migrations
Write-Host "3. Running database migrations..." -ForegroundColor Yellow
mvn flyway:migrate -pl ZhiCore-migration

# 4. Start user service in background
Write-Host "4. Starting user service..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "mvn spring-boot:run -pl ZhiCore-user"

# 5. Wait a bit
Start-Sleep -Seconds 15

# 6. Start upload service in background with S3 mode
Write-Host "5. Starting upload service (S3 mode)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$env:STORAGE_TYPE='s3'; mvn spring-boot:run -pl ZhiCore-upload"

# 7. Wait for services to start
Write-Host "6. Waiting for services to start..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

Write-Host ""
Write-Host "Test environment ready!" -ForegroundColor Green
Write-Host "Run tests with: cd tests/api/upload; .\test-multipart-upload-integration.ps1" -ForegroundColor Cyan
```

## 故障排查

### RustFS 状态为 unhealthy

这通常不影响测试，因为 RustFS 的健康检查可能配置较严格。只要能连接到端口 9100 即可。

### 服务启动失败

检查日志：

```powershell
# User service logs
mvn spring-boot:run -pl ZhiCore-user

# Upload service logs  
$env:STORAGE_TYPE = 's3'
mvn spring-boot:run -pl ZhiCore-upload
```

### 数据库连接错误

确保 PostgreSQL 正在运行且迁移已执行：

```powershell
docker-compose ps postgres
mvn flyway:migrate -pl ZhiCore-migration
```

### 测试失败

查看测试输出中的详细错误信息，通常会指出具体问题。

## 清理

测试完成后，可以停止服务：

```powershell
# 停止 Docker 服务
cd docker
docker-compose down

# 停止 Spring Boot 服务（在各自的终端窗口中按 Ctrl+C）
```

## 下一步

测试通过后，可以继续实现：
- Task 20: 文件访问控制
- Task 22: 秒传功能
- Task 24: 预签名 URL 服务
