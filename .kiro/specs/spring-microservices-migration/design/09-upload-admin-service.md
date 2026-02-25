# Upload & Admin Service 设计

## Upload Service

### DDD 分层结构

```
upload-service/
├── src/main/java/com/ZhiCore/upload/
│   ├── interfaces/
│   │   ├── controller/
│   │   │   └── UploadController.java
│   │   └── dto/
│   ├── application/
│   │   └── service/
│   │       └── UploadApplicationService.java
│   ├── domain/
│   │   ├── model/
│   │   │   ├── UploadFile.java
│   │   │   └── ImageProcessConfig.java
│   │   └── service/
│   │       └── ImageProcessService.java
│   └── infrastructure/
│       ├── storage/
│       │   ├── StorageService.java
│       │   ├── LocalStorageService.java
│       │   └── OssStorageService.java
│       └── image/
│           └── ImageProcessor.java
```


### 上传服务实现

```java
@Service
public class UploadApplicationService {
    
    private final StorageService storageService;
    private final ImageProcessor imageProcessor;
    private final LeafIdGenerator idGenerator;
    
    @Value("${upload.max-size:10485760}")  // 10MB
    private long maxFileSize;
    
    @Value("${upload.allowed-types:image/jpeg,image/png,image/gif,image/webp}")
    private Set<String> allowedImageTypes;
    
    public UploadResult uploadImage(MultipartFile file, String userId) {
        // 1. 验证文件
        validateFile(file);
        
        // 2. 生成文件名
        String fileId = String.valueOf(idGenerator.nextId());
        String extension = getExtension(file.getOriginalFilename());
        String fileName = fileId + "." + extension;
        
        // 3. 图片处理（压缩、转WebP、生成缩略图）
        byte[] processedImage = imageProcessor.process(file.getBytes(), 
            ImageProcessConfig.builder()
                .maxWidth(1920)
                .maxHeight(1080)
                .quality(0.85)
                .convertToWebP(true)
                .build()
        );
        
        byte[] thumbnail = imageProcessor.generateThumbnail(file.getBytes(), 200, 200);
        
        // 4. 上传到存储
        String imageUrl = storageService.upload(processedImage, "images/" + fileName);
        String thumbnailUrl = storageService.upload(thumbnail, "thumbnails/" + fileName);
        
        return UploadResult.builder()
            .fileId(fileId)
            .url(imageUrl)
            .thumbnailUrl(thumbnailUrl)
            .originalName(file.getOriginalFilename())
            .size(processedImage.length)
            .build();
    }
    
    public UploadResult uploadFile(MultipartFile file, String userId) {
        validateFile(file);
        
        String fileId = String.valueOf(idGenerator.nextId());
        String extension = getExtension(file.getOriginalFilename());
        String fileName = fileId + "." + extension;
        
        String fileUrl = storageService.upload(file.getBytes(), "files/" + fileName);
        
        return UploadResult.builder()
            .fileId(fileId)
            .url(fileUrl)
            .originalName(file.getOriginalFilename())
            .size(file.getSize())
            .build();
    }
    
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        if (file.getSize() > maxFileSize) {
            throw new BusinessException("文件大小超过限制");
        }
    }
}
```

### 存储服务（策略模式）

```java
public interface StorageService {
    String upload(byte[] data, String path);
    void delete(String path);
    String getUrl(String path);
}

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "oss")
public class OssStorageService implements StorageService {
    
    private final OSS ossClient;
    
    @Value("${storage.oss.bucket}")
    private String bucket;
    
    @Value("${storage.oss.cdn-domain}")
    private String cdnDomain;
    
    @Override
    public String upload(byte[] data, String path) {
        ossClient.putObject(bucket, path, new ByteArrayInputStream(data));
        return cdnDomain + "/" + path;
    }
    
    @Override
    public void delete(String path) {
        ossClient.deleteObject(bucket, path);
    }
    
    @Override
    public String getUrl(String path) {
        return cdnDomain + "/" + path;
    }
}
```

---

## Admin Service

### DDD 分层结构

```
admin-service/
├── src/main/java/com/ZhiCore/admin/
│   ├── interfaces/
│   │   ├── controller/
│   │   │   ├── UserManageController.java
│   │   │   ├── PostManageController.java
│   │   │   ├── CommentManageController.java
│   │   │   └── ReportManageController.java
│   │   └── dto/
│   ├── application/
│   │   └── service/
│   │       ├── UserManageService.java
│   │       ├── PostManageService.java
│   │       ├── CommentManageService.java
│   │       └── ReportManageService.java
│   ├── domain/
│   │   ├── model/
│   │   │   └── AuditLog.java
│   │   └── repository/
│   │       └── AuditLogRepository.java
│   └── infrastructure/
│       ├── feign/
│       │   ├── UserServiceClient.java
│       │   ├── PostServiceClient.java
│       │   └── CommentServiceClient.java
│       └── repository/
│           └── mapper/
```


