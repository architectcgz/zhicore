# ZhiCore-Post 服务架构需求要点包

**文档版本**: 1.0  
**创建日期**: 2026-02-19  
**目的**: 明确架构设计的关键决策点

---

## 1. 服务边界与外部依赖

### 1.1 外部服务调用清单

#### 同步调用（Feign）
```
ZhiCore-user (用户服务)
├─ 场景: 创建文章时获取作者信息
├─ 接口: GET /api/v1/users/{userId}/profile
├─ 降级: 使用默认值"未知用户"，后续通过MQ补偿
└─ 超时: 3秒

ZhiCore-upload (上传服务)
├─ 场景: 验证封面图/内容图片是否存在
├─ 接口: GET /api/v1/files/{fileId}/exists
├─ 降级: 跳过验证，允许引用不存在的文件
└─ 超时: 2秒

ZhiCore-id-generator (ID生成服务)
├─ 场景: 创建文章时生成雪花ID
├─ 接口: GET /api/v1/id/snowflake
├─ 降级: 使用本地UUID（不推荐）
└─ 超时: 1秒
```

#### 异步调用（RocketMQ）
```
ZhiCore-search (搜索服务)
├─ 消费事件: PostCreated, PostUpdated, PostDeleted
├─ 用途: 更新ES索引
└─ 可靠性: 至少一次，幂等消费

ZhiCore-notification (通知服务)
├─ 消费事件: PostCreated, PostPublished
├─ 用途: 通知关注者
└─ 可靠性: 至少一次，允许重复通知

ZhiCore-ranking (排行服务)
├─ 消费事件: PostCreated, PostLiked, PostViewed
├─ 用途: 更新热门榜单
└─ 可靠性: 至少一次，最终一致

ZhiCore-comment (评论服务)
├─ 消费事件: PostDeleted
├─ 用途: 级联删除评论
└─ 可靠性: 至少一次，幂等删除
```


### 1.2 数据冗余策略

#### 愿意冗余（提升性能）
```
作者信息（Post 聚合内）
├─ ownerName: 作者昵称
├─ ownerAvatarId: 作者头像文件ID
├─ ownerProfileVersion: 版本号（防乱序）
├─ 更新方式: 消费 UserProfileUpdated 事件
├─ 一致性: 最终一致（延迟1-5秒可接受）
└─ 补偿: 定时任务回填默认值数据

统计数据（PostStats 值对象）
├─ likeCount: 点赞数
├─ commentCount: 评论数
├─ favoriteCount: 收藏数
├─ viewCount: 浏览量
├─ 更新方式: 消费各服务事件 + 定时同步
├─ 一致性: 最终一致（延迟5-10秒可接受）
└─ 真相来源: 各自服务（like/comment/favorite/ranking）
```

#### 必须实时查询（强一致性）
```
评论详情
├─ 原因: 评论内容变化频繁，冗余成本高
└─ 方式: 前端调用 ZhiCore-comment 服务

用户详细资料
├─ 原因: 资料字段多，冗余维护成本高
└─ 方式: 前端调用 ZhiCore-user 服务

点赞状态（当前用户是否点赞）
├─ 原因: 个性化数据，无法冗余
└─ 方式: 前端调用 ZhiCore-like 服务
```

---

## 2. 数据源与"真相来源"

### 2.1 PostgreSQL vs MongoDB 职责划分

#### PostgreSQL（主数据源 - Source of Truth）
```
存储内容
├─ 文章元数据（posts 表）
│   ├─ id, owner_id, title, excerpt
│   ├─ cover_image_id, status, topic_id
│   ├─ published_at, scheduled_at
│   ├─ created_at, updated_at
│   ├─ owner_name, owner_avatar_id, owner_profile_version
│   └─ is_archived
├─ 文章统计（post_stats 表）
│   ├─ post_id, like_count, comment_count
│   ├─ favorite_count, view_count
│   └─ updated_at
├─ 标签（tags 表）
│   └─ id, name, slug, description
└─ 文章-标签关联（post_tags 表）
    └─ post_id, tag_id

查询场景
├─ 文章列表（按时间/热度/作者/标签）
├─ 统计查询（总数/分组统计）
├─ 关系查询（标签关联/作者文章）
└─ 事务操作（创建/更新/删除）

索引策略
├─ 主键: id
├─ 外键: owner_id, topic_id
├─ 查询: (status, published_at), (owner_id, status)
└─ 全文: title (GIN索引)
```


#### MongoDB（内容存储 - Content Store）
```
存储内容
├─ 文章内容（post_content 集合）
│   ├─ _id, post_id
│   ├─ content_type (markdown/html/rich-text)
│   ├─ raw (原始内容)
│   ├─ html (渲染后HTML)
│   ├─ text (纯文本，用于搜索)
│   ├─ word_count, reading_time
│   ├─ media_urls (图片/视频URL列表)
│   ├─ created_at, updated_at
│   └─ version (乐观锁)
└─ 草稿（post_drafts 集合）
    ├─ _id, post_id, user_id
    ├─ content, is_auto_save
    ├─ created_at, updated_at
    └─ expires_at (TTL索引)

查询场景
├─ 文章详情（根据post_id查询完整内容）
├─ 草稿管理（自动保存/手动保存）
└─ 内容搜索（全文检索，配合ES）

索引策略
├─ 主键: _id
├─ 唯一: post_id
├─ 查询: (post_id, user_id) for drafts
└─ TTL: expires_at for drafts
```

