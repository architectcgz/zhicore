# Spring Boot 组件扫描问题排查指南

## 问题描述

服务启动失败，报错：

```
Error creating bean with name 'xxxApplicationService': 
Unsatisfied dependency expressed through constructor parameter X: 
Error creating bean with name 'com.blog.api.client.LeafServiceClient': 
FactoryBean threw exception on object creation
```

或者：

```
Could not autowire. No beans of 'LeafServiceFallbackFactory' type found.
```

## 根本原因

**Spring Boot 的 `@SpringBootApplication` 注解默认只扫描当前包及其子包**。如果你的 Bean（如 `@Component`、`@Service`、`@Repository`）定义在其他包中，Spring 无法发现并创建这些 Bean。

### 问题场景

在多模块项目中，当：
1. 服务模块（如 `blog-user`）依赖 API 模块（`blog-api`）
2. API 模块中有 `@Component` 类（如 `LeafServiceFallbackFactory`）
3. 服务模块的 `@SpringBootApplication` 没有扫描 API 模块的包

就会导致 Spring 找不到 API 模块中的 Bean。

## 问题分析

### Spring Boot 启动流程

```
1. 扫描指定包 
   ↓
2. 发现 @Component/@Service 等注解的类
   ↓
3. 创建 Bean 并注册到容器
   ↓
4. 处理依赖注入
```

### 错误配置示例

```java
@SpringBootApplication(scanBasePackages = {"com.blog.user", "com.blog.common"})
@EnableFeignClients(basePackages = "com.blog.api.client")
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
```

**问题**：
- ✅ 扫描了 `com.blog.user` 和 `com.blog.common`
- ❌ **没有扫描 `com.blog.api`**
- 结果：`LeafServiceFallbackFactory` 在 `com.blog.api.client.fallback` 包中，Spring 看不到它

### 依赖链分析

```
CheckInApplicationService (需要注入)
    ↓
LeafServiceClient (Feign 客户端)
    ↓ 配置了 fallbackFactory
LeafServiceFallbackFactory (@Component)
    ↓ 需要被 Spring 扫描
@SpringBootApplication(scanBasePackages = ?)
```

如果 `scanBasePackages` 不包含 `com.blog.api`，链条在最后一步断裂。

## 解决方案

### 方案 1：添加包扫描（推荐）

```java
@SpringBootApplication(scanBasePackages = {
    "com.blog.user",      // 当前服务
    "com.blog.common",    // 公共模块
    "com.blog.api"        // API 模块 ← 添加这个！
})
@EnableFeignClients(basePackages = {
    "com.blog.api.client",                    // API 模块的 Feign 客户端
    "com.blog.user.infrastructure.feign"      // 当前服务的 Feign 客户端
})
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
```

### 方案 2：使用 @ComponentScan（等效）

```java
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.blog.user",
    "com.blog.common",
    "com.blog.api"
})
@EnableFeignClients(basePackages = {
    "com.blog.api.client",
    "com.blog.user.infrastructure.feign"
})
public class UserApplication { ... }
```

## 两个注解的区别

### @SpringBootApplication vs @EnableFeignClients

| 注解 | 作用 | 扫描对象 |
|------|------|----------|
| `@SpringBootApplication(scanBasePackages)` | 让 Spring 扫描并创建 Bean | `@Component`、`@Service`、`@Repository`、`@Configuration` 等 |
| `@EnableFeignClients(basePackages)` | 让 Feign 创建客户端代理 | `@FeignClient` 接口 |

**关键点**：
- `@EnableFeignClients` **不会**扫描 `@Component` 类
- `@SpringBootApplication` **不会**扫描 `@FeignClient` 接口
- **两者互不替代，需要同时配置**

### 为什么需要两个配置

```java
// 1. Feign 需要找到接口
@FeignClient(name = "blog-leaf", fallbackFactory = LeafServiceFallbackFactory.class)
public interface LeafServiceClient { ... }

// 2. Spring 需要找到 FallbackFactory
@Component  // ← 这个注解需要 @SpringBootApplication 扫描
public class LeafServiceFallbackFactory implements FallbackFactory<LeafServiceClient> { ... }
```

## 验证方法

### 1. 检查启动日志

**修复前**：
```
Error creating bean with name 'checkInApplicationService': 
Unsatisfied dependency... LeafServiceFallbackFactory
```

**修复后**：
```
INFO  o.s.c.o.FeignClientFactoryBean - For 'blog-leaf' URL not provided
INFO  o.s.b.w.e.tomcat.TomcatWebServer - Tomcat started on port 8081
INFO  c.a.c.n.r.NacosServiceRegistry - nacos registry, BLOG_SERVICE blog-user 192.168.7.1:8081 register finished
INFO  com.blog.user.UserApplication - Started UserApplication in 18.535 seconds
```