### 管理服务实现

```java
@Service
public class UserManageService {
    
    private final UserServiceClient userServiceClient;
    private final AuditLogRepository auditLogRepository;
    
    public PageResult<UserManageVO> listUsers(UserQueryRequest request) {
        return userServiceClient.queryUsers(request);
    }
    
    @Transactional
    public void disableUser(String adminId, String userId, String reason) {
        // 1. 禁用用户
        userServiceClient.disableUser(userId);
        
        // 2. 使用户 Token 失效
        userServiceClient.invalidateUserTokens(userId);
        
        // 3. 记录审计日志
        AuditLog log = AuditLog.create(
            adminId,
            AuditAction.DISABLE_USER,
            "user",
            userId,
            reason
        );
        auditLogRepository.save(log);
    }
    
    @Transactional
    public void enableUser(String adminId, String userId) {
        userServiceClient.enableUser(userId);
        
        AuditLog log = AuditLog.create(
            adminId,
            AuditAction.ENABLE_USER,
            "user",
            userId,
            null
        );
        auditLogRepository.save(log);
    }
}

@Service
public class PostManageService {
    
    private final PostServiceClient postServiceClient;
    private final SearchServiceClient searchServiceClient;
    private final AuditLogRepository auditLogRepository;
    
    public PageResult<PostManageVO> listPosts(PostQueryRequest request) {
        return postServiceClient.queryPosts(request);
    }
    
    @Transactional
    public void deletePost(String adminId, Long postId, String reason) {
        // 1. 软删除文章
        postServiceClient.deletePost(postId);
        
        // 2. 从搜索索引中移除
        searchServiceClient.removePostIndex(postId);
        
        // 3. 记录审计日志
        AuditLog log = AuditLog.create(
            adminId,
            AuditAction.DELETE_POST,
            "post",
            String.valueOf(postId),
            reason
        );
        auditLogRepository.save(log);
    }
}

@Service
public class ReportManageService {
    
    private final ReportRepository reportRepository;
    private final PostManageService postManageService;
    private final CommentManageService commentManageService;
    private final UserManageService userManageService;
    
    public PageResult<ReportVO> listPendingReports(int page, int size) {
        return reportRepository.findPendingReports(page, size);
    }
    
    @Transactional
    public void handleReport(String adminId, Long reportId, ReportHandleRequest request) {
        Report report = reportRepository.findById(reportId);
        
        // 执行处罚
        switch (request.getAction()) {
            case DELETE_CONTENT -> {
                if ("post".equals(report.getTargetType())) {
                    postManageService.deletePost(adminId, 
                        Long.parseLong(report.getTargetId()), request.getReason());
                } else if ("comment".equals(report.getTargetType())) {
                    commentManageService.deleteComment(adminId,
                        Long.parseLong(report.getTargetId()), request.getReason());
                }
            }
            case WARN_USER -> {
                // 发送警告通知
            }
            case BAN_USER -> {
                userManageService.disableUser(adminId, 
                    report.getReportedUserId(), request.getReason());
            }
            case IGNORE -> {
                // 忽略举报
            }
        }
        
        // 更新举报状态
        report.handle(adminId, request.getAction(), request.getReason());
        reportRepository.update(report);
    }
}
```

### 审计日志

```java
public class AuditLog {
    private final Long id;
    private final String operatorId;
    private final AuditAction action;
    private final String targetType;
    private final String targetId;
    private final String reason;
    private final LocalDateTime createdAt;
    
    public static AuditLog create(String operatorId, AuditAction action,
                                  String targetType, String targetId, String reason) {
        AuditLog log = new AuditLog();
        log.operatorId = operatorId;
        log.action = action;
        log.targetType = targetType;
        log.targetId = targetId;
        log.reason = reason;
        log.createdAt = LocalDateTime.now();
        return log;
    }
}

public enum AuditAction {
    DISABLE_USER,
    ENABLE_USER,
    DELETE_POST,
    DELETE_COMMENT,
    HANDLE_REPORT,
    ASSIGN_ROLE,
    REVOKE_ROLE
}
```

### 数据库表设计

```sql
-- 审计日志表 (Admin_DB)
CREATE TABLE audit_logs (
    id BIGINT PRIMARY KEY,
    operator_id VARCHAR(36) NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(50) NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_operator ON audit_logs(operator_id, created_at DESC);
CREATE INDEX idx_audit_logs_target ON audit_logs(target_type, target_id);
```
