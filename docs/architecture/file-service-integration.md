# File Service 集成架构文档

## 概述

本文档描述了 File Service 在 ZhiCore-microservice 系统中的集成架构，包括服务定位、职责划分、交互关系和数据流。

## 服务定位

### File Service 的角色

File Service 是一个独立的文件管理微服务，负责统一处理博客系统中的所有文件操作。它在系统架构中扮演以下角色：

1. **文件存储抽象层**: 为上层业务服务提供统一的文件存储接口，屏蔽底层存储实现细节
2. **文件生命周期管理**: 管理文件的上传、下载、删除、访问控制等完整生命周期
3. **性能优化中心**: 提供秒传、分片上传、CDN 加速等性能优化功能
4. **安全控制点**: 统一处理文件访问权限、租户隔离、配额管理等安全需求

### 为什么需要独立的 File Service

1. **服务解耦**: 将文件管理逻辑从业务服务中分离，降低耦合度
2. **复用性**: 多个业务服务（user、post、comment）可以共享同一个文件服务
3. **可扩展性**: 文件服务可以独立扩展，不影响业务服务
4. **技术选型灵活**: 可以独立选择最适合文件存储的技术栈
5. **统一管理**: 集中管理文件元数据、访问控制、配额限制等

## 系统架构

### 整体架构图

```mermaid
graph TB
    subgraph "客户端层"
        Client[Web/Mobile 客户端]
    end
    
    subgraph "网关层"
        Gateway[ZhiCore-gateway<br/>API 网关]
    end
    
    subgraph "业务服务层"
        UserService[ZhiCore-user<br/>用户服务]
        PostService[ZhiCore-post<br/>文章服务]
        CommentService[ZhiCore-comment<br/>评论服务]
    end
    
    subgraph "文件服务层"
        FileService[file-service<br/>文件服务]
        FileClient[file-service-client<br/>客户端库]
    end
    
    subgraph "存储层"
        MinIO[MinIO<br/>对象存储]
        PostgreSQL[(PostgreSQL<br/>文件元数据)]
    end
    
    subgraph "基础设施层"
        Nacos[Nacos<br/>配置中心]
        Redis[Redis<br/>缓存]
        CDN[CDN<br/>内容分发]
    end
    
    Client --> Gateway
    Gateway --> UserService
    Gateway --> PostService
    Gateway --> CommentService
    
    UserService --> FileClient
    PostService --> FileClient
    CommentService --> FileClient
    
    FileClient -.HTTP.-> FileService
    
    FileService --> MinIO
    FileService --> PostgreSQL
    FileService --> Redis
    FileService --> CDN
    
    UserService --> Nacos
    PostService --> Nacos
    CommentService --> Nacos
    FileService --> Nacos
    
    style FileService fill:#e1f5ff,stroke:#0066cc,stroke-width:2px
    style FileClient fill:#e1f5ff,stroke:#0066cc,stroke-width:2px
    style MinIO fill:#fff4e1,stroke:#ff9900,stroke-width:2px
    style CDN fill:#f0f0f0,stroke:#666666,stroke-width:2px
```

### 服务职责划分

#### 业务服务（ZhiCore-user, ZhiCore-post, ZhiCore-comment）

**职责**:
- 处理业务逻辑
- 调用 File Service Client 上传/删除文件
- 管理文件 URL 与业务实体的关联关系
- 在业务实体删除时清理关联文件

**不负责**:
- 文件的实际存储
- 文件访问权限控制
- 文件元数据管理
- CDN 配置和管理

#### File Service

**职责**:
- 文件上传、下载、删除
- 文件元数据管理（文件名、大小、类型、哈希值等）
- 访问权限控制（PUBLIC/PRIVATE）
- 租户隔离
- 配额管理
- 秒传检测
- 分片上传协调
- CDN URL 生成

**不负责**:
- 业务逻辑处理
- 用户认证（由业务服务通过 JWT 传递）
- 业务实体与文件的关联关系

#### MinIO

**职责**:
- 文件的物理存储
- 对象存储 API
- 数据持久化

**不负责**:
- 文件元数据管理
- 访问权限控制
- 业务逻辑

## 服务交互

### 文件上传流程

