# File Service 数据流文档

## 概述

本文档详细描述了文件在 ZhiCore-microservice 系统中的完整数据流，包括上传、访问、删除等操作的详细流程。

## 核心数据流

### 1. 文件上传流程

#### 1.1 用户头像上传

```mermaid
sequenceDiagram
    autonumber
    participant User as 用户
    participant Browser as 浏览器
    participant Gateway as API 网关
    participant UserService as 用户服务
    participant SecurityContext as Security Context
    participant FileClient as File Service Client
    participant FileService as File Service
    participant PostgreSQL as PostgreSQL
    participant MinIO as MinIO
    participant UserDB as 用户数据库
    
    User->>Browser: 选择头像文件
    Browser->>Gateway: POST /api/user/avatar/upload<br/>+ JWT Token + File
    Gateway->>Gateway: 验证 JWT Token
    Gateway->>UserService: 转发请求
    
    UserService->>SecurityContext: 存储认证信息
    UserService->>UserService: 验证用户身份
    
    UserService->>FileClient: uploadImage(file)
    FileClient->>FileClient: 验证文件类型<br/>(只允许图片)
    FileClient->>FileClient: 验证文件大小<br/>(最大 10MB)
    FileClient->>FileClient: 转换为临时文件
    
    FileClient->>SecurityContext: 获取 JWT Token
    SecurityContext-->>FileClient: 返回 JWT Token
    
    FileClient->>FileService: POST /api/files/upload<br/>Authorization: Bearer {JWT}<br/>+ File
    
    FileService->>FileService: 验证 JWT Token
    FileService->>FileService: 提取用户信息和租户ID
    FileService->>FileService: 计算文件哈希值 (SHA-256)
    
    FileService->>PostgreSQL: SELECT * FROM files<br/>WHERE hash = ? AND file_size = ? AND tenant_id = ?
    
    alt 文件已存在（秒传）
        PostgreSQL-->>FileService: 返回已有文件信息
        FileService->>FileService: 记录秒传日志
        FileService->>PostgreSQL: 原子增加 ref_count
        FileService-->>FileClient: 返回文件 URL<br/>(秒传成功)
    else 文件不存在（正常上传）
        PostgreSQL-->>FileService: 文件不存在
        
        FileService->>FileService: 生成文件 ID (UUID)
        FileService->>FileService: 确定存储路径<br/>/{tenant}/{date}/{fileId}
        
        FileService->>MinIO: PUT /{bucket}/{path}<br/>上传文件
        MinIO-->>FileService: 返回存储成功
        
        FileService->>PostgreSQL: INSERT INTO files<br/>(id, name, size, hash, path, ...)
        PostgreSQL-->>FileService: 保存成功
        
        FileService->>FileService: 生成访问 URL<br/>(CDN 或直接访问)
        FileService-->>FileClient: 返回文件 URL
    end
    
    FileClient->>FileClient: 清理临时文件
    FileClient-->>UserService: 返回文件 URL
    
    UserService->>UserService: 提取旧头像 URL
    
    alt 存在旧头像
        UserService->>FileClient: extractFileId(oldUrl)
        FileClient-->>UserService: 返回旧文件 ID
        UserService->>FileClient: deleteFile(oldFileId)
        FileClient->>FileService: DELETE /api/files/{oldFileId}
        FileService->>MinIO: 删除旧文件
        FileService->>PostgreSQL: 删除旧文件元数据
    end
    
    UserService->>UserDB: UPDATE users<br/>SET avatar_url = ?<br/>WHERE id = ?
    UserDB-->>UserService: 更新成功
    
    UserService-->>Gateway: 返回成功响应<br/>+ 新头像 URL
    Gateway-->>Browser: 返回响应
    Browser-->>User: 显示新头像
```

#### 1.2 文章封面图上传