### 2.2 写入顺序与一致性保证

#### 创建文章（三阶段提交）
```
阶段1: PostgreSQL Insert (DRAFT)
├─ 写入 posts 表
├─ 写入 post_stats 表
├─ 事务: @Transactional
└─ 失败: 整体回滚

阶段2: MongoDB Insert
├─ 写入 post_content 集合
├─ 内容增强: 计算字数/阅读时间/提取媒体
├─ 失败: 触发 PostgreSQL 回滚
└─ 孤立数据: 定时任务清理

阶段3: PostgreSQL Update (PUBLISHED)
├─ 更新 status = PUBLISHED
├─ 更新 published_at
├─ 失败: 触发 PostgreSQL 回滚
└─ 孤立数据: MongoDB 数据需要补偿删除

一致性窗口
├─ 正常: < 100ms
├─ 异常: 孤立数据存在时间 < 5分钟（定时任务清理）
└─ 可接受: 短暂不一致，最终一致
```


#### 更新文章（双写）
```
阶段1: PostgreSQL Update
├─ 更新 posts 表（title, excerpt, cover_image_id）
├─ 事务: @Transactional
└─ 失败: 整体回滚

阶段2: MongoDB Update
├─ 更新 post_content 集合（content, html, text）
├─ 乐观锁: version 字段
├─ 失败: 触发 PostgreSQL 回滚
└─ 冲突: 返回错误，要求用户重试

阶段3: 缓存失效
├─ 删除 Redis 缓存
├─ 失败: 记录日志，允许继续（缓存会过期）
└─ 一致性: 最终一致（TTL 5分钟）
```

#### 删除文章（软删除 + 异步清理）
```
阶段1: PostgreSQL Soft Delete
├─ 更新 status = DELETED
├─ 事务: @Transactional
└─ 失败: 整体回滚

阶段2: 发布 PostDeleted 事件
├─ RocketMQ 异步发送
├─ 消费者: search/comment/notification
└─ 可靠性: 至少一次

阶段3: 定时任务硬删除（可选）
├─ 延迟: 30天后
├─ 清理: PostgreSQL + MongoDB
└─ 备份: 归档到对象存储
```

### 2.3 失败补偿策略

#### 补偿机制
```
定时任务（每5分钟）
├─ 清理孤立的 MongoDB 数据
│   └─ 查询 MongoDB 中 post_id 在 PostgreSQL 不存在的记录
├─ 回填默认作者信息
│   └─ 查询 owner_name = "未知用户" 的记录，调用 user-service 补偿
└─ 同步统计数据
    └─ 从各服务查询最新统计，更新 post_stats 表

消息重试（RocketMQ）
├─ 最大重试: 16次
├─ 重试间隔: 指数退避（1s, 5s, 10s, 30s, 1m, 2m, 3m, 4m, 5m, 6m, 7m, 8m, 9m, 10m, 20m, 30m）
└─ 死信队列: 人工介入处理

对账任务（每天凌晨）
├─ PostgreSQL vs MongoDB 数据一致性检查
├─ 统计数据准确性检查
└─ 生成对账报告，发送告警
```


### 2.4 并发控制

#### 乐观锁（MongoDB）
```
场景: 多人同时编辑同一篇文章
机制: version 字段
流程:
├─ 读取: 获取当前 version
├─ 更新: WHERE version = old_version, SET version = old_version + 1
├─ 冲突: 更新失败，返回 409 Conflict
└─ 重试: 客户端重新读取并重试
```

#### 分布式锁（Redis + Redisson）
```
场景: 热点文章缓存击穿
机制: RLock 分布式锁
流程:
├─ 尝试获取锁: tryLock(5s, 30s)
├─ DCL 双重检查: 获取锁后再次检查缓存
├─ 查询数据库: 只有一个线程查询
├─ 写入缓存: 更新缓存
└─ 释放锁: unlock()

超时降级:
├─ 获取锁超时: 直接查询数据库（不写缓存）
└─ 持有锁超时: 自动释放（防止死锁）
```

---

## 3. 非功能目标

### 3.1 性能目标