```mermaid
sequenceDiagram
    participant Client as 客户端
    participant Gateway as API 网关
    participant UserService as 用户服务
    participant FileClient as File Client
    participant FileService as File Service
    participant MinIO as MinIO
    participant PostgreSQL as PostgreSQL
    participant CDN as CDN
    
    Client->>Gateway: POST /api/user/avatar/upload
    Gateway->>UserService: 转发请求 + JWT
    
    UserService->>UserService: 验证用户身份
    UserService->>FileClient: uploadImage(file)
    
    FileClient->>FileClient: 从 Security Context<br/>获取 JWT Token
    FileClient->>FileService: POST /api/files/upload<br/>+ JWT + file
    
    FileService->>FileService: 验证 JWT Token
    FileService->>FileService: 计算文件哈希值
    FileService->>PostgreSQL: 检查文件是否存在<br/>(秒传检测)
    
    alt 文件已存在（秒传）
        PostgreSQL-->>FileService: 返回已有文件信息
        FileService-->>FileClient: 返回文件 URL
    else 文件不存在（正常上传）
        FileService->>MinIO: 上传文件到对象存储
        MinIO-->>FileService: 返回存储路径
        FileService->>PostgreSQL: 保存文件元数据
        FileService->>FileService: 生成 CDN URL
        FileService-->>FileClient: 返回文件 URL
    end
    
    FileClient-->>UserService: 返回文件 URL
    UserService->>UserService: 更新用户头像 URL
    UserService-->>Gateway: 返回成功响应
    Gateway-->>Client: 返回文件 URL
    
    Note over Client,CDN: 后续访问文件
    Client->>CDN: GET 文件 URL
    CDN->>MinIO: 回源获取文件
    MinIO-->>CDN: 返回文件内容
    CDN-->>Client: 返回文件内容
```

### 文件删除流程

```mermaid
sequenceDiagram
    participant PostService as 文章服务
    participant FileClient as File Client
    participant FileService as File Service
    participant MinIO as MinIO
    participant PostgreSQL as PostgreSQL
    
    PostService->>PostService: 删除文章
    PostService->>PostService: 提取文章中的<br/>所有文件 ID
    
    loop 每个文件 ID
        PostService->>FileClient: deleteFile(fileId)
        FileClient->>FileService: DELETE /api/files/{fileId}<br/>+ JWT
        FileService->>FileService: 验证权限
        FileService->>PostgreSQL: 查询文件元数据
        FileService->>MinIO: 删除文件
        FileService->>PostgreSQL: 删除文件元数据
        FileService-->>FileClient: 返回成功
        FileClient-->>PostService: 返回成功
    end
    
    PostService->>PostService: 删除文章记录
```

### 认证集成流程

```mermaid
sequenceDiagram
    participant User as 用户
    participant Gateway as API 网关
    participant UserService as 用户服务
    participant SecurityContext as Spring Security<br/>Context
    participant FileClient as File Client
    participant FileService as File Service
    
    User->>Gateway: 请求 + JWT Token
    Gateway->>Gateway: 验证 JWT
    Gateway->>UserService: 转发请求 + JWT
    UserService->>SecurityContext: 存储认证信息
    
    UserService->>FileClient: uploadImage(file)
    FileClient->>SecurityContext: 获取 JWT Token
    SecurityContext-->>FileClient: 返回 JWT Token
    
    FileClient->>FileService: POST /api/files/upload<br/>Authorization: Bearer {JWT}
    FileService->>FileService: 验证 JWT Token
    FileService->>FileService: 提取用户信息
    FileService->>FileService: 执行文件上传
    FileService-->>FileClient: 返回结果
```

## 数据流

### 文件上传数据流

```mermaid
graph LR
    A[客户端] -->|1. 上传文件| B[API 网关]
    B -->|2. 转发请求| C[业务服务]
    C -->|3. 调用 Client| D[File Service Client]
    D -->|4. HTTP 请求| E[File Service]
    E -->|5. 存储文件| F[MinIO]
    E -->|6. 保存元数据| G[PostgreSQL]
    E -->|7. 返回 URL| D
    D -->|8. 返回 URL| C
    C -->|9. 更新业务数据| H[(业务数据库)]
    C -->|10. 返回结果| B
    B -->|11. 返回结果| A
    
    style E fill:#e1f5ff
    style F fill:#fff4e1
```

