# Design Document: ID Type Consistency Migration

## Overview

本设计文档描述了将博客微服务系统中所有 ID 类型从 `String/VARCHAR(64)` 迁移到 `Long/BIGINT` 的完整方案。这是一个全栈迁移项目，涉及数据库 Schema、持久化层、领域层、应用层和接口层的全面改造。

### 背景

当前系统使用 Leaf 分布式 ID 生成器生成 64 位 Long 类型的 ID，但在存储和使用时转换为 String 类型。这种设计存在以下问题：

1. **类型不匹配**：ID 生成器返回 Long，但需要转换为 String 存储
2. **存储浪费**：VARCHAR(64) 比 BIGINT 占用更多存储空间
3. **索引效率**：字符串索引比整数索引效率低
4. **序列化开销**：JSON 序列化时字符串比数字占用更多字节
5. **语义不清**：ID 本质是数字，使用字符串表示语义不清晰

### 目标

1. 统一所有 ID 字段为 Long/BIGINT 类型
2. 移除废弃的 Upload 服务
3. 确保数据迁移安全无损
4. 保持系统性能稳定或提升
5. 提供完整的测试覆盖

### 非目标

1. 不改变 ID 生成算法（继续使用 Leaf）
2. 不改变 API 端点路径
3. 不改变业务逻辑



## Architecture

### 系统分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                     Interface Layer                          │
│  (REST Controllers, DTOs, Request/Response Objects)         │
│                    ID Type: Long                             │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                   Application Layer                          │
│     (Application Services, VOs, Assemblers)                 │
│                    ID Type: Long                             │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                     Domain Layer                             │
│    (Aggregates, Entities, Value Objects, Services)          │
│                    ID Type: Long                             │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                 Infrastructure Layer                         │
│  (PO, Repositories, MyBatis Mappers, Database)              │
│                    ID Type: Long                             │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                      Database                                │
│              ID Column Type: BIGINT                          │
└─────────────────────────────────────────────────────────────┘
```

### 服务架构

```
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  ZhiCore-user   │  │  ZhiCore-post   │  │ ZhiCore-comment │
│   (8081)     │  │   (8082)     │  │   (8083)     │
└──────────────┘  └──────────────┘  └──────────────┘
        │                 │                 │
        └─────────────────┴─────────────────┘
                          │
                ┌─────────┴─────────┐
                │   ZhiCore-gateway    │
                │      (8000)       │
                └───────────────────┘
                          │
                ┌─────────┴─────────┐
                │  ID Generator     │
                │  Service (8010)   │
                │  Returns: Long    │
                └───────────────────┘
```



## Components and Interfaces

### 1. Upload 服务移除

#### 1.1 需要删除的模块

```
ZhiCore-upload/
├── src/
│   ├── main/java/com/ZhiCore/upload/
│   └── test/java/com/ZhiCore/upload/
├── pom.xml
└── Dockerfile
```

#### 1.2 需要删除的配置

- `docker/docker-compose.services.yml` 中的 upload 服务配置
- Nacos 配置中心的 upload 服务配置
- `pom.xml` 根项目中的 upload 模块引用

#### 1.3 需要删除的数据库迁移

- `ZhiCore-migration/src/main/resources/db/migration/upload/`



### 2. 数据库 Schema 迁移

#### 2.1 迁移策略

采用 Flyway 版本化迁移，为每个服务创建独立的迁移脚本。

#### 2.2 迁移脚本命名规范

```
V{version}__migrate_ids_to_bigint_{service_name}.sql
```

示例：
- `V2__migrate_ids_to_bigint_user.sql`
- `V2__migrate_ids_to_bigint_post.sql`
- `V2__migrate_ids_to_bigint_comment.sql`

#### 2.3 迁移步骤（开发环境简化版）

由于当前处于开发阶段，无需保留现有数据，可以直接重建表结构：

```sql
-- 简化方案：直接删除并重建表
DROP TABLE IF EXISTS user_check_ins CASCADE;
DROP TABLE IF EXISTS user_blocks CASCADE;
DROP TABLE IF EXISTS user_follows CASCADE;
DROP TABLE IF EXISTS user_follow_stats CASCADE;
DROP TABLE IF EXISTS user_check_in_stats CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- 重新创建表（使用 BIGINT）
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    -- ... 其他字段
);

