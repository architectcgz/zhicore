# ZhiCore-Post 服务架构深度分析

**文档版本**: 2.0  
**创建日期**: 2026-02-19  
**分析深度**: 深度分析（代码级别）

---

## 执行摘要

本文档对 ZhiCore-post 服务的架构进行深度分析,特别关注 DDD 分层设计和装饰器模式的应用。

**核心发现**:
- ✅ 装饰器模式应用正确,缓存层设计完善
- ⚠️ 领域服务实现放在 Infrastructure 层存在争议
- ⚠️ 领域接口依赖基础设施类型(MongoDB Document)
- ✅ 整体架构在实践中可接受,但不是"纯粹"的 DDD

**建议**: 保持现状并文档化架构决策,重构成本高于收益。

---

## 目录

1. [完整包结构](#完整包结构)
2. [DDD 分层详解](#ddd-分层详解)
3. [装饰器模式深度分析](#装饰器模式深度分析)
4. [完整调用链路](#完整调用链路)
5. [架构问题深度剖析](#架构问题深度剖析)
6. [代码级别分析](#代码级别分析)
7. [性能与可靠性](#性能与可靠性)
8. [改进方案详细对比](#改进方案详细对比)

---

## 完整包结构

### 实际目录树

```
com.zhicore.post/
├── PostApplication.java                    # 启动类
│
├── application/                            # 应用层
│   ├── assembler/                         # DTO 转换器
│   │   └── PostAssembler.java
│   ├── dto/                               # 应用层 DTO
│   │   ├── PostVO.java
│   │   └── PostBriefVO.java
│   └── service/                           # 应用服务
│       └── PostApplicationService.java    # 编排领域服务
│
├── domain/                                 # 领域层 ⭐
│   ├── constant/                          # 领域常量
│   │   └── PostConstants.java
│   ├── event/                             # 领域事件
│   │   ├── PostCreatedEvent.java
│   │   ├── PostPublishedEvent.java
│   │   └── PostTagsUpdatedEvent.java
│   ├── exception/                         # 领域异常
│   │   ├── DomainException.java
│   │   └── DualStorageException.java
│   ├── model/                             # 领域模型
│   │   ├── Post.java                      # 聚合根（充血模型）
│   │   ├── PostStatus.java                # 值对象
│   │   ├── PostStats.java                 # 值对象
│   │   └── Tag.java                       # 实体
│   ├── repository/                        # 仓储接口 ✅
│   │   ├── PostRepository.java
│   │   ├── TagRepository.java
│   │   └── PostTagRepository.java
│   └── service/                           # 领域服务接口 ⚠️
│       ├── DualStorageManager.java        # 双存储管理器接口
│       ├── DraftManager.java              # 草稿管理器接口
│       ├── VersionManager.java            # 版本管理器接口
│       ├── TagDomainService.java          # 标签领域服务接口
│       └── ...
│
└── infrastructure/                         # 基础设施层
    ├── adapter/                           # 适配器
    │   └── ...
    ├── cache/                             # 缓存相关
    │   └── PostRedisKeys.java             # Redis Key 生成器
    ├── config/                            # 配置类
    │   ├── RedisConfig.java
    │   └── MongoDBConfig.java
    ├── feign/                             # Feign 客户端
    │   ├── ZhiCoreUploadClient.java
    │   └── UserServiceClient.java
    ├── mongodb/                           # MongoDB 相关
    │   ├── document/                      # MongoDB 文档
    │   │   ├── PostContent.java           # 文章内容文档 ⚠️
    │   │   └── PostDraft.java             # 草稿文档
    │   └── repository/                    # MongoDB Repository
    │       ├── PostContentRepository.java
    │       └── PostDraftRepository.java
    ├── mq/                                # 消息队列
    │   └── PostEventPublisher.java
    ├── repository/                        # 仓储实现 ✅
    │   ├── PostRepositoryImpl.java
    │   ├── mapper/                        # MyBatis Mapper
    │   │   ├── PostMapper.java
    │   │   └── PostStatsMapper.java
    │   └── po/                            # 持久化对象
    │       ├── PostPO.java
    │       └── PostStatsPO.java
    ├── service/                           # 领域服务实现 ⚠️
    │   ├── DualStorageManagerImpl.java    # 基础实现
    │   ├── CachedDualStorageManager.java  # 缓存装饰器
    │   ├── DraftManagerImpl.java          # 基础实现
    │   ├── CachedDraftManager.java        # 缓存装饰器
    │   └── ...
    └── util/                              # 工具类
        └── ContentEnricher.java           # 内容增强器
```

### 关键观察

1. **领域服务接口在 Domain 层** ✅
   - `domain.service.DualStorageManager`
   - `domain.service.DraftManager`

2. **领域服务实现在 Infrastructure 层** ⚠️
   - `infrastructure.service.DualStorageManagerImpl`
   - `infrastructure.service.CachedDualStorageManager`

3. **MongoDB Document 在 Infrastructure 层** ✅
   - `infrastructure.mongodb.document.PostContent`

4. **领域接口依赖 Infrastructure 类型** ❌
   - `DualStorageManager.createPost(Post post, PostContent content)`
   - `PostContent` 是 MongoDB Document,不是领域对象

---

## DDD 分层详解

### 标准 DDD 四层架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Interfaces Layer (接口层)                  │
│  职责: HTTP 请求处理、参数验证、响应封装                        │
│  组件: Controllers, DTOs, Request/Response                   │
│  依赖: Application Layer                                     │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                  Application Layer (应用层)                   │
│  职责: 用例编排、事务管理、DTO 转换                            │
│  组件: Application Services, Assemblers                      │
│  依赖: Domain Layer                                          │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    Domain Layer (领域层)                      │
│  职责: 业务逻辑、领域规则、领域模型                            │
│  组件: Aggregates, Entities, Value Objects,                 │
│        Domain Services (接口), Repository (接口)             │
│  依赖: 无（纯领域逻辑）                                        │
└─────────────────────────────────────────────────────────────┘
                              ↑
┌─────────────────────────────────────────────────────────────┐
│               Infrastructure Layer (基础设施层)               │
│  职责: 技术实现、外部系统集成、持久化                          │
│  组件: Repository Implementations, Database Access,          │
│        External Service Adapters, Cache, MQ                 │
│  依赖: Domain Layer (实现接口)                                │
└─────────────────────────────────────────────────────────────┘
```

### 依赖方向规则

```
Interfaces → Application → Domain ← Infrastructure
                              ↑
                              └─── 依赖倒置原则 (DIP)
```

**关键原则**:
- **高层模块不依赖低层模块**,两者都依赖抽象
- **Domain 层不依赖任何其他层**
- **Infrastructure 层实现 Domain 层定义的接口**

### 当前实现的依赖关系

```
PostApplicationService (Application)
    ↓ 依赖
DualStorageManager (Domain 接口)
    ↑ 实现
CachedDualStorageManager (Infrastructure)
    ↓ 委托
DualStorageManagerImpl (Infrastructure)
    ↓ 依赖
PostRepository (Domain 接口)
PostContentRepository (Infrastructure)
```

**问题识别**:
1. ✅ Application → Domain: 正确
2. ✅ Infrastructure 实现 Domain 接口: 正确
3. ⚠️ Domain 接口参数使用 Infrastructure 类型: 违反 DIP
4. ⚠️ 领域服务实现在 Infrastructure 层: 有争议

---

## 装饰器模式深度分析

### 设计模式概述

装饰器模式(Decorator Pattern)允许向对象动态添加新功能,而不改变其结构。

**核心要素**:
1. **Component 接口**: `DualStorageManager`
2. **Concrete Component**: `DualStorageManagerImpl`
3. **Decorator**: `CachedDualStorageManager`

### 类图详解

```
┌─────────────────────────────────────────────────────────┐
│  <<interface>>                                          │
│  DualStorageManager                                     │
│  (domain.service)                                       │
├─────────────────────────────────────────────────────────┤
│ + createPost(Post, PostContent): Long                   │
│ + getPostFullDetail(Long): PostDetail                   │
│ + getPostContent(Long): PostContent                     │
│ + updatePost(Post, PostContent): void                   │
│ + deletePost(Long): void                                │
└─────────────────────────────────────────────────────────┘
           ↑                              ↑
           │ implements                   │ implements
           │                              │
┌──────────┴────────────────┐  ┌─────────┴──────────────────────┐
│ DualStorageManagerImpl    │  │ CachedDualStorageManager       │
│ (基础实现)                 │  │ (装饰器)                        │
│ @Service                  │  │ @Primary @Service              │
├───────────────────────────┤  ├────────────────────────────────┤
│ - postRepository          │  │ - delegate: DualStorageManager │
│ - contentRepository       │  │ - redisTemplate                │
│ - contentEnricher         │  │ - redissonClient               │
│ - executorService         │  │ - hotDataIdentifier            │
│                           │  │ - cacheProperties              │
├───────────────────────────┤  ├────────────────────────────────┤
│ + createPost()            │  │ + createPost()                 │
│   - 三阶段提交             │  │   → delegate.createPost()      │
│   - PG Insert             │  │                                │
│   - Mongo Insert          │  │ + getPostFullDetail()          │
│   - PG Update             │  │   - 查缓存                      │
│   - Sentinel 熔断         │  │   - 热点检测                    │
│                           │  │   - 分布式锁                    │
│ + getPostFullDetail()     │  │   - DCL 双重检查                │
│   - 并行查询 PG + Mongo   │  │   → delegate.getPostFull...()  │
│   - CompletableFuture     │  │   - 写缓存                      │
│   - 降级处理              │  │   - 降级处理                    │
│                           │  │                                │
│ + getPostContent()        │  │ + getPostContent()             │
│   - 查询 MongoDB          │  │   - 查缓存                      │
│   - Sentinel 熔断         │  │   - 热点检测                    │
│                           │  │   - 分布式锁                    │
│ + updatePost()            │  │   → delegate.getPostContent()  │
│   - 双写更新              │  │   - 写缓存                      │
│   - @Transactional        │  │                                │
│                           │  │ + updatePost()                 │
│ + deletePost()            │  │   → delegate.updatePost()      │
│   - 双删除                │  │   - 删除缓存                    │
│   - @Transactional        │  │                                │
│                           │  │ + deletePost()                 │
└───────────────────────────┘  │   → delegate.deletePost()      │
                               │   - 删除缓存                    │
                               └────────────────────────────────┘
```

### 依赖注入配置详解

```java
// 1. 基础实现 (Bean 名称: dualStorageManagerImpl)
@Service  // 默认 Bean 名称: dualStorageManagerImpl
@RequiredArgsConstructor
public class DualStorageManagerImpl implements DualStorageManager {
    private final PostRepository postRepository;
    private final PostContentRepository postContentRepository;
    private final ContentEnricher contentEnricher;
    // ...
}

// 2. 装饰器 (优先注入)
@Primary  // ⭐ 标记为主要 Bean,优先注入
@Service
public class CachedDualStorageManager implements DualStorageManager {
    
    private final DualStorageManager delegate;
    
    public CachedDualStorageManager(
            RedisTemplate<String, Object> redisTemplate,
            RedissonClient redissonClient,
            HotDataIdentifier hotDataIdentifier,
            CacheProperties cacheProperties,
            @Qualifier("dualStorageManagerImpl") DualStorageManager delegate) {
        // ⭐ 通过 @Qualifier 注入基础实现
        this.delegate = delegate;
        // ...
    }
}

// 3. 应用服务注入
@Service
@RequiredArgsConstructor
public class PostApplicationService {
    
    // ⭐ 自动注入 @Primary 的 CachedDualStorageManager
    private final DualStorageManager dualStorageManager;
    
    public Long createPost(Long userId, CreatePostRequest request) {
        // 实际调用的是 CachedDualStorageManager
        return dualStorageManager.createPost(post, content);
    }
}
```

### Spring 依赖注入流程

```
1. Spring 扫描所有 @Service 组件
   ├─ DualStorageManagerImpl (Bean: dualStorageManagerImpl)
   └─ CachedDualStorageManager (Bean: cachedDualStorageManager, @Primary)

2. 创建 CachedDualStorageManager Bean
   ├─ 需要注入 DualStorageManager delegate
   ├─ 通过 @Qualifier("dualStorageManagerImpl") 指定注入 DualStorageManagerImpl
   └─ 创建成功

3. 创建 PostApplicationService Bean
   ├─ 需要注入 DualStorageManager
   ├─ 发现有两个候选 Bean: dualStorageManagerImpl, cachedDualStorageManager
   ├─ cachedDualStorageManager 标记了 @Primary
   └─ 注入 cachedDualStorageManager ✅
```

### 装饰器的职责边界

#### CachedDualStorageManager 的职责 (横切关注点)

1. **缓存管理** ✅
   - Cache-Aside 模式
   - 空值缓存防止穿透
   - TTL + 随机抖动防止雪崩

2. **热点检测** ✅
   - 记录访问频率
   - 自动识别热点数据
   - 支持手动标记

3. **分布式锁** ✅
   - 热点数据使用分布式锁
   - DCL 双重检查
   - 锁超时降级

4. **降级处理** ✅
   - Redis 连接失败降级
   - Redisson 连接失败降级
   - 锁获取超时降级

#### DualStorageManagerImpl 的职责 (核心业务逻辑)

1. **三阶段提交** (领域逻辑)
   - 阶段1: PG Insert (DRAFT)
   - 阶段2: Mongo Insert
   - 阶段3: PG Update (PUBLISHED)

2. **数据一致性保证** (领域逻辑)
   - @Transactional 事务管理
   - MongoDB 失败回滚 PG

3. **并行查询优化** (技术实现)
   - CompletableFuture 并行查询
   - ExecutorService 线程池

4. **Sentinel 熔断** (技术实现)
   - @SentinelResource 注解
   - 流控/熔断处理器
   - 降级处理器

### 装饰器模式的优势

1. **关注点分离** ✅
   - 缓存逻辑与业务逻辑分离
   - 易于维护和测试

2. **灵活扩展** ✅
   - 可以添加更多装饰器(监控、限流等)
   - 不修改基础实现

3. **透明性** ✅
   - 对调用方透明
   - 符合开闭原则

4. **可配置性** ✅
   - 通过 @Primary 控制是否启用装饰器
   - 可以在测试环境禁用缓存

---

## 完整调用链路

### 场景 1: 创建文章

```
1. HTTP Request
   POST /api/v1/posts
   Body: { title, content, tags, ... }
   ↓
2. PostController (Interfaces Layer)
   @PostMapping("/api/v1/posts")
   public Result<Long> createPost(@RequestBody CreatePostRequest request) {
       Long postId = postApplicationService.createPost(userId, request);
       return Result.success(postId);
   }
   ↓
3. PostApplicationService (Application Layer)
   @Transactional
   public Long createPost(Long userId, CreatePostRequest request) {
       // 生成 ID
       Long postId = idGeneratorFeignClient.generateSnowflakeId();
       
       // 创建领域对象
       Post post = Post.createDraft(postId, userId, request.getTitle());
       
       // 填充作者信息
       fillAuthorInfo(post, userId);
       
       // 创建 MongoDB 内容文档
       PostContent content = new PostContent();
       content.setRaw(request.getContent());
       
       // 处理标签
       List<Long> tagIds = tagDomainService.findOrCreateBatch(request.getTags());
       
       // ⭐ 调用领域服务
       dualStorageManager.createPost(post, content);
       
       // 创建标签关联
       postTagRepository.attachBatch(postId, tagIds);
       
       // 发布事件
       eventPublisher.publish(new PostCreatedEvent(...));
       
       return postId;
   }
   ↓
4. CachedDualStorageManager (Infrastructure - Decorator)
   @Override
   public Long createPost(Post post, PostContent content) {
       // 创建文章,不缓存(等待第一次查询时缓存)
       Long postId = delegate.createPost(post, content);
       log.debug("Created post: {}, cache will be populated on first read", postId);
       return postId;
   }
   ↓
5. DualStorageManagerImpl (Infrastructure - Base)
   @Override
   @Transactional(rollbackFor = Exception.class)
   @SentinelResource(value = "createPost", ...)
   public Long createPost(Post post, PostContent content) {
       try {
           // 阶段1: 写入 PostgreSQL
           postRepository.save(post);
           log.info("Successfully saved post metadata to PostgreSQL: {}", postId);
           
           // 阶段2: 写入 MongoDB
           try {
               content.setPostId(String.valueOf(postId));
               PostContent enrichedContent = contentEnricher.enrich(content);
               postContentRepository.save(enrichedContent);
               log.info("Successfully saved post content to MongoDB: {}", postId);
               
               // 阶段3: 如果需要发布,更新 PostgreSQL 状态
               if (shouldPublish) {
                   post.publish();
                   postRepository.update(post);
               }
               
               return postId;
           } catch (Exception mongoEx) {
               // MongoDB 失败,PostgreSQL 自动回滚
               throw new DualStorageException("Failed to save content to MongoDB", mongoEx);
           }
       } catch (Exception pgEx) {
           throw new DualStorageException("Failed to save post to PostgreSQL", pgEx);
       }
   }
   ↓
6. PostRepositoryImpl (Infrastructure)
   @Override
   public void save(Post post) {
       PostPO po = toPO(post);
       postMapper.insert(po);  // MyBatis-Plus
       
       // 初始化统计数据
       PostStatsPO statsPO = new PostStatsPO();
       statsPO.setPostId(post.getId());
       postStatsMapper.insert(statsPO);
   }
   ↓
7. PostContentRepository (Infrastructure - MongoDB)
   @Override
   public PostContent save(PostContent content) {
       return mongoTemplate.save(content, "post_content");
   }
```

### 场景 2: 查询文章详情 (带缓存)

```
1. HTTP Request
   GET /api/v1/posts/{postId}
   ↓
2. PostController
   @GetMapping("/api/v1/posts/{postId}")
   public Result<PostVO> getPost(@PathVariable Long postId) {
       PostVO vo = postApplicationService.getPostById(postId);
       return Result.success(vo);
   }
   ↓
3. PostApplicationService
   @Transactional(readOnly = true)
   public PostVO getPostById(Long postId) {
       // ⭐ 调用领域服务获取完整详情
       PostDetail detail = dualStorageManager.getPostFullDetail(postId);
       
       Post post = detail.getPost();
       PostContent content = detail.getContent();
       
       // 转换为 VO
       PostVO vo = PostAssembler.toVOWithContent(post, content);
       
       // 填充作者信息
       enrichPostWithAuthorInfo(vo, post);
       
       return vo;
   }
   ↓
4. CachedDualStorageManager (缓存装饰器)
   @Override
   public PostDetail getPostFullDetail(Long postId) {
       String cacheKey = PostRedisKeys.fullDetail(postId);
       
       try {
           // Step 1: 第一次检查缓存
           Object cached = redisTemplate.opsForValue().get(cacheKey);
           
           // Step 2: 命中缓存
           if (cached != null) {
               if (CacheConstants.NULL_VALUE.equals(cached)) {
                   return null;
               }
               log.debug("Cache hit for full detail: key={}", cacheKey);
               return (PostDetail) cached;
           }
           
           // Step 3: 未命中,记录访问并检查是否为热点数据
           log.debug("Cache miss for full detail: key={}", cacheKey);
           hotDataIdentifier.recordAccess("post", postId);
           
           boolean isHot = hotDataIdentifier.isHotData("post", postId);
           
           if (isHot) {
               // 热点数据: 使用分布式锁防止缓存击穿
               return loadFullDetailWithLock(postId, cacheKey);
           } else {
               // 非热点数据: 直接查询数据库
               PostDetail detail = delegate.getPostFullDetail(postId);
               cacheFullDetail(cacheKey, detail);
               return detail;
           }
       } catch (RedisConnectionFailureException e) {
           // Redis 连接失败,降级直接查询数据库
           log.error("Redis connection failed, falling back to database");
           return delegate.getPostFullDetail(postId);
       }
   }
   
   // 热点数据加载 (使用分布式锁)
   private PostDetail loadFullDetailWithLock(Long postId, String cacheKey) {
       String lockKey = PostRedisKeys.lockFullDetail(postId);
       RLock lock = redissonClient.getLock(lockKey);
       
       try {
           // Step 2: 尝试获取分布式锁
           boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
           
           if (!acquired) {
               // Step 6: 超时降级 - 直接查询数据库
               log.warn("Failed to acquire lock, falling back to database");
               return delegate.getPostFullDetail(postId);
           }
           
           try {
               // Step 3: DCL 双重检查 - 获取锁后再次检查缓存
               Object cached = redisTemplate.opsForValue().get(cacheKey);
               if (cached != null) {
                   log.debug("DCL: Cache hit after acquiring lock");
                   return (PostDetail) cached;
               }
               
               // Step 4: 查询数据库
               PostDetail detail = delegate.getPostFullDetail(postId);
               
               // Step 5: 写入缓存
               cacheFullDetail(cacheKey, detail);
               
               return detail;
           } finally {
               // 确保锁被释放
               if (lock.isHeldByCurrentThread()) {
                   lock.unlock();
               }
           }
       } catch (InterruptedException e) {
           Thread.currentThread().interrupt();
           return delegate.getPostFullDetail(postId);
       }
   }
   ↓
5. DualStorageManagerImpl (基础实现)
   @Override
   @SentinelResource(value = "getPostFullDetail", ...)
   public PostDetail getPostFullDetail(Long postId) {
       try {
           // 并行查询 PostgreSQL 和 MongoDB
           CompletableFuture<Post> postFuture = CompletableFuture.supplyAsync(() -> {
               Optional<Post> postOpt = postRepository.findById(postId);
               if (!postOpt.isPresent()) {
                   throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
               }
               return postOpt.get();
           }, executorService);
           
           CompletableFuture<PostContent> contentFuture = CompletableFuture.supplyAsync(() -> {
               Optional<PostContent> contentOpt = postContentRepository.findByPostId(String.valueOf(postId));
               if (!contentOpt.isPresent()) {
                   throw new DualStorageException("Post content not found in MongoDB");
               }
               return contentOpt.get();
           }, executorService);
           
           // 等待两个查询都完成
           CompletableFuture.allOf(postFuture, contentFuture).join();
           
           Post post = postFuture.get();
           PostContent content = contentFuture.get();
           
           return new PostDetail(post, content);
       } catch (Exception e) {
           log.error("Failed to get full detail for post: {}", postId, e);
           throw new DualStorageException("Failed to get full detail", e);
       }
   }
   ↓
6. PostRepositoryImpl + PostContentRepository
   // 并行执行
   ├─ postRepository.findById(postId)      // PostgreSQL
   └─ postContentRepository.findByPostId() // MongoDB
```

### 调用链路关键点

1. **Controller → Application Service**
   - 参数验证
   - 权限检查
   - DTO 转换

2. **Application Service → Domain Service**
   - 事务管理
   - 用例编排
   - 事件发布

3. **Decorator → Base Implementation**
   - 缓存逻辑
   - 热点检测
   - 分布式锁
   - 降级处理

4. **Base Implementation → Repository**
   - 三阶段提交
   - 并行查询
   - Sentinel 熔断

5. **Repository → Database**
   - ORM 映射
   - SQL 执行

---

## 架构问题深度剖析

### 问题 1: 领域服务实现的位置

#### 当前设计

```
domain/
  service/
    DualStorageManager.java (接口) ✅ 正确

infrastructure/
  service/
    DualStorageManagerImpl.java (实现) ⚠️ 有争议
    CachedDualStorageManager.java (装饰器) ✅ 正确
```

#### 代码分析

**DualStorageManagerImpl 包含的职责**:

```java
@Service
@RequiredArgsConstructor
public class DualStorageManagerImpl implements DualStorageManager {
    
    // ========== 基础设施依赖 ==========
    private final PostRepository postRepository;              // Domain 接口
    private final PostContentRepository postContentRepository; // Infrastructure
    private final ContentEnricher contentEnricher;            // Infrastructure
    private final ExecutorService executorService;            // Infrastructure
    
    @Override
    @Transactional(rollbackFor = Exception.class)  // ← 技术实现
    @SentinelResource(value = "createPost", ...)   // ← 技术实现
    public Long createPost(Post post, PostContent content) {
        // ========== 领域逻辑 ==========
        // 三阶段提交逻辑
        PostStatus originalStatus = post.getStatus();
        boolean shouldPublish = originalStatus == PostStatus.PUBLISHED;
        
        // 阶段1: 写入 PostgreSQL
        postRepository.save(post);
        
        // 阶段2: 写入 MongoDB
        try {
            content.setPostId(String.valueOf(postId));
            PostContent enrichedContent = contentEnricher.enrich(content);
            postContentRepository.save(enrichedContent);
            
            // 阶段3: 如果需要发布,更新状态
            if (shouldPublish) {
                post.publish();  // ← 领域行为
                postRepository.update(post);
            }
            
            return postId;
        } catch (Exception mongoEx) {
            // ========== 数据一致性保证 (领域逻辑) ==========
            throw new DualStorageException("Failed to save content to MongoDB", mongoEx);
        }
    }
}
```

**职责分类**:

| 职责 | 类型 | 应该在哪一层 |
|------|------|-------------|
| 三阶段提交逻辑 | 领域逻辑 | Domain Layer |
| 数据一致性保证 | 领域逻辑 | Domain Layer |
| 状态转换规则 | 领域逻辑 | Domain Layer |
| PostgreSQL 操作 | 技术实现 | Infrastructure Layer |
| MongoDB 操作 | 技术实现 | Infrastructure Layer |
| @Transactional | 技术实现 | Infrastructure Layer |
| @SentinelResource | 技术实现 | Infrastructure Layer |
| CompletableFuture | 技术实现 | Infrastructure Layer |

#### 违反的原则

1. **单一职责原则 (SRP)**
   - 一个类承担了领域逻辑和技术实现两种职责

2. **依赖倒置原则 (DIP)**
   - 领域逻辑直接依赖了具体的技术实现

#### 为什么这样设计?

**实用主义的考虑**:

1. **紧密耦合**: 三阶段提交逻辑与数据库操作紧密耦合,难以分离
2. **事务边界**: @Transactional 需要在数据库操作层面
3. **性能优化**: CompletableFuture 并行查询需要直接访问 Repository
4. **简化实现**: 避免引入过多的抽象层

**如果严格分层会怎样?**

```java
// Domain Layer
public class DualStorageManagerDomainImpl implements DualStorageManager {
    private final PostRepository postRepository;
    private final PostContentRepository postContentRepository;
    
    public Long createPost(Post post, PostContent content) {
        // 只包含领域逻辑
        // 但仍然需要调用 Repository...
        // 而 Repository 的实现在 Infrastructure 层
        // 导致循环依赖或复杂的依赖关系
    }
}

// Infrastructure Layer
public class DualStorageManagerInfraImpl {
    // 实际的数据库操作
    // 但领域逻辑在 Domain 层...
    // 如何协调?
}
```

**结论**: 严格分层会导致更复杂的设计,收益有限。

---

### 问题 2: 领域接口依赖基础设施类型

#### 当前设计

```java
// domain/service/DualStorageManager.java
public interface DualStorageManager {
    Long createPost(Post post, PostContent content);
    //                          ↑
    //                          PostContent 是 MongoDB Document
}

// infrastructure/mongodb/document/PostContent.java
@Document(collection = "post_content")
public class PostContent {
    @Id
    private String id;
    private String postId;
    private String contentType;
    private String raw;
    private String html;
    private String text;
    // ...
}
```

#### 问题分析

**违反的原则**:
- **依赖倒置原则 (DIP)**: Domain 层接口依赖了 Infrastructure 层的具体类型
- **分层架构原则**: 高层模块依赖了低层模块

**依赖关系**:
```
Domain Layer (DualStorageManager 接口)
    ↓ 依赖
Infrastructure Layer (PostContent Document)
```

**正确的依赖关系应该是**:
```
Domain Layer (DualStorageManager 接口)
    ↑ 实现
Infrastructure Layer (实现类)
```

#### 为什么这样设计?

**实用主义的考虑**:

1. **PostContent 是纯数据容器**: 没有复杂的领域逻辑
2. **避免重复定义**: 如果定义领域值对象,需要额外的转换逻辑
3. **简化实现**: 直接使用 MongoDB Document,减少抽象层

**如果引入领域值对象会怎样?**

```java
// domain/model/PostContentVO.java (值对象)
public class PostContentVO {
    private final String content;
    private final String summary;
    private final String contentType;
    // 领域概念,不依赖 MongoDB
}

// domain/service/DualStorageManager.java
public interface DualStorageManager {
    Long createPost(Post post, PostContentVO content);
    //                          ↑
    //                          使用领域值对象
}

// infrastructure/service/DualStorageManagerImpl.java
public class DualStorageManagerImpl implements DualStorageManager {
    @Override
    public Long createPost(Post post, PostContentVO contentVO) {
        // 转换为 MongoDB Document
        PostContent mongoDoc = toMongoDocument(contentVO);
        postContentRepository.save(mongoDoc);
    }
    
    private PostContent toMongoDocument(PostContentVO vo) {
        PostContent doc = new PostContent();
        doc.setRaw(vo.getContent());
        doc.setContentType(vo.getContentType());
        // ... 转换逻辑
        return doc;
    }
}
```

**优点**:
- ✅ 解耦 Domain 层和 Infrastructure 层
- ✅ 符合 DDD 原则

**缺点**:
- ❌ 需要额外的转换逻辑
- ❌ 增加代码复杂度
- ❌ 对于简单的 CRUD 场景,过度设计

**结论**: 对于当前场景,直接使用 MongoDB Document 是可接受的权衡。

---

### 问题 3: 装饰器的职责边界

#### 当前设计评估

**CachedDualStorageManager 的职责**:

```java
@Primary
@Service
public class CachedDualStorageManager implements DualStorageManager {
    
    // ========== 横切关注点 (Cross-Cutting Concerns) ==========
    
    // 1. 缓存管理 ✅
    private void cacheFullDetail(String key, PostDetail detail) {
        if (detail != null) {
            long ttlWithJitter = cacheProperties.getTtl().getEntityDetail() + randomJitter();
            redisTemplate.opsForValue().set(key, detail, ttlWithJitter, TimeUnit.SECONDS);
        } else {
            // 空值缓存防止穿透
            redisTemplate.opsForValue().set(key, CacheConstants.NULL_VALUE, 60, TimeUnit.SECONDS);
        }
    }
    
    // 2. 热点检测 ✅
    boolean isHot = hotDataIdentifier.isHotData("post", postId) 
            || hotDataIdentifier.isManuallyMarkedAsHot("post", postId);
    
    // 3. 分布式锁 ✅
    private PostDetail loadFullDetailWithLock(Long postId, String cacheKey) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
        // DCL 双重检查
        // ...
    }
    
    // 4. 降级处理 ✅
    try {
        // 缓存操作
    } catch (RedisConnectionFailureException e) {
        // Redis 连接失败,降级直接查询数据库
        return delegate.getPostFullDetail(postId);
    }
}
```

**评估结果**: ✅ 装饰器的职责划分是合理的

**理由**:
1. 所有功能都是横切关注点,不是核心业务逻辑
2. 可以独立开关(通过 @Primary 控制)
3. 不影响基础实现的业务逻辑
4. 符合装饰器模式的设计意图

---

### 问题 4: 与其他领域服务的一致性

#### DraftManager 的设计

```java
// domain/service/DraftManager.java
public interface DraftManager {
    void saveDraft(Long postId, Long userId, String content, boolean isAutoSave);
    Optional<PostDraft> getLatestDraft(Long postId, Long userId);
    //      ↑
    //      PostDraft 也是 MongoDB Document
}

// infrastructure/service/DraftManagerImpl.java
@Service
@RequiredArgsConstructor
public class DraftManagerImpl implements DraftManager {
    private final PostDraftRepository draftRepository;  // MongoDB Repository
    
    @Override
    public void saveDraft(Long postId, Long userId, String content, boolean isAutoSave) {
        // 直接操作 MongoDB
        PostDraft draft = PostDraft.builder()
                .postId(String.valueOf(postId))
                .userId(String.valueOf(userId))
                .content(content)
                .build();
        draftRepository.save(draft);
    }
}

// infrastructure/service/CachedDraftManager.java
@Primary
@Service
public class CachedDraftManager implements DraftManager {
    private final DraftManager delegate;
    // 装饰器模式,添加缓存
}
```

**观察**:
1. ✅ 接口在 Domain 层
2. ⚠️ 实现在 Infrastructure 层
3. ⚠️ 接口参数使用 MongoDB Document
4. ✅ 使用装饰器模式添加缓存

**结论**: 整个 ZhiCore-post 服务的领域服务设计是一致的,都采用了相同的模式。

---

## 代码级别分析

### 三阶段提交详解

#### 设计目标

确保 PostgreSQL 和 MongoDB 的数据一致性:
- PostgreSQL: 存储文章元数据
- MongoDB: 存储文章内容

#### 实现代码

```java
@Override
@Transactional(rollbackFor = Exception.class)
public Long createPost(Post post, PostContent content) {
    Long postId = post.getId();
    
    try {
        // ========== 阶段1: 写入 PostgreSQL ==========
        // 记录原始状态,如果是要发布的文章,先保存为草稿
        PostStatus originalStatus = post.getStatus();
        boolean shouldPublish = originalStatus == PostStatus.PUBLISHED;
        
        postRepository.save(post);  // INSERT INTO posts ...
        log.info("Successfully saved post metadata to PostgreSQL: {}", postId);
        
        // ========== 阶段2: 写入 MongoDB ==========
        try {
            content.setPostId(String.valueOf(postId));
            content.setCreatedAt(LocalDateTime.now());
            content.setUpdatedAt(LocalDateTime.now());
            
            // 增强内容:自动计算字数、阅读时间、提取媒体资源等
            PostContent enrichedContent = contentEnricher.enrich(content);
            
            postContentRepository.save(enrichedContent);  // MongoDB insert
            log.info("Successfully saved post content to MongoDB: {}", postId);
            
            // ========== 阶段3: 更新 PostgreSQL 状态 ==========
            if (shouldPublish) {
                post.publish();  // 状态: DRAFT → PUBLISHED
                postRepository.update(post);  // UPDATE posts SET status = 'PUBLISHED' ...
                log.info("Successfully updated post status to PUBLISHED: {}", postId);
            }
            
            return postId;
            
        } catch (Exception mongoEx) {
            log.error("Failed to save content to MongoDB for post: {}", postId, mongoEx);
            // MongoDB 写入失败,PostgreSQL 会自动回滚(因为 @Transactional)
            throw new DualStorageException("Failed to save content to MongoDB", mongoEx);
        }
        
    } catch (Exception pgEx) {
        log.error("Failed to save post to PostgreSQL: {}", postId, pgEx);
        throw new DualStorageException("Failed to save post to PostgreSQL", pgEx);
    }
}
```

#### 一致性保证

**成功场景**:
```
1. PG Insert (DRAFT) ✅
2. Mongo Insert ✅
3. PG Update (PUBLISHED) ✅
→ 结果: 数据一致
```

**失败场景 1: MongoDB 写入失败**:
```
1. PG Insert (DRAFT) ✅
2. Mongo Insert ❌ (抛出异常)
3. @Transactional 回滚 PG Insert
→ 结果: 数据一致(都没有)
```

**失败场景 2: PG Update 失败**:
```
1. PG Insert (DRAFT) ✅
2. Mongo Insert ✅
3. PG Update (PUBLISHED) ❌
4. @Transactional 回滚 PG Insert
5. MongoDB 数据孤立 ⚠️
→ 结果: 数据不一致
```

**问题**: MongoDB 不支持分布式事务,无法回滚

**解决方案**:
1. **补偿机制**: 定时任务清理孤立的 MongoDB 数据
2. **幂等性**: 支持重试,重新执行整个流程
3. **最终一致性**: 接受短暂的不一致,通过补偿达到最终一致

---

### 并行查询优化

#### 设计目标

提升查询性能,减少响应时间:
- PostgreSQL 查询: ~50ms
- MongoDB 查询: ~30ms
- 串行总耗时: 80ms
- 并行总耗时: max(50ms, 30ms) = 50ms

#### 实现代码

```java
@Override
public PostDetail getPostFullDetail(Long postId) {
    try {
        // ========== 并行查询 PostgreSQL 和 MongoDB ==========
        CompletableFuture<Post> postFuture = CompletableFuture.supplyAsync(() -> {
            Optional<Post> postOpt = postRepository.findById(postId);
            if (!postOpt.isPresent()) {
                throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
            }
            return postOpt.get();
        }, executorService);  // 使用线程池
        
        CompletableFuture<PostContent> contentFuture = CompletableFuture.supplyAsync(() -> {
            Optional<PostContent> contentOpt = postContentRepository.findByPostId(String.valueOf(postId));
            if (!contentOpt.isPresent()) {
                throw new DualStorageException("Post content not found in MongoDB");
            }
            return contentOpt.get();
        }, executorService);  // 使用线程池
        
        // ========== 等待两个查询都完成 ==========
        CompletableFuture.allOf(postFuture, contentFuture).join();
        
        Post post = postFuture.get();
        PostContent content = contentFuture.get();
        
        log.info("Successfully retrieved full detail for post: {}", postId);
        return new PostDetail(post, content);
        
    } catch (CompletionException e) {
        // 解包 CompletionException,获取真实异常
        Throwable cause = e.getCause();
        if (cause instanceof BusinessException) {
            throw (BusinessException) cause;
        }
        throw new DualStorageException("Failed to get full detail", e);
    }
}
```

#### 线程池配置

```java
// 用于并行查询的线程池
private final ExecutorService executorService = Executors.newFixedThreadPool(10);
```

**问题**: 硬编码线程池大小

**改进建议**:
```java
@Configuration
public class ThreadPoolConfig {
    
    @Bean("dualStorageExecutor")
    public ExecutorService dualStorageExecutor(
            @Value("${dual-storage.thread-pool.core-size:10}") int coreSize,
            @Value("${dual-storage.thread-pool.max-size:20}") int maxSize) {
        return new ThreadPoolExecutor(
            coreSize,
            maxSize,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactoryBuilder().setNameFormat("dual-storage-%d").build(),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
```

---

### 缓存策略详解

#### Cache-Aside 模式

**读流程**:
```
1. 查缓存
   ├─ 命中 → 返回缓存数据
   └─ 未命中 → 查数据库 → 写缓存 → 返回数据
```

**写流程**:
```
1. 更新数据库
2. 删除缓存 (而不是更新缓存)
```

#### 为什么删除而不是更新?

**删除缓存的优势**:
1. **避免并发问题**: 更新缓存可能导致数据不一致
2. **延迟加载**: 只有被访问时才重新加载
3. **简化逻辑**: 不需要考虑缓存更新的复杂性

**示例**:
```java
@Override
public void updatePost(Post post, PostContent content) {
    Long postId = post.getId();
    
    // 1. 更新数据库
    delegate.updatePost(post, content);
    
    // 2. 删除缓存(Cache-Aside 模式)
    try {
        evictCache(postId);
    } catch (Exception e) {
        log.warn("Failed to evict cache after update: {}", e.getMessage());
    }
}

private void evictCache(Long postId) {
    String contentKey = PostRedisKeys.content(postId);
    String fullDetailKey = PostRedisKeys.fullDetail(postId);
    String postDetailKey = PostRedisKeys.detail(postId);
    
    redisTemplate.delete(contentKey);
    redisTemplate.delete(fullDetailKey);
    redisTemplate.delete(postDetailKey);
}
```

#### 缓存穿透防护

**问题**: 查询不存在的数据,请求穿透到数据库

**解决方案**: 空值缓存

```java
private void cacheFullDetail(String key, PostDetail detail) {
    if (detail != null) {
        // 正常数据缓存
        long ttlWithJitter = cacheProperties.getTtl().getEntityDetail() + randomJitter();
        redisTemplate.opsForValue().set(key, detail, ttlWithJitter, TimeUnit.SECONDS);
    } else {
        // ⭐ 缓存空值防止缓存穿透
        redisTemplate.opsForValue().set(key, CacheConstants.NULL_VALUE,
                CacheConstants.NULL_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
    }
}
```

#### 缓存击穿防护

**问题**: 热点数据过期瞬间,大量请求打到数据库

**解决方案**: 分布式锁 + DCL 双重检查

```java
private PostDetail loadFullDetailWithLock(Long postId, String cacheKey) {
    String lockKey = PostRedisKeys.lockFullDetail(postId);
    RLock lock = redissonClient.getLock(lockKey);
    
    try {
        // Step 1: 尝试获取分布式锁
        boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
        
        if (!acquired) {
            // 超时降级 - 直接查询数据库
            return delegate.getPostFullDetail(postId);
        }
        
        try {
            // Step 2: DCL 双重检查 - 获取锁后再次检查缓存
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return (PostDetail) cached;
            }
            
            // Step 3: 查询数据库
            PostDetail detail = delegate.getPostFullDetail(postId);
            
            // Step 4: 写入缓存
            cacheFullDetail(cacheKey, detail);
            
            return detail;
        } finally {
            // Step 5: 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return delegate.getPostFullDetail(postId);
    }
}
```

#### 缓存雪崩防护

**问题**: 大量缓存同时过期,请求全部打到数据库

**解决方案**: TTL + 随机抖动

```java
private void cacheFullDetail(String key, PostDetail detail) {
    if (detail != null) {
        // ⭐ 添加随机抖动防止缓存雪崩
        long ttlWithJitter = cacheProperties.getTtl().getEntityDetail() + randomJitter();
        redisTemplate.opsForValue().set(key, detail, ttlWithJitter, TimeUnit.SECONDS);
    }
}

private int randomJitter() {
    // 0-60秒随机抖动
    return ThreadLocalRandom.current().nextInt(0, CacheConstants.MAX_JITTER_SECONDS);
}
```

---
## 性能与可靠性

### 性能指标

#### 查询性能

**无缓存场景**:
```
串行查询:
├─ PostgreSQL: 50ms
├─ MongoDB: 30ms
└─ 总耗时: 80ms

并行查询:
├─ PostgreSQL: 50ms (并行)
├─ MongoDB: 30ms (并行)
└─ 总耗时: max(50ms, 30ms) = 50ms

性能提升: (80-50)/80 = 37.5%
```

**有缓存场景**:
```
缓存命中:
├─ Redis 查询: 2-5ms
└─ 性能提升: 90%+

缓存未命中(非热点):
├─ 并行查询: 50ms
├─ 写缓存: 5ms
└─ 总耗时: 55ms

缓存未命中(热点):
├─ 获取分布式锁: 10-20ms
├─ DCL 检查: 2ms
├─ 并行查询: 50ms
├─ 写缓存: 5ms
└─ 总耗时: 67-77ms
```

#### 写入性能

**创建文章**:
```
三阶段提交:
├─ PG Insert: 20ms
├─ Mongo Insert: 15ms
├─ PG Update: 10ms
└─ 总耗时: 45ms
```

**更新文章**:
```
双写更新:
├─ PG Update: 20ms
├─ Mongo Update: 15ms
├─ 删除缓存: 5ms
└─ 总耗时: 40ms
```


### 可靠性保障

#### 1. 数据一致性

**PostgreSQL + MongoDB 一致性**:
- ✅ 使用 @Transactional 保证 PostgreSQL 事务
- ⚠️ MongoDB 不支持分布式事务
- ✅ 三阶段提交减少不一致窗口
- ✅ 补偿机制处理孤立数据

**缓存一致性**:
- ✅ Cache-Aside 模式
- ✅ 更新时删除缓存
- ⚠️ 存在短暂的不一致窗口(可接受)

#### 2. 熔断降级

**Sentinel 熔断**:
```java
@SentinelResource(
    value = "createPost",
    blockHandler = "createPostBlockHandler",
    fallback = "createPostFallback"
)
public Long createPost(Post post, PostContent content) {
    // 正常逻辑
}

// 流控/熔断处理器
public Long createPostBlockHandler(Post post, PostContent content, BlockException ex) {
    log.warn("Create post blocked by Sentinel: {}", ex.getMessage());
    throw new BusinessException(ResultCode.SERVICE_BUSY, "服务繁忙,请稍后重试");
}

// 降级处理器
public Long createPostFallback(Post post, PostContent content, Throwable ex) {
    log.error("Create post fallback: {}", ex.getMessage());
    throw new BusinessException(ResultCode.INTERNAL_ERROR, "创建文章失败");
}
```


**Redis 连接失败降级**:
```java
try {
    // 缓存操作
    Object cached = redisTemplate.opsForValue().get(cacheKey);
    // ...
} catch (RedisConnectionFailureException e) {
    // Redis 连接失败,降级直接查询数据库
    log.error("Redis connection failed, falling back to database", e);
    return delegate.getPostFullDetail(postId);
}
```

**Redisson 连接失败降级**:
```java
try {
    RLock lock = redissonClient.getLock(lockKey);
    boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
    // ...
} catch (Exception e) {
    // Redisson 连接失败,降级直接查询数据库
    log.error("Redisson connection failed, falling back to database", e);
    return delegate.getPostFullDetail(postId);
}
```

#### 3. 超时控制

**分布式锁超时**:
```java
// 等待锁最多 5 秒,持有锁最多 30 秒
boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);

if (!acquired) {
    // 超时降级 - 直接查询数据库
    log.warn("Failed to acquire lock within timeout, falling back to database");
    return delegate.getPostFullDetail(postId);
}
```

**CompletableFuture 超时**:
```java
// 建议添加超时控制
CompletableFuture.allOf(postFuture, contentFuture)
    .orTimeout(3, TimeUnit.SECONDS)  // 3秒超时
    .join();
```


#### 4. 异常处理

**分层异常处理**:
```
DualStorageException (领域异常)
    ↓
BusinessException (业务异常)
    ↓
GlobalExceptionHandler (全局异常处理器)
    ↓
统一响应格式
```

**示例**:
```java
try {
    postContentRepository.save(enrichedContent);
} catch (MongoException mongoEx) {
    // 包装为领域异常
    throw new DualStorageException("Failed to save content to MongoDB", mongoEx);
}
```

---

## 改进方案详细对比

### 方案 1: 保持现状 (推荐)

#### 优点
- ✅ 代码简洁,易于理解
- ✅ 性能优秀(并行查询 + 缓存)
- ✅ 可靠性高(熔断降级 + 补偿机制)
- ✅ 维护成本低
- ✅ 团队熟悉当前架构

#### 缺点
- ⚠️ 不符合"纯粹"的 DDD 分层
- ⚠️ 领域接口依赖基础设施类型
- ⚠️ 领域服务实现在 Infrastructure 层

#### 改进建议
1. **文档化架构决策**: 创建 ADR 文档说明设计权衡
2. **添加代码注释**: 在关键类中说明架构决策
3. **更新 README**: 说明采用的是实用主义 DDD 方法


### 方案 2: 引入领域值对象

#### 实现方式

```java
// 1. 定义领域值对象
// domain/model/PostContentVO.java
public class PostContentVO {
    private final String content;
    private final String summary;
    private final String contentType;
    private final List<String> mediaUrls;
    
    // 领域概念,不依赖 MongoDB
    public PostContentVO(String content, String contentType) {
        this.content = Objects.requireNonNull(content);
        this.contentType = contentType;
        this.summary = extractSummary(content);
        this.mediaUrls = extractMediaUrls(content);
    }
    
    private String extractSummary(String content) {
        // 领域逻辑: 提取摘要
        return content.length() > 200 
            ? content.substring(0, 200) + "..." 
            : content;
    }
    
    private List<String> extractMediaUrls(String content) {
        // 领域逻辑: 提取媒体 URL
        // ...
    }
}

// 2. 修改领域服务接口
// domain/service/DualStorageManager.java
public interface DualStorageManager {
    Long createPost(Post post, PostContentVO content);  // 使用领域值对象
    PostDetail getPostFullDetail(Long postId);
}

// 3. 在 Infrastructure 层转换
// infrastructure/service/DualStorageManagerImpl.java
@Override
public Long createPost(Post post, PostContentVO contentVO) {
    // 转换为 MongoDB Document
    PostContent mongoDoc = toMongoDocument(contentVO);
    postContentRepository.save(mongoDoc);
}

private PostContent toMongoDocument(PostContentVO vo) {
    PostContent doc = new PostContent();
    doc.setRaw(vo.getContent());
    doc.setContentType(vo.getContentType());
    doc.setSummary(vo.getSummary());
    doc.setMediaUrls(vo.getMediaUrls());
    return doc;
}
```


#### 优点
- ✅ 解耦 Domain 层和 Infrastructure 层
- ✅ 符合 DDD 依赖倒置原则
- ✅ 领域逻辑集中在值对象中
- ✅ 更容易测试(不依赖 MongoDB)

#### 缺点
- ❌ 需要额外的转换逻辑
- ❌ 增加代码复杂度
- ❌ 性能开销(对象转换)
- ❌ 维护成本增加(两套模型)

#### 适用场景
- 领域值对象包含复杂的业务逻辑
- 需要在多个存储之间切换
- 团队严格遵循 DDD 原则

#### 重构成本
- **代码修改**: 中等(需要修改所有调用点)
- **测试修改**: 高(需要重写大量测试)
- **风险**: 中等(可能引入新 bug)
- **时间估算**: 2-3 周

---

### 方案 3: 将领域服务实现移到 Domain 层

#### 实现方式

```java
// 1. 创建纯领域服务
// domain/service/impl/DualStorageManagerDomainImpl.java
public class DualStorageManagerDomainImpl implements DualStorageManager {
    
    private final PostRepository postRepository;
    private final PostContentRepository postContentRepository;
    
    @Override
    public Long createPost(Post post, PostContent content) {
        // 只包含领域逻辑
        PostStatus originalStatus = post.getStatus();
        boolean shouldPublish = originalStatus == PostStatus.PUBLISHED;
        
        // 委托给 Repository
        postRepository.save(post);
        postContentRepository.save(content);
        
        if (shouldPublish) {
            post.publish();
            postRepository.update(post);
        }
        
        return post.getId();
    }
}

// 2. Infrastructure 层只负责技术实现
// infrastructure/service/DualStorageManagerInfraWrapper.java
@Service
public class DualStorageManagerInfraWrapper implements DualStorageManager {
    
    private final DualStorageManager domainService;
    
    @Override
    @Transactional
    @SentinelResource(...)
    public Long createPost(Post post, PostContent content) {
        // 添加技术实现
        return domainService.createPost(post, content);
    }
}
```


#### 优点
- ✅ 符合 DDD 分层原则
- ✅ 领域逻辑和技术实现分离
- ✅ 更容易单元测试领域逻辑

#### 缺点
- ❌ 增加抽象层,代码复杂度提升
- ❌ 领域逻辑和技术实现紧密耦合,难以分离
- ❌ @Transactional 边界不清晰
- ❌ 性能优化(并行查询)难以实现

#### 问题分析

**问题 1: 事务边界**
```java
// Domain 层
public Long createPost(Post post, PostContent content) {
    postRepository.save(post);  // 需要事务
    postContentRepository.save(content);  // 需要事务
    // 但 @Transactional 在 Infrastructure 层...
}
```

**问题 2: 并行查询**
```java
// Domain 层
public PostDetail getPostFullDetail(Long postId) {
    // 如何实现并行查询?
    // CompletableFuture 是技术实现,不应该在 Domain 层
    Post post = postRepository.findById(postId);
    PostContent content = postContentRepository.findByPostId(postId);
}
```

#### 适用场景
- 领域逻辑非常复杂,需要独立测试
- 技术实现可能频繁变化
- 团队严格遵循 DDD 原则

#### 重构成本
- **代码修改**: 高(需要重新设计架构)
- **测试修改**: 高(需要重写所有测试)
- **风险**: 高(可能破坏现有功能)
- **时间估算**: 4-6 周

---


### 方案对比总结

| 维度 | 方案1: 保持现状 | 方案2: 引入值对象 | 方案3: 移到Domain层 |
|------|----------------|------------------|-------------------|
| **DDD 纯度** | ⚠️ 中等 | ✅ 高 | ✅ 高 |
| **代码复杂度** | ✅ 低 | ⚠️ 中等 | ❌ 高 |
| **维护成本** | ✅ 低 | ⚠️ 中等 | ❌ 高 |
| **性能** | ✅ 优秀 | ⚠️ 良好 | ⚠️ 良好 |
| **测试难度** | ✅ 低 | ⚠️ 中等 | ⚠️ 中等 |
| **重构成本** | ✅ 无 | ⚠️ 2-3周 | ❌ 4-6周 |
| **风险** | ✅ 无 | ⚠️ 中等 | ❌ 高 |
| **适用场景** | ✅ 当前项目 | 复杂领域逻辑 | 严格DDD项目 |

### 推荐方案

**推荐: 方案 1 - 保持现状**

**理由**:
1. **成本收益比**: 重构成本高,收益有限
2. **实用主义**: 当前架构在实践中运行良好
3. **团队熟悉度**: 团队已经熟悉当前架构
4. **业务优先**: 应该专注于业务功能而非架构纯度

**改进措施**:
1. 文档化架构决策(ADR)
2. 添加代码注释说明设计权衡
3. 更新 README 说明架构方法
4. 定期审查架构决策

---

## 最佳实践建议

### 1. 代码组织

#### 包结构规范
```
domain/
  ├── model/           # 聚合根、实体、值对象
  ├── service/         # 领域服务接口
  ├── repository/      # 仓储接口
  ├── event/           # 领域事件
  └── exception/       # 领域异常

infrastructure/
  ├── service/         # 领域服务实现
  ├── repository/      # 仓储实现
  ├── mongodb/         # MongoDB 相关
  ├── cache/           # 缓存相关
  └── mq/              # 消息队列
```


#### 命名规范
- **接口**: `DualStorageManager` (不加 Interface 后缀)
- **实现**: `DualStorageManagerImpl` (基础实现)
- **装饰器**: `CachedDualStorageManager` (功能前缀)
- **Repository**: `PostRepository` (接口), `PostRepositoryImpl` (实现)

### 2. 依赖注入

#### 使用 @Qualifier 明确依赖
```java
@Service
public class CachedDualStorageManager implements DualStorageManager {
    
    public CachedDualStorageManager(
            @Qualifier("dualStorageManagerImpl") DualStorageManager delegate) {
        this.delegate = delegate;
    }
}
```

#### 使用 @Primary 标记主要实现
```java
@Primary  // 优先注入
@Service
public class CachedDualStorageManager implements DualStorageManager {
    // ...
}
```

### 3. 事务管理

#### 事务边界清晰
```java
@Override
@Transactional(rollbackFor = Exception.class)
public Long createPost(Post post, PostContent content) {
    // PostgreSQL 操作在事务中
    postRepository.save(post);
    
    try {
        // MongoDB 操作
        postContentRepository.save(content);
    } catch (Exception e) {
        // 抛出异常,触发 PostgreSQL 回滚
        throw new DualStorageException("MongoDB save failed", e);
    }
}
```

#### 只读事务优化
```java
@Transactional(readOnly = true)
public PostDetail getPostFullDetail(Long postId) {
    // 只读事务,优化性能
}
```


### 4. 异常处理

#### 分层异常设计
```java
// 领域异常
public class DualStorageException extends DomainException {
    public DualStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

// 业务异常
public class BusinessException extends RuntimeException {
    private final ResultCode code;
    
    public BusinessException(ResultCode code, String message) {
        super(message);
        this.code = code;
    }
}
```

#### 异常转换
```java
try {
    postContentRepository.save(content);
} catch (MongoException e) {
    // 基础设施异常 → 领域异常
    throw new DualStorageException("Failed to save content", e);
}
```

### 5. 日志规范

#### 日志级别
- **DEBUG**: 详细的调试信息(缓存命中/未命中)
- **INFO**: 重要的业务操作(创建文章、更新文章)
- **WARN**: 警告信息(降级处理、锁获取失败)
- **ERROR**: 错误信息(数据库异常、Redis 连接失败)

#### 日志内容
```java
// ✅ 好的日志
log.info("Successfully created post: postId={}, userId={}, title={}", 
    postId, userId, post.getTitle());

log.warn("Failed to acquire lock for post: postId={}, falling back to database", 
    postId);

log.error("Failed to save content to MongoDB: postId={}", postId, e);

// ❌ 不好的日志
log.info("Created post");  // 缺少上下文
log.error("Error: " + e.getMessage());  // 丢失堆栈信息
```


### 6. 性能优化

#### 并行查询
```java
// 使用 CompletableFuture 并行查询
CompletableFuture<Post> postFuture = CompletableFuture.supplyAsync(
    () -> postRepository.findById(postId).orElseThrow(),
    executorService
);

CompletableFuture<PostContent> contentFuture = CompletableFuture.supplyAsync(
    () -> postContentRepository.findByPostId(postId).orElseThrow(),
    executorService
);

CompletableFuture.allOf(postFuture, contentFuture).join();
```

#### 缓存策略
```java
// 1. 热点数据使用分布式锁
if (hotDataIdentifier.isHotData("post", postId)) {
    return loadWithLock(postId);
}

// 2. 普通数据直接查询
return loadFromDatabase(postId);

// 3. TTL + 随机抖动防止雪崩
long ttl = baseTtl + ThreadLocalRandom.current().nextInt(0, 60);
```

#### 批量操作
```java
// 批量查询而非循环单个查询
List<Post> posts = postRepository.findByIds(postIds);

// 批量插入而非循环单个插入
postTagRepository.attachBatch(postId, tagIds);
```

### 7. 测试策略

#### 单元测试
```java
@Test
void testCreatePost_Success() {
    // Given
    Post post = Post.createDraft(1L, 1L, "Test Title");
    PostContent content = new PostContent();
    
    when(postRepository.save(any())).thenReturn(post);
    when(postContentRepository.save(any())).thenReturn(content);
    
    // When
    Long postId = dualStorageManager.createPost(post, content);
    
    // Then
    assertNotNull(postId);
    verify(postRepository).save(post);
    verify(postContentRepository).save(content);
}
```

