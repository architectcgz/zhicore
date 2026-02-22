# 错误处理与容错设计

## 全局异常处理

### 异常层次结构

```java
// 基础业务异常
public class BusinessException extends RuntimeException {
    private final String errorCode;
    private final String message;
    
    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.message = message;
    }
}

// 领域异常
public class DomainException extends BusinessException {
    public DomainException(String message) {
        super("DOMAIN_ERROR", message);
    }
}

// 资源不存在异常
public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super("NOT_FOUND", resourceType + " not found: " + resourceId);
    }
}

// 权限不足异常
public class ForbiddenException extends BusinessException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", message);
    }
}

// 参数校验异常
public class ValidationException extends BusinessException {
    private final List<FieldError> fieldErrors;
    
    public ValidationException(List<FieldError> fieldErrors) {
        super("VALIDATION_ERROR", "参数校验失败");
        this.fieldErrors = fieldErrors;
    }
}
```


### 全局异常处理器

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage());
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }
    
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<?>> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(ValidationException e) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(e.getErrorCode(), e.getMessage(), e.getFieldErrors()));
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e) {
        List<FieldError> errors = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
            .collect(Collectors.toList());
        return ResponseEntity.badRequest()
            .body(ApiResponse.error("VALIDATION_ERROR", "参数校验失败", errors));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleUnknown(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("INTERNAL_ERROR", "服务器内部错误"));
    }
}
```

---

## 统一错误码定义

> **设计说明：统一错误码规范**
> 
> 所有服务使用统一的错误码格式，便于前端处理和问题排查。
> 错误码格式：`{服务代码}{错误类型}{序号}`
> - 服务代码：1xxx=通用，2xxx=用户，3xxx=文章，4xxx=评论，5xxx=消息，6xxx=通知
> - 错误类型：0xx=系统错误，1xx=参数错误，2xx=业务错误，3xx=权限错误

```java
// common-core 模块
package com.zhicore.common.exception;

public enum ErrorCode {
    
    // ==================== 通用错误 1xxx ====================
    INTERNAL_ERROR("1000", "服务器内部错误"),
    PARAM_INVALID("1001", "参数无效"),
    PARAM_MISSING("1002", "缺少必要参数"),
    REQUEST_TOO_FREQUENT("1003", "请求过于频繁"),
    SERVICE_UNAVAILABLE("1004", "服务暂时不可用"),
    DATA_NOT_FOUND("1005", "数据不存在"),
    DATA_ALREADY_EXISTS("1006", "数据已存在"),
    OPERATION_FAILED("1007", "操作失败"),
    
    // ==================== 用户服务 2xxx ====================
    USER_NOT_FOUND("2001", "用户不存在"),
    USER_ALREADY_EXISTS("2002", "用户已存在"),
    EMAIL_ALREADY_EXISTS("2003", "邮箱已被注册"),
    USERNAME_ALREADY_EXISTS("2004", "用户名已被使用"),
    PASSWORD_INCORRECT("2005", "密码错误"),
    USER_DISABLED("2006", "用户已被禁用"),
    TOKEN_INVALID("2007", "Token无效"),
    TOKEN_EXPIRED("2008", "Token已过期"),
    LOGIN_REQUIRED("2009", "请先登录"),
    FOLLOW_SELF_NOT_ALLOWED("2010", "不能关注自己"),
    ALREADY_FOLLOWED("2011", "已经关注"),
    NOT_FOLLOWED("2012", "尚未关注"),
    USER_BLOCKED("2013", "用户已被拉黑"),
    ALREADY_CHECKED_IN("2014", "今日已签到"),
    
    // ==================== 文章服务 3xxx ====================
    POST_NOT_FOUND("3001", "文章不存在"),
    POST_ALREADY_PUBLISHED("3002", "文章已发布"),
    POST_NOT_PUBLISHED("3003", "文章未发布"),
    POST_ALREADY_DELETED("3004", "文章已删除"),
    POST_TITLE_EMPTY("3005", "文章标题不能为空"),
    POST_CONTENT_EMPTY("3006", "文章内容不能为空"),
    POST_TITLE_TOO_LONG("3007", "文章标题过长"),
    ALREADY_LIKED("3008", "已经点赞"),
    NOT_LIKED("3009", "尚未点赞"),
    ALREADY_FAVORITED("3010", "已经收藏"),
    NOT_FAVORITED("3011", "尚未收藏"),
    CATEGORY_NOT_FOUND("3012", "分类不存在"),
    