-- 或者使用 Flyway 的 clean + migrate
-- flyway clean
-- flyway migrate
```

**注意**：生产环境需要使用完整的数据迁移方案（见附录）。



#### 2.4 需要迁移的表清单

**User 服务（6 张表）：**
- `users`: id
- `user_follows`: ~~id~~, follower_id, following_id（**移除冗余 id，使用复合主键**）
- `user_blocks`: ~~id~~, blocker_id, blocked_id（**移除冗余 id，使用复合主键**）
- `user_check_ins`: ~~id~~, user_id, check_in_date（**移除冗余 id，使用复合主键**）
- `user_follow_stats`: user_id
- `user_check_in_stats`: user_id

**Post 服务（5 张表）：**
- `posts`: id, owner_id, topic_id
- `post_likes`: ~~id~~, post_id, user_id（**移除冗余 id，使用复合主键**）
- `post_favorites`: ~~id~~, post_id, user_id（**移除冗余 id，使用复合主键**）
- `post_stats`: post_id

**Comment 服务（2 张表）：**
- `comments`: id, post_id, author_id, parent_id, root_id, reply_to_user_id
- `comment_likes`: ~~id~~, comment_id, user_id（**移除冗余 id，使用复合主键**）
- `comment_stats`: comment_id

**Message 服务（2 张表）：**
- `conversations`: id, participant1_id, participant2_id
- `messages`: id, conversation_id, sender_id

**Notification 服务（3 张表）：**
- `notifications`: id, recipient_id
- `global_announcements`: id
- `assistant_messages`: id, user_id

**Content 服务（3 张表）：**
- `topics`: id, creator_id
- `reports`: id, target_id, reporter_id, processor_id
- `feedbacks`: id, user_id, processor_id

**Admin 服务（2 张表）：**
- `audit_logs`: id, operator_id
- `reports`: id, reporter_id, reported_user_id, handler_id

**总计：23 张表，约 60+ 个 ID 字段**

**优化说明：**
- 移除了 5 个冗余的自增 ID 字段（user_follows, user_blocks, user_check_ins, post_likes, post_favorites, comment_likes）
- 这些表使用复合主键更符合业务语义，且已有 UNIQUE 索引保证唯一性
- 每条记录节省 8 字节存储空间，减少索引开销



### 3. PO 层（Infrastructure Layer）

#### 3.1 类型变更模式

**变更前：**
```java
@Data
@TableName("users")
public class UserPO {
    @TableId(type = IdType.INPUT)
    private String id;
    
    private String userName;
    // ...
}
```

**变更后：**
```java
@Data
@TableName("users")
public class UserPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    
    private String userName;
    // ...
}
```

#### 3.2 外键字段变更

**变更前：**
```java
@Data
@TableName("posts")
public class PostPO {
    @TableId(type = IdType.INPUT)
    private String id;
    
    private String ownerId;  // 外键
    private String topicId;  // 外键
    // ...
}
```

**变更后：**
```java
@Data
@TableName("posts")
public class PostPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    
    private Long ownerId;  // 外键
    private Long topicId;  // 外键
    // ...
}
```

#### 3.3 MyBatis-Plus 配置

MyBatis-Plus 会自动处理 Long 到 BIGINT 的映射，无需额外配置。

```java
// 自动映射：
// Java Long <-> PostgreSQL BIGINT
// Java Integer <-> PostgreSQL INTEGER
// Java String <-> PostgreSQL VARCHAR
```



### 4. Domain 层

#### 4.1 聚合根 ID 变更

**变更前：**
```java
@Getter
public class User {
    private final String id;
    private String userName;
    // ...
    
    private User(String id, String userName, String email, String passwordHash) {
        Assert.hasText(id, "用户ID不能为空");
        // ...
    }
    
    public static User create(String id, String userName, String email, String passwordHash) {
        return new User(id, userName, email, passwordHash);
    }
}
```

**变更后：**
```java
@Getter
public class User {
    private final Long id;
    private String userName;
    // ...
    
    private User(Long id, String userName, String email, String passwordHash) {
        Assert.notNull(id, "用户ID不能为空");
        Assert.isTrue(id > 0, "用户ID必须为正数");
        // ...
    }
    
    public static User create(Long id, String userName, String email, String passwordHash) {
        return new User(id, userName, email, passwordHash);
    }
}
```

#### 4.2 Jackson 序列化支持

需要更新 `@JsonCreator` 构造函数的参数类型：

**变更前：**
```java
@JsonCreator
private User(
    @JsonProperty("id") String id,
    @JsonProperty("userName") String userName,
    // ...
) {
    this.id = id;
    // ...
}
```

**变更后：**
```java
@JsonCreator
private User(
    @JsonProperty("id") Long id,
    @JsonProperty("userName") String userName,
    // ...
) {
    this.id = id;
    // ...
}
```

#### 4.3 ID 验证规则

```java
// 新的 ID 验证规则
private void validateId(Long id) {
    Assert.notNull(id, "ID不能为空");
    Assert.isTrue(id > 0, "ID必须为正数");
}
```



### 5. Application 层

#### 5.1 DTO/VO 类型变更

**变更前：**
```java
@Data
public class UserVO {
    private String id;
    private String userName;
    private String nickName;
    // ...
}
```

**变更后：**
```java
@Data
public class UserVO {
    private Long id;
    private String userName;
    private String nickName;
    // ...
}
```

#### 5.2 Application Service 变更

**变更前：**
```java
@Service
@RequiredArgsConstructor
public class UserApplicationService {
    private final IdGeneratorService idGeneratorService;
    