#### QPS 目标（按接口）
```
文章详情 GET /api/v1/posts/{id}
├─ 目标 QPS: 10,000
├─ 峰值 QPS: 50,000
├─ 缓存命中率: > 95%
└─ P99 延迟: < 50ms (缓存命中), < 200ms (缓存未命中)

文章列表 GET /api/v1/posts
├─ 目标 QPS: 5,000
├─ 峰值 QPS: 20,000
├─ 缓存命中率: > 90%
└─ P99 延迟: < 100ms (缓存命中), < 300ms (缓存未命中)

创建文章 POST /api/v1/posts
├─ 目标 QPS: 100
├─ 峰值 QPS: 500
└─ P99 延迟: < 500ms

更新文章 PUT /api/v1/posts/{id}
├─ 目标 QPS: 200
├─ 峰值 QPS: 1,000
└─ P99 延迟: < 300ms

点赞/浏览量更新（异步）
├─ 目标 QPS: 50,000
├─ 峰值 QPS: 200,000
├─ 处理方式: 消息队列批量更新
└─ 延迟: 1-5秒可接受
```


### 3.2 延迟目标

#### 延迟分布（P50/P95/P99）
```
文章详情（缓存命中）
├─ P50: < 10ms
├─ P95: < 20ms
└─ P99: < 50ms

文章详情（缓存未命中）
├─ P50: < 80ms
├─ P95: < 150ms
└─ P99: < 200ms

文章列表（缓存命中）
├─ P50: < 20ms
├─ P95: < 50ms
└─ P99: < 100ms

文章列表（缓存未命中）
├─ P50: < 150ms
├─ P95: < 250ms
└─ P99: < 300ms

写操作（创建/更新）
├─ P50: < 200ms
├─ P95: < 400ms
└─ P99: < 500ms
```

### 3.3 一致性窗口

#### 可接受的不一致延迟
```
作者信息冗余
├─ 延迟: 1-5秒
├─ 场景: 用户修改昵称/头像
└─ 影响: 文章列表显示旧昵称/头像

统计数据（点赞/评论/收藏）
├─ 延迟: 5-10秒
├─ 场景: 用户点赞/评论/收藏
└─ 影响: 统计数字延迟更新

浏览量
├─ 延迟: 10-30秒
├─ 场景: 用户浏览文章
└─ 影响: 浏览量延迟增加（批量更新）

搜索索引
├─ 延迟: 30-60秒
├─ 场景: 创建/更新/删除文章
└─ 影响: 搜索结果延迟更新
```

### 3.4 可用性要求

#### 降级策略
```
Redis 缓存挂了
├─ 降级: 直接查询数据库
├─ 性能: P99 延迟 200ms → 500ms
├─ 限流: 开启 Sentinel 流控
└─ 告警: 立即通知运维

PostgreSQL 主库挂了
├─ 降级: 切换到从库（只读）
├─ 影响: 写操作失败，返回 503
├─ 恢复: 自动故障转移（< 30秒）
└─ 告警: 立即通知运维

MongoDB 挂了
├─ 降级: 只返回文章元数据（无内容）
├─ 影响: 文章详情无法查看完整内容
├─ 恢复: 手动重启（< 5分钟）
└─ 告警: 立即通知运维

RocketMQ 挂了
├─ 降级: 事件发送失败，记录本地日志
├─ 影响: 异步功能延迟（搜索/通知/统计）
├─ 补偿: 定时任务扫描日志重发
└─ 告警: 立即通知运维

User Service 挂了
├─ 降级: 使用默认值"未知用户"
├─ 影响: 新创建文章作者信息为默认值
├─ 补偿: 定时任务回填
└─ 告警: 通知运维
```


---

## 4. 事件与消息

### 4.1 领域事件清单

#### PostCreated（文章创建事件）
```
触发时机: 文章创建成功后
事件内容: 全量数据
├─ postId, title, content, excerpt
├─ authorId, authorName
├─ tagIds, categoryId, categoryName
├─ status, publishedAt, createdAt
└─ eventTime, eventId

消费者:
├─ ZhiCore-search: 创建ES索引
├─ ZhiCore-notification: 通知关注者（如果已发布）
├─ ZhiCore-ranking: 初始化排行数据
└─ ZhiCore-stats: 初始化统计数据

可靠性:
├─ 发送: 至少一次（RocketMQ 重试）
├─ 消费: 幂等（根据 postId 去重）
└─ 顺序: 不保证（使用版本号防乱序）
```

#### PostPublished（文章发布事件）
```
触发时机: 草稿发布 或 定时发布执行
事件内容: 最小数据
├─ postId, title, excerpt
├─ authorId, authorName
├─ publishedAt, eventTime
└─ eventId

消费者:
├─ ZhiCore-notification: 通知关注者
├─ ZhiCore-feed: 推送到关注者Feed流
└─ ZhiCore-ranking: 加入热门榜单

可靠性:
├─ 发送: 至少一次
├─ 消费: 幂等（根据 postId + publishedAt 去重）
└─ 顺序: 不保证
```

#### PostUpdated（文章更新事件）
```
触发时机: 文章内容/元数据更新
事件内容: 增量数据
├─ postId, title, excerpt
├─ updatedFields: [title, content, tags]
├─ updatedAt, eventTime
└─ eventId

消费者:
├─ ZhiCore-search: 更新ES索引
└─ ZhiCore-cache: 失效缓存

可靠性:
├─ 发送: 至少一次
├─ 消费: 幂等（根据 postId + updatedAt 去重）
└─ 顺序: 不保证（使用版本号防乱序）
```


