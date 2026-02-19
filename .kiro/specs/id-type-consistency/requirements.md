# Requirements Document

## Introduction

本需求文档旨在解决博客微服务系统中 ID 类型不一致的问题。当前系统存在以下问题：
1. 数据库 schema 中所有表使用 `VARCHAR(64)` 作为 ID 类型，但应该使用 `BIGINT`
2. Java 代码层面（PO、Domain、Application、Interface）都使用 `String` 类型，但应该使用 `Long`
3. Upload 服务已废弃（已接入外部 file-service），需要移除
4. 需要将整个系统的 ID 类型从 String/VARCHAR 统一迁移到 Long/BIGINT

## Glossary

- **ID**: 实体的唯一标识符，在本系统中使用分布式 ID 生成器（Leaf）生成 64 位 Long 类型
- **PO (Persistent Object)**: 持久化对象，对应数据库表结构
- **Domain Model**: 领域模型，业务逻辑的核心对象
- **DTO (Data Transfer Object)**: 数据传输对象，用于不同层之间的数据传递
- **VO (Value Object)**: 值对象，用于展示层的数据封装
- **Schema**: 数据库表结构定义
- **Leaf**: 美团开源的分布式 ID 生成器，生成 64 位 Long 类型 ID
- **MyBatis-Plus**: ORM 框架，用于数据库操作
- **BIGINT**: PostgreSQL 64 位整数类型，范围 -9223372036854775808 到 9223372036854775807

## Requirements

### Requirement 1: 移除废弃的 Upload 服务

**User Story:** 作为系统架构师，我希望移除已废弃的 Upload 服务，因为系统已接入外部 file-service。

#### Acceptance Criteria

1. THE System SHALL remove the `blog-upload` module from the project
2. THE System SHALL remove upload service database migration scripts
3. THE System SHALL remove upload service configuration from Nacos
4. THE System SHALL remove upload service from Docker Compose files
5. THE System SHALL update documentation to reflect upload service removal

### Requirement 2: 数据库 Schema ID 类型迁移

**User Story:** 作为系统架构师，我希望所有数据库表的 ID 字段使用 BIGINT 类型，以便与 Leaf ID 生成器的 Long 类型保持一致。

#### Acceptance Criteria

1. THE System SHALL change all primary key ID columns from VARCHAR(64) to BIGINT across all database tables
2. THE System SHALL change all foreign key ID columns (user_id, post_id, comment_id, etc.) from VARCHAR(64) to BIGINT
3. WHEN a migration script is executed, THE System SHALL convert existing VARCHAR IDs to BIGINT without data loss
4. THE System SHALL maintain all indexes and constraints after schema migration
5. THE System SHALL maintain referential integrity after schema migration

### Requirement 3: User 服务 Schema 迁移和优化

**User Story:** 作为开发人员，我希望 User 服务的所有 ID 字段迁移到 BIGINT 类型，并移除冗余的 ID 字段。

#### Acceptance Criteria

1. THE User_Service SHALL change `users.id` from VARCHAR(64) to BIGINT
2. THE User_Service SHALL remove redundant `id` column from `user_follows` table and use composite primary key `(follower_id, following_id)`
3. THE User_Service SHALL remove redundant `id` column from `user_blocks` table and use composite primary key `(blocker_id, blocked_id)`
4. THE User_Service SHALL remove redundant `id` column from `user_check_ins` table and use composite primary key `(user_id, check_in_date)`
5. THE User_Service SHALL change `user_follow_stats.user_id` to BIGINT
6. THE User_Service SHALL change `user_check_in_stats.user_id` to BIGINT

### Requirement 4: Post 服务 Schema 迁移和优化

**User Story:** 作为开发人员，我希望 Post 服务的所有 ID 字段迁移到 BIGINT 类型，并移除冗余的 ID 字段。

#### Acceptance Criteria