    public String registerUser(RegisterRequest request) {
        // 生成ID并转换为String
        Long userId = idGeneratorService.nextSnowflakeId();
        String userIdStr = String.valueOf(userId);
        
        User user = User.create(userIdStr, request.getUserName(), 
                                request.getEmail(), passwordHash);
        // ...
        return userIdStr;
    }
}
```

**变更后：**
```java
@Service
@RequiredArgsConstructor
public class UserApplicationService {
    private final IdGeneratorService idGeneratorService;
    
    public Long registerUser(RegisterRequest request) {
        // 直接使用Long ID
        Long userId = idGeneratorService.nextSnowflakeId();
        
        User user = User.create(userId, request.getUserName(), 
                                request.getEmail(), passwordHash);
        // ...
        return userId;
    }
}
```



### 6. Interface 层（REST API）

#### 6.1 Controller 方法签名变更

**变更前：**
```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    
    @GetMapping("/{id}")
    public Result<UserVO> getUserById(@PathVariable String id) {
        // ...
    }
    
    @PostMapping
    public Result<String> createUser(@RequestBody CreateUserRequest request) {
        String userId = userApplicationService.createUser(request);
        return Result.success(userId);
    }
}
```

**变更后：**
```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    
    @GetMapping("/{id}")
    public Result<UserVO> getUserById(@PathVariable Long id) {
        // Spring 会自动将路径参数转换为 Long
        // ...
    }
    
    @PostMapping
    public Result<Long> createUser(@RequestBody CreateUserRequest request) {
        Long userId = userApplicationService.createUser(request);
        return Result.success(userId);
    }
}
```

#### 6.2 JSON 序列化格式

**变更前（String ID）：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "1234567890123456789",
    "userName": "testuser"
  }
}
```

**变更后（Long ID）：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1234567890123456789,
    "userName": "testuser"
  }
}
```

#### 6.3 参数验证

```java
@GetMapping("/{id}")
public Result<UserVO> getUserById(
    @PathVariable 
    @Min(value = 1, message = "用户ID必须为正数") 
    Long id) {
    // ...
}
```



## Data Models

### ID 类型对照表

| 层级 | 变更前 | 变更后 | 说明 |
|------|--------|--------|------|
| Database | VARCHAR(64) | BIGINT | PostgreSQL 64位整数 |
| PO (Infrastructure) | String | Long | Java 包装类型 |
| Domain Model | String | Long | Java 包装类型 |
| DTO/VO (Application) | String | Long | Java 包装类型 |
| REST API (Interface) | String (JSON) | Number (JSON) | JSON 数字类型 |

### 数据类型映射

```
Java Long (64-bit)
    ↕
PostgreSQL BIGINT (64-bit)
    ↕
JSON Number (up to 2^53-1 safe in JavaScript)
```

### JavaScript 精度问题处理

JavaScript 的 Number 类型只能安全表示 -(2^53-1) 到 2^53-1 之间的整数。
Snowflake ID 是 64 位整数，可能超出 JavaScript 安全范围。

**解决方案：**

1. **后端配置**：使用 Jackson 配置将 Long 序列化为字符串（可选）
```java
@JsonSerialize(using = ToStringSerializer.class)
private Long id;
```

2. **前端处理**：使用字符串处理 ID
```typescript
interface User {
  id: string;  // 虽然后端是 Long，前端用 string 处理
  userName: string;
}
```

3. **推荐方案**：保持 JSON 中 ID 为数字，前端使用 BigInt 或字符串处理



## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property Reflection

After analyzing all acceptance criteria, I identified the following testable properties. Some redundant properties were consolidated:

- Schema migration properties (2.1-2.5) can be combined into comprehensive migration validation
- Code layer properties (10.1-13.5) can be grouped by layer
- Some properties are subsumed by others (e.g., "all IDs are Long" implies "primary keys are Long")

### Core Properties

#### Property 1: Schema Migration Completeness
*For any* database table in the system, all ID columns (primary keys and foreign keys) should be of type BIGINT after migration.

**Validates: Requirements 2.1, 2.2, 3.1-9.4**

**Test Strategy:**
```sql
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE column_name LIKE '%id%'
  AND data_type != 'bigint'
  AND table_schema = 'public';
