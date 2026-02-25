---
inclusion: fileMatch
fileMatchPattern: '**/*{Cache,cache,Redis,redis}*.java'
---

# 缓存规范

[返回索引](./README-zh.md)

---

## 缓存策略选择

### Cache-Aside（旁路缓存）

**推荐使用的默认策略**

```java
// 读取流程
public User getUser(Long userId) {
    // 1. 先查缓存
    User user = cache.get(userId);
    if (user != null) {
        return user;
    }
    
    // 2. 缓存未命中，查数据库
    user = userRepository.findById(userId);
    
    // 3. 写入缓存
    if (user != null) {
        cache.put(userId, user, Duration.ofHours(1));
    }
    return user;
}

// 写入流程
public void updateUser(User user) {
    // 1. 更新数据库
    userRepository.save(user);
    
    // 2. 删除缓存（不是更新缓存）
    cache.delete(user.getId());
}
```

### 策略对比

| 策略 | 适用场景 | 优点 | 缺点 |
|------|---------|------|------|
| **Cache-Aside** | 通用场景 | 实现简单、灵活 | 首次访问慢 |
| **Read-Through** | 读多写少 | 代码简洁 | 需要缓存框架支持 |
| **Write-Through** | 数据一致性要求高 | 强一致性 | 写入延迟高 |
| **Write-Behind** | 写密集场景 | 写入快 | 数据可能丢失 |

---

## 缓存穿透防护

**问题**：查询不存在的数据，请求穿透到数据库。

### 方案1：空值缓存

```java
public User getUser(Long userId) {
    // 查缓存（包括空值标记）
    String cached = redis.get("user:" + userId);
    
    // 空值标记，直接返回
    if ("NULL".equals(cached)) {
        return null;
    }
    
    if (cached != null) {
        return deserialize(cached);
    }
    
    // 查数据库
    User user = userRepository.findById(userId);
    
    if (user != null) {
        redis.setex("user:" + userId, 3600, serialize(user));
    } else {
        // 缓存空值，短过期时间
        redis.setex("user:" + userId, 300, "NULL");
    }
    
    return user;
}
```

### 方案2：布隆过滤器

```java
@Component
public class UserBloomFilter {
    
    private final BloomFilter<Long> bloomFilter;
    
    @PostConstruct
    public void init() {
        // 预期元素数量、误判率
        bloomFilter = BloomFilter.create(
            Funnels.longFunnel(), 
            1_000_000,  // 100万用户
            0.01        // 1% 误判率
        );
        
        // 初始化时加载所有用户ID
        userRepository.findAllIds().forEach(bloomFilter::put);
    }
    
    public boolean mightContain(Long userId) {
        return bloomFilter.mightContain(userId);
    }
    
    public User getUser(Long userId) {
        // 布隆过滤器判断
        if (!bloomFilter.mightContain(userId)) {
            return null;  // 一定不存在
        }
        
        // 可能存在，查缓存和数据库
        return getUserFromCacheOrDb(userId);
    }
}
```

---

## 缓存击穿防护

**问题**：热点数据过期瞬间，大量请求打到数据库。

### 方案1：互斥锁

```java
public User getUser(Long userId) {
    String key = "user:" + userId;
    String lockKey = "lock:user:" + userId;
    
    User user = cache.get(key);
    if (user != null) {
        return user;
    }
    
    // 获取分布式锁
    boolean locked = redis.setnx(lockKey, "1", Duration.ofSeconds(10));
    
    if (locked) {
        try {
            // 双重检查
            user = cache.get(key);
            if (user != null) {
                return user;
            }
            
            // 查数据库并写入缓存
            user = userRepository.findById(userId);
            if (user != null) {
                cache.put(key, user, Duration.ofHours(1));
            }
            return user;
        } finally {
            redis.delete(lockKey);
        }
    } else {
        // 未获取锁，等待后重试
        Thread.sleep(50);
        return getUser(userId);
    }
}
```

### 方案2：逻辑过期

```java
@Data
public class CacheWrapper<T> {
    private T data;
    private long expireTime;  // 逻辑过期时间
}

public User getUser(Long userId) {
    String key = "user:" + userId;
    
    CacheWrapper<User> wrapper = cache.get(key);
    if (wrapper == null) {
        return loadAndCache(userId);
    }
    
    // 未过期，直接返回
    if (wrapper.getExpireTime() > System.currentTimeMillis()) {
        return wrapper.getData();
    }
    
    // 已过期，异步刷新（返回旧数据）
    CompletableFuture.runAsync(() -> loadAndCache(userId));
    return wrapper.getData();
}
```

---

## 缓存雪崩防护

**问题**：大量缓存同时过期，请求全部打到数据库。

### 方案：过期时间随机化

```java
public void cacheUser(User user) {
    String key = "user:" + user.getId();
    
    // 基础过期时间 + 随机偏移（防止同时过期）
    int baseExpire = 3600;  // 1小时
    int randomOffset = new Random().nextInt(600);  // 0-10分钟随机
    
    redis.setex(key, baseExpire + randomOffset, serialize(user));
}

// 批量缓存时
public void batchCacheUsers(List<User> users) {
    Random random = new Random();
    int baseExpire = 3600;
    
    Map<String, String> batch = new HashMap<>();
    for (User user : users) {
        int expire = baseExpire + random.nextInt(600);
        // 使用 Pipeline 批量写入
        redis.setex("user:" + user.getId(), expire, serialize(user));
    }
}
```

---

## 缓存一致性

### 方案1：延迟双删

```java
public void updateUser(User user) {
    String key = "user:" + user.getId();
    
    // 1. 删除缓存
    cache.delete(key);
    
    // 2. 更新数据库
    userRepository.save(user);
    
    // 3. 延迟再删一次（异步）
    CompletableFuture.runAsync(() -> {
        try {
            Thread.sleep(500);  // 等待主从同步
            cache.delete(key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    });
}
```

### 方案2：订阅数据库变更（Canal/Debezium）

```java
// 监听 MySQL binlog 变更
@Component
public class UserChangeListener {
    
    @CanalEventListener
    public void onUserChange(CanalEntry.Entry entry) {
        if (entry.getEntryType() == EntryType.ROWDATA) {
            RowChange rowChange = RowChange.parseFrom(entry.getStoreValue());
            
            for (RowData rowData : rowChange.getRowDatasList()) {
                Long userId = extractUserId(rowData);
                cache.delete("user:" + userId);
            }
        }
    }
}
```

---

## 缓存预热

### 启动时预热

```java
@Component
public class CacheWarmer implements ApplicationRunner {
    
    @Override
    public void run(ApplicationArguments args) {
        log.info("开始缓存预热...");
        
        // 预热热点数据
        List<User> hotUsers = userRepository.findTopActiveUsers(1000);
        for (User user : hotUsers) {
            cache.put("user:" + user.getId(), user, Duration.ofHours(2));
        }
        
        log.info("缓存预热完成，预热用户数: {}", hotUsers.size());
    }
}
```

---

## TTL 规范

| 数据类型 | 推荐 TTL | 说明 |
|---------|---------|------|
| 用户信息 | 1-2 小时 | 变更不频繁 |
| 配置数据 | 5-10 分钟 | 需要及时生效 |
| Token | 根据业务 | 通常 7 天 |
| 验证码 | 5 分钟 | 安全考虑 |
| 计数器 | 永不过期或按周期 | 定期重建 |
| 排行榜 | 根据刷新频率 | 通常 1 小时 |

---

**最后更新**：2026-02-01