```mermaid
sequenceDiagram
    autonumber
    participant Author as 作者
    participant Browser as 浏览器
    participant Gateway as API 网关
    participant PostService as 文章服务
    participant FileClient as File Service Client
    participant FileService as File Service
    participant MinIO as MinIO
    participant PostgreSQL as PostgreSQL
    participant PostDB as 文章数据库
    
    Author->>Browser: 上传封面图
    Browser->>Gateway: POST /api/post/image/cover<br/>+ JWT + File
    Gateway->>PostService: 转发请求
    
    PostService->>FileClient: uploadImage(file, true)
    FileClient->>FileService: POST /api/files/upload<br/>+ JWT + File
    
    FileService->>FileService: 计算文件哈希
    FileService->>PostgreSQL: 检查秒传
    
    alt 秒传
        PostgreSQL-->>FileService: 返回已有文件
        FileService-->>FileClient: 返回 URL
    else 正常上传
        FileService->>MinIO: 上传文件
        FileService->>PostgreSQL: 保存元数据
        FileService-->>FileClient: 返回 URL
    end
    
    FileClient-->>PostService: 返回封面图 URL
    
    PostService->>PostDB: UPDATE posts<br/>SET cover_image = ?<br/>WHERE id = ?
    
    PostService-->>Gateway: 返回成功
    Gateway-->>Browser: 返回封面图 URL
    Browser-->>Author: 显示封面图预览
```

#### 1.3 文章内容图片上传

```mermaid
sequenceDiagram
    autonumber
    participant Author as 作者
    participant Editor as 编辑器
    participant Gateway as API 网关
    participant PostService as 文章服务
    participant FileClient as File Service Client
    participant FileService as File Service
    participant MinIO as MinIO
    
    Author->>Editor: 在编辑器中插入图片
    Editor->>Gateway: POST /api/post/image/content<br/>+ JWT + File
    Gateway->>PostService: 转发请求
    
    PostService->>FileClient: uploadImage(file, true)
    FileClient->>FileService: POST /api/files/upload
    FileService->>MinIO: 上传文件
    FileService-->>FileClient: 返回 URL
    FileClient-->>PostService: 返回 URL
    
    PostService->>PostService: 生成 Markdown 格式<br/>![filename](url)
    PostService-->>Gateway: 返回 Markdown 链接
    Gateway-->>Editor: 返回 Markdown 链接
    Editor-->>Author: 在编辑器中插入图片
```

### 2. 文件访问流程

#### 2.1 通过 CDN 访问公共文件

```mermaid
sequenceDiagram
    autonumber
    participant User as 用户
    participant Browser as 浏览器
    participant CDN as CDN 节点
    participant FileService as File Service
    participant MinIO as MinIO
    
    User->>Browser: 访问页面
    Browser->>Browser: 解析页面<br/>发现图片 URL
    
    Browser->>CDN: GET https://cdn.example.com/files/{fileId}
    
    alt CDN 缓存命中
        CDN->>CDN: 从缓存获取文件
        CDN-->>Browser: 返回文件内容<br/>(200 OK)
    else CDN 缓存未命中
        CDN->>FileService: GET /api/files/{fileId}<br/>(回源请求)
        FileService->>FileService: 验证文件访问权限<br/>(PUBLIC 文件)
        FileService->>MinIO: 获取文件
        MinIO-->>FileService: 返回文件内容
        FileService-->>CDN: 返回文件内容
        CDN->>CDN: 缓存文件<br/>(根据缓存策略)
        CDN-->>Browser: 返回文件内容
    end
    
    Browser-->>User: 显示图片
```

#### 2.2 直接访问私有文件

```mermaid
sequenceDiagram
    autonumber
    participant User as 用户
    participant Browser as 浏览器
    participant Gateway as API 网关
    participant FileService as File Service
    participant PostgreSQL as PostgreSQL
    participant MinIO as MinIO
    
    User->>Browser: 点击私有文件链接
    Browser->>Gateway: GET /api/files/{fileId}<br/>+ JWT Token
    Gateway->>Gateway: 验证 JWT Token
    Gateway->>FileService: 转发请求
    
    FileService->>FileService: 验证 JWT Token
    FileService->>FileService: 提取用户信息
    
    FileService->>PostgreSQL: SELECT * FROM files<br/>WHERE id = ?
    PostgreSQL-->>FileService: 返回文件元数据
    
    FileService->>FileService: 检查访问权限<br/>(PRIVATE 文件)
    
    alt 有权限访问
        FileService->>MinIO: 获取文件
        MinIO-->>FileService: 返回文件内容
        FileService-->>Gateway: 返回文件内容
        Gateway-->>Browser: 返回文件内容
        Browser-->>User: 显示/下载文件
    else 无权限访问
        FileService-->>Gateway: 返回 403 Forbidden
        Gateway-->>Browser: 返回错误
        Browser-->>User: 显示"无权访问"
    end
```