-- Should return 0 rows
```

#### Property 2: Data Migration Integrity
*For any* row in any table before migration, the same row after migration should have the same ID value (converted from string to number).

**Validates: Requirements 2.3, 15.2, 15.4**

**Test Strategy:**
```sql
-- Before migration: SELECT id, checksum FROM table
-- After migration: SELECT id, checksum FROM table
-- Verify: all IDs match and checksums match
```

#### Property 3: PO Layer Type Consistency
*For any* PO class in the system, all fields with names ending in "Id" or "ID" should be of type Long.

**Validates: Requirements 10.1, 10.2**

**Test Strategy:**
- Parse all PO classes using reflection
- Check field types for ID fields
- Verify all are Long type

#### Property 4: Domain Layer Type Consistency
*For any* Domain Model class, all ID fields should be of type Long and validated as positive numbers.

**Validates: Requirements 11.1, 11.2, 11.4**

**Test Strategy:**
- Parse all Domain Model classes
- Verify ID field types are Long
- Test constructors reject null/negative IDs



#### Property 5: PO-Domain Conversion Round Trip
*For any* valid PO object, converting it to Domain Model and back to PO should preserve the ID value.

**Validates: Requirements 11.3**

**Test Strategy:**
```java
UserPO originalPO = createTestUserPO();
User domain = UserAssembler.toDomain(originalPO);
UserPO convertedPO = UserAssembler.toPO(domain);
assertEquals(originalPO.getId(), convertedPO.getId());
```

#### Property 6: MyBatis-Plus CRUD Round Trip
*For any* entity with a Long ID, saving it to database and retrieving it should preserve the ID value.

**Validates: Requirements 10.3**

**Test Strategy:**
```java
UserPO user = new UserPO();
user.setId(123456789L);
userMapper.insert(user);
UserPO retrieved = userMapper.selectById(123456789L);
assertEquals(user.getId(), retrieved.getId());
```

#### Property 7: JSON Serialization Format
*For any* DTO/VO object with Long ID, serializing to JSON should produce a numeric value (not a string).

**Validates: Requirements 12.3**

**Test Strategy:**
```java
UserVO user = new UserVO();
user.setId(123456789L);
String json = objectMapper.writeValueAsString(user);
assertTrue(json.contains("\"id\":123456789"));  // Number, not "123456789"
```

#### Property 8: JSON Deserialization Acceptance
*For any* JSON with numeric ID, deserializing should create an object with Long ID.

**Validates: Requirements 12.4**

**Test Strategy:**
```java
String json = "{\"id\":123456789,\"userName\":\"test\"}";
UserVO user = objectMapper.readValue(json, UserVO.class);
assertEquals(Long.valueOf(123456789L), user.getId());
```

#### Property 9: API Path Parameter Conversion
*For any* REST API endpoint with ID path parameter, Spring should correctly convert numeric string to Long.

**Validates: Requirements 13.1**

**Test Strategy:**
```java
mockMvc.perform(get("/api/v1/users/123456789"))
    .andExpect(status().isOk());
