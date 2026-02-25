# User Service 设计

## DDD 分层结构

```
user-service/
├── src/main/java/com/ZhiCore/user/
│   ├── interfaces/                    # 接口层
│   │   ├── controller/
│   │   │   ├── UserController.java
│   │   │   ├── AuthController.java
│   │   │   ├── FollowController.java
│   │   │   └── CheckInController.java
│   │   ├── dto/
│   │   │   ├── request/
│   │   │   └── response/
│   │   └── assembler/
│   │       └── UserAssembler.java
│   ├── application/                   # 应用层
│   │   ├── service/
│   │   │   ├── UserApplicationService.java
│   │   │   ├── AuthApplicationService.java
│   │   │   ├── FollowApplicationService.java
│   │   │   └── CheckInApplicationService.java
│   │   ├── dto/
│   │   └── event/
│   │       └── UserEventPublisher.java
│   ├── domain/                        # 领域层
│   │   ├── model/
│   │   │   ├── User.java              # 聚合根
│   │   │   ├── UserFollow.java
│   │   │   ├── UserBlock.java
│   │   │   ├── UserCheckIn.java
│   │   │   └── Role.java
│   │   ├── repository/
│   │   │   ├── UserRepository.java
│   │   │   ├── UserFollowRepository.java
│   │   │   └── UserCheckInRepository.java
│   │   ├── service/
│   │   │   ├── UserDomainService.java
│   │   │   └── FollowDomainService.java
│   │   └── event/
│   │       ├── UserProfileUpdatedEvent.java
│   │       └── UserFollowedEvent.java
│   └── infrastructure/                # 基础设施层
│       ├── repository/
│       │   ├── UserRepositoryImpl.java
│       │   ├── CachedUserRepository.java
│       │   └── mapper/
│       │       └── UserMapper.java
│       ├── config/
│       │   ├── MyBatisPlusConfig.java
│       │   └── RedisConfig.java
│       └── mq/
│           └── UserEventPublisherImpl.java
└── src/main/resources/
    ├── application.yml
    └── mapper/
        └── UserMapper.xml
```

## 核心接口定义

```java
// 领域层 - Repository 接口
public interface UserRepository {
    User findById(String userId);
    User findByEmail(String email);
    User save(User user);
    void updateProfile(User user);
    boolean existsByEmail(String email);
}

public interface UserFollowRepository {
    void save(UserFollow follow);
    void delete(String followerId, String followingId);
    boolean exists(String followerId, String followingId);
    List<UserFollow> findFollowers(String userId, int page, int size);
    List<UserFollow> findFollowings(String userId, int page, int size);
}

// 应用层 - Application Service
@Service
public class UserApplicationService {
    
    private final UserRepository userRepository;
    private final UserDomainService userDomainService;
    private final UserEventPublisher eventPublisher;
    
    @Transactional(readOnly = true)
    public UserVO getUserById(String userId) {
        User user = userRepository.findById(userId);
        return UserAssembler.toVO(user);
    }
    
    @Transactional
    public void updateProfile(String userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId);
        userDomainService.updateProfile(user, request);
        userRepository.updateProfile(user);
        eventPublisher.publish(new UserProfileUpdatedEvent(userId));
    }
}

// 应用层 - 关注服务
@Service
public class FollowApplicationService {
    
    private final UserFollowRepository followRepository;
    private final UserFollowStatsRepository followStatsRepository;
    private final FollowDomainService followDomainService;
    private final UserEventPublisher eventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DistributedLock distributedLock;
    private final TransactionTemplate transactionTemplate;
    
    /**
     * 关注用户
     * 
     * 设计说明：
     * 1. 使用分布式锁防止并发重复关注
     * 2. 数据库操作在事务中执行，Redis 操作在事务提交后执行
     * 3. 如果 Redis 更新失败，通过 CDC 或定时任务从数据库同步修复
     */
    public void follow(String followerId, String followingId) {
        // 1. 业务验证
        followDomainService.validateFollow(followerId, followingId);
        
        // 2. 分布式锁防止并发
        String lockKey = "follow:" + followerId + ":" + followingId;
        String requestId = UUID.randomUUID().toString();
        
        if (!distributedLock.tryLock(lockKey, requestId, Duration.ofSeconds(5))) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }
        
        try {
            // 3. 幂等性检查
            if (followRepository.exists(followerId, followingId)) {
                return; // 已关注，直接返回
            }
            
            // 4. 数据库操作在事务中执行
            transactionTemplate.executeWithoutResult(status -> {
                // 保存关注关系
                UserFollow follow = new UserFollow(followerId, followingId);
                followRepository.save(follow);
                
                // 更新数据库统计表（作为数据源）
                followStatsRepository.incrementFollowing(followerId);
                followStatsRepository.incrementFollowers(followingId);
            });
            
            // 5. 事务提交成功后，更新 Redis 缓存（允许失败，CDC/定时任务会修复）
            try {
                redisTemplate.opsForValue().increment(UserRedisKeys.followingCount(followerId));
                redisTemplate.opsForValue().increment(UserRedisKeys.followersCount(followingId));
            } catch (Exception e) {
                log.warn("Redis 更新失败，将由 CDC 或定时任务修复: {}", e.getMessage());
            }
            
            // 6. 发布事件
            eventPublisher.publish(new UserFollowedEvent(followerId, followingId));
            
        } finally {
            distributedLock.unlock(lockKey, requestId);
        }
    }
    
    public void unfollow(String followerId, String followingId) {
        String lockKey = "follow:" + followerId + ":" + followingId;
        String requestId = UUID.randomUUID().toString();
        
        if (!distributedLock.tryLock(lockKey, requestId, Duration.ofSeconds(5))) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }
        
        try {
            // 幂等性检查
            if (!followRepository.exists(followerId, followingId)) {
                return; // 未关注，直接返回
            }
            
            // 数据库操作在事务中执行
            transactionTemplate.executeWithoutResult(status -> {
                followRepository.delete(followerId, followingId);
                
                // 更新数据库统计表
                followStatsRepository.decrementFollowing(followerId);
                followStatsRepository.decrementFollowers(followingId);
            });
            
            // 事务提交成功后，更新 Redis 缓存
            try {
                redisTemplate.opsForValue().decrement(UserRedisKeys.followingCount(followerId));
                redisTemplate.opsForValue().decrement(UserRedisKeys.followersCount(followingId));
            } catch (Exception e) {
                log.warn("Redis 更新失败，将由 CDC 或定时任务修复: {}", e.getMessage());
            }
            
        } finally {
            distributedLock.unlock(lockKey, requestId);
        }
    }
    
    /**
     * 获取关注数（优先 Redis，降级数据库）
     */
    public int getFollowingCount(String userId) {
        try {
            Object cached = redisTemplate.opsForValue().get(UserRedisKeys.followingCount(userId));
            if (cached != null) {
                return ((Number) cached).intValue();
            }
        } catch (Exception e) {
            log.warn("Redis 查询失败，降级到数据库: {}", e.getMessage());
        }
        
        // 从数据库查询并回填缓存
        int count = followStatsRepository.getFollowingCount(userId);
        try {
            redisTemplate.opsForValue().set(
                UserRedisKeys.followingCount(userId), count, Duration.ofHours(1));
        } catch (Exception ignored) {}
        
        return count;
    }
}

/**
 * 定时任务：修复 Redis 与数据库的统计不一致
 */
@Component
public class FollowStatsReconciliationTask {
    
    @Scheduled(cron = "0 0 3 * * ?")  // 每天凌晨3点执行
    public void reconcile() {
        // 从数据库查询所有用户的实际统计，与 Redis 对比并修复
        List<UserFollowStats> allStats = followStatsRepository.findAll();
        for (UserFollowStats stats : allStats) {
            redisTemplate.opsForValue().set(
                UserRedisKeys.followingCount(stats.getUserId()), 
                stats.getFollowingCount()
            );
            redisTemplate.opsForValue().set(
                UserRedisKeys.followersCount(stats.getUserId()), 
                stats.getFollowersCount()
            );
        }
    }
}
```