### 3. 文件删除流程

#### 3.1 删除用户头像

```mermaid
sequenceDiagram
    autonumber
    participant User as 用户
    participant Browser as 浏览器
    participant Gateway as API 网关
    participant UserService as 用户服务
    participant FileClient as File Service Client
    participant FileService as File Service
    participant MinIO as MinIO
    participant PostgreSQL as PostgreSQL
    participant UserDB as 用户数据库
    
    User->>Browser: 点击删除头像
    Browser->>Gateway: DELETE /api/user/avatar<br/>+ JWT Token
    Gateway->>UserService: 转发请求
    
    UserService->>UserDB: SELECT avatar_url<br/>FROM users<br/>WHERE id = ?
    UserDB-->>UserService: 返回头像 URL
    
    UserService->>FileClient: extractFileId(avatarUrl)
    FileClient-->>UserService: 返回文件 ID
    
    UserService->>FileClient: deleteFile(fileId)
    FileClient->>FileService: DELETE /api/files/{fileId}<br/>+ JWT Token
    
    FileService->>FileService: 验证 JWT Token
    FileService->>FileService: 验证删除权限
    
    FileService->>PostgreSQL: SELECT * FROM files<br/>WHERE id = ?
    PostgreSQL-->>FileService: 返回文件元数据
    
    FileService->>MinIO: DELETE /{bucket}/{path}
    MinIO-->>FileService: 删除成功
    
    FileService->>PostgreSQL: DELETE FROM files<br/>WHERE id = ?
    PostgreSQL-->>FileService: 删除成功
    
    FileService->>FileService: 记录删除日志
    FileService-->>FileClient: 返回成功
    FileClient-->>UserService: 返回成功
    
    UserService->>UserDB: UPDATE users<br/>SET avatar_url = NULL<br/>WHERE id = ?
    UserDB-->>UserService: 更新成功
    
    UserService-->>Gateway: 返回成功
    Gateway-->>Browser: 返回成功
    Browser-->>User: 显示默认头像
```

#### 3.2 删除文章及关联图片

```mermaid
sequenceDiagram
    autonumber
    participant Author as 作者
    participant Browser as 浏览器
    participant Gateway as API 网关
    participant PostService as 文章服务
    participant FileClient as File Service Client
    participant FileService as File Service
    participant MinIO as MinIO
    participant PostgreSQL as PostgreSQL
    participant PostDB as 文章数据库
    
    Author->>Browser: 点击删除文章
    Browser->>Gateway: DELETE /api/post/{postId}<br/>+ JWT Token
    Gateway->>PostService: 转发请求
    
    PostService->>PostDB: SELECT cover_image, content<br/>FROM posts<br/>WHERE id = ?
    PostDB-->>PostService: 返回文章数据
    
    PostService->>PostService: 提取封面图 URL
    PostService->>PostService: 解析内容中的图片 URL
    
    PostService->>PostService: 收集全部 fileId
    PostService->>FileClient: deleteFiles(fileIds)
    FileClient->>FileService: POST /api/files/batch-delete
    FileService->>FileService: 批量校验权限与去重
    FileService->>PostgreSQL: 原子递减 ref_count
    FileService->>MinIO: 仅删除 ref_count = 0 的物理文件
    FileService->>PostgreSQL: 标记删除完成
    FileService-->>FileClient: 返回批量删除结果
    FileClient-->>PostService: 返回批量删除结果
    
    PostService->>PostDB: DELETE FROM posts<br/>WHERE id = ?
    PostDB-->>PostService: 删除成功
    
    PostService-->>Gateway: 返回成功
    Gateway-->>Browser: 返回成功
    Browser-->>Author: 显示删除成功
```

### 4. 大文件分片上传流程