// Verify controller receives Long value 123456789L
```



#### Property 10: API Response ID Format
*For any* REST API response containing entities, all ID fields should be represented as JSON numbers.

**Validates: Requirements 13.3**

**Test Strategy:**
```java
mockMvc.perform(get("/api/v1/users/123456789"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.id").isNumber())
    .andExpect(jsonPath("$.data.id").value(123456789));
```

#### Property 11: ID Validation Rejection
*For any* API request with invalid ID (negative, zero, or non-numeric), the system should return 400 Bad Request.

**Validates: Requirements 13.4, 13.5**

**Test Strategy:**
```java
// Test negative ID
mockMvc.perform(get("/api/v1/users/-1"))
    .andExpect(status().isBadRequest());

// Test zero ID
mockMvc.perform(get("/api/v1/users/0"))
    .andExpect(status().isBadRequest());

// Test non-numeric ID
mockMvc.perform(get("/api/v1/users/abc"))
    .andExpect(status().isBadRequest());
```

#### Property 12: ID Generator Direct Usage
*For any* service that generates IDs, the generated Long value should be used directly without String conversion.

**Validates: Requirements 14.1**

**Test Strategy:**
- Code review: verify no `String.valueOf(id)` calls after ID generation
- Static analysis: check for String conversion patterns

#### Property 13: Generated ID Positivity
*For any* ID generated by the ID generator, the value should be a positive Long.

**Validates: Requirements 14.2**

**Test Strategy:**
```java
for (int i = 0; i < 1000; i++) {
    Long id = idGeneratorService.nextSnowflakeId();
    assertTrue(id > 0);
}
```

#### Property 14: Migration Pre-validation
*For any* existing VARCHAR ID in the database, it should be a valid numeric string before migration.

**Validates: Requirements 15.1**

**Test Strategy:**
```sql
-- Pre-migration check
SELECT id FROM users WHERE id !~ '^\d+$';
-- Should return 0 rows
```



#### Property 15: Referential Integrity Preservation
*For any* foreign key relationship before migration, the same relationship should exist after migration.

**Validates: Requirements 2.5**

**Test Strategy:**
```sql
-- Before migration: count foreign key violations
SELECT COUNT(*) FROM posts p
LEFT JOIN users u ON p.owner_id = u.id
WHERE u.id IS NULL;

-- After migration: should be same count (ideally 0)
```

### Edge Cases

The following edge cases should be handled by the property tests' input generators:

- **Null IDs**: Domain constructors should reject null IDs
- **Negative IDs**: Validation should reject negative IDs
- **Zero IDs**: Validation should reject zero IDs
- **Maximum Long value**: System should handle Long.MAX_VALUE
- **JSON null values**: Serialization should handle null IDs gracefully



## Error Handling

### 1. Migration Errors

#### 1.1 Non-numeric ID Detection
```sql
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE id !~ '^\d+$') THEN
        RAISE EXCEPTION 'Non-numeric IDs found in users table. Migration aborted.';
    END IF;
END $$;
```

#### 1.2 Data Loss Prevention
```sql
-- Verify row count before and after
DO $$
DECLARE
    before_count INTEGER;
    after_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO before_count FROM users_backup;
    SELECT COUNT(*) INTO after_count FROM users;
    
    IF before_count != after_count THEN
        RAISE EXCEPTION 'Row count mismatch: before=%, after=%', before_count, after_count;
    END IF;
END $$;
```

### 2. Application Errors

#### 2.1 Invalid ID Format
```java
@ExceptionHandler(MethodArgumentTypeMismatchException.class)
public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    if (ex.getName().endsWith("Id") || ex.getName().endsWith("id")) {
        return Result.error(ResultCode.INVALID_PARAM, 
            "Invalid ID format: " + ex.getValue());
    }
    return Result.error(ResultCode.INVALID_PARAM, ex.getMessage());
}
```

#### 2.2 ID Validation Failure
```java
@ExceptionHandler(ConstraintViolationException.class)
public Result<Void> handleValidation(ConstraintViolationException ex) {
    String message = ex.getConstraintViolations().stream()
        .map(ConstraintViolation::getMessage)
        .collect(Collectors.joining(", "));
    return Result.error(ResultCode.INVALID_PARAM, message);
}
```

### 3. ID Generation Errors

```java
public Long generateUserId() {
    try {
        Long id = idGeneratorService.nextSnowflakeId();
        if (id == null || id <= 0) {
            throw new BusinessException("Invalid ID generated: " + id);
        }
        return id;
    } catch (Exception e) {
        log.error("Failed to generate user ID", e);
        throw new BusinessException("ID generation failed", e);
    }
}
```



## Testing Strategy

### Dual Testing Approach

We will use both unit tests and property-based tests to ensure comprehensive coverage:

- **Unit tests**: Verify specific examples, edge cases, and error conditions
- **Property tests**: Verify universal properties across all inputs

### Property-Based Testing Framework

We will use **jqwik** (Java QuickCheck) for property-based testing:

```xml
<dependency>
    <groupId>net.jqwik</groupId>
    <artifactId>jqwik</artifactId>
    <version>1.7.4</version>
    <scope>test</scope>
