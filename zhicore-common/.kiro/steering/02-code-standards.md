---
inclusion: fileMatch
fileMatchPattern: '**/*.java'
---

# 代码规范

[返回索引](./README-zh.md)

---

## 注释语言规范

**所有代码注释必须使用中文编写，除非是英文专业术语。**

### 类级注释
```java
/**
 * RocketMQ 配置类
 * 
 * 配置 RocketMQ 生产者，包括重试设置、超时配置和死信队列支持
 */
@Configuration
public class RocketMQConfig {
```

### 方法级注释
```java
/**
 * 配置 DefaultMQProducer，包括重试设置和超时配置
 * 
 * @param userId 用户ID
 * @return 配置好的 DefaultMQProducer 实例
 * @throws MQClientException 如果生产者初始化失败
 */
@Bean
public DefaultMQProducer defaultMQProducer() throws MQClientException {
```

### 英文专业术语保留规则

可以保留英文的情况：
- **技术术语**：Spring Boot, RocketMQ, Redis, MyBatis-Plus
- **设计模式**：Factory, Singleton, Observer, Strategy
- **架构术语**：DDD, CQRS, Event Sourcing
- **协议名称**：HTTP, TCP, WebSocket, STOMP

---

## Jackson 反序列化规范

**问题**：领域模型使用私有构造函数时，Jackson 无法反序列化。

**解决方案**：使用 `@JsonCreator` 注解

```java
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Post {
    private final String id;
    private final String ownerId;
    
    /**
     * 私有构造函数（从持久化恢复）
     * 使用 @JsonCreator 支持 Jackson 反序列化
     */
    @JsonCreator
    private Post(@JsonProperty("id") String id,
                 @JsonProperty("ownerId") String ownerId,
                 @JsonProperty("title") String title) {
        this.id = id;
        this.ownerId = ownerId;
        this.title = title;
    }
    
    // 工厂方法保持不变
    public static Post createDraft(String id, String ownerId, String title) {
        return new Post(id, ownerId, title);
    }
}
```

### 检查清单
- [ ] 是否使用了私有构造函数？
- [ ] 是否需要缓存到 Redis？
- [ ] 该构造函数是否添加了 `@JsonCreator` 注解？
- [ ] 所有参数是否都添加了 `@JsonProperty` 注解？
- [ ] 嵌套的值对象是否也支持反序列化？

---

**最后更新**：2026-02-01
