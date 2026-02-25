# 文章作者信息冗余与版本号控制机制

## 概述

本文档说明文章服务中作者信息冗余的版本号控制机制，用于防止消息乱序和重复更新，保证数据的最终一致性。

**相关 Spec**: `.kiro/specs/ZhiCore-post-author-redundancy/`

---

## 问题背景

### 为什么需要冗余作者信息？

**问题**：当 user-service 不可用时，post-service 无法获取作者信息，导致所有文章显示"未知用户"。

**解决方案**：在 `posts` 表中冗余作者的基本展示信息：
- `owner_name`: 作者昵称快照
- `owner_avatar_id`: 作者头像文件 ID 快照
- `owner_profile_version`: 作者资料版本号

**优势**：
- 读取时不依赖 user-service，极致稳定
- 通过 RocketMQ 事件异步同步，保持最终一致性

---

## 版本号机制

### 为什么需要版本号？

在分布式系统中，消息可能会：
1. **乱序到达**：后发送的消息可能先到达
2. **重复投递**：RocketMQ 保证"至少一次"投递，可能重复发送

**如果没有版本号**，会导致：
- 旧数据覆盖新数据（乱序问题）
- 重复更新浪费资源（重复投递问题）

**版本号机制**通过乐观锁模式解决这些问题。

---

## 工作流程

### 1. 用户修改资料

```java
// User Service
@Transactional
public void updateProfile(Long userId, UpdateProfileRequest request) {
    User user = userRepository.findById(userId).orElseThrow();
    
    // 更新资料（会递增版本号）
    user.updateProfile(request.getNickname(), request.getAvatarId(), request.getBio());
    // profileVersion: 1 → 2
    
    // 保存到数据库
    userRepository.update(user);
    
    // 发送事件（包含版本号）
    eventPublisher.publish(new UserProfileUpdatedEvent(
        userId, 
        user.getNickname(), 
        user.getAvatarId(),
        user.getProfileVersion(),  // version = 2
        LocalDateTime.now()
    ));
}
```

### 2. Post Service 消费事件

```java
// Post Service
@Transactional
public void syncAuthorInfo(UserProfileUpdatedEvent event) {
    // 批量更新该用户的所有文章
    // 关键：使用版本号过滤
    int updatedCount = postRepository.updateAuthorInfo(
        event.getUserId(), 
        event.getNickname(), 
        event.getAvatarId(), 
        event.getVersion()  // version = 2
    );
}
```

### 3. 数据库更新（关键 SQL）

```sql
UPDATE posts 
SET owner_name = :nickname,
    owner_avatar_id = :avatarId,
    owner_profile_version = :version,
    updated_at = now()
WHERE owner_id = :userId 
  AND owner_profile_version < :version  -- 关键：只更新版本号更小的记录
```

**WHERE 条件说明**：
- `owner_id = :userId`：只更新该用户的文章
- `owner_profile_version < :version`：只更新版本号更小的记录

---

## 场景分析

### 场景 1：正常更新

```
初始状态：
- User: nickname="Alice", profileVersion=1
- Post: owner_name="Alice", owner_profile_version=1

用户修改昵称为 "Bob"：
1. User Service: profileVersion++ (1 → 2)
2. 发送事件: {userId, nickname="Bob", version=2}
3. Post Service 收到事件
4. 执行 SQL: WHERE owner_profile_version < 2
5. 更新成功: owner_name="Bob", owner_profile_version=2
```

### 场景 2：消息乱序

```
用户快速修改两次昵称：
1. 第一次：Alice → Bob (version=2)
2. 第二次：Bob → Charlie (version=3)

Post Service 收到顺序（乱序）：
1. 先收到 version=3 (Charlie)
2. 后收到 version=2 (Bob)

处理过程：
1. 收到 version=3:
   - WHERE owner_profile_version < 3 (1 < 3 ✓)
   - 更新成功: owner_name="Charlie", owner_profile_version=3

2. 收到 version=2:
   - WHERE owner_profile_version < 2 (3 < 2 ✗)
   - 更新失败: 跳过，不会用旧数据覆盖新数据

最终结果：owner_name="Charlie" ✓ (正确)
```

### 场景 3：消息重复投递

```
RocketMQ 重复发送同一条消息：
1. 第一次收到 version=2 (Bob)
2. 第二次收到 version=2 (Bob) -- 重复

处理过程：
1. 第一次收到 version=2:
   - WHERE owner_profile_version < 2 (1 < 2 ✓)
   - 更新成功: owner_name="Bob", owner_profile_version=2

2. 第二次收到 version=2:
   - WHERE owner_profile_version < 2 (2 < 2 ✗)
   - 更新失败: 跳过，实现幂等性

最终结果：只更新一次 ✓ (幂等)
```

### 场景 4：消息丢失后补偿

```
假设 version=2 的消息丢失，只收到 version=3：

初始状态：
- Post: owner_name="Alice", owner_profile_version=1

收到 version=3 (Charlie)：
- WHERE owner_profile_version < 3 (1 < 3 ✓)
- 更新成功: owner_name="Charlie", owner_profile_version=3

结果：虽然跳过了 version=2，但最终数据是正确的 ✓
```

---

## 版本号的生命周期

### 1. 初始化

**创建文章时**：
```java
// 从 User Service 获取作者信息
UserSimpleDTO author = userServiceClient.getUsersSimple(userId);

// 填充冗余字段
post.setOwnerName(author.getNickname());
post.setOwnerAvatarId(author.getAvatarId());
post.setOwnerProfileVersion(author.getProfileVersion());  // 初始版本号
```

