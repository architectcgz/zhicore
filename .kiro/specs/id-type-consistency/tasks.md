# Implementation Plan: ID Type Consistency Migration

## Overview

将博客微服务系统中所有 ID 类型从 String/VARCHAR(64) 迁移到 Long/BIGINT。这是一个全栈迁移项目，涉及数据库 Schema、持久化层、领域层、应用层和接口层。

由于当前处于开发阶段，可以直接修改数据库结构，无需保留现有数据。

## Tasks

- [x] 1. 移除废弃的 Upload 服务
  - 删除 `blog-upload` 模块目录
  - 从根 `pom.xml` 移除 upload 模块引用
  - 从 `docker/docker-compose.services.yml` 移除 upload 服务配置
  - 删除 `blog-migration/src/main/resources/db/migration/upload/` 目录
  - _Requirements: 1.1, 1.2, 1.4_

- [-] 2. 修改数据库 Schema（User 服务）
  - [x] 2.1 修改 User 服务迁移脚本
    - 修改 `blog-migration/src/main/resources/db/migration/user/V1__create_user_tables.sql`
    - 将 `users.id` 从 VARCHAR(64) 改为 BIGINT
    - **移除 `user_follows.id` 字段**，使用复合主键 `(follower_id, following_id)`
    - **移除 `user_blocks.id` 字段**，使用复合主键 `(blocker_id, blocked_id)`
    - **移除 `user_check_ins.id` 字段**，使用复合主键 `(user_id, check_in_date)`
    - 将 `user_follow_stats.user_id` 改为 BIGINT
    - 将 `user_check_in_stats.user_id` 改为 BIGINT
    - _Requirements: 2.1, 2.2, 3.1-3.6_

- [x] 3. 修改数据库 Schema（Post 服务）
  - [x] 3.1 修改 Post 服务迁移脚本
    - 修改 `blog-migration/src/main/resources/db/migration/post/V1__create_post_tables.sql`
    - 将 `posts` 表的 id, owner_id, topic_id 改为 BIGINT
    - **移除 `post_likes.id` 字段**，使用复合主键 `(post_id, user_id)`
    - **移除 `post_favorites.id` 字段**，使用复合主键 `(post_id, user_id)`
    - 将 `post_stats.post_id` 改为 BIGINT
    - _Requirements: 2.1, 2.2, 4.1-4.5_

- [x] 4. 修改数据库 Schema（Comment 服务）
  - [x] 4.1 修改 Comment 服务迁移脚本
    - 修改 `blog-migration/src/main/resources/db/migration/comment/V1__create_comment_tables.sql`
    - 将 `comments` 表的所有 ID 字段改为 BIGINT（id, post_id, author_id, parent_id, root_id, reply_to_user_id）
    - **移除 `comment_likes.id` 字段**，使用复合主键 `(comment_id, user_id)`
    - 将 `comment_stats.comment_id` 改为 BIGINT
    - _Requirements: 2.1, 2.2, 5.1-5.2_

- [x] 5. 修改数据库 Schema（Message 服务）
  - [x] 5.1 修改 Message 服务迁移脚本
    - 修改 `blog-migration/src/main/resources/db/migration/message/V1__create_message_tables.sql`
    - 将 `conversations` 表的所有 ID 字段改为 BIGINT
    - 将 `messages` 表的所有 ID 字段改为 BIGINT
    - _Requirements: 2.1, 2.2, 6.1-6.2_



- [x] 6. 修改数据库 Schema（Notification 服务）
  - [x] 6.1 修改 Notification 服务迁移脚本
    - 修改 `blog-migration/src/main/resources/db/migration/notification/V1__create_notification_tables.sql`
    - 将 `notifications` 表的 id, recipient_id 改为 BIGINT
    - 将 `global_announcements.id` 改为 BIGINT
    - 将 `assistant_messages` 表的 id, user_id 改为 BIGINT
    - _Requirements: 2.1, 2.2, 7.1-7.3_

- [x] 7. 修改数据库 Schema（Content 服务）
  - [x] 7.1 修改 Content 服务迁移脚本
    - 修改 `blog-migration/src/main/resources/db/migration/content/V1__create_content_tables.sql`
    - 将 `topics.id` 改为 BIGINT
    - 将 `reports` 表的 id, target_id 改为 BIGINT
    - 将 `feedbacks.id` 改为 BIGINT
    - _Requirements: 2.1, 2.2, 8.1-8.3_