#### PostDeleted（文章删除事件）
```
触发时机: 文章软删除
事件内容: 最小数据
├─ postId, authorId
├─ deletedAt, eventTime
└─ eventId

消费者:
├─ ZhiCore-search: 删除ES索引
├─ ZhiCore-comment: 级联删除评论
├─ ZhiCore-notification: 删除相关通知
└─ ZhiCore-cache: 失效缓存

可靠性:
├─ 发送: 至少一次
├─ 消费: 幂等（根据 postId 去重，重复删除无影响）
└─ 顺序: 不保证
```

#### PostTagsUpdated（标签更新事件）
```
触发时机: 文章标签变更
事件内容: 增量数据
├─ postId
├─ oldTagIds: [1, 2, 3]
├─ newTagIds: [2, 3, 4]
├─ updatedAt, eventTime
└─ eventId

消费者:
├─ ZhiCore-search: 更新ES索引标签字段
└─ ZhiCore-stats: 更新标签统计

可靠性:
├─ 发送: 至少一次
├─ 消费: 幂等（根据 postId + updatedAt 去重）
└─ 顺序: 不保证
```

#### PostLiked / PostUnliked（点赞事件）
```
触发时机: 用户点赞/取消点赞
事件来源: ZhiCore-like 服务
事件内容:
├─ postId, userId
├─ action: LIKE / UNLIKE
├─ eventTime, eventId
└─ likeCount (当前总数)

消费者:
├─ ZhiCore-post: 更新 post_stats.like_count
└─ ZhiCore-ranking: 更新热门榜单

可靠性:
├─ 发送: 至少一次
├─ 消费: 幂等（使用 likeCount 覆盖，而非增量）
└─ 顺序: 不保证（使用最新 likeCount）
```


#### PostCommented / CommentDeleted（评论事件）
```
触发时机: 评论创建/删除
事件来源: ZhiCore-comment 服务
事件内容:
├─ postId, commentId, userId
├─ action: CREATED / DELETED
├─ eventTime, eventId
└─ commentCount (当前总数)

消费者:
├─ ZhiCore-post: 更新 post_stats.comment_count
└─ ZhiCore-notification: 通知文章作者

可靠性:
├─ 发送: 至少一次
├─ 消费: 幂等（使用 commentCount 覆盖）
└─ 顺序: 不保证
```

#### PostViewed（浏览事件）
```
触发时机: 用户浏览文章
事件来源: ZhiCore-post 服务（异步记录）
事件内容:
├─ postId, userId (可选)
├─ viewTime, eventTime
└─ eventId

消费者:
├─ ZhiCore-ranking: 更新热门榜单
└─ ZhiCore-stats: 批量更新浏览量（每10秒一批）

可靠性:
├─ 发送: 至少一次
├─ 消费: 允许丢失（浏览量不要求100%准确）
└─ 顺序: 不保证
```

#### UserProfileUpdated（用户资料更新事件）
```
触发时机: 用户修改昵称/头像
事件来源: ZhiCore-user 服务
事件内容:
├─ userId, nickname, avatarId
├─ profileVersion (版本号)
├─ updatedAt, eventTime
└─ eventId

消费者:
├─ ZhiCore-post: 更新 posts.owner_name, owner_avatar_id
└─ 其他服务: 更新各自的用户信息冗余

可靠性:
├─ 发送: 至少一次
├─ 消费: 幂等（使用 profileVersion 防乱序）
└─ 顺序: 严格保证（使用版本号）
```

### 4.2 消息可靠性要求

#### 至少一次（At-Least-Once）
```
适用场景: 所有事件
保证机制:
├─ 生产者: RocketMQ 同步发送 + 重试
├─ 消费者: 手动ACK + 重试
└─ 幂等: 消费者根据 eventId 去重

实现方式:
├─ 生产者: sendMessageInTransaction()
├─ 消费者: @RocketMQMessageListener(consumeMode = CONCURRENTLY)
└─ 去重表: consumed_events (eventId, consumedAt)
```


#### 严格幂等（Idempotent）
```
适用场景: 统计更新、状态变更
保证机制:
├─ 使用最终值而非增量（likeCount 而非 +1）
├─ 使用版本号防止乱序（profileVersion）
└─ 使用唯一键防止重复（postId + eventId）

实现方式:
├─ 统计: UPDATE SET count = ? WHERE post_id = ?
├─ 版本: UPDATE SET ... WHERE version < ?
└─ 去重: INSERT IGNORE / ON CONFLICT DO NOTHING
```

#### 不乱序（Ordered）
```
适用场景: 用户资料更新
保证机制:
├─ 使用版本号: profileVersion
├─ 只接受更新版本: WHERE version < new_version
└─ 拒绝旧版本: 直接丢弃

实现方式:
public boolean updateOwnerInfo(String name, String avatar, Long version) {
    if (this.ownerProfileVersion != null && version <= this.ownerProfileVersion) {
        return false; // 拒绝旧版本
    }
    this.ownerName = name;
    this.ownerAvatarId = avatar;
    this.ownerProfileVersion = version;
    return true;
}
```

