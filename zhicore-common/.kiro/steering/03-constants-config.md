---
inclusion: fileMatch
fileMatchPattern: '**/*.{java,yml,yaml,properties}'
---

# 常量与配置管理

[返回索引](./README-zh.md)

---

## 常量管理规范

**使用 Java 常量类（而非配置文件）来管理各类常量字符串。**

### 常量分类

| 常量类型 | 管理方式 | 位置示例 | 示例 |
|---------|---------|---------|------|
| RocketMQ Topics/Tags/Consumer Groups | `TopicConstants.java` | common 模块 | `TopicConstants.POST_TOPIC` |
| Redis Keys | 各服务 `*RedisKeys.java` | 各服务 infrastructure/cache | `UserRedisKeys.userInfo(userId)` |
| 协议内部常量 | 类内部 private static final | 使用该常量的类 | `MESSAGE_DESTINATION = "/queue/messages"` |

### Redis Key 命名规范

```java
public class UserRedisKeys {
    private static final String PREFIX = "user";
    
    // user:{userId}:detail
    public static String userDetail(String userId) {
        return PREFIX + ":" + userId + ":detail";
    }
    
    // user:{userId}:stats:following
    public static String followingCount(String userId) {
        return PREFIX + ":" + userId + ":stats:following";
    }
}
```

Key 命名格式：`{service}:{id}:{entity}:{field}`
- 示例：`user:123:detail`、`post:456:detail`
- 排行榜：`ranking:{type}:{dimension}:{period}`

---

## 避免硬编码规范

**所有可配置的常量必须提取到配置文件中，禁止在代码中硬编码。**

### 需要配置化的常量类型

#### 1. 时间相关常量
```yaml
im:
  auth:
    token:
      access-expire: 7200      # 2小时（秒）
      refresh-expire: 604800   # 7天（秒）
```

#### 2. Redis Key 前缀
```yaml
im:
  cache:
    user:
      key-prefix: "im:user:info:"
      expire: 3600  # 1小时（秒）
```

#### 3. 消息队列配置
```yaml
rocketmq:
  topic:
    message-persist: im-message-persist
  tag:
    single-message: single
```

#### 4. 业务规则常量
```yaml
im:
  group:
    normal:
      max-members: 200
    super:
      max-members: 2000
```

### 配置注入方式

```java
// 方式1：使用 @Value 注解（简单配置）
@Service
public class UserService {
    @Value("${im.cache.user.key-prefix}")
    private String userCacheKeyPrefix;
}

// 方式2：使用 @ConfigurationProperties（复杂配置）
@Component
@ConfigurationProperties(prefix = "im.auth.token")
@Data
public class TokenProperties {
    private String keyPrefix;
    private long accessExpire;
}
```

### 可以保留的硬编码

以下情况的硬编码是合理的：
- 业务状态码：`if (user.getStatus() == 0)`
- 枚举值：使用枚举类
- 算法常量：Snowflake 算法的固定位数

---

**最后更新**：2026-02-01