- [x] 8. 修改数据库 Schema（Admin 服务）
  - [x] 8.1 修改 Admin 服务迁移脚本
    - 修改 `blog-migration/src/main/resources/db/migration/admin/V1__create_admin_tables.sql`
    - 将 `audit_logs` 表的 id, operator_id 改为 BIGINT
    - 将 `reports` 表的所有 ID 字段改为 BIGINT
    - _Requirements: 2.1, 2.2, 9.1-9.2_

- [x] 9. Checkpoint - 验证数据库 Schema 修改
  - 停止所有服务：`docker-compose down`
  - 删除数据库卷：`docker volume rm blog-postgres-data`
  - 重新启动数据库：`docker-compose up -d postgres`
  - 验证所有表的 ID 字段都是 BIGINT 类型
  - 确保所有索引和约束正确创建

- [x] 10. 修改 User 服务 PO 层
  - [x] 10.1 修改 UserPO 类
    - 将 `blog-user/src/main/java/com/blog/user/infrastructure/repository/po/UserPO.java` 的 id 字段从 String 改为 Long
    - _Requirements: 10.1_
  
  - [x] 10.2 修改 UserFollowPO 类
    - **移除 `id` 字段**，使用复合主键 `(followerId, followingId)`
    - 将 followerId, followingId 从 String 改为 Long
    - _Requirements: 10.2_
  
  - [x] 10.3 修改 UserBlockPO 类
    - **移除 `id` 字段**，使用复合主键 `(blockerId, blockedId)`
    - 将 blockerId, blockedId 从 String 改为 Long
    - _Requirements: 10.2_
  
  - [x] 10.4 修改 UserCheckInPO 类
    - **移除 `id` 字段**，使用复合主键 `(userId, checkInDate)`
    - 将 userId 从 String 改为 Long
    - _Requirements: 10.2_

- [x] 11. 修改 Post 服务 PO 层
  - [x] 11.1 修改 PostPO 类
    - 将 `blog-post/src/main/java/com/blog/post/infrastructure/repository/po/PostPO.java` 的 id, ownerId, topicId 从 String 改为 Long
    - _Requirements: 10.1, 10.2_
  
  - [x] 11.2 修改 PostLikePO 类
    - **移除 `id` 字段**，使用复合主键 `(postId, userId)`
    - 将 postId, userId 从 String 改为 Long
    - _Requirements: 10.2_
  
  - [x] 11.3 修改 PostFavoritePO 类
    - **移除 `id` 字段**，使用复合主键 `(postId, userId)`
    - 将 postId, userId 从 String 改为 Long
    - _Requirements: 10.2_
  
  - [x] 11.4 修改 PostStatsPO 类
    - 将 postId 字段从 String 改为 Long
    - _Requirements: 10.1_

- [x] 12. 修改 Comment 服务 PO 层
  - [x] 12.1 修改 CommentPO 类
    - 将 `blog-comment/src/main/java/com/blog/comment/infrastructure/repository/po/CommentPO.java` 的所有 ID 字段从 String 改为 Long
    - 包括：id, postId, authorId, parentId, rootId, replyToUserId
    - _Requirements: 10.1, 10.2_
  
  - [x] 12.2 修改 CommentLikePO 类
    - **移除 `id` 字段**，使用复合主键 `(commentId, userId)`
    - 将 commentId, userId 从 String 改为 Long
    - _Requirements: 10.2_
  
  - [x] 12.3 修改 CommentStatsPO 类
    - 将 commentId 字段从 String 改为 Long
    - _Requirements: 10.1_



- [x] 13. 修改其他服务 PO 层
  - [x] 13.1 修改 Message 服务 PO 层
    - 修改 Conversation 和 Message 相关 PO 类的 ID 字段
    - _Requirements: 10.1, 10.2_
  
  - [x] 13.2 修改 Notification 服务 PO 层
    - 修改 Notification 相关 PO 类的 ID 字段
    - _Requirements: 10.1, 10.2_
  
  - [x] 13.3 修改 Admin 服务 PO 层
    - 修改 AuditLog 和 Report 相关 PO 类的 ID 字段
    - _Requirements: 10.1, 10.2_