### 文件访问数据流

```mermaid
graph LR
    A[客户端] -->|1. 请求文件| B[CDN]
    B -->|2. 缓存未命中| C[File Service]
    C -->|3. 获取文件| D[MinIO]
    D -->|4. 返回文件| C
    C -->|5. 返回文件| B
    B -->|6. 缓存文件| B
    B -->|7. 返回文件| A
    
    A -.->|后续请求| B
    B -.->|缓存命中| A
    
    style B fill:#f0f0f0
    style D fill:#fff4e1
```

## 服务边界

### File Service 的服务边界

**内部职责**（File Service 负责）:
- 文件的物理存储和检索
- 文件元数据的 CRUD 操作
- 文件哈希计算和秒传检测
- 分片上传的协调和管理
- 访问级别控制（PUBLIC/PRIVATE）
- 租户隔离
- 配额管理和限流
- CDN URL 生成
- 文件访问日志记录

**外部职责**（业务服务负责）:
- 业务实体与文件的关联关系
- 业务级别的权限控制（如：只有作者可以删除文章图片）
- 文件的业务语义（如：这是头像还是封面图）
- 业务实体删除时触发文件清理
- 文件 URL 的持久化存储

### 数据所有权

| 数据类型 | 所有者 | 存储位置 | 说明 |
|---------|--------|---------|------|
| 文件内容 | File Service | MinIO | 二进制文件数据 |
| 文件元数据 | File Service | File Service DB | 文件名、大小、哈希、创建时间等 |
| 文件 URL | 业务服务 | 业务服务 DB | 用户头像 URL、文章封面 URL 等 |
| 业务关联 | 业务服务 | 业务服务 DB | 哪个用户的头像、哪篇文章的封面 |

## 配置管理

### 配置层次

```mermaid
graph TB
    A[应用配置] --> B[Nacos 配置中心]
    A --> C[本地配置文件]
    A --> D[环境变量]
    
    B --> E[file-service-config.yml<br/>共享配置]
    C --> F[application.yml<br/>默认配置]
    D --> G[Docker Compose<br/>.env 文件]
    
    style B fill:#e1f5ff
    style E fill:#e1f5ff
```

### 配置优先级

1. **环境变量** (最高优先级)
   - Docker 部署时使用
   - 适合敏感信息（密码、密钥）

2. **Nacos 配置中心**
   - 生产环境配置
   - 支持动态刷新
   - 多环境配置管理

3. **本地配置文件** (最低优先级)
   - 开发环境默认配置
   - 配置模板和示例

## 扩展性设计

### 水平扩展

```mermaid
graph TB
    subgraph "负载均衡"
        LB[Load Balancer]
    end
    
    subgraph "File Service 集群"
        FS1[file-service-1]
        FS2[file-service-2]
        FS3[file-service-3]
    end
    
    subgraph "存储层"
        MinIO[MinIO 集群]
        DB[(PostgreSQL<br/>主从复制)]
    end
    
    LB --> FS1
    LB --> FS2
    LB --> FS3
    
    FS1 --> MinIO
    FS2 --> MinIO
    FS3 --> MinIO
    
    FS1 --> DB
    FS2 --> DB
    FS3 --> DB
    
    style FS1 fill:#e1f5ff
    style FS2 fill:#e1f5ff
    style FS3 fill:#e1f5ff
```

### 存储扩展

- **MinIO 集群**: 支持分布式部署，提供高可用和高性能
- **CDN 加速**: 公共文件通过 CDN 分发，减轻源站压力
- **缓存策略**: 文件元数据缓存在 Redis，减少数据库查询

## 安全设计

### 认证和授权

```mermaid
graph TB
    A[客户端请求] --> B{JWT Token 验证}
    B -->|有效| C{租户验证}
    B -->|无效| D[返回 401]
    C -->|匹配| E{权限检查}
    C -->|不匹配| F[返回 403]
    E -->|有权限| G[执行操作]
    E -->|无权限| F
    
    style B fill:#ffe6e6
    style C fill:#ffe6e6
    style E fill:#ffe6e6
```

### 安全措施