    // ==================== 评论服务 4xxx ====================
    COMMENT_NOT_FOUND("4001", "评论不存在"),
    COMMENT_ALREADY_DELETED("4002", "评论已删除"),
    COMMENT_CONTENT_EMPTY("4003", "评论内容不能为空"),
    COMMENT_CONTENT_TOO_LONG("4004", "评论内容过长"),
    ROOT_COMMENT_NOT_FOUND("4005", "根评论不存在"),
    REPLY_TO_COMMENT_NOT_FOUND("4006", "被回复的评论不存在"),
    COMMENT_ALREADY_LIKED("4007", "已经点赞该评论"),
    COMMENT_NOT_LIKED("4008", "尚未点赞该评论"),
    
    // ==================== 消息服务 5xxx ====================
    CONVERSATION_NOT_FOUND("5001", "会话不存在"),
    MESSAGE_NOT_FOUND("5002", "消息不存在"),
    MESSAGE_ALREADY_RECALLED("5003", "消息已撤回"),
    MESSAGE_RECALL_TIMEOUT("5004", "消息发送超过2分钟，无法撤回"),
    MESSAGE_CONTENT_EMPTY("5005", "消息内容不能为空"),
    MESSAGE_CONTENT_TOO_LONG("5006", "消息内容过长"),
    CANNOT_MESSAGE_SELF("5007", "不能给自己发消息"),
    USER_BLOCKED_CANNOT_MESSAGE("5008", "对方已将你拉黑，无法发送消息"),
    
    // ==================== 通知服务 6xxx ====================
    NOTIFICATION_NOT_FOUND("6001", "通知不存在"),
    
    // ==================== 权限错误 x3xx ====================
    PERMISSION_DENIED("1301", "权限不足"),
    ROLE_REQUIRED("1302", "需要特定角色"),
    RESOURCE_ACCESS_DENIED("1303", "无权访问该资源"),
    OPERATION_NOT_ALLOWED("1304", "不允许执行此操作");
    
    private final String code;
    private final String message;
    
    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
}

// 使用示例
public class PostService {
    public Post getPost(Long postId) {
        Post post = postRepository.findById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }
}
```

---

## Sentinel 熔断降级

### 熔断配置

```java
@Configuration
public class SentinelConfig {
    
    @PostConstruct
    public void initRules() {
        // 熔断规则
        List<DegradeRule> degradeRules = new ArrayList<>();
        
        // User Service 熔断规则
        DegradeRule userServiceRule = new DegradeRule("user-service")
            .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
            .setCount(0.5)           // 错误率 50%
            .setTimeWindow(30)       // 熔断时长 30 秒
            .setMinRequestAmount(10) // 最小请求数
            .setStatIntervalMs(10000); // 统计时间窗口 10 秒
        degradeRules.add(userServiceRule);
        
        // Post Service 熔断规则
        DegradeRule postServiceRule = new DegradeRule("post-service")
            .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
            .setCount(0.5)           // 慢调用比例 50%
            .setSlowRatioThreshold(1000) // 慢调用阈值 1 秒
            .setTimeWindow(30)
            .setMinRequestAmount(10)
            .setStatIntervalMs(10000);
        degradeRules.add(postServiceRule);
        
        DegradeRuleManager.loadRules(degradeRules);
    }
}
```


### Feign 降级实现

```java
@FeignClient(name = "user-service", fallbackFactory = UserServiceFallbackFactory.class)
public interface UserServiceClient {
    
    @GetMapping("/api/users/{userId}")
    UserDTO getUserById(@PathVariable String userId);
    
    @GetMapping("/api/users/batch")
    Map<String, UserDTO> batchGetUsers(@RequestParam List<String> userIds);
}