```mermaid
sequenceDiagram
    autonumber
    participant User as 用户
    participant Browser as 浏览器
    participant Gateway as API 网关
    participant PostService as 文章服务
    participant FileClient as File Service Client
    participant FileService as File Service
    participant MinIO as MinIO
    participant PostgreSQL as PostgreSQL
    
    User->>Browser: 选择大文件 (>10MB)
    Browser->>Gateway: POST /api/post/file/upload<br/>+ JWT + File
    Gateway->>PostService: 转发请求
    
    PostService->>FileClient: uploadLargeFile(file)
    FileClient->>FileClient: 检测文件大小 > 10MB
    FileClient->>FileClient: 计算文件哈希
    
    FileClient->>FileService: POST /api/files/multipart/init<br/>+ JWT + FileInfo
    FileService->>FileService: 生成上传 ID
    FileService->>PostgreSQL: 保存上传任务信息
    FileService-->>FileClient: 返回 uploadId
    
    FileClient->>FileClient: 将文件分片<br/>(每片 5MB)
    
    loop 每个分片
        FileClient->>FileService: POST /api/files/multipart/upload<br/>+ uploadId + partNumber + data
        FileService->>MinIO: 上传分片
        MinIO-->>FileService: 返回 ETag
        FileService->>PostgreSQL: 记录分片信息
        FileService-->>FileClient: 返回成功
        FileClient->>Browser: 更新上传进度
        Browser-->>User: 显示进度条
    end
    
    FileClient->>FileService: POST /api/files/multipart/complete<br/>+ uploadId + parts
    FileService->>MinIO: 合并分片
    MinIO-->>FileService: 返回文件信息
    FileService->>PostgreSQL: 保存文件元数据
    FileService->>PostgreSQL: 删除上传任务信息
    FileService-->>FileClient: 返回文件 URL
    
    FileClient-->>PostService: 返回文件 URL
    PostService-->>Gateway: 返回成功
    Gateway-->>Browser: 返回文件 URL
    Browser-->>User: 显示上传成功
```

### 5. 秒传流程

```mermaid
sequenceDiagram
    autonumber
    participant User as 用户
    participant Browser as 浏览器
    participant Gateway as API 网关
    participant PostService as 文章服务
    participant FileClient as File Service Client
    participant FileService as File Service
    participant PostgreSQL as PostgreSQL
    
    User->>Browser: 上传文件
    Browser->>Gateway: POST /api/post/image/upload<br/>+ JWT + File
    Gateway->>PostService: 转发请求
    
    PostService->>FileClient: uploadImage(file)
    FileClient->>FileClient: 计算文件哈希值<br/>(SHA-256)
    
    FileClient->>FileService: POST /api/files/upload<br/>+ JWT + File + Hash
    
    FileService->>FileService: 验证 JWT Token
    FileService->>FileService: 提取租户 ID
    
    FileService->>PostgreSQL: SELECT * FROM files<br/>WHERE hash = ?<br/>AND file_size = ?<br/>AND tenant_id = ?
    
    alt 文件已存在（秒传）
        PostgreSQL-->>FileService: 返回已有文件信息
        
        FileService->>FileService: 记录秒传日志<br/>log.info("秒传成功")
        FileService->>FileService: 增加引用计数
        FileService->>PostgreSQL: UPDATE files<br/>SET ref_count = ref_count + 1,<br/>updated_at = NOW()<br/>WHERE id = ? AND deleted_at IS NULL
        
        FileService-->>FileClient: 返回文件 URL<br/>(instant_upload: true)
        FileClient-->>PostService: 返回 URL<br/>(秒传成功)
        
        PostService->>PostService: 记录秒传统计
        PostService-->>Gateway: 返回成功<br/>(instant_upload: true)
        Gateway-->>Browser: 返回成功
        Browser-->>User: 显示"上传成功（秒传）"
    else 文件不存在（正常上传）
        PostgreSQL-->>FileService: 文件不存在
        Note over FileService: 执行正常上传流程
    end
```

## 数据存储

### 1. 文件元数据（PostgreSQL）

```sql
-- files 表结构
CREATE TABLE files (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    hash VARCHAR(64) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    access_level VARCHAR(20) NOT NULL,
    ref_count INTEGER DEFAULT 1,
    created_by VARCHAR(36),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    
    UNIQUE KEY uk_tenant_hash_size (tenant_id, hash, file_size),
    INDEX idx_hash (hash),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_created_by (created_by),
    INDEX idx_created_at (created_at)
);
```

### 2. 文件内容（MinIO）

```
MinIO 存储结构:
ZhiCore-files/
├── ZhiCore/                    # 租户 ID
│   ├── 2024/               # 年份
│   │   ├── 01/            # 月份
│   │   │   ├── 23/       # 日期
│   │   │   │   ├── {uuid1}.jpg
│   │   │   │   ├── {uuid2}.png
│   │   │   │   └── {uuid3}.pdf
│   │   │   └── 24/
│   │   └── 02/
│   └── 2025/
└── other-tenant/
```

