# 快速运行秒传功能测试

> **注意**: 本测试文档针对旧的 ZhiCore-upload 服务。RustFS 对象存储已移至独立的 `file-service`。
> 新的文件服务测试请参考 `file-service/docker/README.md`。

## 一键启动测试

### 前提条件
- Docker Desktop 已安装并运行
- Java 17+ 已安装
- Maven 已安装

### 步骤 1: 启动基础设施

```powershell
# 进入 docker 目录
cd docker

# 启动 PostgreSQL, Redis, RustFS
docker-compose up -d postgres redis rustfs

# 等待服务启动（约 10 秒）
Start-Sleep -Seconds 10

# 验证服务状态
docker-compose ps
```

### 步骤 2: 运行数据库迁移

```powershell
# 返回项目根目录
cd ..

# 运行 Flyway 迁移
cd ZhiCore-migration
mvn flyway:migrate

# 返回根目录
cd ..
```

### 步骤 3: 启动用户服务（新终端）

```powershell
# 新终端 1
cd ZhiCore-user
mvn spring-boot:run
```

### 步骤 4: 启动上传服务（新终端，S3 模式）

```powershell
# 新终端 2
cd ZhiCore-upload
$env:STORAGE_TYPE = 's3'
mvn spring-boot:run
```

### 步骤 5: 运行测试

```powershell
# 原终端
cd tests/api/upload
.\test-instant-upload-integration.ps1
```

## 预期结果

```
========================================
Instant Upload Integration Tests
Upload Service URL: http://localhost:8089
========================================

Creating test files...
Test files created: ...\test-image.jpg, ...\test-file.txt

Setting up first test user...
  User registered: instant_test_20260119124530
  User logged in: userId=12345

Setting up second test user...
  Second user registered: instant_test2_20260119124530
  Second user logged in: userId=12346

=== SECTION 1: Basic Instant Upload Tests ===

[INSTANT-001] Testing first upload (baseline)...
  PASS - First upload successful (FileId: 019bd496-xxxx-xxxx-xxxx-xxxxxxxxxxxx)

[INSTANT-002] Testing instant upload (same user, same file)...
  PASS - Instant upload successful (same FileId)

=== SECTION 2: File Deduplication Tests ===

[INSTANT-003] Testing deduplication (different user, same file)...
  PASS - File deduplicated (FileId: 019bd496-yyyy-yyyy-yyyy-yyyyyyyyyyyy)

=== SECTION 3: Reference Count Deletion Tests ===

[INSTANT-004] Testing file deletion (reference count decrement)...
  PASS - File deleted successfully (45ms)

[INSTANT-005] Testing second user file still exists...
  PASS - Second user file still exists

[INSTANT-006] Testing final file deletion (reference count to zero)...
  PASS - File deleted successfully (42ms)

=== SECTION 4: Different File Type Tests ===

[INSTANT-007] Testing text file upload (second baseline)...
  PASS - Text file uploaded (FileId: 019bd496-zzzz-zzzz-zzzz-zzzzzzzzzzzz)

[INSTANT-008] Testing text file instant upload...
  PASS - Text file instant upload successful

========================================
Test Results Summary
========================================

Total Tests: 8
Passed: 8
Failed: 0
Skipped: 0

All tests passed successfully!
```

## 验证数据库

### 查看 storage_objects 表

```powershell
# 连接到 PostgreSQL
docker exec -it ZhiCore-postgres psql -U postgres -d ZhiCore_upload

# 查询存储对象
SELECT id, file_hash, storage_path, reference_count, created_at
FROM storage_objects
ORDER BY created_at DESC
LIMIT 5;
```

### 查看 file_records 表

```sql
SELECT id, user_id, storage_object_id, original_name, status, created_at
FROM file_records
ORDER BY created_at DESC
LIMIT 10;
```

## 验证 RustFS

1. 打开浏览器访问: http://localhost:9101
2. 登录: admin / admin123456
3. 浏览 `ZhiCore-uploads` bucket
4. 验证文件是否存在

## 故障排查

### 问题: 用户服务启动失败

```powershell
# 检查端口占用
netstat -ano | findstr :8081

# 如果端口被占用，终止进程
taskkill /PID <PID> /F
```

### 问题: 上传服务启动失败

```powershell
# 检查 RustFS 是否运行
docker ps | findstr rustfs

# 检查 RustFS 日志
docker logs ZhiCore-rustfs

# 重启 RustFS
docker-compose restart rustfs
```

### 问题: 测试失败

```powershell
# 检查服务健康状态
Invoke-WebRequest http://localhost:8081/actuator/health
Invoke-WebRequest http://localhost:8089/actuator/health

# 查看服务日志
# 用户服务日志在启动终端
# 上传服务日志在启动终端
```

## 清理环境

```powershell
# 停止服务（Ctrl+C 在各个终端）

# 停止 Docker 容器
cd docker
docker-compose down

# 清理数据（可选）
docker-compose down -v
```

## 下一步

测试通过后，可以继续：
- Task 24: 实现预签名 URL 服务
- Task 26: 实现文件类型验证服务
- Task 28: 实现文件访问控制服务

## 相关文档

- [详细测试指南](README-INSTANT-UPLOAD-TEST.md)
- [秒传功能设计](../../../.kiro/specs/minio-storage/design.md#9-秒传功能设计)
- [任务列表](../../../.kiro/specs/minio-storage/tasks.md)
