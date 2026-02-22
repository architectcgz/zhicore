# Design Document: 生产环境可用的管理服务实现

## Overview

本设计文档描述了如何将 ZhiCore-user 和 ZhiCore-post 服务中的管理功能从简化实现（内存过滤分页）改造为生产环境可用的实现（数据库层过滤分页）。

核心改进点：
1. 在 Repository 层添加支持动态条件查询的方法
2. 在 Mapper 层使用 MyBatis 动态 SQL 实现灵活的查询条件组合
3. 添加数据库索引以优化查询性能
4. 保持 Application Service 层的 API 接口不变，确保向后兼容

## Architecture

### 分层架构

```
┌─────────────────────────────────────────┐
│   Interfaces Layer (Controller)        │
│   - AdminUserController                 │
│   - AdminPostController                 │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│   Application Layer (Service)          │
│   - AdminUserApplicationService         │
│   - AdminPostApplicationService         │
│   (移除内存过滤逻辑，调用新的Repository方法) │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│   Domain Layer (Repository Interface)  │
│   - UserRepository                      │
│     + findByConditions(...)             │ ← 新增
│     + countByConditions(...)            │ ← 新增
│   - PostRepository                      │
│     + findByConditions(...)             │ ← 新增
│     + countByConditions(...)            │ ← 新增
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│   Infrastructure Layer (Mapper)        │
│   - UserMapper                          │
│     + selectByConditions(...)           │ ← 新增
│     + countByConditions(...)            │ ← 新增
│   - PostMapper                          │
│     + selectByConditions(...)           │ ← 新增
│     + countByConditions(...)            │ ← 新增
│   (使用 MyBatis 动态 SQL)                │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│   Database Layer (PostgreSQL)          │
│   - users 表                            │
│   - posts 表                            │
│   (添加索引优化查询性能)                  │
└─────────────────────────────────────────┘
```

### 数据流

**查询用户列表流程：**
```
1. Controller 接收请求参数 (keyword, status, page, size)
2. Application Service 调用 Repository.findByConditions()
3. Repository 调用 Mapper.selectByConditions()
4. Mapper 使用动态 SQL 构建查询语句
5. PostgreSQL 执行查询（使用索引）
6. 返回分页数据和总数
```

## Components and Interfaces

### 1. UserRepository 接口扩展

```java
public interface UserRepository {
    // ... 现有方法 ...
    
    /**
     * 根据条件查询用户列表（分页）
     *
     * @param keyword 关键词（用户名或邮箱）
     * @param status 状态
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 用户列表
     */
    List<User> findByConditions(String keyword, String status, int offset, int limit);
    
    /**
     * 根据条件统计用户数量
     *
     * @param keyword 关键词（用户名或邮箱）
     * @param status 状态
     * @return 用户数量
     */
    long countByConditions(String keyword, String status);
}
```

### 2. PostRepository 接口扩展

```java
public interface PostRepository {
    // ... 现有方法 ...
    
    /**
     * 根据条件查询文章列表（分页）
     *
     * @param keyword 关键词（标题）
     * @param status 状态
     * @param authorId 作者ID
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 文章列表
     */
    List<Post> findByConditions(String keyword, String status, String authorId, int offset, int limit);
    
    /**
     * 根据条件统计文章数量
     *
     * @param keyword 关键词（标题）
     * @param status 状态
     * @param authorId 作者ID
     * @return 文章数量
     */
    long countByConditions(String keyword, String status, String authorId);
}
```

### 3. UserMapper 接口扩展

```java
@Mapper
public interface UserMapper extends BaseMapper<UserPO> {
    // ... 现有方法 ...
    
    /**
     * 根据条件查询用户列表
     */
    List<UserPO> selectByConditions(@Param("keyword") String keyword,
                                     @Param("status") String status,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);
    
    /**
     * 根据条件统计用户数量
     */
    long countByConditions(@Param("keyword") String keyword,
                           @Param("status") String status);
}
```

### 4. PostMapper 接口扩展

```java
@Mapper
public interface PostMapper extends BaseMapper<PostPO> {
    // ... 现有方法 ...
    
    /**
     * 根据条件查询文章列表
     */
    List<PostPO> selectByConditions(@Param("keyword") String keyword,
                                     @Param("status") String status,
                                     @Param("authorId") String authorId,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);
    
    /**
     * 根据条件统计文章数量
     */
    long countByConditions(@Param("keyword") String keyword,
                           @Param("status") String status,
                           @Param("authorId") String authorId);
}
```

