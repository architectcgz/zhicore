# Multipart Upload Integration Test Guide

> **Note**: This test documentation is for the legacy ZhiCore-upload service. RustFS object storage has been moved to the independent `file-service`.
> For new file service tests, please refer to `file-service/docker/README.md`.

## Overview

This document describes the comprehensive integration test for the multipart upload feature, which validates the complete flow of uploading large files using S3-compatible storage (RustFS/MinIO).

## Test Coverage

The integration test (`test-multipart-upload-integration.ps1`) covers the following scenarios:

### ✅ Test 1: Create Large Test File
- Creates a 15MB test file (exceeds 10MB threshold for multipart upload)
- Calculates MD5 hash for file integrity verification
- **Validates**: File creation and hash calculation

### ✅ Test 2: Initialize Multipart Upload
- Sends initialization request with file metadata
- Receives task ID, upload ID, chunk size, and total parts
- **Validates**: REQ-7 (分片上传初始化)

### ✅ Test 3: Upload Parts
- Uploads all parts sequentially
- Tracks ETag for each uploaded part
- **Validates**: REQ-7 (分片上传)

### ✅ Test 4: Check Upload Progress
- Queries upload progress during upload
- Verifies completed parts count and percentage
- **Validates**: REQ-9 (上传进度查询)

### ✅ Test 5: Complete Multipart Upload
- Completes the multipart upload
- Receives file ID and URL
- **Validates**: REQ-7 (完成分片上传)

### ✅ Test 6: Verify File in RustFS
- Checks if uploaded file is accessible
- Verifies file size and content type
- **Validates**: REQ-1, REQ-3 (S3 存储验证)

### ✅ Test 7: Test Resumption (Same Hash)
- Attempts to upload file with same hash
- Verifies system detects existing completed upload
- **Validates**: REQ-8 (断点续传 - 已完成文件)

### ✅ Test 8: Test Partial Upload and Resumption
- Uploads only first 2 parts (simulates interruption)
- Re-initializes with same hash
- Verifies system returns existing task with completed parts
- **Validates**: REQ-8 (断点续传 - 未完成上传)

### ✅ Test 9: List Upload Tasks
- Lists all upload tasks for the user
- Verifies task metadata
- **Validates**: REQ-9 (任务列表查询)

## Prerequisites

### 1. Infrastructure Services

Ensure the following services are running:

```powershell
# Start RustFS (S3-compatible storage)
cd docker
docker-compose up -d rustfs

# Start PostgreSQL (for metadata storage)
docker-compose up -d postgres

# Verify services are running
docker-compose ps
```

### 2. Database Migrations

Ensure database migrations have been applied:

```powershell
# Run migrations
mvn flyway:migrate -pl ZhiCore-migration
```

### 3. Upload Service Configuration

The upload service must be configured to use S3 storage:

```powershell
# Set environment variable
$env:STORAGE_TYPE = 's3'

# Start the upload service
mvn spring-boot:run -pl ZhiCore-upload
```

Or configure in `application.yml`:

```yaml
storage:
  type: s3
  s3:
    endpoint: http://localhost:9100
    access-key: admin
    secret-key: admin123456
    bucket: ZhiCore-uploads
    region: us-east-1
  multipart:
    enabled: true
    threshold: 10485760        # 10MB
    chunk-size: 5242880        # 5MB
    max-parts: 10000
    task-expire-hours: 24
```

### 4. User Service

The user service must be running for authentication:

```powershell
mvn spring-boot:run -pl ZhiCore-user
```

## Running the Test

### Execute the Test Script

```powershell
cd tests/api/upload
.\test-multipart-upload-integration.ps1
```

### Expected Output