</dependency>
```

### Test Configuration

Each property test should run minimum 100 iterations:

```java
@Property(tries = 100)
void propertyTest(@ForAll Long id) {
    // Test implementation
}
```

### Test Categories

#### 1. Database Migration Tests

**Unit Tests:**
- Test migration script syntax
- Test rollback script syntax
- Test specific table migrations

**Property Tests:**
- Property 1: Schema Migration Completeness
- Property 2: Data Migration Integrity
- Property 14: Migration Pre-validation
- Property 15: Referential Integrity Preservation

```java
@Property(tries = 100)
@Label("Feature: id-type-consistency, Property 2: Data Migration Integrity")
void dataMigrationPreservesIds(@ForAll @LongRange(min = 1) Long originalId) {
    // Insert with VARCHAR ID
    // Run migration
    // Verify BIGINT ID matches
}
```

#### 2. PO Layer Tests

**Unit Tests:**
- Test MyBatis-Plus CRUD operations
- Test null ID handling
- Test ID annotation presence

**Property Tests:**
- Property 3: PO Layer Type Consistency
- Property 6: MyBatis-Plus CRUD Round Trip

```java
@Property(tries = 100)
@Label("Feature: id-type-consistency, Property 6: MyBatis-Plus CRUD Round Trip")
void crudPreservesId(@ForAll @LongRange(min = 1) Long id) {
    UserPO user = new UserPO();
    user.setId(id);
    userMapper.insert(user);
    UserPO retrieved = userMapper.selectById(id);
    assertEquals(id, retrieved.getId());
}
```



#### 3. Domain Layer Tests

**Unit Tests:**
- Test domain model creation with valid IDs
- Test domain model rejects invalid IDs
- Test domain behavior with IDs

**Property Tests:**
- Property 4: Domain Layer Type Consistency
- Property 5: PO-Domain Conversion Round Trip

```java
@Property(tries = 100)
@Label("Feature: id-type-consistency, Property 4: Domain Layer Type Consistency")
void domainRejectsInvalidIds(@ForAll Long id) {
    if (id == null || id <= 0) {
        assertThrows(IllegalArgumentException.class, () -> {
            User.create(id, "user", "email@test.com", "hash");
        });
    } else {
        User user = User.create(id, "user", "email@test.com", "hash");
        assertEquals(id, user.getId());
    }
}
```

#### 4. Application Layer Tests

**Unit Tests:**
- Test DTO/VO serialization
- Test service methods with IDs
- Test ID generation integration

**Property Tests:**
- Property 7: JSON Serialization Format
- Property 8: JSON Deserialization Acceptance
- Property 13: Generated ID Positivity

```java
@Property(tries = 100)
@Label("Feature: id-type-consistency, Property 7: JSON Serialization Format")
void jsonSerializesIdAsNumber(@ForAll @LongRange(min = 1) Long id) {
    UserVO user = new UserVO();
    user.setId(id);
    String json = objectMapper.writeValueAsString(user);
    
    // Verify ID is a number, not a string
    assertFalse(json.contains("\"id\":\"" + id + "\""));
    assertTrue(json.contains("\"id\":" + id));
}
```

#### 5. Interface Layer Tests

**Unit Tests:**
- Test controller methods with valid IDs
- Test controller methods with invalid IDs
- Test error responses

**Property Tests:**
- Property 9: API Path Parameter Conversion
- Property 10: API Response ID Format
- Property 11: ID Validation Rejection

```java
@Property(tries = 100)
@Label("Feature: id-type-consistency, Property 10: API Response ID Format")
void apiResponseContainsNumericId(@ForAll @LongRange(min = 1) Long id) {
    mockMvc.perform(get("/api/v1/users/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").isNumber())
        .andExpect(jsonPath("$.data.id").value(id));
}
```



### Test Data Generators

#### ID Generator
```java
@Provide
Arbitrary<Long> validIds() {
    return Arbitraries.longs()
        .between(1L, Long.MAX_VALUE);
}

@Provide
Arbitrary<Long> invalidIds() {
    return Arbitraries.longs()
        .between(Long.MIN_VALUE, 0L);
}
```

#### Entity Generators
```java
@Provide
Arbitrary<UserPO> userPOs() {
    return Combinators.combine(
        validIds(),
        Arbitraries.strings().alpha().ofLength(10),
        Arbitraries.strings().email()
    ).as((id, userName, email) -> {
        UserPO user = new UserPO();
        user.setId(id);
        user.setUserName(userName);
        user.setEmail(email);
        return user;
    });
}
```

### Integration Test Strategy

#### Database Integration Tests
- Use Testcontainers for PostgreSQL
- Run actual migrations on test database
- Verify schema changes
- Verify data integrity

```java
@Testcontainers
class MigrationIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    
    @Test
    void migrationShouldConvertAllIdsToB igint() {
        // Run Flyway migration
        // Query information_schema
        // Verify all ID columns are BIGINT
    }
}
```

#### API Integration Tests
- Use MockMvc for REST API testing
- Test complete request/response cycle
- Verify JSON format
- Test error handling

```java
@SpringBootTest
@AutoConfigureMockMvc
class UserApiIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void shouldAcceptLongIdInPath() throws Exception {
        mockMvc.perform(get("/api/v1/users/123456789"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(123456789));
    }
}
```



## Migration Plan

### 开发环境迁移计划（简化版）

由于当前处于开发阶段，无需保留数据，迁移流程大大简化：

### Phase 1: Preparation (Day 1)

1. **Code Audit**
   - 识别所有使用 String ID 的类
   - 创建修改文件清单

2. **Test Environment Setup**
   - 配置 Testcontainers
   - 设置 jqwik 属性测试框架

### Phase 2: Upload Service Removal (Day 1)

1. **Remove Module**
   - 删除 `ZhiCore-upload` 目录
   - 从 `pom.xml` 移除模块
   - 从 Docker Compose 移除服务

2. **Remove Configuration**
   - 删除 Nacos 配置
   - 删除数据库迁移脚本

### Phase 3: Database Schema Update (Day 2)

**简化方案：直接修改 Flyway 初始迁移脚本**

1. **修改所有 V1__create_*_tables.sql 文件**
   - 将所有 `id VARCHAR(64)` 改为 `id BIGINT`
   - 将所有外键字段从 VARCHAR(64) 改为 BIGINT

2. **重建数据库**
   ```bash
   # 停止所有服务
   docker-compose down
   
   # 删除数据库卷
   docker volume rm ZhiCore-postgres-data
   
   # 重新启动
   docker-compose up -d postgres
   
   # Flyway 会自动创建新表结构
   ```

### Phase 4: Code Migration (Day 3-5)

#### Day 3: Infrastructure & Domain Layers

1. **PO Layer Migration**
   - 批量替换：`private String id` → `private Long id`
   - 批量替换：`private String userId` → `private Long userId`
   - 批量替换：`private String postId` → `private Long postId`
   - 等等...

2. **Domain Layer Migration**
   - 更新领域模型 ID 字段类型
   - 更新 @JsonCreator 构造函数参数
   - 更新 ID 验证逻辑

#### Day 4: Application & Interface Layers

3. **Application Layer Migration**
   - 更新 DTOs 和 VOs
   - 更新 Application Services
   - 移除 `String.valueOf(id)` 转换

4. **Interface Layer Migration**
   - 更新 Controllers
   - 更新 Request/Response 对象

### Phase 5: Testing (Day 6-7)

1. **Unit Tests**
   - 修复所有单元测试
   - 确保测试通过

2. **Property-Based Tests**
   - 实现 15 个正确性属性
   - 每个属性运行 100+ 次迭代

3. **Integration Tests**
   - 测试完整工作流
   - 测试 API 契约

### Phase 6: Verification (Day 8)

1. **Manual Testing**
   - 测试所有主要功能
   - 验证 API 响应格式

2. **Performance Check**
   - 简单的性能对比
   - 确认无明显退化

**总计：约 8 个工作日（1-2 周）**



## Rollback Strategy

### 开发环境回滚策略

由于是开发环境，回滚策略相对简单：

### Code Rollback

使用 Git 回滚：

```bash
# 创建分支前打标签
git tag -a v1.0.0-before-id-migration -m "Before ID type migration"

