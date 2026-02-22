---
inclusion: fileMatch
fileMatchPattern: '**/*{Service,service,Lock,lock}*.java'
---

# 并发控制规范

[返回索引](./README-zh.md)

---

## 锁策略选择

### 策略对比

| 锁类型 | 适用场景 | 优点 | 缺点 |
|--------|---------|------|------|
| **乐观锁** | 读多写少、冲突少 | 无锁等待、性能好 | 冲突多时重试代价高 |
| **悲观锁** | 写多、冲突频繁 | 避免冲突 | 阻塞等待、死锁风险 |
| **分布式锁** | 跨实例协调 | 分布式一致性 | 网络开销、复杂度高 |

---

## 乐观锁

### 版本号实现

```java
@Entity
@Table(name = "post")
public class Post {
    
    @Id
    private Long id;
    
    private String title;
    
    @Version  // JPA 乐观锁
    private Integer version;
}

// 更新时自动检查版本号
@Transactional
public void updatePost(Long postId, String newTitle) {
    Post post = postRepository.findById(postId)
        .orElseThrow(() -> new BusinessException("文章不存在"));
    
    post.setTitle(newTitle);
    
    try {
        postRepository.save(post);
    } catch (OptimisticLockException e) {
        throw new BusinessException("数据已被其他用户修改，请刷新后重试");
    }
}
```

### MyBatis-Plus 乐观锁

```java
@Data
@TableName("post")
public class PostEntity {
    
    @TableId
    private Long id;
    
    private String title;
    
    @Version
    private Integer version;
}

// 配置乐观锁插件
@Configuration
public class MyBatisPlusConfig {
    
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
```

---

## 悲观锁

### 数据库行锁

```java
// JPA 悲观锁
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Post p WHERE p.id = :id")
Optional<Post> findByIdForUpdate(@Param("id") Long id);

// 使用示例
@Transactional
public void decreaseStock(Long productId, int quantity) {
    // SELECT ... FOR UPDATE
    Product product = productRepository.findByIdForUpdate(productId)
        .orElseThrow(() -> new BusinessException("商品不存在"));
    
    if (product.getStock() < quantity) {
        throw new BusinessException("库存不足");
    }
    
    product.setStock(product.getStock() - quantity);
    productRepository.save(product);
}
```

### 注意事项

```java
// [FAIL] 错误 - 事务范围过大
@Transactional
public void processOrder(Long productId) {
    Product product = productRepository.findByIdForUpdate(productId);
    
    // 锁持有时间过长
    paymentService.pay();  // 外部调用
    notificationService.notify();  // 外部调用
    
    product.decreaseStock();
}

// [PASS] 正确 - 缩小锁范围
public void processOrder(Long productId) {
    // 先完成外部调用
    paymentService.pay();
    
    // 最后加锁修改库存
    decreaseStock(productId, 1);
    
    // 锁释放后发通知
    notificationService.notify();
}
```

---

## 分布式锁

### Redis 分布式锁（Redisson 推荐）

```java
@Component
public class DistributedLockService {
    
    @Autowired
    private RedissonClient redissonClient;
    
    /**
     * 执行带锁的操作
     */
    public <T> T executeWithLock(String lockKey, long waitTime, 
                                  long leaseTime, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException("获取锁失败，请稍后重试");
            }
            
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("操作被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}

// 使用示例
public void processPayment(String orderId) {
    String lockKey = "lock:payment:" + orderId;
    
    lockService.executeWithLock(lockKey, 5, 30, () -> {
        // 业务逻辑
        return paymentService.process(orderId);
    });
}
```

### 锁配置规范

| 参数 | 推荐值 | 说明 |
|------|-------|------|
| **waitTime** | 3-10 秒 | 等待获取锁的超时时间 |
| **leaseTime** | 30-60 秒 | 锁的持有时间（需大于业务执行时间） |
| **看门狗** | 启用 | 自动续期，防止业务未完成锁过期 |

### 锁粒度设计

```java
// [FAIL] 错误 - 锁粒度过大
String lockKey = "lock:order";  // 所有订单共用一把锁

// [PASS] 正确 - 细粒度锁
String lockKey = "lock:order:" + orderId;  // 每个订单独立锁

// [PASS] 正确 - 用户维度锁
String lockKey = "lock:user:bindPhone:" + userId;  // 用户绑定手机号
```