---

## 5. 查询模型

### 5.1 查询场景清单

#### 文章列表查询
```
按时间排序（最新）
├─ SQL: ORDER BY published_at DESC
├─ 索引: (status, published_at)
├─ 缓存: Redis List (前100条)
└─ TTL: 5分钟

按热度排序（热门）
├─ SQL: ORDER BY (like_count * 0.5 + comment_count * 0.3 + view_count * 0.0001) DESC
├─ 索引: 复合索引 (status, like_count, comment_count, view_count)
├─ 缓存: Redis ZSet (score = 热度值)
└─ TTL: 10分钟

按作者查询
├─ SQL: WHERE owner_id = ? ORDER BY published_at DESC
├─ 索引: (owner_id, status, published_at)
├─ 缓存: Redis List (每个作者前50条)
└─ TTL: 10分钟

按标签查询
├─ SQL: JOIN post_tags WHERE tag_id = ? ORDER BY published_at DESC
├─ 索引: post_tags(tag_id, post_id)
├─ 缓存: Redis List (每个标签前100条)
└─ TTL: 10分钟

关注Feed流
├─ 来源: ZhiCore-feed 服务（独立服务）
├─ 实现: 推拉结合（活跃用户推，非活跃用户拉）
└─ 缓存: Redis ZSet (score = 发布时间戳)
```


#### 全文搜索
```
关键词搜索
├─ 实现: Elasticsearch（未来）
├─ 索引字段: title, content, tags
├─ 分词: IK Analyzer (中文)
└─ 高亮: 搜索结果高亮显示

当前方案（PostgreSQL）
├─ SQL: WHERE title ILIKE '%keyword%'
├─ 索引: GIN索引 (title)
├─ 限制: 性能较差，仅支持简单搜索
└─ 迁移: 逐步迁移到 ES
```

#### 运营能力
```
置顶文章
├─ 字段: is_pinned (Boolean)
├─ 排序: ORDER BY is_pinned DESC, published_at DESC
├─ 索引: (status, is_pinned, published_at)
└─ 缓存: 单独缓存置顶列表

推荐文章
├─ 来源: ZhiCore-recommendation 服务（算法推荐）
├─ 实现: 协同过滤 + 内容推荐
└─ 缓存: Redis List (个性化推荐)

精选文章
├─ 字段: is_featured (Boolean)
├─ 管理: 运营后台手动标记
├─ 展示: 首页精选区域
└─ 缓存: Redis List (前20条)
```

### 5.2 CQRS 策略

#### 当前方案（PG 直接查 + 缓存）
```
优点:
├─ 实现简单，维护成本低
├─ 数据一致性好
└─ 适合中小规模

缺点:
├─ 复杂查询性能差
├─ 全文搜索能力弱
└─ 扩展性受限

适用场景:
├─ QPS < 10,000
├─ 数据量 < 1000万
└─ 查询模式简单
```

#### 未来方案（CQRS + 读模型）
```
写模型（Command）
├─ PostgreSQL + MongoDB
├─ 强一致性
└─ 事务保证

读模型（Query）
├─ Elasticsearch (全文搜索)
├─ Redis (热点数据)
├─ PostgreSQL 从库 (复杂查询)
└─ 最终一致性

同步方式:
├─ 实时: RocketMQ 事件驱动
├─ 延迟: 1-5秒
└─ 补偿: 定时任务对账

触发条件:
├─ QPS > 50,000
├─ 数据量 > 5000万
└─ 复杂查询需求增加
```


---

## 6. 权限与状态机

### 6.1 文章状态机

#### 状态定义
```
DRAFT (草稿)
├─ 初始状态
├─ 可编辑
└─ 不可见（仅作者可见）

PUBLISHED (已发布)
├─ 公开可见
├─ 可编辑（更新后仍为已发布）
└─ 可撤回到草稿

SCHEDULED (定时发布)
├─ 设置了发布时间
├─ 不可见（仅作者可见）
└─ 到时间后自动发布

DELETED (已删除)
├─ 软删除
├─ 不可见（仅管理员可见）
└─ 30天后硬删除
```

#### 状态转换规则
```
DRAFT → PUBLISHED
├─ 操作: publish()
├─ 条件: 标题不为空
└─ 权限: 作者

DRAFT → SCHEDULED
├─ 操作: schedulePublish(time)
├─ 条件: 标题不为空 && time > now
└─ 权限: 作者

SCHEDULED → PUBLISHED
├─ 操作: executeScheduledPublish()
├─ 条件: 到达定时时间
└─ 权限: 系统定时任务

SCHEDULED → DRAFT
├─ 操作: cancelSchedule()
├─ 条件: 无
└─ 权限: 作者

PUBLISHED → DRAFT
├─ 操作: unpublish()
├─ 条件: 无
└─ 权限: 作者

ANY → DELETED
├─ 操作: delete()
├─ 条件: 无
└─ 权限: 作者 或 管理员

禁止的转换:
├─ DELETED → ANY (已删除不可恢复)
├─ PUBLISHED → SCHEDULED (已发布不可改为定时)
└─ SCHEDULED → SCHEDULED (不可重复设置定时)
```