1. THE Post_Service SHALL change `posts.id`, `posts.owner_id` to BIGINT
2. THE Post_Service SHALL remove redundant `id` column from `post_likes` table and use composite primary key `(post_id, user_id)`
3. THE Post_Service SHALL remove redundant `id` column from `post_favorites` table and use composite primary key `(post_id, user_id)`
4. THE Post_Service SHALL change `categories.id` to BIGINT
5. THE Post_Service SHALL change `topics.id` to BIGINT

### Requirement 5: Comment 服务 Schema 迁移和优化

**User Story:** 作为开发人员，我希望 Comment 服务的所有 ID 字段迁移到 BIGINT 类型，并移除冗余的 ID 字段。

#### Acceptance Criteria

1. THE Comment_Service SHALL change `comments.id`, `comments.post_id`, `comments.author_id`, `comments.parent_id` to BIGINT
2. THE Comment_Service SHALL remove redundant `id` column from `comment_likes` table and use composite primary key `(comment_id, user_id)`

### Requirement 6: Message 服务 Schema 迁移

**User Story:** 作为开发人员，我希望 Message 服务的所有 ID 字段迁移到 BIGINT 类型。

#### Acceptance Criteria

1. THE Message_Service SHALL change `conversations.id`, `conversations.participant1_id`, `conversations.participant2_id` to BIGINT
2. THE Message_Service SHALL change `messages.id`, `messages.conversation_id`, `messages.sender_id` to BIGINT

### Requirement 7: Notification 服务 Schema 迁移

**User Story:** 作为开发人员，我希望 Notification 服务的所有 ID 字段迁移到 BIGINT 类型。

#### Acceptance Criteria

1. THE Notification_Service SHALL change `notifications.id`, `notifications.recipient_id` to BIGINT
2. THE Notification_Service SHALL change `global_announcements.id` to BIGINT
3. THE Notification_Service SHALL change `assistant_messages.id`, `assistant_messages.user_id` to BIGINT

### Requirement 8: Content 服务 Schema 迁移

**User Story:** 作为开发人员，我希望 Content 服务的所有 ID 字段迁移到 BIGINT 类型。

#### Acceptance Criteria

1. THE Content_Service SHALL change `topics.id` to BIGINT
2. THE Content_Service SHALL change `reports.id`, `reports.target_id` to BIGINT
3. THE Content_Service SHALL change `feedbacks.id` to BIGINT

### Requirement 9: Admin 服务 Schema 迁移

**User Story:** 作为开发人员，我希望 Admin 服务的所有 ID 字段迁移到 BIGINT 类型。

#### Acceptance Criteria

1. THE Admin_Service SHALL change `audit_logs.id`, `audit_logs.operator_id` to BIGINT
2. THE Admin_Service SHALL change `reports.id`, `reports.reporter_id`, `reports.reported_user_id` to BIGINT

### Requirement 10: PO 层类型迁移

**User Story:** 作为开发人员，我希望所有 PO 类的 ID 字段使用 Long 类型，与数据库 BIGINT 类型对应。

#### Acceptance Criteria

1. THE System SHALL change all ID fields in PO classes from String to Long
2. THE System SHALL change all foreign key fields (userId, postId, commentId, etc.) in PO classes from String to Long
3. WHEN MyBatis-Plus performs CRUD operations, THE System SHALL correctly map Long IDs to BIGINT columns
4. THE System SHALL use `@TableId(type = IdType.INPUT)` annotation for all primary key fields
5. THE System SHALL handle null ID values appropriately in PO classes

### Requirement 11: Domain 层类型迁移

**User Story:** 作为开发人员，我希望领域模型的 ID 字段类型从 String 迁移到 Long。

#### Acceptance Criteria

1. THE System SHALL change all ID fields in Domain Model classes from String to Long
2. THE System SHALL change all foreign key fields in Domain Model classes from String to Long
3. WHEN converting between PO and Domain Model, THE System SHALL maintain ID value integrity
4. THE System SHALL validate ID values (non-null, positive) in Domain Model constructors
5. THE System SHALL use Long type for ID parameters in domain service methods

### Requirement 12: Application 层 DTO/VO 类型迁移

**User Story:** 作为开发人员，我希望应用层的 DTO 和 VO 对象使用 Long 类型的 ID。