## 数据库表设计 (User_DB)

> **设计原则：逻辑外键**
> 
> 所有数据库表均不使用物理外键约束（FOREIGN KEY），而是采用逻辑外键设计。原因：
> 1. **微服务架构**：数据分布在不同数据库，无法使用跨库外键
> 2. **性能考虑**：外键约束会增加写入时的检查开销
> 3. **灵活性**：便于数据迁移、分库分表和水平扩展
> 4. **解耦**：服务间通过 API 或事件保证数据一致性

```sql
-- 用户表
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    user_name VARCHAR(50) NOT NULL UNIQUE,
    nick_name VARCHAR(100),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(500),
    bio TEXT,
    status SMALLINT DEFAULT 0,
    email_confirmed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 角色表
CREATE TABLE roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- 用户角色关联表
CREATE TABLE user_roles (
    user_id VARCHAR(36) NOT NULL,
    role_id INT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

-- 用户关注表
CREATE TABLE user_follows (
    id BIGINT PRIMARY KEY,
    follower_id VARCHAR(36) NOT NULL,
    following_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (follower_id, following_id)
);
CREATE INDEX idx_user_follows_follower ON user_follows(follower_id);
CREATE INDEX idx_user_follows_following ON user_follows(following_id);

-- 用户关注统计表
CREATE TABLE user_follow_stats (
    user_id VARCHAR(36) PRIMARY KEY,
    followers_count INT DEFAULT 0,
    following_count INT DEFAULT 0
);

-- 用户拉黑表
CREATE TABLE user_blocks (
    id BIGINT PRIMARY KEY,
    blocker_id VARCHAR(36) NOT NULL,
    blocked_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (blocker_id, blocked_id)
);

-- 用户签到表
CREATE TABLE user_check_ins (
    id BIGINT PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    check_in_date DATE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (user_id, check_in_date)
);

-- 用户签到统计表
CREATE TABLE user_check_in_stats (
    user_id VARCHAR(36) PRIMARY KEY,
    total_days INT DEFAULT 0,
    continuous_days INT DEFAULT 0,
    max_continuous_days INT DEFAULT 0,
    last_check_in_date DATE
);

-- 用户签到位图表
CREATE TABLE user_check_in_bitmaps (
    id BIGINT PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    year_month VARCHAR(7) NOT NULL,
    bitmap BYTEA,
    UNIQUE (user_id, year_month)
);

-- 用户操作历史表
CREATE TABLE user_action_histories (
    id BIGINT PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50),
    target_id VARCHAR(50),
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_user_action_histories_user ON user_action_histories(user_id, created_at DESC);
```