### 6.2 权限控制

#### 角色定义
```
游客（未登录）
├─ 查看已发布文章
├─ 查看文章列表
└─ 无法操作

普通用户（已登录）
├─ 创建文章（草稿）
├─ 编辑自己的文章
├─ 发布自己的文章
├─ 删除自己的文章
└─ 查看所有已发布文章

作者（文章所有者）
├─ 所有普通用户权限
├─ 查看自己的草稿
├─ 定时发布
└─ 撤回发布

管理员
├─ 查看所有文章（包括草稿/已删除）
├─ 编辑任何文章
├─ 删除任何文章
├─ 恢复已删除文章
└─ 置顶/精选文章

黑名单用户
├─ 禁止创建文章
├─ 禁止编辑文章
├─ 禁止发布文章
└─ 可查看已发布文章
```

#### 权限检查逻辑
```java
// 检查是否可以编辑
public boolean canEdit(Long userId, String userRole) {
    if ("ADMIN".equals(userRole)) {
        return true; // 管理员可以编辑任何文章
    }
    if (this.status == PostStatus.DELETED) {
        return false; // 已删除文章不可编辑
    }
    return this.ownerId.equals(userId); // 作者可以编辑自己的文章
}

// 检查是否可以查看
public boolean canView(Long userId, String userRole) {
    if ("ADMIN".equals(userRole)) {
        return true; // 管理员可以查看任何文章
    }
    if (this.status == PostStatus.PUBLISHED) {
        return true; // 已发布文章所有人可见
    }
    if (this.status == PostStatus.DELETED) {
        return false; // 已删除文章不可见
    }
    return this.ownerId.equals(userId); // 草稿/定时发布仅作者可见
}

// 检查是否可以删除
public boolean canDelete(Long userId, String userRole) {
    if ("ADMIN".equals(userRole)) {
        return true; // 管理员可以删除任何文章
    }
    return this.ownerId.equals(userId); // 作者可以删除自己的文章
}
```


### 6.3 审核流程（可选）

#### 审核状态（未来扩展）
```
PENDING_REVIEW (待审核)
├─ 用户提交发布后进入
├─ 不可见（仅作者和审核员可见）
└─ 等待审核

APPROVED (审核通过)
├─ 审核员批准
├─ 自动转为 PUBLISHED
└─ 发送通知

REJECTED (审核拒绝)
├─ 审核员拒绝
├─ 转为 DRAFT
├─ 附带拒绝原因
└─ 发送通知

审核规则:
├─ 敏感词检测（自动）
├─ 内容质量检查（人工）
├─ 版权检查（人工）
└─ 时效: 24小时内完成
```

### 6.4 回收站（软删除）

#### 回收站机制
```
删除操作
├─ 软删除: status = DELETED
├─ 保留数据: PostgreSQL + MongoDB
├─ 不可见: 列表/搜索不显示
└─ 可恢复: 30天内

恢复操作
├─ 权限: 作者 或 管理员
├─ 操作: restore()
├─ 状态: DELETED → DRAFT
└─ 通知: 发送恢复通知

硬删除（定时任务）
├─ 触发: 删除后30天
├─ 操作: 物理删除数据
├─ 备份: 归档到对象存储
└─ 不可恢复
```

---

## 7. 迁移约束

### 7.1 数据库结构变更

#### 允许的变更
```
添加字段
├─ 方式: ALTER TABLE ADD COLUMN
├─ 默认值: 必须提供
├─ 兼容性: 向后兼容
└─ 示例: ALTER TABLE posts ADD COLUMN is_pinned BOOLEAN DEFAULT FALSE

添加索引
├─ 方式: CREATE INDEX CONCURRENTLY
├─ 影响: 不锁表
├─ 时间: 根据数据量（可能几分钟到几小时）
└─ 示例: CREATE INDEX CONCURRENTLY idx_posts_pinned ON posts(status, is_pinned, published_at)

修改字段类型（扩展）
├─ 方式: ALTER TABLE ALTER COLUMN TYPE
├─ 限制: 只能扩展（VARCHAR(100) → VARCHAR(200)）
├─ 影响: 可能锁表
└─ 示例: ALTER TABLE posts ALTER COLUMN title TYPE VARCHAR(300)
```


#### 禁止的变更
```
删除字段
├─ 原因: 破坏向后兼容性
├─ 替代: 标记为废弃，保留字段
└─ 清理: 等待所有服务升级后再删除

修改字段类型（收缩）
├─ 原因: 可能导致数据丢失
├─ 示例: VARCHAR(200) → VARCHAR(100)
└─ 替代: 创建新字段，迁移数据

重命名字段
├─ 原因: 破坏向后兼容性
├─ 替代: 创建新字段，同步数据，废弃旧字段
└─ 清理: 等待所有服务升级后再删除旧字段

删除索引
├─ 原因: 可能影响查询性能
├─ 评估: 确认没有查询使用该索引
└─ 监控: 删除后监控慢查询
```