#### Acceptance Criteria

1. THE System SHALL change all ID fields in DTO classes from String to Long
2. THE System SHALL change all ID fields in VO classes from String to Long
3. WHEN serializing to JSON, THE System SHALL represent IDs as numbers
4. WHEN deserializing from JSON, THE System SHALL accept numeric IDs
5. THE System SHALL handle null ID values in JSON serialization/deserialization

### Requirement 13: Interface 层类型迁移

**User Story:** 作为 API 使用者，我希望所有 API 接口的 ID 参数和响应使用 Long 类型。

#### Acceptance Criteria

1. THE System SHALL accept Long IDs in all REST API path parameters
2. THE System SHALL accept Long IDs in all REST API request bodies
3. THE System SHALL return Long IDs in all REST API responses
4. WHEN an invalid ID format is provided, THE System SHALL return a 400 Bad Request error with descriptive message
5. THE System SHALL validate ID values (positive numbers) in controller methods

### Requirement 14: ID 生成器集成

**User Story:** 作为开发人员，我希望 Leaf ID 生成器返回的 Long 类型 ID 可以直接使用，无需类型转换。

#### Acceptance Criteria

1. WHEN the Leaf ID Generator generates a Long ID, THE System SHALL use it directly without conversion
2. THE System SHALL validate that generated IDs are positive Long values
3. THE System SHALL handle ID generation failures gracefully
4. THE System SHALL log ID generation operations for debugging

### Requirement 15: 数据迁移安全性

**User Story:** 作为系统管理员，我希望数据迁移过程安全可靠，不会丢失或损坏现有数据。

#### Acceptance Criteria

1. WHEN a migration script is executed, THE System SHALL validate that all existing VARCHAR IDs are numeric
2. THE System SHALL convert numeric string IDs to BIGINT values
3. IF any non-numeric ID is found, THEN THE System SHALL abort migration and report the issue
4. THE System SHALL validate data integrity before and after migration
5. THE System SHALL provide rollback scripts for each migration
6. THE System SHALL log all migration operations for audit purposes

### Requirement 16: 向后兼容性处理

**User Story:** 作为系统维护者，我希望了解类型变更对现有 API 客户端的影响。

#### Acceptance Criteria

1. THE System SHALL document that ID format changes from string to number in JSON responses
2. THE System SHALL accept both numeric and string ID formats in API requests during transition period
3. THE System SHALL normalize all ID inputs to Long type internally
4. THE System SHALL document the breaking change in API changelog
5. THE System SHALL provide migration guide for API clients

### Requirement 17: 测试覆盖

**User Story:** 作为质量保证工程师，我希望有完整的测试覆盖来验证 ID 类型变更的正确性。

#### Acceptance Criteria

1. THE System SHALL have unit tests for ID type handling in all layers
2. THE System SHALL have integration tests for database operations with Long IDs
3. THE System SHALL have API tests verifying Long ID handling in requests and responses
4. THE System SHALL have property-based tests for ID validation
5. THE System SHALL have migration tests to verify data conversion correctness

### Requirement 18: 文档更新

**User Story:** 作为开发人员，我希望相关文档反映 ID 类型的标准化决策。

#### Acceptance Criteria

1. THE System SHALL update database schema documentation to reflect BIGINT ID standard
2. THE System SHALL update API documentation to specify Long/numeric ID format
3. THE System SHALL update coding guidelines to mandate Long type for IDs
4. THE System SHALL document the migration process and rationale
5. THE System SHALL update architecture diagrams to reflect upload service removal

### Requirement 19: 性能影响评估

**User Story:** 作为系统架构师，我希望了解 ID 类型变更对系统性能的影响。

#### Acceptance Criteria

1. THE System SHALL measure query performance before and after schema migration
2. THE System SHALL measure index performance with BIGINT vs VARCHAR
3. THE System SHALL measure serialization/deserialization performance with Long IDs
4. THE System SHALL ensure performance improvement or no significant degradation
5. THE System SHALL document performance benchmarks and findings

