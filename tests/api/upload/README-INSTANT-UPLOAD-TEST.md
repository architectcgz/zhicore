# 秒传功能集成测试指南

> **注意**: 本测试文档针对旧的 ZhiCore-upload 服务。RustFS 对象存储已移至独立的 `file-service`。
> 新的文件服务测试请参考 `file-service/docker/README.md`。

## 概述

本测试脚本验证文件秒传（Instant Upload）功能，包括文件去重和引用计数管理。

## 测试覆盖

### Section 1: 基础秒传测试
- **INSTANT-001**: 首次上传文件（建立基准）
- **INSTANT-002**: 同一用户上传相同文件（秒传）

### Section 2: 文件去重测试
- **INSTANT-003**: 不同用户上传相同文件（去重）

### Section 3: 引用计数删除测试
- **INSTANT-004**: 删除第一个用户的文件（引用计数减1）
- **INSTANT-005**: 验证第二个用户的文件仍然存在
- **INSTANT-006**: 删除第二个用户的文件（引用计数归零，S3对象应被删除）

### Section 4: 不同文件类型测试
- **INSTANT-007**: 上传文本文件（建立第二个基准）
- **INSTANT-008**: 同一用户再次上传相同文本文件（秒传）

## 前置条件

### 1. 启动必要的服务

```powershell
# 启动基础设施
cd docker
docker-compose up -d postgres redis rustfs

# 启动用户服务
cd ../ZhiCore-user
mvn spring-boot:run

# 启动上传服务（S3 模式）
cd ../ZhiCore-upload
$env:STORAGE_TYPE = 's3'
mvn spring-boot:run
```

### 2. 运行数据库迁移

```powershell
cd ZhiCore-migration
mvn flyway:migrate
```

## 运行测试

```powershell
cd tests/api/upload
.\test-instant-upload-integration.ps1
```

## 测试原理

### 秒传流程

1. **首次上传**:
   - 计算文件 MD5 hash
   - 上传文件到 S3
   - 创建 `storage_objects` 记录（reference_count = 1）
   - 创建 `file_records` 记录

2. **秒传（同一用户）**:
   - 计算文件 MD5 hash
   - 查询 `storage_objects` 表（根据 hash）
   - 查询 `file_records` 表（根据 userId + hash）
   - 如果已存在，直接返回文件信息
   - 如果不存在，创建新的 `file_records` 记录

3. **去重（不同用户）**:
   - 计算文件 MD5 hash
   - 查询 `storage_objects` 表（根据 hash）
   - 如果存在，创建新的 `file_records` 记录
   - 增加 `storage_objects.reference_count`
   - **不上传文件到 S3**（复用已有对象）

4. **引用计数删除**:
   - 删除 `file_records` 记录（软删除）
   - 减少 `storage_objects.reference_count`
   - 如果 `reference_count = 0`，删除 S3 对象和 `storage_objects` 记录

## 验证要点

### 1. 秒传验证
- 同一用户上传相同文件应该立即返回
- 响应时间应该明显快于首次上传
- 返回的 URL 应该相同或指向相同的存储对象

### 2. 去重验证
- 不同用户上传相同文件应该创建不同的 `file_records`
- 但应该共享同一个 `storage_objects`
- S3 中只有一份文件副本

### 3. 引用计数验证
- 删除一个用户的文件后，其他用户的文件应该仍然可访问
- 删除最后一个引用后，S3 对象应该被删除
- 可以通过 RustFS Console 验证对象是否被删除

## 数据库验证

### 查询 storage_objects 表
```sql
SELECT id, file_hash, storage_path, reference_count, created_at
FROM storage_objects
WHERE file_hash = '<your_file_hash>';
```

### 查询 file_records 表
```sql
SELECT id, user_id, storage_object_id, original_name, status, created_at
FROM file_records
WHERE file_hash = '<your_file_hash>';
```

## RustFS Console 验证

1. 访问 http://localhost:9101
2. 登录（admin / admin123456）
3. 浏览 `ZhiCore-uploads` bucket
4. 验证文件是否存在或已被删除

## 故障排查

### 问题：秒传不生效

**可能原因**:
- 文件 hash 计算不一致
- `storage_objects` 表中没有记录
- 数据库连接问题

**解决方法**:
```powershell
# 检查数据库连接
docker exec -it ZhiCore-postgres psql -U postgres -d ZhiCore_upload

# 查询 storage_objects
SELECT * FROM storage_objects ORDER BY created_at DESC LIMIT 5;
```

### 问题：引用计数不正确

**可能原因**:
- 事务未提交
- 并发问题
- 删除逻辑错误

**解决方法**:
```sql
-- 检查引用计数
SELECT so.id, so.file_hash, so.reference_count, COUNT(fr.id) as actual_references
FROM storage_objects so
LEFT JOIN file_records fr ON fr.storage_object_id = so.id AND fr.status = 'COMPLETED'
GROUP BY so.id, so.file_hash, so.reference_count
HAVING so.reference_count != COUNT(fr.id);
```

### 问题：S3 对象未删除

**可能原因**:
- 引用计数未归零
- S3 删除失败
- 异步删除延迟

**解决方法**:
```powershell
# 检查 RustFS 日志
docker logs ZhiCore-rustfs

# 手动验证 S3 对象
aws s3 ls s3://ZhiCore-uploads/ --endpoint-url http://localhost:9100
```

## 性能基准

| 操作 | 预期响应时间 |
|------|-------------|
| 首次上传（1KB 文件） | < 100ms |
| 秒传（同一用户） | < 50ms |
| 去重（不同用户） | < 50ms |
| 文件删除 | < 50ms |

## 注意事项

1. **测试隔离**: 每次测试使用唯一的用户名和邮箱
2. **文件清理**: 测试结束后自动清理临时文件
3. **数据库状态**: 测试不会清理数据库记录（用于验证）
4. **并发测试**: 当前脚本不支持并发测试

## 扩展测试

### 大文件秒传测试
```powershell
# 创建 10MB 测试文件
$LargeFile = New-Object byte[] (10MB)
[System.IO.File]::WriteAllBytes("large-test.bin", $LargeFile)

# 运行测试（需要修改脚本支持大文件）
```

### 并发秒传测试
```powershell
# 使用 PowerShell Jobs 进行并发测试
1..10 | ForEach-Object -Parallel {
    .\test-instant-upload-integration.ps1
} -ThrottleLimit 5
```

## 相关文档

- [秒传功能设计文档](../../../.kiro/specs/minio-storage/design.md#9-秒传功能设计)
- [文件去重设计文档](../../../.kiro/specs/minio-storage/design.md#10-文件去重与引用计数设计)
- [RustFS 配置指南](../../../docker/README.md)
