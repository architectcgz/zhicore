---
inclusion: fileMatch
fileMatchPattern: '**/*{Test,test}*.java'
---

# 单元测试规范

[返回索引](./README-zh.md)

---

## 测试命名规范

### 方法命名格式

**格式**：`should_期望结果_when_条件`

```java
// [PASS] 正确 - 清晰表达测试意图
@Test
void should_return_user_when_user_exists() { }

@Test
void should_throw_exception_when_user_not_found() { }

@Test
void should_create_order_successfully_when_stock_sufficient() { }

// [FAIL] 错误 - 无意义的命名
@Test
void test1() { }

@Test
void testUser() { }

@Test
void userTest() { }
```

### 测试类命名

```
[被测类名]Test.java          // 单元测试
[被测类名]IntegrationTest.java  // 集成测试
```

---

## 测试结构（Given-When-Then/AAA 模式）

```java
@Test
void should_return_user_when_user_exists() {
    // Given（Arrange）- 准备测试数据
    Long userId = 1L;
    User expectedUser = new User(userId, "张三");
    when(userRepository.findById(userId)).thenReturn(Optional.of(expectedUser));
    
    // When（Act）- 执行被测方法
    User actualUser = userService.getUser(userId);
    
    // Then（Assert）- 验证结果
    assertThat(actualUser).isNotNull();
    assertThat(actualUser.getName()).isEqualTo("张三");
    verify(userRepository, times(1)).findById(userId);
}
```

---

## Mock 使用规范

### 基本用法

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private EmailService emailService;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    void should_send_welcome_email_when_user_registered() {
        // Given
        User user = new User(1L, "test@example.com");
        when(userRepository.save(any(User.class))).thenReturn(user);
        
        // When
        userService.register(user);
        
        // Then
        verify(emailService).sendWelcomeEmail("test@example.com");
    }
}
```

### Mock vs Spy

| 类型 | 用途 | 行为 |
|------|------|------|
| **@Mock** | 完全模拟对象 | 所有方法返回默认值（null/0/false） |
| **@Spy** | 部分模拟对象 | 真实方法执行，可选择性 stub |
| **@InjectMocks** | 被测对象 | 自动注入 Mock 依赖 |

```java
@Spy
private UserValidator userValidator;  // 使用真实实现

@Test
void should_validate_user() {
    // 部分 mock：只 stub 特定方法
    doReturn(true).when(userValidator).isEmailValid(anyString());
    
    // 其他方法使用真实实现
}
```

### 禁止事项

```java
// [FAIL] 错误 - Mock 被测类本身
@Mock
private UserService userService;  // 不要 Mock 被测类

// [FAIL] 错误 - 过度 Mock（Mock 简单对象）
@Mock
private User user;  // 简单 POJO 不需要 Mock

// [PASS] 正确 - 直接创建简单对象
User user = new User(1L, "张三");
```

---

## 断言规范

### 推荐使用 AssertJ

```java
// [PASS] 推荐 - AssertJ 流式断言
import static org.assertj.core.api.Assertions.*;

@Test
void should_return_user_list() {
    List<User> users = userService.findAll();
    
    assertThat(users)
        .isNotNull()
        .hasSize(3)
        .extracting(User::getName)
        .containsExactly("张三", "李四", "王五");
}

@Test
void should_throw_exception() {
    assertThatThrownBy(() -> userService.getUser(999L))
        .isInstanceOf(BusinessException.class)
        .hasMessage("用户不存在");
}

// [FAIL] 不推荐 - JUnit 原生断言（可读性差）
assertEquals(3, users.size());
assertNotNull(users);
```

### 常用断言

```java
// 对象断言
assertThat(user).isNotNull();
assertThat(user).isEqualTo(expectedUser);
assertThat(user.getName()).isEqualTo("张三");

// 集合断言
assertThat(list).isEmpty();
assertThat(list).hasSize(3);
assertThat(list).contains(item1, item2);
assertThat(list).containsExactly(item1, item2, item3);

// 异常断言
assertThatThrownBy(() -> methodThatThrows())
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("参数错误");

// 数值断言
assertThat(result).isGreaterThan(0);
assertThat(result).isBetween(1, 100);
```

---

## 测试覆盖率

### 覆盖率要求

| 模块类型 | 行覆盖率 | 分支覆盖率 |
|---------|---------|-----------|
| **核心业务** | ≥ 80% | ≥ 70% |
| **通用工具** | ≥ 70% | ≥ 60% |
| **Controller** | ≥ 60% | ≥ 50% |
| **配置类** | 可豁免 | 可豁免 |

### 不需要测试的代码

- getter/setter（Lombok 生成）
- 配置类（@Configuration）
- 启动类（Application.java）
- 纯 DTO/VO 类

---

## 集成测试

### Spring Boot 集成测试

```java
@SpringBootTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
class UserServiceIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = 
        new PostgreSQLContainer<>("postgres:15");
    
    @Autowired
    private UserService userService;
    
    @Test
    void should_save_and_find_user() {
        // Given
        User user = new User("张三", "test@example.com");
        
        // When
        User saved = userService.save(user);
        User found = userService.findById(saved.getId());
        
        // Then
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("张三");
    }
}
```

### Testcontainers 使用

```java
@Testcontainers
class RedisIntegrationTest {
    
    @Container
    static GenericContainer<?> redis = 
        new GenericContainer<>("redis:7")
            .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
    }
}
```

---

## 测试数据管理

### 测试数据隔离

```java
@Test
void should_create_user() {
    // 使用时间戳保证唯一性
    String uniqueEmail = "test_" + System.currentTimeMillis() + "@example.com";
    
    User user = new User("测试用户", uniqueEmail);
    User saved = userService.save(user);
    
    assertThat(saved.getId()).isNotNull();
}
```

### 测试夹具（Test Fixtures）

```java
public class UserFixture {
    
    public static User createDefaultUser() {
        return User.builder()
            .name("默认用户")
            .email("default@example.com")
            .status(UserStatus.ACTIVE)
            .build();
    }
    
    public static User createUserWithName(String name) {
        return User.builder()
            .name(name)
            .email(name.toLowerCase() + "@example.com")
            .status(UserStatus.ACTIVE)
            .build();
    }
}

// 使用
@Test
void should_update_user() {
    User user = UserFixture.createDefaultUser();
    // ...
}
```

---

## 测试分类与执行

### Maven 配置

```xml
<profiles>
    <!-- 单元测试（默认） -->
    <profile>
        <id>unit-tests</id>
        <activation>
            <activeByDefault>true</activeByDefault>
        </activation>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration>
                        <excludes>
                            <exclude>**/*IntegrationTest.java</exclude>
                        </excludes>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
    
    <!-- 集成测试 -->
    <profile>
        <id>integration-tests</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-failsafe-plugin</artifactId>
                    <configuration>
                        <includes>
                            <include>**/*IntegrationTest.java</include>
                        </includes>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

### 执行命令

```bash
# 运行单元测试
mvn test

# 运行集成测试
mvn verify -Pintegration-tests

# 运行所有测试
mvn verify -Punit-tests -Pintegration-tests
```

---

**最后更新**：2026-02-01