## Data Models

### UserQueryCondition（内部使用）

```java
@Data
@Builder
public class UserQueryCondition {
    private String keyword;      // 关键词（用户名或邮箱）
    private String status;       // 状态
    private int page;            // 页码（从1开始）
    private int size;            // 每页大小
    
    public int getOffset() {
        return (page - 1) * size;
    }
    
    public int getLimit() {
        return size;
    }
}
```

### PostQueryCondition（内部使用）

```java
@Data
@Builder
public class PostQueryCondition {
    private String keyword;      // 关键词（标题）
    private String status;       // 状态
    private String authorId;     // 作者ID
    private int page;            // 页码（从1开始）
    private int size;            // 每页大小
    
    public int getOffset() {
        return (page - 1) * size;
    }
    
    public int getLimit() {
        return size;
    }
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: 数据库层过滤正确性

*For any* 查询条件（keyword, status），数据库返回的结果应该与应用层过滤的结果完全一致

**Validates: Requirements 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 2.4**

### Property 2: 分页一致性

*For any* 有效的页码和页面大小，返回的数据应该是完整数据集的正确子集，且不重复不遗漏

**Validates: Requirements 1.4, 2.5**

### Property 3: 总数准确性

*For any* 查询条件，返回的总记录数应该等于满足条件的所有记录数

**Validates: Requirements 1.4, 2.5**

### Property 4: 空条件处理

*For any* 空或 null 的查询条件，系统应该忽略该条件并返回所有符合其他条件的记录

**Validates: Requirements 1.5, 1.6, 2.6, 2.7, 2.8**

### Property 5: 参数验证

*For any* 无效的页码或页面大小，系统应该使用默认值而不是抛出异常

**Validates: Requirements 1.7, 2.9, 7.4, 7.5**

### Property 6: SQL 注入防护

*For any* 包含特殊字符的输入，系统应该正确转义并防止 SQL 注入

**Validates: Requirements 7.1, 7.2**

### Property 7: 性能要求

*For any* 查询请求，在数据量达到指定规模时，响应时间应该在 SLA 要求范围内

**Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5**

### Property 8: API 兼容性

*For any* 现有的 API 调用，新实现应该返回与旧实现相同结构的数据

**Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5**

## MyBatis 动态 SQL 设计

### UserMapper.xml

```xml
<!-- 根据条件查询用户列表 -->
<select id="selectByConditions" resultType="com.ZhiCore.user.infrastructure.repository.po.UserPO">
    SELECT * FROM users
    <where>
        <if test="keyword != null and keyword != ''">
            AND (user_name LIKE CONCAT('%', #{keyword}, '%') 
                 OR email LIKE CONCAT('%', #{keyword}, '%'))
        </if>
        <if test="status != null and status != ''">
            AND status = #{status}
        </if>
    </where>
    ORDER BY created_at DESC
    LIMIT #{limit} OFFSET #{offset}
</select>

<!-- 根据条件统计用户数量 -->
<select id="countByConditions" resultType="long">
    SELECT COUNT(*) FROM users
    <where>
        <if test="keyword != null and keyword != ''">
            AND (user_name LIKE CONCAT('%', #{keyword}, '%') 
                 OR email LIKE CONCAT('%', #{keyword}, '%'))
        </if>
        <if test="status != null and status != ''">
            AND status = #{status}
        </if>
    </where>
</select>
```

### PostMapper.xml

```xml
<!-- 根据条件查询文章列表 -->
<select id="selectByConditions" resultType="com.zhicore.post.infrastructure.repository.po.PostPO">
    SELECT * FROM posts
    <where>
        <if test="keyword != null and keyword != ''">
            AND title LIKE CONCAT('%', #{keyword}, '%')
        </if>
        <if test="status != null and status != ''">
            AND status = #{status}
        </if>
        <if test="authorId != null and authorId != ''">
            AND author_id = #{authorId}
        </if>
    </where>
    ORDER BY created_at DESC
    LIMIT #{limit} OFFSET #{offset}
</select>

<!-- 根据条件统计文章数量 -->
<select id="countByConditions" resultType="long">
    SELECT COUNT(*) FROM posts
    <where>
        <if test="keyword != null and keyword != ''">
            AND title LIKE CONCAT('%', #{keyword}, '%')
        </if>
        <if test="status != null and status != ''">
            AND status = #{status}
        </if>
        <if test="authorId != null and authorId != ''">
            AND author_id = #{authorId}
        </if>
    </where>
</select>
```

## 数据库索引设计

### Users 表索引

```sql
-- 用户名索引（支持模糊查询）
CREATE INDEX idx_users_user_name ON users USING btree (user_name text_pattern_ops);

-- 邮箱索引（支持模糊查询）
CREATE INDEX idx_users_email ON users USING btree (email text_pattern_ops);

-- 状态索引
CREATE INDEX idx_users_status ON users (status);

-- 创建时间索引（用于排序）
CREATE INDEX idx_users_created_at ON users (created_at DESC);

-- 组合索引（状态 + 创建时间）
CREATE INDEX idx_users_status_created_at ON users (status, created_at DESC);
```

### Posts 表索引

```sql
-- 标题索引（支持模糊查询）
CREATE INDEX idx_posts_title ON posts USING btree (title text_pattern_ops);

-- 状态索引
CREATE INDEX idx_posts_status ON posts (status);

-- 作者ID索引
CREATE INDEX idx_posts_author_id ON posts (author_id);

-- 创建时间索引（用于排序）
CREATE INDEX idx_posts_created_at ON posts (created_at DESC);

-- 组合索引（状态 + 创建时间）
CREATE INDEX idx_posts_status_created_at ON posts (status, created_at DESC);

-- 组合索引（作者ID + 状态 + 创建时间）
CREATE INDEX idx_posts_author_status_created_at ON posts (author_id, status, created_at DESC);
```

### 索引选择策略

1. **单字段索引**：用于单一条件查询
2. **组合索引**：用于多条件组合查询，遵循最左前缀原则
3. **text_pattern_ops**：用于 LIKE 模糊查询优化（PostgreSQL 特有）
4. **DESC 排序索引**：优化 ORDER BY created_at DESC 查询

## Error Handling

### 参数验证

```java
public class QueryParamValidator {
    
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;
    
    /**
     * 验证并规范化页码
     */
    public static int validatePage(int page) {
        return page > 0 ? page : DEFAULT_PAGE;
    }
    
    /**
     * 验证并规范化页面大小
     */
    public static int validateSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
    
    /**
     * 验证并规范化关键词
     */
    public static String validateKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw new ValidationException("关键词长度不能超过" + MAX_KEYWORD_LENGTH + "字符");
        }
        // 转义特殊字符防止 SQL 注入（MyBatis 会自动处理，这里做额外验证）
        return trimmed;
    }
    
    /**
     * 验证状态参数
     */
    public static String validateStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }
        return status.trim().toUpperCase();
    }
}
```

### 异常处理

```java
@Slf4j
public class AdminUserApplicationService {
    
    public PageResult<UserManageDTO> queryUsers(String keyword, String status, int page, int size) {
        try {
            // 参数验证和规范化
            keyword = QueryParamValidator.validateKeyword(keyword);
            status = QueryParamValidator.validateStatus(status);
            page = QueryParamValidator.validatePage(page);
            size = QueryParamValidator.validateSize(size);
            
            // 执行查询
            int offset = (page - 1) * size;
            List<User> users = userRepository.findByConditions(keyword, status, offset, size);
            long total = userRepository.countByConditions(keyword, status);
            
            // 转换为 DTO
            List<UserManageDTO> dtoList = users.stream()
                    .map(this::convertToManageDTO)
                    .collect(Collectors.toList());
            
            return PageResult.of(dtoList, total, page, size);
            
        } catch (ValidationException e) {
            log.warn("Invalid query parameters: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to query users: keyword={}, status={}, page={}, size={}", 
                      keyword, status, page, size, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "查询用户列表失败");
        }
    }
}
```

## Testing Strategy

### 单元测试

#### Repository 层测试

```java
@SpringBootTest
@Transactional
class UserRepositoryImplTest {
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    void testFindByConditions_withKeyword() {
        // Given: 准备测试数据
        createTestUsers();
        
        // When: 使用关键词查询
        List<User> users = userRepository.findByConditions("test", null, 0, 10);
        
        // Then: 验证结果
        assertThat(users).isNotEmpty();
        assertThat(users).allMatch(u -> 
            u.getUserName().contains("test") || u.getEmail().contains("test"));
    }
    
    @Test
    void testFindByConditions_withStatus() {
        // Given: 准备测试数据
        createTestUsers();
        
        // When: 使用状态过滤
        List<User> users = userRepository.findByConditions(null, "ACTIVE", 0, 10);
        
        // Then: 验证结果
        assertThat(users).isNotEmpty();
        assertThat(users).allMatch(u -> u.getStatus() == UserStatus.ACTIVE);
    }
    
    @Test
    void testFindByConditions_withPagination() {
        // Given: 准备50条测试数据
        createTestUsers(50);
        
        // When: 分页查询
        List<User> page1 = userRepository.findByConditions(null, null, 0, 20);
        List<User> page2 = userRepository.findByConditions(null, null, 20, 20);
        
        // Then: 验证分页结果
        assertThat(page1).hasSize(20);
        assertThat(page2).hasSize(20);
        assertThat(page1).doesNotContainAnyElementsOf(page2);
    }
    
    @Test
    void testCountByConditions() {
        // Given: 准备测试数据
        createTestUsers(30);
        
        // When: 统计数量
        long total = userRepository.countByConditions(null, null);
        long activeCount = userRepository.countByConditions(null, "ACTIVE");
        
        // Then: 验证统计结果
        assertThat(total).isEqualTo(30);
        assertThat(activeCount).isLessThanOrEqualTo(total);
    }
}
```

#### Application Service 层测试

```java
@ExtendWith(MockitoExtension.class)
class AdminUserApplicationServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private AdminUserApplicationService adminUserApplicationService;
    
    @Test
    void testQueryUsers_success() {
        // Given
        List<User> mockUsers = createMockUsers(10);
        when(userRepository.findByConditions(any(), any(), anyInt(), anyInt()))
            .thenReturn(mockUsers);
        when(userRepository.countByConditions(any(), any()))
            .thenReturn(10L);
        
        // When
        PageResult<UserManageDTO> result = adminUserApplicationService
            .queryUsers("test", "ACTIVE", 1, 10);
        
        // Then
        assertThat(result.getData()).hasSize(10);
        assertThat(result.getTotal()).isEqualTo(10);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
    }
    
    @Test
    void testQueryUsers_withInvalidPage() {
        // Given
        when(userRepository.findByConditions(any(), any(), anyInt(), anyInt()))
            .thenReturn(Collections.emptyList());
        when(userRepository.countByConditions(any(), any()))
            .thenReturn(0L);
        
        // When: 使用无效页码
        PageResult<UserManageDTO> result = adminUserApplicationService
            .queryUsers(null, null, -1, 10);
        
        // Then: 应该使用默认页码
        verify(userRepository).findByConditions(null, null, 0, 10);
    }
    
    @Test
    void testQueryUsers_withLongKeyword() {
        // Given: 超长关键词
        String longKeyword = "a".repeat(150);
        
        // When & Then: 应该抛出验证异常
        assertThatThrownBy(() -> adminUserApplicationService
            .queryUsers(longKeyword, null, 1, 10))
            .isInstanceOf(ValidationException.class);
    }
}
```

### 性能测试

```java
@SpringBootTest
class AdminServicePerformanceTest {
    
    @Autowired
    private AdminUserApplicationService adminUserApplicationService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    @Disabled("Performance test - run manually")
    void testQueryUsers_performance_100k() {
        // Given: 准备10万条数据
        createTestUsers(100_000);
        
        // When: 执行查询
        long startTime = System.currentTimeMillis();
        PageResult<UserManageDTO> result = adminUserApplicationService
            .queryUsers("test", null, 1, 20);
        long endTime = System.currentTimeMillis();
        
        // Then: 验证性能
        long duration = endTime - startTime;
        assertThat(duration).isLessThan(500); // 应该在500ms内完成
        assertThat(result.getData()).hasSize(20);
    }
}
```

### 集成测试

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AdminUserControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void testQueryUsers_integration() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/admin/users")
                .param("keyword", "test")
                .param("status", "ACTIVE")
                .param("page", "1")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.data").isArray())
            .andExpect(jsonPath("$.data.total").isNumber())
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(20));
    }
}
```

## 性能优化建议

### 1. 查询优化

- 使用 `EXPLAIN ANALYZE` 分析查询计划
- 确保索引被正确使用
- 避免全表扫描

### 2. 缓存策略

```java
@Service
public class AdminUserApplicationService {
    
    @Cacheable(value = "admin:users", key = "#keyword + ':' + #status + ':' + #page + ':' + #size")
    public PageResult<UserManageDTO> queryUsers(String keyword, String status, int page, int size) {
        // ... 查询逻辑
    }
}
```

### 3. 连接池配置

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### 4. 慢查询监控

```yaml
logging:
  level:
    com.ZhiCore.user.infrastructure.repository.mapper: DEBUG
    
mybatis:
  configuration:
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
```

## 部署和迁移策略

### 1. 数据库迁移

使用 Flyway 创建迁移脚本：

```sql
-- V2__add_admin_query_indexes.sql

-- Users 表索引
CREATE INDEX IF NOT EXISTS idx_users_user_name ON users USING btree (user_name text_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_users_email ON users USING btree (email text_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_status_created_at ON users (status, created_at DESC);

-- Posts 表索引
CREATE INDEX IF NOT EXISTS idx_posts_title ON posts USING btree (title text_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_posts_status ON posts (status);
CREATE INDEX IF NOT EXISTS idx_posts_author_id ON posts (author_id);
CREATE INDEX IF NOT EXISTS idx_posts_created_at ON posts (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_status_created_at ON posts (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_author_status_created_at ON posts (author_id, status, created_at DESC);
```

### 2. 灰度发布

1. 部署新版本代码（保持旧实现作为备份）
2. 使用 Feature Toggle 控制新旧实现切换
3. 监控性能指标和错误率
4. 逐步增加新实现的流量比例
5. 确认稳定后完全切换到新实现

### 3. 回滚方案

```java
@Service
public class AdminUserApplicationService {
    
    @Value("${admin.query.use-new-implementation:true}")
    private boolean useNewImplementation;
    
    public PageResult<UserManageDTO> queryUsers(String keyword, String status, int page, int size) {
        if (useNewImplementation) {
            return queryUsersNew(keyword, status, page, size);
        } else {
            return queryUsersOld(keyword, status, page, size);
        }
    }
    
    private PageResult<UserManageDTO> queryUsersNew(String keyword, String status, int page, int size) {
        // 新实现（数据库层过滤）
    }
    
    private PageResult<UserManageDTO> queryUsersOld(String keyword, String status, int page, int size) {
        // 旧实现（内存过滤）- 保留作为备份
    }
}
```

## 监控和告警

### 1. 性能指标

```java
@Service
public class AdminUserApplicationService {
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    public PageResult<UserManageDTO> queryUsers(String keyword, String status, int page, int size) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            PageResult<UserManageDTO> result = // ... 查询逻辑
            sample.stop(Timer.builder("admin.query.users")
                .tag("status", status != null ? status : "all")
                .register(meterRegistry));
            return result;
        } catch (Exception e) {
            meterRegistry.counter("admin.query.users.error").increment();
            throw e;
        }
    }
}
```

### 2. 告警规则

```yaml
# Prometheus 告警规则
groups:
  - name: admin_query_alerts
    rules:
      - alert: AdminQuerySlowResponse
        expr: histogram_quantile(0.95, rate(admin_query_users_seconds_bucket[5m])) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "管理查询响应时间过慢"
          description: "95分位响应时间超过500ms"
      
      - alert: AdminQueryHighErrorRate
        expr: rate(admin_query_users_error_total[5m]) > 0.01
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "管理查询错误率过高"
          description: "错误率超过1%"
```

## 总结

本设计通过以下改进将简化实现升级为生产环境可用的实现：

1. **性能提升**：将过滤和分页逻辑下推到数据库层，避免内存溢出
2. **可扩展性**：支持大数据量查询，响应时间稳定
3. **安全性**：使用参数化查询防止 SQL 注入
4. **可维护性**：遵循 DDD 分层架构，代码结构清晰
5. **向后兼容**：保持 API 接口不变，平滑升级
6. **可观测性**：添加性能监控和告警机制