```
========================================
Multipart Upload Integration Test
========================================

=== Pre-check: RustFS Connection ===
[PASS] RustFS is running and healthy

=== Pre-check: Upload Service Connection ===
[PASS] Upload Service is running

=== Setup: User Authentication ===
[PASS] User created with ID: 123456
[PASS] Login successful, token obtained

=== Test 1: Create Large Test File ===
[PASS] Test file created successfully
  Path: C:\Users\...\large_test_file.bin
  Size: 15.0 MB
  Creation Time: 2.5 seconds
  Hash: d41d8cd98f00b204e9800998ecf8427e

=== Test 2: Initialize Multipart Upload ===
[PASS] Multipart upload initialized successfully
  Task ID: 01912345-6789-7abc-def0-123456789abc
  Upload ID: abc123...
  Chunk Size: 5.0 MB
  Total Parts: 3
  Completed Parts: 0
  Response Time: 150ms

=== Test 3: Upload Parts ===
  Part 1 uploaded successfully (ETag: "abc123", 250ms)
  Part 2 uploaded successfully (ETag: "def456", 245ms)
  Part 3 uploaded successfully (ETag: "ghi789", 240ms)
[PASS] All 3 parts uploaded successfully

=== Test 4: Check Upload Progress ===
[PASS] Upload progress retrieved successfully
  Total Parts: 3
  Completed Parts: 3
  Uploaded Bytes: 15.0 MB
  Total Bytes: 15.0 MB
  Percentage: 100%
[PASS] Progress matches uploaded parts count

=== Test 5: Complete Multipart Upload ===
[PASS] Multipart upload completed successfully
  File ID: 01912345-6789-7abc-def0-123456789abc
  URL: http://localhost:9100/ZhiCore-uploads/files/2026/01/18/123/01912345.bin
  Response Time: 300ms

=== Test 6: Verify File in RustFS ===
[PASS] File is accessible via returned URL
  Content-Type: application/octet-stream
  Content-Length: 15.0 MB

=== Test 7: Test Resumption (Same Hash) ===
[PASS] Resumption detected: returned existing completed task
  Task ID: 01912345-6789-7abc-def0-123456789abc (same as previous)
  Completed Parts: 3

=== Test 8: Test Partial Upload and Resumption ===
[PASS] Partial upload initialized
  Task ID: 01912345-6789-7abc-def0-987654321abc
  Total Parts: 3
  Part 1 uploaded
  Part 2 uploaded
[PASS] Resumption successful: returned existing task with completed parts
  Task ID: 01912345-6789-7abc-def0-987654321abc (same as previous)
  Completed Parts: 2
  Parts: 1, 2

=== Test 9: List Upload Tasks ===
[PASS] Upload tasks retrieved successfully
  Total Tasks: 2
  - Task ID: 01912345-6789-7abc-def0-123456789abc, Status: completed, File: large_test_file.bin
  - Task ID: 01912345-6789-7abc-def0-987654321abc, Status: uploading, File: partial_test_file.bin

========================================
Test Results Summary
========================================

Total Tests: 15
Passed: 15
Failed: 0
Skipped: 0

========================================
ALL TESTS PASSED
========================================

Multipart upload integration is working correctly!
Key features verified:
  - Large file multipart upload
  - Upload progress tracking
  - Upload resumption (断点续传)
  - Task management
```

## Troubleshooting

### RustFS Not Running

**Error**: `[FAIL] RustFS is not running or not accessible`

**Solution**:
```powershell
cd docker
docker-compose up -d rustfs
docker-compose logs rustfs
```

### Upload Service Not Running

**Error**: `Test Aborted - Upload Service Not Running`

**Solution**:
```powershell
# Check if service is running
curl http://localhost:8089/actuator/health

# Start the service
$env:STORAGE_TYPE = 's3'
mvn spring-boot:run -pl ZhiCore-upload
```

### Database Connection Error

**Error**: Upload initialization fails with database error

**Solution**:
```powershell
# Check PostgreSQL is running
docker-compose ps postgres

# Run migrations
mvn flyway:migrate -pl ZhiCore-migration

# Verify tables exist
docker-compose exec postgres psql -U ZhiCore_user -d ZhiCore_db -c "\dt upload_*"
```

### Authentication Failed

**Error**: `Test Aborted - Authentication Failed`

**Solution**:
```powershell
# Ensure user service is running
mvn spring-boot:run -pl ZhiCore-user

# Check user service health
curl http://localhost:8081/actuator/health
```

### File Not Accessible (403 Error)

**Note**: This is expected behavior if the bucket is private. The test will show a warning but continue.

**Explanation**: RustFS buckets are private by default. Files are stored successfully but require presigned URLs for access.

## Test Data Cleanup

The test script automatically cleans up temporary files after execution. However, uploaded files remain in RustFS for verification.

### Manual Cleanup

To remove test files from RustFS:

1. Open RustFS Console: http://localhost:9100/rustfs/console/browser
2. Login with: admin / admin123456
3. Navigate to bucket: ZhiCore-uploads
4. Delete test files manually

Or use AWS CLI:

```powershell
# List files
aws --endpoint-url http://localhost:9100 s3 ls s3://ZhiCore-uploads/files/ --recursive

# Delete specific file
aws --endpoint-url http://localhost:9100 s3 rm s3://ZhiCore-uploads/files/2026/01/18/123/01912345.bin
```

## Performance Benchmarks

Expected performance metrics (on typical development machine):

- **File Creation (15MB)**: 2-3 seconds
- **Upload Initialization**: 100-200ms
- **Part Upload (5MB)**: 200-300ms per part
- **Complete Upload**: 200-400ms
- **Total Upload Time (15MB)**: 1-2 seconds

## Next Steps

After successful integration test:

1. ✅ **Task 18 Complete**: Multipart upload integration verified
2. ⏭️ **Task 20**: Implement access control features
3. ⏭️ **Task 22**: Implement instant upload (秒传) service
4. ⏭️ **Task 24**: Implement presigned URL service

## Related Documentation

- [Requirements Document](../../../.kiro/specs/minio-storage/requirements.md)
- [Design Document](../../../.kiro/specs/minio-storage/design.md)
- [Task List](../../../.kiro/specs/minio-storage/tasks.md)
- [S3 Upload Integration Test](./test-s3-upload-integration.ps1)

## Support

For issues or questions:
1. Check service logs: `docker-compose logs -f ZhiCore-upload`
2. Verify RustFS logs: `docker-compose logs -f rustfs`
3. Check database state: `docker-compose exec postgres psql -U ZhiCore_user -d ZhiCore_db`