@Component
public class UserServiceFallbackFactory implements FallbackFactory<UserServiceClient> {
    
    private static final Logger log = LoggerFactory.getLogger(UserServiceFallbackFactory.class);
    
    @Override
    public UserServiceClient create(Throwable cause) {
        log.error("User service fallback triggered", cause);
        
        return new UserServiceClient() {
            @Override
            public UserDTO getUserById(String userId) {
                // 返回默认用户信息
                return UserDTO.builder()
                    .id(userId)
                    .nickName("用户" + userId.substring(0, 4))
                    .avatarUrl("/default-avatar.png")
                    .build();
            }
            
            @Override
            public Map<String, UserDTO> batchGetUsers(List<String> userIds) {
                // 返回默认用户信息
                return userIds.stream()
                    .collect(Collectors.toMap(
                        id -> id,
                        id -> UserDTO.builder()
                            .id(id)
                            .nickName("用户" + id.substring(0, 4))
                            .avatarUrl("/default-avatar.png")
                            .build()
                    ));
            }
        };
    }
}
```

---

## 缓存问题解决方案

### 缓存穿透

```java
// 方案1：缓存空值
public Post findById(Long postId) {
    String key = PostRedisKeys.detail(postId);
    Object cached = redisTemplate.opsForValue().get(key);
    
    if (cached != null) {
        if (cached instanceof NullValue) {
            return null;  // 缓存的空值
        }
        return (Post) cached;
    }
    
    Post post = delegate.findById(postId);
    
    if (post != null) {
        redisTemplate.opsForValue().set(key, post, Duration.ofMinutes(10));
    } else {
        // 缓存空值，短过期时间
        redisTemplate.opsForValue().set(key, NullValue.INSTANCE, Duration.ofMinutes(1));
    }
    
    return post;
}

// 方案2：布隆过滤器
@Component
public class PostBloomFilter {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String BLOOM_KEY = "bloom:posts";
    
    public void add(Long postId) {
        // 使用 Redis 的 BF.ADD 命令
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.execute("BF.ADD", BLOOM_KEY.getBytes(), 
                String.valueOf(postId).getBytes());
            return null;
        });
    }
    
    public boolean mightExist(Long postId) {
        // 使用 Redis 的 BF.EXISTS 命令
        return (Boolean) redisTemplate.execute((RedisCallback<Object>) connection -> {
            return connection.execute("BF.EXISTS", BLOOM_KEY.getBytes(),
                String.valueOf(postId).getBytes());
        });
    }
}
```

### 缓存雪崩

```java
// 方案：过期时间加随机抖动
public void cachePost(Post post) {
    String key = PostRedisKeys.detail(post.getId());
    
    // 基础过期时间 + 随机抖动（0-60秒）
    Duration baseTtl = cacheConfig.getEntityDetailTtl();
    int jitter = ThreadLocalRandom.current().nextInt(0, 60);
    Duration ttl = baseTtl.plusSeconds(jitter);
    
    redisTemplate.opsForValue().set(key, post, ttl);
}
```

### 缓存击穿

```java
// 方案：分布式锁 + 双重检查
public Post findByIdWithLock(Long postId) {
    String key = PostRedisKeys.detail(postId);
    
    // 第一次检查缓存
    Post cached = (Post) redisTemplate.opsForValue().get(key);
    if (cached != null) {
        return cached;
    }
    
    // 获取分布式锁
    String lockKey = "lock:post:" + postId;
    String requestId = UUID.randomUUID().toString();
    
    try {
        if (distributedLock.tryLock(lockKey, requestId, Duration.ofSeconds(10))) {
            // 第二次检查缓存（双重检查）
            cached = (Post) redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return cached;
            }
            
            // 查询数据库
            Post post = delegate.findById(postId);
            if (post != null) {
                redisTemplate.opsForValue().set(key, post, Duration.ofMinutes(10));
            }
            return post;
        } else {
            // 获取锁失败，等待后重试
            Thread.sleep(100);
            return findByIdWithLock(postId);
        }
    } finally {
        distributedLock.unlock(lockKey, requestId);
    }
}
```