**如果 User Service 不可用**：
```java
// 使用默认值
post.setOwnerName("未知用户");
post.setOwnerAvatarId(null);
post.setOwnerProfileVersion(0L);  // 版本号为 0
```

### 2. 更新

**用户每次修改资料**：
```java
// User 领域模型
public void updateProfile(String nickName, String avatarId, String bio) {
    // ... 更新字段
    
    // 递增版本号
    this.profileVersion = (this.profileVersion == null ? 0 : this.profileVersion) + 1;
    this.updatedAt = LocalDateTime.now();
}
```

**Post Service 消费事件**：
```sql
-- 只更新版本号更小的记录
UPDATE posts 
SET owner_name = :nickname,
    owner_avatar_id = :avatarId,
    owner_profile_version = :version
WHERE owner_id = :userId 
  AND owner_profile_version < :version
```

### 3. 数据回填

**回填脚本**：
```java
// 从 User Service 批量获取作者信息
Map<Long, UserSimpleDTO> authorMap = getUsersSimple(authorIds);

// 更新每篇文章
for (Post post : posts) {
    UserSimpleDTO author = authorMap.get(post.getOwnerId());
    
    if (author != null) {
        post.setOwnerName(author.getNickname());
        post.setOwnerAvatarId(author.getAvatarId());
        post.setOwnerProfileVersion(author.getProfileVersion());  // 使用当前版本号
    } else {
        post.setOwnerName("未知用户");
        post.setOwnerAvatarId(null);
        post.setOwnerProfileVersion(0L);
    }
    
    postRepository.update(post);
}
```

---

## 数据库 Schema

```sql
-- posts 表新增字段
ALTER TABLE posts 
ADD COLUMN owner_name VARCHAR(50) NOT NULL DEFAULT '',
ADD COLUMN owner_avatar_id VARCHAR(36),
ADD COLUMN owner_profile_version BIGINT NOT NULL DEFAULT 0;

-- 添加注释
COMMENT ON COLUMN posts.owner_name IS '作者昵称快照（冗余字段）';
COMMENT ON COLUMN posts.owner_avatar_id IS '作者头像文件ID快照（冗余字段，UUIDv7格式）';
COMMENT ON COLUMN posts.owner_profile_version IS '作者资料版本号（用于防止消息乱序）';

-- 添加索引（用于批量更新）
CREATE INDEX IF NOT EXISTS idx_posts_owner_id ON posts(owner_id);
```

---

## 监控和告警

### 1. 日志

**正常更新**：
```
INFO: Author info synced to posts: userId=123, version=2, updatedCount=5
```

**版本号冲突（跳过更新）**：
```
INFO: Skipped outdated event: userId=123, eventVersion=2, currentVersion=3
```

**更新失败**：
```
ERROR: Failed to process UserProfileUpdatedEvent: userId=123, version=2
```

### 2. Prometheus 指标

```java
// 事件消费总数
user_profile_event_consumed_total{service="post-service"}

// 事件消费失败总数
user_profile_event_failed_total{service="post-service"}

// 更新的文章总数
user_profile_posts_updated_total{service="post-service"}

// 批量更新执行时间
user_profile_sync_duration{service="post-service"}
```

### 3. 告警规则

```yaml
# 事件消费失败率过高
- alert: HighUserProfileEventFailureRate
  expr: |
    rate(user_profile_event_failed_total[5m]) / 
    rate(user_profile_event_consumed_total[5m]) > 0.1
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "用户资料事件消费失败率过高"
```

---

## 常见问题

### Q1: 如果版本号溢出怎么办？

**A**: BIGINT 类型的最大值是 9,223,372,036,854,775,807，即使每秒更新一次，也需要 292 亿年才会溢出。实际上不会发生溢出。

### Q2: 如果用户删除账号怎么办？

**A**: 用户删除账号时，可以：
1. 发送 `user-deleted` 事件
2. Post Service 消费事件，将 `owner_name` 更新为 "已注销用户"
3. 或者保持原样，显示用户注销前的昵称

### Q3: 如果消息堆积怎么办？

**A**: 
1. 增加消费者并发线程数
2. 检查数据库性能，优化批量更新 SQL
3. 如果用户拥有大量文章，考虑分批更新

### Q4: 如何验证数据一致性？

**A**: 可以定期运行一致性检查脚本：
```java
// 对比 posts 表的冗余字段与 users 表的实际数据
List<Post> posts = postRepository.findAll();
for (Post post : posts) {
    User user = userRepository.findById(post.getOwnerId());
    if (!post.getOwnerName().equals(user.getNickname())) {
        log.warn("Inconsistent data: postId={}, expected={}, actual={}", 
            post.getId(), user.getNickname(), post.getOwnerName());
    }
}
```

---

## 总结

版本号机制的核心优势：
1. **防止乱序**：旧版本的事件不会覆盖新版本的数据
2. **实现幂等性**：重复的事件不会重复更新
3. **保证最终一致性**：即使消息乱序或重复，最终数据都是正确的
4. **简单高效**：只需一个 BIGINT 字段和一个 WHERE 条件

这是分布式系统中常用的**乐观锁**模式，广泛应用于：
- 数据库并发控制
- 分布式缓存更新
- 事件驱动架构
- 微服务数据同步

---

**最后更新**：2026-02-18  
**维护者**：开发团队  
**相关文档**：
- [Spec 需求文档](../.kiro/specs/ZhiCore-post-author-redundancy/requirements.md)
- [Spec 设计文档](../.kiro/specs/ZhiCore-post-author-redundancy/design.md)
- [RocketMQ 消息顺序性保证](./rocketmq-message-ordering.md)
- [分布式系统最终一致性](./eventual-consistency.md)
