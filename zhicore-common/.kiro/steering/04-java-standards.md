---
inclusion: fileMatch
fileMatchPattern: '**/*.java'
---

# Java 编码标准

[返回索引](./README-zh.md)

---

## Jakarta EE 迁移（Spring Boot 3.x+）

### 验证注解

```java
// ✅ 正确 - 使用 jakarta 包
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;

@Data
public class LoginReq {
    @NotNull(message = "应用ID不能为空")
    private Integer appId;
    
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}

// ❌ 错误 - 使用 javax 包（已过时）
import javax.validation.constraints.NotBlank;
```

### 包迁移对照表

| 旧包名 (javax.*) | 新包名 (jakarta.*) |
|-----------------|-------------------|
| `javax.validation.*` | `jakarta.validation.*` |
| `javax.servlet.*` | `jakarta.servlet.*` |
| `javax.persistence.*` | `jakarta.persistence.*` |
| `javax.annotation.*` | `jakarta.annotation.*` |

---

## 避免硬编码 - 使用枚举

```java
// ✅ 正确 - 定义枚举
@Getter
public enum UserStatus {
    NORMAL(0, "正常"),
    FORBIDDEN(1, "禁用");
    
    private final int code;
    private final String description;
    
    UserStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public static UserStatus fromCode(int code) {
        for (UserStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的用户状态码: " + code);
    }
}

// ✅ 正确 - 使用枚举
entity.setStatus(UserStatus.NORMAL.getCode());

// ❌ 错误 - 硬编码数字
entity.setStatus(0);  // 0是什么意思？
```

---

## 异常处理

```java
// ✅ 正确 - 使用统一的业务异常
if (user == null) {
    throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
}

// ❌ 错误 - 使用通用异常
if (user == null) {
    throw new RuntimeException("用户不存在");
}
```

---

## Optional 使用规范

### 允许的用法

```java
// ✅ 正确 - 作为方法返回值
public Optional<User> findById(Long id) {
    return Optional.ofNullable(userRepository.findById(id));
}

// ✅ 正确 - 链式处理
String userName = findById(1L)
    .map(User::getName)
    .orElse("未知用户");

// ✅ 正确 - 条件执行
findById(1L).ifPresent(user -> {
    log.info("找到用户: {}", user.getName());
});
```

### 禁止的用法

```java
// ❌ 错误 - 作为方法参数
public void update(Long id, Optional<String> name) { }

// ❌ 错误 - 作为类字段
public class User {
    private Optional<String> nickname;  // 禁止
}

// ❌ 错误 - 在集合中使用
List<Optional<User>> users;  // 禁止

// ❌ 错误 - 使用 get() 而不检查
Optional<User> opt = findById(1L);
User user = opt.get();  // 可能抛出 NoSuchElementException

// ✅ 正确 - 使用安全方法
User user = opt.orElseThrow(() -> new BusinessException("用户不存在"));
```

---

## Stream API 规范

### 链式调用长度限制

```java
// ❌ 错误 - 链式调用过长（超过 5 步）
users.stream()
    .filter(u -> u.getStatus() == 1)
    .filter(u -> u.getAge() > 18)
    .map(u -> u.getName())
    .map(String::toUpperCase)
    .filter(n -> n.startsWith("A"))
    .sorted()
    .distinct()
    .collect(Collectors.toList());

// ✅ 正确 - 拆分为多个步骤或提取方法
List<User> activeAdults = users.stream()
    .filter(this::isActiveAdult)
    .collect(Collectors.toList());

List<String> names = activeAdults.stream()
    .map(User::getName)
    .map(String::toUpperCase)
    .filter(n -> n.startsWith("A"))
    .distinct()
    .sorted()
    .collect(Collectors.toList());

private boolean isActiveAdult(User user) {
    return user.getStatus() == 1 && user.getAge() > 18;
}
```

### 并行流使用

```java
// ❌ 错误 - 小数据量使用并行流
List<String> names = smallList.parallelStream()  // smallList.size() < 1000
    .map(User::getName)
    .collect(Collectors.toList());

// ✅ 正确 - 大数据量且 CPU 密集型操作使用并行流
List<Result> results = largeList.parallelStream()  // largeList.size() > 10000
    .map(this::heavyComputation)  // CPU 密集型
    .collect(Collectors.toList());

// ❌ 错误 - 并行流中有共享状态
List<String> result = new ArrayList<>();
users.parallelStream().forEach(u -> result.add(u.getName()));  // 线程不安全

// ✅ 正确 - 使用 collect
List<String> result = users.parallelStream()
    .map(User::getName)
    .collect(Collectors.toList());
```

---

## Null 安全规范

### 注解使用

```java
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

public class UserService {
    
    /**
     * 根据 ID 查找用户
     * @param id 用户 ID（不能为空）
     * @return 用户对象，可能为空
     */
    @Nullable
    public User findById(@NonNull Long id) {
        // ...
    }
    
    /**
     * 保存用户
     * @param user 用户对象（不能为空）
     * @return 保存后的用户（不为空）
     */
    @NonNull
    public User save(@NonNull User user) {
        // ...
    }
}
```

### 防御性编程

```java
// ✅ 正确 - 方法入口校验
public void updateUser(@NonNull Long id, @NonNull UpdateReq req) {
    Objects.requireNonNull(id, "用户 ID 不能为空");
    Objects.requireNonNull(req, "更新请求不能为空");
    
    // 业务逻辑
}

// ✅ 正确 - 使用工具类
import org.springframework.util.Assert;

public void createOrder(OrderReq req) {
    Assert.notNull(req, "订单请求不能为空");
    Assert.hasText(req.getProductId(), "商品 ID 不能为空");
    Assert.isTrue(req.getQuantity() > 0, "数量必须大于 0");
}
```

---

## Lombok 使用规范

### 推荐使用

| 注解 | 用途 | 推荐场景 |
|------|------|---------|
| `@Getter/@Setter` | 生成 getter/setter | 所有 POJO |
| `@ToString` | 生成 toString | 调试用 DTO |
| `@EqualsAndHashCode` | 生成 equals/hashCode | 值对象 |
| `@RequiredArgsConstructor` | 生成构造函数 | 依赖注入 |
| `@Slf4j` | 生成日志对象 | 所有类 |

### 谨慎使用

```java
// ⚠️ 谨慎 - @Data 包含所有字段的 equals/hashCode
@Data  // 可能导致循环引用问题
public class User {
    private Long id;
    private List<Order> orders;  // 双向关联时危险
}

// ✅ 正确 - 明确指定
@Getter
@Setter
@ToString(exclude = "orders")
@EqualsAndHashCode(of = "id")  // 只使用 ID
public class User {
    private Long id;
    private List<Order> orders;
}
```

### @Builder 陷阱

```java
// ⚠️ 问题 - 没有默认值
@Builder
public class CreateUserReq {
    private String name;
    private Integer status;  // 默认 null，不是 0
}

// ✅ 正确 - 使用 @Builder.Default
@Builder
public class CreateUserReq {
    private String name;
    
    @Builder.Default
    private Integer status = 0;
    
    @Builder.Default
    private List<String> tags = new ArrayList<>();
}
```

### 禁止使用

```java
// ❌ 禁止 - @AllArgsConstructor 用于依赖注入
@AllArgsConstructor  // 顺序敏感，容易出错
@Service
public class UserService {
    private final UserRepository userRepository;
    private final EmailService emailService;
}

// ✅ 正确 - 使用 @RequiredArgsConstructor
@RequiredArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;
    private final EmailService emailService;
}
```

---

**最后更新**：2026-02-01