- [x] 14. 修改 User 服务 Domain 层
  - [x] 14.1 修改 User 聚合根
    - 将 `blog-user/src/main/java/com/blog/user/domain/model/User.java` 的 id 字段从 String 改为 Long
    - 更新构造函数参数类型
    - 更新 @JsonCreator 构造函数参数类型
    - 更新 ID 验证逻辑（Assert.notNull + Assert.isTrue(id > 0)）
    - _Requirements: 11.1, 11.4_
  
  - [x] 14.2 修改 UserFollow 实体
    - 将所有 ID 字段从 String 改为 Long
    - _Requirements: 11.2_
  
  - [x] 14.3 修改 UserBlock 实体
    - 将所有 ID 字段从 String 改为 Long
    - _Requirements: 11.2_
  
  - [x] 14.4 修改 UserCheckIn 实体
    - 将所有 ID 字段从 String 改为 Long
    - _Requirements: 11.2_

- [x] 15. 修改 Post 服务 Domain 层
  - [x] 15.1 修改 Post 聚合根
    - 将 `blog-post/src/main/java/com/blog/post/domain/model/Post.java` 的 id, ownerId, topicId 从 String 改为 Long
    - 更新构造函数和 @JsonCreator
    - 更新 ID 验证逻辑
    - _Requirements: 11.1, 11.2, 11.4_
  
  - [x] 15.2 修改 PostLike 和 PostFavorite 实体
    - 将所有 ID 字段从 String 改为 Long
    - _Requirements: 11.2_
  
  - [x] 15.3 修改 PostStats 值对象
    - 如果包含 ID 引用，更新为 Long
    - _Requirements: 11.2_

- [x] 16. 修改 Comment 服务 Domain 层
  - [x] 16.1 修改 Comment 聚合根
    - 将 `blog-comment/src/main/java/com/blog/comment/domain/model/Comment.java` 的所有 ID 字段从 String 改为 Long
    - 更新构造函数和 @JsonCreator
    - 更新 ID 验证逻辑
    - _Requirements: 11.1, 11.2, 11.4_
  
  - [x] 16.2 修改 CommentLike 实体
    - 将所有 ID 字段从 String 改为 Long
    - _Requirements: 11.2_

- [x] 17. 修改其他服务 Domain 层
  - [x] 17.1 修改 Message 服务 Domain 层
    - 修改 Conversation 和 Message 聚合根的 ID 字段
    - _Requirements: 11.1, 11.2, 11.4_
  
  - [x] 17.2 修改 Notification 服务 Domain 层
    - 修改 Notification 聚合根的 ID 字段
    - _Requirements: 11.1, 11.2, 11.4_
  
  - [x] 17.3 修改 Admin 服务 Domain 层
    - 修改 AuditLog 和 Report 领域模型的 ID 字段
    - _Requirements: 11.1, 11.2, 11.4_



- [x] 18. 修改 User 服务 Application 层
  - [x] 18.1 修改 UserVO 和 DTOs
    - 将 `blog-user/src/main/java/com/blog/user/application/dto/UserVO.java` 的 id 字段从 String 改为 Long
    - 修改所有相关 DTO 类的 ID 字段
    - _Requirements: 12.1, 12.2_
  
  - [x] 18.2 修改 UserApplicationService
    - 移除 `String.valueOf(id)` 转换
    - 直接使用 Long 类型 ID
    - 更新方法返回类型从 String 改为 Long
    - _Requirements: 14.1_
  
  - [x] 18.3 修改 FollowApplicationService
    - 移除 ID 类型转换
    - 更新方法签名
    - _Requirements: 14.1_
  
  - [x] 18.4 修改 CheckInApplicationService
    - 移除 ID 类型转换
    - 更新方法签名
    - _Requirements: 14.1_

- [x] 19. 修改 Post 服务 Application 层
  - [x] 19.1 修改 PostVO 和 PostBriefVO
    - 将所有 ID 字段从 String 改为 Long
    - _Requirements: 12.1, 12.2_
  
  - [x] 19.2 修改 PostApplicationService
    - 移除 `String.valueOf(id)` 转换
    - 更新方法返回类型
    - _Requirements: 14.1_
  
  - [x] 19.3 修改 PostLikeApplicationService
    - 移除 ID 类型转换
    - _Requirements: 14.1_
  
  - [x] 19.4 修改 PostFavoriteApplicationService
    - 移除 ID 类型转换
    - _Requirements: 14.1_

- [x] 20. 修改 Comment 服务 Application 层
  - [x] 20.1 修改 CommentVO 和 DTOs
    - 将所有 ID 字段从 String 改为 Long
    - _Requirements: 12.1, 12.2_
  
  - [x] 20.2 修改 CommentApplicationService
    - 移除 ID 类型转换
    - 更新方法签名
    - _Requirements: 14.1_