1. **认证**: 所有请求必须携带有效的 JWT Token
2. **租户隔离**: 通过 tenant-id 隔离不同租户的数据
3. **访问控制**: PUBLIC 文件公开访问，PRIVATE 文件需要权限验证
4. **配额限制**: 限制单个租户的存储空间和上传频率
5. **文件类型验证**: 只允许上传指定类型的文件
6. **文件大小限制**: 限制单个文件的最大大小

## 监控和运维

### 监控指标

| 指标类别 | 具体指标 | 说明 |
|---------|---------|------|
| 可用性 | 服务健康状态 | File Service 是否正常运行 |
| 性能 | 上传响应时间 | P50, P95, P99 |
| 性能 | 下载响应时间 | P50, P95, P99 |
| 业务 | 上传成功率 | 成功上传数 / 总上传数 |
| 业务 | 秒传命中率 | 秒传次数 / 总上传次数 |
| 资源 | MinIO 存储使用率 | 已用空间 / 总空间 |
| 资源 | CDN 命中率 | CDN 命中次数 / 总请求次数 |
| 错误 | 错误率 | 错误请求数 / 总请求数 |

### 日志记录

```java
// 上传成功日志
log.info("文件上传成功: fileId={}, fileName={}, size={}, userId={}, tenantId={}", 
    fileId, fileName, fileSize, userId, tenantId);

// 上传失败日志
log.error("文件上传失败: fileName={}, userId={}, error={}", 
    fileName, userId, errorMessage, exception);

// 秒传日志
log.info("秒传成功: fileHash={}, userId={}, savedTime={}ms", 
    fileHash, userId, savedTime);

// 删除日志
log.info("文件删除: fileId={}, userId={}, tenantId={}", 
    fileId, userId, tenantId);
```

## 故障处理

### 降级策略

```mermaid
graph TB
    A[文件上传请求] --> B{File Service 可用?}
    B -->|是| C[正常上传]
    B -->|否| D{启用降级?}
    D -->|是| E[返回默认图片 URL]
    D -->|否| F[返回错误]
    
    C --> G[返回实际 URL]
    
    style D fill:#ffe6e6
    style E fill:#fff4e1
```

### 熔断机制

使用 Resilience4j 实现熔断：

```yaml
resilience4j:
  circuitbreaker:
    instances:
      fileService:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        sliding-window-size: 10
```

## 迁移策略

### 从旧存储迁移到 File Service

```mermaid
graph TB
    A[开始迁移] --> B[备份数据库]
    B --> C[启动 File Service]
    C --> D[执行迁移脚本]
    D --> E{迁移成功?}
    E -->|是| F[更新配置]
    E -->|否| G[记录失败项]
    F --> H[验证功能]
    G --> I[重试失败项]
    H --> J[完成迁移]
    I --> E
    
    style E fill:#ffe6e6
```

## 最佳实践

### 业务服务集成

1. **使用 Starter**: 通过 `file-service-spring-boot-starter` 简化集成
2. **统一封装**: 在 `ZhiCore-common` 中创建 `FileUploadService` 统一接口
3. **异常处理**: 捕获并转换 File Service 异常为业务异常
4. **资源清理**: 业务实体删除时记得清理关联文件
5. **URL 存储**: 只存储文件 URL，不存储文件 ID

### 文件管理

1. **访问级别**: 用户头像和文章图片使用 PUBLIC，私密文件使用 PRIVATE
2. **文件命名**: 使用有意义的文件名，便于调试和审计
3. **大小限制**: 根据业务需求设置合理的文件大小限制
4. **类型限制**: 只允许上传必要的文件类型
5. **清理策略**: 定期清理未关联的孤儿文件

### 性能优化

1. **秒传**: 利用秒传功能减少重复上传
2. **分片上传**: 大文件使用分片上传提高成功率
3. **CDN 加速**: 公共文件通过 CDN 分发
4. **缓存**: 文件元数据缓存在 Redis
5. **异步处理**: 文件删除等非关键操作可以异步执行

## 总结

File Service 作为独立的文件管理微服务，为 ZhiCore-microservice 系统提供了统一、高效、安全的文件管理能力。通过清晰的服务边界划分、完善的认证授权机制、灵活的扩展性设计，File Service 能够很好地支撑博客系统的文件管理需求，并为未来的功能扩展提供了坚实的基础。