---

## 防重设计

### 幂等 Token 机制

```java
@RestController
public class OrderController {
    
    /**
     * 获取幂等 Token（前端调用）
     */
    @GetMapping("/api/v1/orders/token")
    public Result<String> getIdempotentToken() {
        String token = UUID.randomUUID().toString();
        redis.setex("idempotent:token:" + token, 300, "1");  // 5分钟有效
        return Result.success(token);
    }
    
    /**
     * 创建订单（需要携带幂等 Token）
     */
    @PostMapping("/api/v1/orders")
    public Result<Order> createOrder(
            @RequestBody CreateOrderReq req,
            @RequestHeader("X-Idempotent-Token") String token) {
        
        String tokenKey = "idempotent:token:" + token;
        
        // 原子性删除并检查
        Long deleted = redis.del(tokenKey);
        if (deleted == 0) {
            throw new BusinessException("请勿重复提交");
        }
        
        // 执行业务逻辑
        Order order = orderService.create(req);
        return Result.success(order);
    }
}
```

### 唯一索引防重

```sql
-- 订单表唯一索引
CREATE UNIQUE INDEX uk_order_no ON orders(order_no);

-- 用户绑定表唯一索引
CREATE UNIQUE INDEX uk_user_phone ON user_binding(user_id, phone);
```

```java
@Transactional
public Order createOrder(CreateOrderReq req) {
    Order order = new Order();
    order.setOrderNo(generateOrderNo());
    
    try {
        return orderRepository.save(order);
    } catch (DuplicateKeyException e) {
        // 唯一索引冲突，说明重复提交
        return orderRepository.findByOrderNo(order.getOrderNo());
    }
}
```

### 状态机防重

```java
public void processOrder(Long orderId) {
    // UPDATE orders SET status = 'PROCESSING' 
    // WHERE id = ? AND status = 'PENDING'
    int updated = orderRepository.updateStatus(
        orderId, 
        OrderStatus.PENDING, 
        OrderStatus.PROCESSING
    );
    
    if (updated == 0) {
        // 状态已变更，说明已被处理或重复处理
        throw new BusinessException("订单状态已变更，无法处理");
    }
    
    // 执行业务逻辑
    doProcess(orderId);
}
```

---

## 限流规范

### 接口限流

```java
@RestController
public class ApiController {
    
    private final RateLimiter rateLimiter = 
        RateLimiter.create(100);  // 每秒100个请求
    
    @GetMapping("/api/v1/data")
    public Result<Object> getData() {
        if (!rateLimiter.tryAcquire()) {
            throw new BusinessException("请求过于频繁，请稍后重试");
        }
        
        return Result.success(service.getData());
    }
}
```

### Redis 滑动窗口限流

```java
public boolean isAllowed(String userId, int maxRequests, int windowSeconds) {
    String key = "ratelimit:" + userId;
    long now = System.currentTimeMillis();
    long windowStart = now - windowSeconds * 1000L;
    
    // 使用 Lua 脚本保证原子性
    String script = """
        redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1])
        local count = redis.call('ZCARD', KEYS[1])
        if count < tonumber(ARGV[2]) then
            redis.call('ZADD', KEYS[1], ARGV[3], ARGV[3])
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            return 1
        else
            return 0
        end
        """;
    
    Long result = redis.eval(script, 
        List.of(key), 
        String.valueOf(windowStart),
        String.valueOf(maxRequests),
        String.valueOf(now),
        String.valueOf(windowSeconds)
    );
    
    return result == 1;
}
```

---

## 死锁预防

### 规范

1. **锁顺序一致**：多个锁时，按固定顺序获取
2. **超时机制**：所有锁必须设置超时时间
3. **避免嵌套锁**：尽量不在持有锁时获取其他锁

```java
// [FAIL] 错误 - 可能死锁
public void transfer(Account from, Account to) {
    synchronized(from) {
        synchronized(to) {
            // ...
        }
    }
}

// [PASS] 正确 - 按 ID 顺序加锁
public void transfer(Account from, Account to) {
    Account first = from.getId() < to.getId() ? from : to;
    Account second = from.getId() < to.getId() ? to : from;
    
    synchronized(first) {
        synchronized(second) {
            // ...
        }
    }
}
```

---

**最后更新**：2026-02-01