- [x] 21. 修改其他服务 Application 层
  - [x] 21.1 修改 Message 服务 Application 层
    - 修改 MessageVO 和相关 DTOs
    - 修改 MessageApplicationService
    - _Requirements: 12.1, 12.2, 14.1_
  
  - [x] 21.2 修改 Notification 服务 Application 层
    - 修改 NotificationVO 和相关 DTOs
    - 修改 NotificationApplicationService
    - _Requirements: 12.1, 12.2, 14.1_
  
  - [x] 21.3 修改 Admin 服务 Application 层
    - 修改相关 VOs 和 DTOs
    - 修改 Application Services
    - _Requirements: 12.1, 12.2, 14.1_

- [x] 22. 修改 User 服务 Interface 层
  - [x] 22.1 修改 UserController
    - 将 `@PathVariable String id` 改为 `@PathVariable Long id`
    - 将返回类型中的 String ID 改为 Long
    - 添加 ID 验证注解：`@Min(value = 1, message = "用户ID必须为正数")`
    - _Requirements: 13.1, 13.2, 13.3, 13.5_
  
  - [x] 22.2 修改 Request/Response 对象
    - 修改所有请求和响应对象中的 ID 字段类型
    - _Requirements: 13.2, 13.3_

- [x] 23. 修改 Post 服务 Interface 层
  - [x] 23.1 修改 PostController
    - 更新所有路径参数和请求体中的 ID 类型
    - 添加 ID 验证注解
    - _Requirements: 13.1, 13.2, 13.3, 13.5_
  
  - [x] 23.2 修改 Request/Response 对象
    - 修改所有 ID 字段类型
    - _Requirements: 13.2, 13.3_



- [x] 24. 修改 Comment 服务 Interface 层
  - [x] 24.1 修改 CommentController
    - 更新所有路径参数和请求体中的 ID 类型
    - 添加 ID 验证注解
    - _Requirements: 13.1, 13.2, 13.3, 13.5_
  
  - [x] 24.2 修改 Request/Response 对象
    - 修改所有 ID 字段类型
    - _Requirements: 13.2, 13.3_

- [x] 25. 修改其他服务 Interface 层
  - [x] 25.1 修改 Message 服务 Interface 层
    - 修改 MessageController 和相关对象
    - _Requirements: 13.1, 13.2, 13.3, 13.5_
  
  - [x] 25.2 修改 Notification 服务 Interface 层
    - 修改 NotificationController 和相关对象
    - _Requirements: 13.1, 13.2, 13.3, 13.5_
  
  - [x] 25.3 修改 Admin 服务 Interface 层
    - 修改 AdminController 和相关对象
    - _Requirements: 13.1, 13.2, 13.3, 13.5_

- [x] 26. 修改 blog-api 共享模块
  - [x] 26.1 修改共享 DTOs
    - 修改 `blog-api/src/main/java/com/blog/api/dto/` 下的所有 DTO 类
    - 将 UserDTO, PostDTO, UserSimpleDTO 等的 ID 字段从 String 改为 Long
    - _Requirements: 12.1, 12.2_

- [x] 27. Checkpoint - 编译验证
  - 编译整个项目：`mvn clean compile`
  - 修复所有编译错误
  - 确保所有模块都能成功编译

- [x] 28. 修复单元测试
  - [x] 28.1 修复 User 服务单元测试
    - 更新测试数据中的 ID 类型
    - 修复 Mock 对象的 ID 类型
    - _Requirements: 17.1_
  
  - [x] 28.2 修复 Post 服务单元测试
    - 更新测试数据中的 ID 类型
    - _Requirements: 17.1_
  
  - [x] 28.3 修复 Comment 服务单元测试
    - 更新测试数据中的 ID 类型
    - _Requirements: 17.1_
  
  - [x] 28.4 修复其他服务单元测试
    - 修复 Message, Notification, Admin 服务的单元测试
    - 已修复文件：
      - `blog-message/src/test/java/com/blog/message/domain/model/MessageTest.java`
      - `blog-message/src/test/java/com/blog/message/domain/model/ConversationTest.java`
      - `blog-notification/src/test/java/com/blog/notification/domain/model/NotificationTest.java`
      - `blog-notification/src/test/java/com/blog/notification/application/service/NotificationAggregationServiceTest.java`
      - `blog-admin/src/test/java/com/blog/admin/domain/model/ReportTest.java`
      - `blog-admin/src/test/java/com/blog/admin/domain/model/AuditLogTest.java`
    - _Requirements: 17.1_