# 如果需要回滚
git reset --hard v1.0.0-before-id-migration
```

### Database Rollback

重建数据库：

```bash
# 停止服务
docker-compose down

# 删除数据库卷
docker volume rm ZhiCore-postgres-data

# 恢复旧的迁移脚本（如果已修改）
git checkout v1.0.0-before-id-migration -- ZhiCore-migration/

# 重新启动
docker-compose up -d
```

### 生产环境回滚策略（未来参考）

生产环境需要更复杂的回滚策略：

1. **Database Rollback Scripts**
   - 每个迁移脚本都有对应的回滚脚本
   - 使用事务确保原子性

2. **Blue-Green Deployment**
   - 保持旧版本运行
   - 新版本部署到独立环境
   - 流量切换
   - 如有问题，切回旧版本

3. **Data Backup**
   - 迁移前完整备份
   - 保留备份至少 30 天

## Performance Considerations

### Storage Savings

| Type | Size | Example | Storage for 1M rows |
|------|------|---------|---------------------|
| VARCHAR(64) | Variable, up to 64 bytes | "1234567890123456789" | ~19 MB (avg 19 bytes) |
| BIGINT | Fixed, 8 bytes | 1234567890123456789 | 8 MB |
| **Savings** | | | **~58% reduction** |

### Index Performance

BIGINT indexes are more efficient than VARCHAR indexes:

- **B-tree comparison**: Integer comparison is faster than string comparison
- **Index size**: Smaller index size means more entries fit in memory
- **Cache efficiency**: Better CPU cache utilization with fixed-size integers

### Serialization Performance

JSON serialization of numbers is more efficient than strings:

```json
// String ID: 23 bytes
{"id":"1234567890123456789"}

// Number ID: 21 bytes
{"id":1234567890123456789}