### 7.2 停机策略

#### 零停机部署（推荐）
```
蓝绿部署
├─ 部署新版本到绿环境
├─ 健康检查通过后切换流量
├─ 保留蓝环境用于快速回滚
└─ 停机时间: 0秒

滚动更新
├─ 逐个实例更新
├─ 每次更新1-2个实例
├─ 等待健康检查通过后继续
└─ 停机时间: 0秒

数据库迁移
├─ 使用 Flyway/Liquibase
├─ 向后兼容的 SQL 脚本
├─ 在应用启动前执行
└─ 停机时间: 0秒（如果脚本兼容）
```

#### 计划停机（不推荐）
```
维护窗口
├─ 时间: 凌晨2-4点（低峰期）
├─ 通知: 提前24小时通知用户
├─ 时长: < 30分钟
└─ 回滚: 准备回滚方案

适用场景:
├─ 大规模数据迁移
├─ 数据库结构重大变更
└─ 基础设施升级
```


### 7.3 接口兼容性

#### API 版本策略
```
当前版本: v1
├─ 路径: /api/v1/posts
├─ 兼容性: 向后兼容
└─ 废弃: 提前6个月通知

版本升级
├─ 新版本: /api/v2/posts
├─ 并存: v1 和 v2 同时提供
├─ 迁移期: 6个月
└─ 下线: v1 在迁移期后下线

兼容性规则:
├─ 可以添加新字段（响应）
├─ 可以添加可选参数（请求）
├─ 不能删除字段
├─ 不能修改字段类型
└─ 不能修改字段语义
```

#### 事件兼容性
```
事件版本
├─ 字段: eventVersion (v1, v2)
├─ 消费者: 根据版本处理
└─ 兼容: 新旧版本同时支持

添加字段
├─ 允许: 添加新字段
├─ 默认值: 旧消费者忽略新字段
└─ 示例: PostCreatedEvent 添加 categoryId

修改字段
├─ 禁止: 修改现有字段语义
├─ 替代: 添加新字段，废弃旧字段
└─ 示例: authorId (Long) → authorUuid (String)
```

### 7.4 数据迁移

#### 在线迁移（推荐）
```
双写策略
├─ 阶段1: 新旧字段同时写入
├─ 阶段2: 后台任务回填历史数据
├─ 阶段3: 切换读取新字段
├─ 阶段4: 停止写入旧字段
└─ 阶段5: 删除旧字段

示例: owner_id (Long) → owner_uuid (String)
├─ 添加 owner_uuid 字段
├─ 创建/更新时同时写入 owner_id 和 owner_uuid
├─ 后台任务: UPDATE posts SET owner_uuid = uuid WHERE owner_uuid IS NULL
├─ 查询切换: WHERE owner_uuid = ? 替代 WHERE owner_id = ?
├─ 停止写入 owner_id
└─ 删除 owner_id 字段（6个月后）
```


#### 离线迁移（不推荐）
```
停机迁移
├─ 停止服务
├─ 执行迁移脚本
├─ 验证数据
├─ 启动服务
└─ 时长: 根据数据量

适用场景:
├─ 数据量小（< 100万）
├─ 迁移逻辑复杂
└─ 无法实现在线迁移

风险:
├─ 停机时间长
├─ 用户体验差
└─ 回滚困难
```

#### 回填任务
```
定时任务
├─ 触发: Cron 表达式（每小时）
├─ 批量: 每次处理1000条
├─ 条件: WHERE field IS NULL OR field = default_value
├─ 幂等: 可重复执行
└─ 监控: 记录处理进度

示例: 回填作者信息
@Scheduled(cron = "0 0 * * * ?") // 每小时执行
public void backfillAuthorInfo() {
    List<Post> posts = postRepository.findByOwnerName("未知用户");
    for (Post post : posts) {
        try {
            UserProfile profile = userService.getProfile(post.getOwnerId());
            post.setOwnerInfo(profile.getName(), profile.getAvatarId(), profile.getVersion());
            postRepository.update(post);
        } catch (Exception e) {
            log.error("Failed to backfill author info for post: {}", post.getId(), e);
        }
    }
}
```

---

## 8. 总结与建议

### 8.1 架构决策总结

#### 已确定的设计
```
✅ DualStorage 模式（PG + MongoDB）
├─ PG: 元数据 + 关系 + 统计
├─ MongoDB: 内容 + 草稿
└─ 一致性: 三阶段提交 + 补偿

✅ 装饰器模式（缓存层）
├─ 基础实现: DualStorageManagerImpl
├─ 缓存装饰器: CachedDualStorageManager
└─ 职责分离: 业务逻辑 vs 横切关注点

✅ 事件驱动（异步解耦）
├─ RocketMQ: 领域事件发布
├─ 消费者: search/notification/ranking/stats
└─ 可靠性: 至少一次 + 幂等

✅ 数据冗余（性能优化）
├─ 作者信息: ownerName, ownerAvatarId
├─ 统计数据: likeCount, commentCount, viewCount
└─ 一致性: 最终一致（1-10秒延迟）
```