### 2. 检查 Bean 是否创建

```java
@SpringBootApplication(scanBasePackages = {"com.blog.user", "com.blog.common", "com.blog.api"})
public class UserApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(UserApplication.class, args);
        
        // 验证 Bean 是否存在
        LeafServiceFallbackFactory factory = context.getBean(LeafServiceFallbackFactory.class);
        System.out.println("LeafServiceFallbackFactory found: " + factory);
    }
}
```

## 常见错误模式

### 错误 1：只配置了 @EnableFeignClients

```java
// ❌ 错误
@SpringBootApplication  // 默认只扫描 com.blog.user
@EnableFeignClients(basePackages = "com.blog.api.client")  // 只扫描 @FeignClient
```

**问题**：Feign 客户端能创建，但 FallbackFactory 找不到。

### 错误 2：混淆了两个注解的作用

```java
// ❌ 错误 - 以为 @EnableFeignClients 会扫描所有类
@SpringBootApplication(scanBasePackages = "com.blog.user")
@EnableFeignClients(basePackages = "com.blog.api")  // 这只扫描 @FeignClient！
```

**问题**：`@EnableFeignClients` 不会扫描 `@Component` 类。

### 错误 3：忘记添加依赖模块的包

```java
// ❌ 错误 - 忘记扫描 com.blog.api
@SpringBootApplication(scanBasePackages = {"com.blog.user", "com.blog.common"})
@EnableFeignClients(basePackages = "com.blog.api.client")
```

**问题**：API 模块的 `@Component` 类不会被扫描。

## 项目中的应用

### 已修复的服务

| 服务 | 端口 | 修复内容 |
|------|------|----------|
| blog-user | 8081 | 添加 `"com.blog.api"` 到 scanBasePackages |
| blog-comment | 8083 | 添加 `"com.blog.api"` 到 scanBasePackages |
| blog-message | 8086 | 添加 `"com.blog.api"` 到 scanBasePackages |
| blog-notification | 8086 | 添加 `"com.blog.api"` 到 scanBasePackages |
| blog-admin | 8090 | 添加 `"com.blog.api"` 到 scanBasePackages |
| blog-post | 8082 | 已包含 `"com.blog.api"` |

### 标准配置模板

```java
@SpringBootApplication(scanBasePackages = {
    "com.blog.{service}",              // 当前服务包
    "com.blog.common",                 // 公共模块
    "com.blog.api"                     // API 模块（包含共享的 FallbackFactory）
})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {
    "com.blog.api.client",                          // API 模块的 Feign 客户端
    "com.blog.{service}.infrastructure.feign"       // 当前服务的 Feign 客户端
})
@MapperScan("com.blog.{service}.infrastructure.repository.mapper")
public class {Service}Application {
    public static void main(String[] args) {
        SpringApplication.run({Service}Application.class, args);
    }
}
```

## 预防措施

### 1. 新服务创建检查清单

创建新服务时，确保：
- [ ] `scanBasePackages` 包含所有依赖模块的包
- [ ] `@EnableFeignClients` 包含所有 Feign 客户端的包
- [ ] 编译通过后，启动服务验证 Bean 创建

### 2. 代码审查要点

审查 `*Application.java` 时检查：
- [ ] 是否使用了 `blog-api` 模块？
- [ ] 如果使用了，`scanBasePackages` 是否包含 `"com.blog.api"`？
- [ ] `@EnableFeignClients` 是否包含所有需要的包？

### 3. 依赖分析

如果服务依赖其他模块的 `@Component` 类：
1. 检查 `pom.xml` 中的依赖
2. 确保 `scanBasePackages` 包含这些模块的包

## 相关资源

- [Spring Boot 官方文档 - Component Scanning](https://docs.spring.io/spring-boot/docs/current/reference/html/using.html#using.structuring-your-code)
- [Spring Cloud OpenFeign 文档](https://docs.spring.io/spring-cloud-openfeign/docs/current/reference/html/)
- 项目修复记录：`config/nacos/ENCODING_FIX_SUMMARY.md`

## 总结

**记住这个简单规则**：

> 如果你的服务依赖其他模块的 `@Component`/`@Service`/`@Repository` 类，
> 必须在 `@SpringBootApplication(scanBasePackages)` 中包含那个模块的包。

**图书馆类比**：
- Spring 是图书管理员
- `scanBasePackages` 告诉管理员在哪些区域找书
- 如果你要的书在 C 区，但管理员只在 A 区和 B 区找，永远找不到
- 解决方法：告诉管理员也去 C 区找（添加 `"com.blog.api"` 到扫描列表）
