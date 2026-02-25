---
inclusion: always
---

# Jackson 反序列化规范

## 问题背景

在使用 Redis 缓存领域模型对象时，如果领域模型使用了**私有构造函数 + 工厂方法**模式（充血模型/DDD），会导致 Jackson 无法反序列化 JSON，出现以下错误：

```
Could not read JSON: Cannot construct instance of `com.zhicore.post.domain.model.Post` 
(no Creators, like default constructor, exist): cannot deserialize from Object value 
(no delegate- or property-based Creator)
```

## 核心原因

Jackson 默认需要以下之一才能反序列化对象：
1. 无参构造函数（public 或 package-private）
2. 带 `@JsonCreator` 注解的构造函数
3. 带 `@JsonCreator` 注解的静态工厂方法

当领域模型使用私有构造函数时，Jackson 无法访问，导致反序列化失败。

## 解决方案

### ✅ 推荐方案：使用 @JsonCreator 注解

在用于持久化恢复的构造函数上添加 `@JsonCreator` 和 `@JsonProperty` 注解：

```java
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Post {
    private final String id;
    private final String ownerId;
    private String title;
    // ... 其他字段

    /**
     * 私有构造函数（从持久化恢复）
     * 使用 @JsonCreator 支持 Jackson 反序列化
     */
    @JsonCreator
    private Post(@JsonProperty("id") String id,
                 @JsonProperty("ownerId") String ownerId,
                 @JsonProperty("title") String title,
                 @JsonProperty("raw") String raw,
                 @JsonProperty("html") String html,
                 @JsonProperty("excerpt") String excerpt,
                 @JsonProperty("status") PostStatus status,
                 @JsonProperty("topicId") String topicId,
                 @JsonProperty("publishedAt") LocalDateTime publishedAt,
                 @JsonProperty("scheduledAt") LocalDateTime scheduledAt,
                 @JsonProperty("createdAt") LocalDateTime createdAt,
                 @JsonProperty("updatedAt") LocalDateTime updatedAt,
                 @JsonProperty("stats") PostStats stats) {
        this.id = id;
        this.ownerId = ownerId;
        this.title = title;
        // ... 初始化其他字段
    }

    // 工厂方法保持不变
    public static Post createDraft(String id, String ownerId, String title, String content) {
        return new Post(id, ownerId, title, content);
    }
}
```

### ❌ 不推荐方案：添加无参构造函数

虽然可以添加无参构造函数解决问题，但会破坏领域模型的不变性约束：

```java
// 不推荐：破坏了领域模型设计
protected Post() {
    // Jackson 反序列化用
}
```

## 适用场景

以下情况需要添加 `@JsonCreator` 注解：

1. **领域模型对象**（DDD 聚合根、实体、值对象）
   - 使用私有构造函数
   - 通过工厂方法创建
   - 需要缓存到 Redis

2. **值对象**（不可变对象）
   - 所有字段都是 final
   - 只有带参构造函数
   - 需要序列化/反序列化

## 检查清单

当创建或修改领域模型时，检查以下内容：

- [ ] 是否使用了私有构造函数？
- [ ] 是否需要缓存到 Redis？
- [ ] 是否有用于持久化恢复的构造函数？
- [ ] 该构造函数是否添加了 `@JsonCreator` 注解？
- [ ] 所有参数是否都添加了 `@JsonProperty` 注解？
- [ ] 嵌套的值对象是否也支持反序列化？

## 常见错误

### 错误 1：忘记为嵌套对象添加注解

```java
// 错误：PostStats 也需要 @JsonCreator
public class Post {
    @JsonCreator
    private Post(@JsonProperty("stats") PostStats stats) {
        this.stats = stats;
    }
}

// PostStats 缺少 @JsonCreator，反序列化会失败
public class PostStats {
    private final int likeCount;
    
    public PostStats(int likeCount) {  // 缺少 @JsonCreator
        this.likeCount = likeCount;
    }
}
```

**修复**：为 `PostStats` 也添加 `@JsonCreator`：

```java
public class PostStats {
    @JsonCreator
    public PostStats(@JsonProperty("likeCount") int likeCount) {
        this.likeCount = likeCount;
    }
}
```

### 错误 2：参数名不匹配

```java
// 错误：@JsonProperty 名称与 JSON 字段不匹配
@JsonCreator
private Post(@JsonProperty("postId") String id) {  // JSON 中是 "id"，不是 "postId"
    this.id = id;
}
```

**修复**：确保 `@JsonProperty` 值与 JSON 字段名一致：

```java
@JsonCreator
private Post(@JsonProperty("id") String id) {
    this.id = id;
}
```

### 错误 3：多个构造函数都标记了 @JsonCreator

```java
// 错误：只能有一个 @JsonCreator
@JsonCreator
private Post(String id, String ownerId) { }

@JsonCreator  // 编译错误：不能有多个 @JsonCreator
private Post(String id, String ownerId, String title) { }
```

**修复**：只在用于反序列化的构造函数上添加 `@JsonCreator`。

## 测试验证

添加 `@JsonCreator` 后，应该测试序列化和反序列化：

```java
@Test
void testJsonSerialization() {
    // 创建对象
    Post post = Post.createDraft("1", "user1", "Title", "Content");
    
    // 序列化
    String json = objectMapper.writeValueAsString(post);
    
    // 反序列化
    Post deserialized = objectMapper.readValue(json, Post.class);
    
    // 验证
    assertEquals(post.getId(), deserialized.getId());
    assertEquals(post.getTitle(), deserialized.getTitle());
}
```

## 性能影响

使用 `@JsonCreator` 对性能的影响：
- ✅ 序列化性能：无影响
- ✅ 反序列化性能：轻微影响（需要反射调用构造函数）
- ✅ 内存占用：无影响

在高并发场景下，反序列化性能影响可忽略不计。

## 相关文档

- [Jackson Annotations](https://github.com/FasterXML/jackson-annotations)
- [DDD 领域模型设计](https://martinfowler.com/bliki/DomainDrivenDesign.html)
- [Redis 缓存最佳实践](../docs/redis-best-practices.md)

## 历史问题记录

### 2026-01-21: Post 模型反序列化失败

**问题**：压测时发现 99% 请求失败，日志显示：
```
Cache lookup failed, falling back to database: Could not read JSON: 
Cannot construct instance of `com.zhicore.post.domain.model.Post`
```

**原因**：`Post` 和 `PostStats` 类使用私有构造函数，缺少 `@JsonCreator` 注解。

**影响**：
- Redis 缓存完全失效
- 所有请求都回源数据库
- 数据库连接池耗尽
- 系统错误率 98.88%

**修复**：
1. 为 `Post` 的持久化恢复构造函数添加 `@JsonCreator` 和 `@JsonProperty`
2. 为 `PostStats` 构造函数添加 `@JsonCreator` 和 `@JsonProperty`
3. 重新编译部署

**教训**：
- 领域模型使用私有构造函数时，必须考虑序列化需求
- 缓存失败应该有降级策略，而不是直接失败
- 压测前应该验证缓存是否正常工作

---

**记住：任何需要缓存到 Redis 的领域模型，如果使用了私有构造函数，都必须添加 @JsonCreator 注解！**