- [ ]* 29. 编写 PO 层集成测试
  - [ ]* 29.1 编写 MyBatis-Plus CRUD 测试
    - **Property 6: MyBatis-Plus CRUD Round Trip**
    - **Validates: Requirements 10.3**
    - 测试保存和检索 Long ID 的正确性
  
  - [ ]* 29.2 编写 Schema 验证测试
    - **Property 1: Schema Migration Completeness**
    - **Validates: Requirements 2.1, 2.2**
    - 查询 information_schema 验证所有 ID 列都是 BIGINT

- [ ]* 30. 编写 Domain 层属性测试
  - [ ]* 30.1 编写 Domain ID 验证测试
    - **Property 4: Domain Layer Type Consistency**
    - **Validates: Requirements 11.1, 11.2, 11.4**
    - 测试 Domain 构造函数拒绝 null/负数 ID
  
  - [ ]* 30.2 编写 PO-Domain 转换测试
    - **Property 5: PO-Domain Conversion Round Trip**
    - **Validates: Requirements 11.3**
    - 测试 PO ↔ Domain 转换保持 ID 值不变



- [ ]* 31. 编写 Application 层属性测试
  - [ ]* 31.1 编写 JSON 序列化测试
    - **Property 7: JSON Serialization Format**
    - **Validates: Requirements 12.3**
    - 测试 Long ID 序列化为 JSON 数字
  
  - [ ]* 31.2 编写 JSON 反序列化测试
    - **Property 8: JSON Deserialization Acceptance**
    - **Validates: Requirements 12.4**
    - 测试 JSON 数字反序列化为 Long ID
  
  - [ ]* 31.3 编写 ID 生成器测试
    - **Property 13: Generated ID Positivity**
    - **Validates: Requirements 14.2**
    - 测试生成的 ID 都是正数

- [ ]* 32. 编写 Interface 层 API 测试
  - [ ]* 32.1 编写路径参数测试
    - **Property 9: API Path Parameter Conversion**
    - **Validates: Requirements 13.1**
    - 测试 Spring 正确转换路径参数为 Long
  
  - [ ]* 32.2 编写响应格式测试
    - **Property 10: API Response ID Format**
    - **Validates: Requirements 13.3**
    - 测试 API 响应中 ID 是数字格式
  
  - [ ]* 32.3 编写 ID 验证测试
    - **Property 11: ID Validation Rejection**
    - **Validates: Requirements 13.4, 13.5**
    - 测试无效 ID（负数、零、非数字）返回 400

- [ ] 33. 运行完整测试套件
  - 运行所有单元测试：`mvn test`
  - 运行所有集成测试：`mvn verify`
  - 确保所有测试通过
  - 修复任何失败的测试

- [ ] 34. 手动测试主要功能
  - [ ] 34.1 测试用户注册和登录
    - 验证返回的用户 ID 是数字
    - 验证 JWT Token 中的用户 ID
  
  - [ ] 34.2 测试文章 CRUD
    - 创建文章，验证返回的文章 ID
    - 获取文章详情，验证 ID 格式
    - 更新和删除文章
  
  - [ ] 34.3 测试评论功能
    - 创建评论，验证 ID 格式
    - 获取评论列表，验证所有 ID 都是数字
  
  - [ ] 34.4 测试点赞和收藏
    - 点赞文章/评论
    - 验证 API 响应格式

- [ ] 35. 更新 API 文档
  - [-] 35.1 更新 OpenAPI/Swagger 规范
    - 将所有 ID 字段的类型从 `string` 改为 `integer` (format: int64)
    - _Requirements: 18.1_
  
  - [ ] 35.2 创建迁移指南
    - 创建 `docs/migration/id-type-migration.md`
    - 说明 API 变更
    - 提供前端迁移示例
    - _Requirements: 16.5, 18.4_
  
  - [ ] 35.3 更新 CHANGELOG
    - 添加 Breaking Changes 说明
    - 记录性能改进
    - _Requirements: 18.5_

- [ ] 36. 最终验证
  - 启动所有服务
  - 验证服务间通信正常
  - 检查日志无错误
  - 验证 API Gateway 路由正常

## Notes

- 标记 `*` 的任务为可选测试任务，可以根据时间安排决定是否实施
- 每个属性测试应运行至少 100 次迭代
- 建议使用 jqwik 框架进行属性测试
- 所有代码修改前建议创建 Git 分支：`git checkout -b feature/id-type-migration`
- 数据库重建命令：`docker-compose down && docker volume rm blog-postgres-data && docker-compose up -d postgres`