#### 待优化的设计
```
⚠️ 领域服务实现位置
├─ 当前: Infrastructure 层
├─ 问题: 不符合纯粹 DDD
├─ 建议: 保持现状，文档化决策
└─ 原因: 重构成本高，收益有限

⚠️ 领域接口依赖基础设施类型
├─ 当前: PostContent (MongoDB Document)
├─ 问题: 违反依赖倒置原则
├─ 建议: 保持现状，考虑引入值对象
└─ 原因: PostContent 是纯数据容器

⚠️ 全文搜索能力
├─ 当前: PostgreSQL ILIKE
├─ 问题: 性能差，功能弱
├─ 建议: 迁移到 Elasticsearch
└─ 触发: QPS > 10,000 或 数据量 > 1000万
```

### 8.2 性能优化建议

#### 短期优化（3个月内）
```
1. 缓存优化
├─ 增加缓存预热（启动时加载热点数据）
├─ 优化缓存 Key 设计（减少内存占用）
└─ 实现缓存分级（L1: 本地缓存, L2: Redis）

2. 查询优化
├─ 添加复合索引（覆盖常用查询）
├─ 优化 SQL（避免 N+1 查询）
└─ 实现查询结果缓存

3. 并发优化
├─ 增加线程池大小（根据压测结果）
├─ 优化锁粒度（减少锁竞争）
└─ 实现批量操作（减少数据库往返）
```

#### 中期优化（6个月内）
```
1. 读写分离
├─ PostgreSQL 主从复制
├─ 读操作路由到从库
└─ 写操作路由到主库

2. 分库分表
├─ 按 owner_id 分片（用户维度）
├─ 或按 created_at 分片（时间维度）
└─ 使用 ShardingSphere

3. 引入 Elasticsearch
├─ 全文搜索迁移到 ES
├─ 复杂查询迁移到 ES
└─ 实时同步（RocketMQ）
```


#### 长期优化（12个月内）
```
1. CQRS 架构
├─ 写模型: PostgreSQL + MongoDB
├─ 读模型: ES + Redis + PG从库
└─ 同步: RocketMQ 事件驱动

2. 微服务拆分
├─ ZhiCore-post-write: 写操作
├─ ZhiCore-post-read: 读操作
├─ ZhiCore-post-stats: 统计服务
└─ ZhiCore-post-search: 搜索服务

3. 多级缓存
├─ L1: 本地缓存（Caffeine）
├─ L2: Redis 集群
└─ L3: CDN（静态资源）
```

### 8.3 监控指标

#### 关键指标
```
性能指标
├─ QPS: 每秒请求数
├─ 延迟: P50/P95/P99
├─ 错误率: 4xx/5xx 比例
└─ 可用性: SLA 99.9%

业务指标
├─ 文章创建数: 每日/每小时
├─ 文章浏览量: 每日/每小时
├─ 缓存命中率: > 95%
└─ 数据一致性: 不一致数据比例 < 0.1%

资源指标
├─ CPU 使用率: < 70%
├─ 内存使用率: < 80%
├─ 数据库连接数: < 80%
└─ 消息队列积压: < 1000
```

#### 告警规则
```
P0 告警（立即处理）
├─ 服务不可用（5xx > 10%）
├─ 数据库连接失败
├─ 消息队列积压 > 10000
└─ 缓存雪崩

P1 告警（1小时内处理）
├─ P99 延迟 > 1秒
├─ 错误率 > 5%
├─ 缓存命中率 < 80%
└─ 数据不一致 > 1%

P2 告警（24小时内处理）
├─ P95 延迟 > 500ms
├─ CPU 使用率 > 80%
├─ 内存使用率 > 90%
└─ 慢查询 > 100ms
```

---

## 9. 附录

### 9.1 术语表

```
DDD: Domain-Driven Design（领域驱动设计）
CQRS: Command Query Responsibility Segregation（命令查询职责分离）
PG: PostgreSQL
ES: Elasticsearch
MQ: Message Queue（消息队列）
TTL: Time To Live（生存时间）
DCL: Double-Checked Locking（双重检查锁）
QPS: Queries Per Second（每秒查询数）
SLA: Service Level Agreement（服务等级协议）
```

### 9.2 参考文档

```
内部文档
├─ ARCHITECTURE-DEEP-DIVE.md: 架构深度分析
├─ ADR-001-dual-storage-manager-placement.md: 架构决策记录
└─ ARCHITECTURE-ANALYSIS.md: 架构分析（简版）

外部资源
├─ DDD 实践指南: https://domain-driven-design.org/
├─ CQRS 模式: https://martinfowler.com/bliki/CQRS.html
└─ 微服务架构: https://microservices.io/
```

---

**文档维护**:
- 最后更新: 2026-02-19
- 维护者: ZhiCore Team
- 审查频率: 每季度或架构变更时