// Savings: ~9% per ID field
```

### Expected Performance Impact

- **Query performance**: 10-20% improvement for ID-based queries
- **Index size**: 50-60% reduction
- **JSON payload**: 5-10% reduction in size
- **Memory usage**: 10-15% reduction for in-memory caches



## Risks and Mitigation

### Risk 1: 数据库重建导致开发数据丢失

**Probability**: High（开发环境）  
**Impact**: Low（开发数据可重建）

**Mitigation**:
- 提前通知团队成员
- 如有重要测试数据，手动导出
- 使用种子数据脚本快速重建测试数据

### Risk 2: JavaScript Precision Issues

**Probability**: Medium  
**Impact**: Medium

**Mitigation**:
- 文档说明大数字处理方式
- 前端使用字符串处理 ID（如需要）
- 考虑使用 `@JsonSerialize(using = ToStringSerializer.class)` 如果前端有问题

### Risk 3: Breaking API Changes

**Probability**: High  
**Impact**: Medium（开发阶段影响较小）

**Mitigation**:
- 更新 API 文档
- 通知前端团队
- 提供迁移示例

### Risk 4: 遗漏的 String ID 引用

**Probability**: Medium  
**Impact**: High

**Mitigation**:
- 使用 IDE 全局搜索 `private String.*Id`
- 编译时会发现类型不匹配
- 运行完整测试套件
- 代码审查

## Dependencies

### External Dependencies

1. **Leaf ID Generator Service**
   - Must be running and accessible
   - Must return valid Long IDs
   - Must handle high concurrency

2. **PostgreSQL Database**
   - Version 12+ for BIGINT support
   - Sufficient storage for migration
   - Backup and restore capabilities

3. **Frontend Applications**
   - Must be updated to handle numeric IDs
   - Must handle potential precision issues
   - Must update API client code

### Internal Dependencies

1. **ZhiCore-common Module**
   - IdGeneratorService interface
   - Result wrapper classes
   - Exception handling

2. **ZhiCore-api Module**
   - Shared DTOs
   - API contracts

3. **All Microservices**
   - Must be migrated together
   - Must maintain API compatibility
   - Must coordinate deployment



## Documentation Updates

### 1. API Documentation

Update OpenAPI/Swagger specifications:

```yaml
# Before
User:
  type: object
  properties:
    id:
      type: string
      example: "1234567890123456789"

# After
User:
  type: object
  properties:
    id:
      type: integer
      format: int64
      example: 1234567890123456789
```

### 2. Database Schema Documentation

Update schema documentation in `docs/database/`:

```markdown
## users Table

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key, generated by Leaf ID generator |
| username | VARCHAR(50) | Unique username |
```

### 3. Architecture Documentation

Update architecture diagrams to reflect:
- Upload service removal
- ID type standardization
- Data flow with Long IDs

### 4. Migration Guide for API Clients

Create `docs/migration/id-type-migration.md`:

```markdown
# ID Type Migration Guide

## Overview
All ID fields have been changed from strings to numbers.

## Changes

### Request Format
**Before:**
```json
{"userId": "1234567890123456789"}
```

**After:**
```json
{"userId": 1234567890123456789}
```

### Response Format
Same as request format.

## JavaScript Clients

Use string representation for large IDs:
```javascript
const userId = response.data.id.toString();
```

## Migration Checklist
- [ ] Update API client code
- [ ] Update type definitions
- [ ] Test with new API
- [ ] Handle precision issues
```

### 5. Changelog

Add entry to `CHANGELOG.md`:

```markdown
## [2.0.0] - 2026-XX-XX

### Breaking Changes
- **ID Type Migration**: All ID fields changed from String to Long
  - Database: VARCHAR(64) → BIGINT
  - Java: String → Long
  - JSON: string → number
  - See migration guide for details

### Removed
- Upload service (replaced by external file-service)

### Performance
- 10-20% improvement in ID-based queries
- 50-60% reduction in index size
- 5-10% reduction in JSON payload size
```



## Success Criteria

### Functional Success Criteria

1. ✅ All database ID columns are BIGINT
2. ✅ All Java ID fields are Long
3. ✅ All API responses use numeric IDs
4. ✅ No data loss during migration
5. ✅ All tests pass (unit, integration, property-based)
6. ✅ Upload service completely removed

### Performance Success Criteria

1. ✅ Query performance improved or maintained
2. ✅ Index size reduced by at least 50%
3. ✅ JSON payload size reduced by at least 5%
4. ✅ No increase in error rates

### Quality Success Criteria

1. ✅ All 15 correctness properties verified
2. ✅ Property tests run with 100+ iterations
3. ✅ Code coverage > 80% for modified code
4. ✅ No critical bugs in production

### Documentation Success Criteria

1. ✅ API documentation updated
2. ✅ Database schema documentation updated
3. ✅ Migration guide created
4. ✅ Changelog updated

## Conclusion

This design provides a comprehensive plan for migrating all ID types from String/VARCHAR to Long/BIGINT across the entire ZhiCore microservices system. The migration will:

1. **Improve Performance**: Faster queries, smaller indexes, reduced JSON payload
2. **Simplify Code**: No more String ↔ Long conversions
3. **Align with Standards**: IDs are numbers, not strings
4. **Remove Technical Debt**: Eliminate the deprecated Upload service

The migration is complex and touches all layers of the system, but with careful planning, comprehensive testing, and a phased rollback strategy, we can execute it safely and successfully.

**Key Success Factors:**
- Comprehensive property-based testing
- Thorough data validation before migration
- Clear rollback procedures
- Good communication with API clients
- Careful monitoring during deployment

**Estimated Timeline:** 1-2 weeks（开发环境）
**Estimated Effort:** 1-2 developer-weeks
**Risk Level:** Low（开发阶段，可以重建数据）

**生产环境部署时间线：** 4-6 weeks（需要数据迁移和更严格的测试）