### 3. 业务数据（业务数据库）

```sql
-- users 表
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    avatar_url VARCHAR(500),  -- 存储文件 URL
    ...
);

-- posts 表
CREATE TABLE posts (
    id BIGINT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,      -- 包含图片 URL
    cover_image VARCHAR(500),   -- 存储封面图 URL
    ...
);
```

## 数据一致性

### 1. 文件上传失败回滚

```mermaid
sequenceDiagram
    participant FileClient as File Service Client
    participant FileService as File Service
    participant MinIO as MinIO
    participant PostgreSQL as PostgreSQL
    
    FileClient->>FileService: 上传文件
    FileService->>MinIO: 上传文件
    MinIO-->>FileService: 上传成功
    
    FileService->>PostgreSQL: 保存元数据
    PostgreSQL-->>FileService: 保存失败
    
    FileService->>MinIO: 删除已上传的文件<br/>(回滚)
    MinIO-->>FileService: 删除成功
    
    FileService-->>FileClient: 返回错误
```

### 2. 业务操作失败清理

```mermaid
sequenceDiagram
    participant UserService as 用户服务
    participant FileClient as File Service Client
    participant FileService as File Service
    participant UserDB as 用户数据库
    
    UserService->>FileClient: 上传新头像
    FileClient->>FileService: 上传文件
    FileService-->>FileClient: 返回新 URL
    FileClient-->>UserService: 返回新 URL
    
    UserService->>UserDB: 更新用户头像 URL
    UserDB-->>UserService: 更新失败
    
    UserService->>FileClient: 删除新上传的文件<br/>(清理)
    FileClient->>FileService: 删除文件
    FileService-->>FileClient: 删除成功
    
    UserService-->>UserService: 返回错误
```

### 3. 孤儿文件清理

```mermaid
graph TB
    A[定时任务启动] --> B[查询文件元数据]
    B --> C{文件是否被引用?}
    C -->|是| D[跳过]
    C -->|否| E{创建时间 > 7天?}
    E -->|是| F[标记为孤儿文件]
    E -->|否| D
    F --> G[删除文件]
    G --> H[删除元数据]
    H --> I[记录清理日志]
    
    style F fill:#ffe6e6
    style G fill:#ffe6e6
```

## 性能优化

### 1. 秒传优化

- **哈希计算**: 使用 SHA-256 计算文件哈希
- **联合判重**: 使用 `tenant_id + hash + file_size` 做秒传命中判断
- **数据库索引**: 在 `(tenant_id, hash, file_size)` 上建立唯一索引
- **租户隔离**: 只在同一租户内检查秒传

### 2. CDN 缓存

- **缓存策略**: 根据文件类型设置不同的缓存时间
- **回源优化**: 使用 CDN 回源减少源站压力
- **缓存预热**: 热门文件提前加载到 CDN

### 3. 分片上传

- **分片大小**: 每片 5MB
- **并发上传**: 支持多个分片并发上传
- **断点续传**: 支持上传失败后继续上传

## 监控指标

### 1. 上传指标

- 上传成功率
- 上传响应时间（P50, P95, P99）
- 秒传命中率
- 分片上传成功率

### 2. 访问指标

- 文件访问次数
- CDN 命中率
- 访问响应时间
- 错误率

### 3. 存储指标

- 存储空间使用率
- 文件数量
- 孤儿文件数量
- 引用计数分布

## 总结

本文档详细描述了文件在 ZhiCore-microservice 系统中的完整数据流，包括：

1. **文件上传流程**: 用户头像、文章封面图、文章内容图片的上传
2. **文件访问流程**: 通过 CDN 访问公共文件、直接访问私有文件
3. **文件删除流程**: 删除用户头像、删除文章及关联图片
4. **大文件处理**: 分片上传流程
5. **性能优化**: 秒传流程
6. **数据存储**: 文件元数据、文件内容、业务数据的存储结构
7. **数据一致性**: 失败回滚、清理机制、孤儿文件处理
8. **性能优化**: 秒传、CDN 缓存、分片上传
9. **监控指标**: 上传、访问、存储相关指标

通过清晰的数据流设计，确保文件在系统中的安全、高效流转。
