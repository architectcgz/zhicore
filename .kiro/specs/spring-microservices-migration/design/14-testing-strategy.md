# 测试策略

## 关键接口 SLA 指标

> **设计说明：性能基线与告警阈值**
> 
> 以下 SLA 指标用于性能测试验收和生产监控告警。

### 接口性能 SLA

| 接口 | P50 | P95 | P99 | 吞吐量 (QPS) | 并发用户 | 告警阈值 |
|------|-----|-----|-----|-------------|---------|---------|
| **Gateway** | | | | | | |
| GET /api/v1/posts/{id} | 20ms | 50ms | 100ms | 2000 | 500 | P99 > 200ms |
| GET /api/v1/posts | 50ms | 100ms | 200ms | 1000 | 300 | P99 > 400ms |
| POST /api/v1/posts | 100ms | 300ms | 500ms | 200 | 100 | P99 > 1000ms |
| **Post Service** | | | | | | |
| 点赞 POST /api/v1/posts/{id}/like | 20ms | 50ms | 100ms | 3000 | 500 | P99 > 200ms |
| 取消点赞 | 20ms | 50ms | 100ms | 3000 | 500 | P99 > 200ms |
| **Comment Service** | | | | | | |
| GET /api/v1/comments/post/{id} | 30ms | 80ms | 150ms | 1000 | 300 | P99 > 300ms |
| POST /api/v1/comments | 50ms | 150ms | 300ms | 500 | 200 | P99 > 500ms |
| **Notification Service** | | | | | | |
| GET /api/v1/notifications | 30ms | 80ms | 200ms | 800 | 300 | P99 > 400ms |
| **Search Service** | | | | | | |
| GET /api/v1/search | 50ms | 150ms | 300ms | 500 | 200 | P99 > 500ms |

### 跨服务链路 SLA

| 链路 | 描述 | 端到端 P99 | 告警阈值 |
|------|------|-----------|---------|
| 文章详情 | Gateway → Post → User | 150ms | > 300ms |
| 评论列表 | Gateway → Comment → User | 200ms | > 400ms |
| 发布文章 | Gateway → Post → MQ → Search/Notification | 600ms | > 1200ms |
| 点赞 | Gateway → Post → MQ → Notification/Ranking | 150ms | > 300ms |

### 容量规划指标

| 指标 | 当前容量 | 扩容阈值 | 扩容方式 |
|------|---------|---------|---------|
| 日活用户 (DAU) | 10万 | 8万 | 水平扩容 |
| 峰值 QPS | 5000 | 4000 | 水平扩容 |
| 数据库连接数 | 100/实例 | 80 | 增加实例 |
| Redis 内存 | 8GB | 6GB | 增加节点 |
| MQ 堆积 | 10万条 | 5万条 | 增加消费者 |

### Prometheus 告警规则

```yaml
groups:
  - name: api-sla-alerts
    rules:
      - alert: HighLatencyP99
        expr: histogram_quantile(0.99, rate(http_request_duration_seconds_bucket[5m])) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "API P99 延迟过高"
          description: "{{ $labels.uri }} P99 延迟 {{ $value }}s 超过阈值"
      
      - alert: HighErrorRate
        expr: rate(http_requests_total{status=~"5.."}[5m]) / rate(http_requests_total[5m]) > 0.01
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "API 错误率过高"
          description: "{{ $labels.uri }} 错误率 {{ $value | humanizePercentage }} 超过 1%"
      
      - alert: LowThroughput
        expr: rate(http_requests_total[5m]) < 100
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "API 吞吐量过低"
          description: "{{ $labels.uri }} QPS {{ $value }} 低于预期"
```

---

## 测试金字塔

```
                    ┌─────────────┐
                    │   E2E Tests │  (少量)
                    │  端到端测试   │
                    └─────────────┘
                   ┌───────────────┐
                   │Integration    │  (适量)
                   │Tests 集成测试  │
                   └───────────────┘
                  ┌─────────────────┐
                  │   Unit Tests    │  (大量)
                  │    单元测试      │
                  └─────────────────┘
```

---

## 单元测试

### 领域模型测试

```java
class PostTest {
    
    @Test
    void createDraft_shouldSetCorrectInitialState() {
        Post post = Post.createDraft(1L, "user-1", "标题", "内容");
        
        assertThat(post.getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(post.getPublishedAt()).isNull();
        assertThat(post.getOwnerId()).isEqualTo("user-1");
    }
    
    @Test
    void publish_shouldChangeStatusToPublished() {
        Post post = Post.createDraft(1L, "user-1", "标题", "内容");
        
        post.publish();
        
        assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post.getPublishedAt()).isNotNull();
    }
    
    @Test
    void publish_whenAlreadyPublished_shouldThrowException() {
        Post post = Post.createDraft(1L, "user-1", "标题", "内容");
        post.publish();
        
        assertThatThrownBy(() -> post.publish())
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("已经发布");
    }
    
    @Test
    void publish_whenDeleted_shouldThrowException() {
        Post post = Post.createDraft(1L, "user-1", "标题", "内容");
        post.delete();
        
        assertThatThrownBy(() -> post.publish())
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("已删除");
    }
}
```


### 应用服务测试

```java
@ExtendWith(MockitoExtension.class)
class PostApplicationServiceTest {
    
    @Mock
    private PostRepository postRepository;
    
    @Mock
    private PostEventPublisher eventPublisher;
    
    @Mock
    private LeafIdGenerator idGenerator;
    
    @InjectMocks
    private PostApplicationService postApplicationService;
    
    @Test
    void createPost_shouldSaveAndPublishEvent() {
        // Given
        when(idGenerator.nextId()).thenReturn(1L);
        CreatePostRequest request = new CreatePostRequest("标题", "内容");
        
        // When
        Long postId = postApplicationService.createPost("user-1", request);
        
        // Then
        assertThat(postId).isEqualTo(1L);
        verify(postRepository).save(any(Post.class));
    }
    
    @Test
    void publishPost_whenNotOwner_shouldThrowException() {
        // Given
        Post post = Post.createDraft(1L, "user-1", "标题", "内容");
        when(postRepository.findById(1L)).thenReturn(post);
        
        // When & Then
        assertThatThrownBy(() -> postApplicationService.publishPost("user-2", 1L))
            .isInstanceOf(ForbiddenException.class);
    }
}
```

---

## Property-Based Testing (PBT)

### 点赞计数一致性测试

```java
@Property
void likeCount_shouldAlwaysMatchActualLikes(
        @ForAll @Size(min = 1, max = 100) List<@From("userId") String> userIds,
        @ForAll @LongRange(min = 1, max = 1000) Long postId) {
    
    // Given
    PostLikeService likeService = new PostLikeService(/* dependencies */);
    Set<String> uniqueUsers = new HashSet<>();
    
    // When - 模拟多个用户点赞（可能重复）
    for (String userId : userIds) {
        try {
            likeService.likePost(userId, postId);
            uniqueUsers.add(userId);
        } catch (BusinessException e) {
            // 重复点赞会抛异常，忽略
        }
    }
    
    // Then - 点赞数应该等于去重后的用户数
    int likeCount = likeService.getLikeCount(postId);
    assertThat(likeCount).isEqualTo(uniqueUsers.size());
}

@Provide
Arbitrary<String> userId() {
    return Arbitraries.strings()
        .withCharRange('a', 'z')
        .ofMinLength(5)
        .ofMaxLength(10)
        .map(s -> "user-" + s);
}
```

### 关注统计一致性测试

```java
@Property
void followStats_shouldBeConsistent(
        @ForAll @Size(min = 1, max = 50) List<FollowAction> actions) {
    
    // Given
    FollowService followService = new FollowService(/* dependencies */);
    Map<String, Set<String>> followingMap = new HashMap<>();
    Map<String, Set<String>> followersMap = new HashMap<>();
    
    // When - 执行一系列关注/取关操作
    for (FollowAction action : actions) {
        try {
            if (action.isFollow()) {
                followService.follow(action.getFollowerId(), action.getFollowingId());
                followingMap.computeIfAbsent(action.getFollowerId(), k -> new HashSet<>())
                    .add(action.getFollowingId());
                followersMap.computeIfAbsent(action.getFollowingId(), k -> new HashSet<>())
                    .add(action.getFollowerId());
            } else {
                followService.unfollow(action.getFollowerId(), action.getFollowingId());
                followingMap.getOrDefault(action.getFollowerId(), Collections.emptySet())
                    .remove(action.getFollowingId());
                followersMap.getOrDefault(action.getFollowingId(), Collections.emptySet())
                    .remove(action.getFollowerId());
            }
        } catch (BusinessException e) {
            // 忽略业务异常（如自己关注自己）
        }
    }
    
    // Then - 验证统计数据一致性
    for (String userId : followingMap.keySet()) {
        int expectedFollowing = followingMap.get(userId).size();
        int actualFollowing = followService.getFollowingCount(userId);
        assertThat(actualFollowing).isEqualTo(expectedFollowing);
    }
    
    for (String userId : followersMap.keySet()) {
        int expectedFollowers = followersMap.get(userId).size();
        int actualFollowers = followService.getFollowersCount(userId);
        assertThat(actualFollowers).isEqualTo(expectedFollowers);
    }
}
```


### 签到幂等性测试

```java
@Property
void checkIn_shouldBeIdempotent(
        @ForAll @From("userId") String userId,
        @ForAll @IntRange(min = 1, max = 10) int repeatCount) {
    
    // Given
    CheckInService checkInService = new CheckInService(/* dependencies */);
    LocalDate today = LocalDate.now();
    
    // When - 同一天多次签到
    int successCount = 0;
    for (int i = 0; i < repeatCount; i++) {
        try {
            checkInService.checkIn(userId, today);
            successCount++;
        } catch (BusinessException e) {
            // 重复签到会抛异常
        }
    }
    
    // Then - 只有第一次成功
    assertThat(successCount).isEqualTo(1);
    
    // And - 签到记录只有一条
    List<CheckInRecord> records = checkInService.getCheckInRecords(userId, today, today);
    assertThat(records).hasSize(1);
}
```

### 消息顺序性测试

```java
@Property
void messages_shouldMaintainOrder(
        @ForAll @Size(min = 2, max = 20) List<String> messageContents) {
    
    // Given
    MessageService messageService = new MessageService(/* dependencies */);
    String senderId = "user-1";
    String receiverId = "user-2";
    
    // When - 按顺序发送消息
    List<Long> messageIds = new ArrayList<>();
    for (String content : messageContents) {
        Long messageId = messageService.sendMessage(senderId, receiverId, content);
        messageIds.add(messageId);
    }
    
    // Then - 查询结果应该保持顺序
    Long conversationId = messageService.getConversationId(senderId, receiverId);
    List<Message> messages = messageService.getMessages(conversationId, 0, 100);
    
    // 消息按时间排序后，ID 顺序应该一致
    List<Long> retrievedIds = messages.stream()
        .sorted(Comparator.comparing(Message::getCreatedAt))
        .map(Message::getId)
        .collect(Collectors.toList());
    
    assertThat(retrievedIds).isEqualTo(messageIds);
}
```

---

## 集成测试

### Repository 集成测试

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PostRepositoryIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test");
    
    @Autowired
    private PostRepository postRepository;
    
    @Test
    void save_shouldPersistPost() {
        Post post = Post.createDraft(1L, "user-1", "标题", "内容");
        
        postRepository.save(post);
        
        Post found = postRepository.findById(1L);
        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("标题");
    }
}
```

### API 集成测试

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PostControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    void createPost_shouldReturn201() throws Exception {
        CreatePostRequest request = new CreatePostRequest("标题", "内容");
        
        mockMvc.perform(post("/api/posts")
                .header("X-User-Id", "user-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.id").exists());
    }
    
    @Test
    void getPost_whenNotFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/posts/999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
```

### 跨服务集成测试

> **设计说明：跨服务集成测试**
> 
> 使用 Testcontainers 模拟完整的微服务环境，验证服务间交互的正确性。

#### 测试环境架构

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         Testcontainers 测试环境                                  │
│                                                                                  │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│   │  PostgreSQL  │  │    Redis     │  │   RocketMQ   │  │Elasticsearch │        │
│   │  Container   │  │  Container   │  │  Container   │  │  Container   │        │
│   └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘        │
│          │                 │                 │                 │                 │
│          └─────────────────┴─────────────────┴─────────────────┘                 │
│                                      │                                           │
│                              ┌───────┴───────┐                                   │
│                              │  Test Runner  │                                   │
│                              │  (JUnit 5)    │                                   │
│                              └───────────────┘                                   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

#### 基础测试配置

```java
/**
 * 跨服务集成测试基类
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("integration-test")
public abstract class CrossServiceIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("blog_test")
        .withUsername("test")
        .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
        .withExposedPorts(6379);
    
    @Container
    static GenericContainer<?> rocketmq = new GenericContainer<>("apache/rocketmq:5.1.0")
        .withExposedPorts(9876, 10911)
        .withCommand("sh", "-c", "mqnamesrv & sleep 5 && mqbroker -n localhost:9876");
    
    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
        "docker.elastic.co/elasticsearch/elasticsearch:8.11.0")
        .withEnv("xpack.security.enabled", "false");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
        registry.add("rocketmq.name-server", () -> 
            rocketmq.getHost() + ":" + rocketmq.getMappedPort(9876));
        registry.add("spring.elasticsearch.uris", elasticsearch::getHttpHostAddress);
    }
}
```

#### 文章发布→搜索索引→通知 集成测试

```java
/**
 * 测试场景：发布文章 → 搜索索引更新 → 粉丝收到通知
 */
class PostPublishFlowIntegrationTest extends CrossServiceIntegrationTest {
    
    @Autowired
    private PostApplicationService postService;
    
    @Autowired
    private SearchService searchService;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private FollowService followService;
    
    @Test
    void publishPost_shouldUpdateSearchIndexAndNotifyFollowers() throws Exception {
        // Given - 用户 A 有一个粉丝 B
        String authorId = "user-author";
        String followerId = "user-follower";
        followService.follow(followerId, authorId);
        
        // When - 用户 A 发布文章
        CreatePostRequest request = new CreatePostRequest("测试标题", "测试内容");
        Long postId = postService.createPost(authorId, request);
        postService.publishPost(authorId, postId);
        
        // Then - 等待异步处理完成
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            // 1. 搜索索引应该包含该文章
            SearchResult result = searchService.search("测试标题", 0, 10);
            assertThat(result.getItems())
                .extracting("id")
                .contains(postId);
            
            // 2. 粉丝 B 应该收到通知
            List<Notification> notifications = notificationService
                .getNotifications(followerId, 0, 10);
            assertThat(notifications)
                .anyMatch(n -> n.getType() == NotificationType.NEW_POST 
                    && n.getSourceId().equals(postId.toString()));
        });
    }
}
```

#### 点赞→通知→排行榜 集成测试

```java
/**
 * 测试场景：点赞文章 → 作者收到通知 → 排行榜更新
 */
class PostLikeFlowIntegrationTest extends CrossServiceIntegrationTest {
    
    @Autowired
    private PostLikeService likeService;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private RankingService rankingService;
    
    @Test
    void likePost_shouldNotifyAuthorAndUpdateRanking() throws Exception {
        // Given - 存在一篇已发布的文章
        String authorId = "user-author";
        String likerId = "user-liker";
        Long postId = createAndPublishPost(authorId, "热门文章", "内容");
        
        // When - 用户点赞文章
        likeService.likePost(likerId, postId);
        
        // Then - 等待异步处理完成
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            // 1. 作者应该收到点赞通知
            List<Notification> notifications = notificationService
                .getNotifications(authorId, 0, 10);
            assertThat(notifications)
                .anyMatch(n -> n.getType() == NotificationType.POST_LIKED 
                    && n.getActorId().equals(likerId));
            
            // 2. 排行榜应该更新
            List<RankingItem> hotPosts = rankingService.getHotPosts(0, 10);
            assertThat(hotPosts)
                .extracting("postId")
                .contains(postId);
        });
    }
}
```

#### 评论→通知 集成测试

```java
/**
 * 测试场景：评论文章 → 作者收到通知；回复评论 → 评论者收到通知
 */
class CommentFlowIntegrationTest extends CrossServiceIntegrationTest {
    
    @Autowired
    private CommentService commentService;
    
    @Autowired
    private NotificationService notificationService;
    
    @Test
    void createComment_shouldNotifyPostAuthor() throws Exception {
        // Given
        String authorId = "user-author";
        String commenterId = "user-commenter";
        Long postId = createAndPublishPost(authorId, "文章标题", "内容");
        
        // When - 用户评论文章
        CreateCommentRequest request = new CreateCommentRequest(postId, "评论内容");
        commentService.createComment(commenterId, request);
        
        // Then - 作者收到评论通知
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notification> notifications = notificationService
                .getNotifications(authorId, 0, 10);
            assertThat(notifications)
                .anyMatch(n -> n.getType() == NotificationType.COMMENT 
                    && n.getActorId().equals(commenterId));
        });
    }
    
    @Test
    void replyComment_shouldNotifyCommentAuthor() throws Exception {
        // Given
        String authorId = "user-author";
        String commenterId = "user-commenter";
        String replierId = "user-replier";
        Long postId = createAndPublishPost(authorId, "文章标题", "内容");
        Long commentId = commentService.createComment(commenterId, 
            new CreateCommentRequest(postId, "原评论"));
        
        // When - 用户回复评论
        commentService.replyComment(replierId, commentId, "回复内容");
        
        // Then - 原评论者收到回复通知
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notification> notifications = notificationService
                .getNotifications(commenterId, 0, 10);
            assertThat(notifications)
                .anyMatch(n -> n.getType() == NotificationType.REPLY 
                    && n.getActorId().equals(replierId));
        });
    }
}
```

#### 消息幂等性集成测试

```java
/**
 * 测试场景：消息重复消费时的幂等性保证
 */
class MessageIdempotencyIntegrationTest extends CrossServiceIntegrationTest {
    
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    @Autowired
    private NotificationRepository notificationRepository;
    
    @Test
    void duplicateMessage_shouldBeProcessedOnlyOnce() throws Exception {
        // Given - 构造一个点赞事件
        String eventId = UUID.randomUUID().toString();
        PostLikedEvent event = new PostLikedEvent(eventId, 1L, "user-1", "user-author");
        
        // When - 发送两次相同的消息（模拟重复消费）
        String destination = RocketMQConfig.TOPIC_POST + ":" + RocketMQConfig.TAG_POST_LIKED;
        rocketMQTemplate.syncSend(destination, event);
        rocketMQTemplate.syncSend(destination, event);
        
        // Then - 只应该产生一条通知
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository
                .findBySourceId(event.getPostId().toString());
            assertThat(notifications).hasSize(1);
        });
    }
}
```

---

## 性能测试

### 性能测试目标

| 接口 | 响应时间 (P99) | 吞吐量 (QPS) | 并发用户数 |
|------|---------------|-------------|-----------|
| 获取文章详情 | < 100ms | > 1000 | 500 |
| 获取文章列表 | < 200ms | > 500 | 300 |
| 发布文章 | < 500ms | > 100 | 100 |
| 点赞文章 | < 100ms | > 2000 | 500 |
| 获取通知列表 | < 200ms | > 500 | 300 |
| 搜索文章 | < 300ms | > 200 | 200 |

### JMeter 测试计划

```xml
<!-- post-api-load-test.jmx -->
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="博客API性能测试">
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments">
        <collectionProp name="Arguments.arguments">
          <elementProp name="BASE_URL" elementType="Argument">
            <stringProp name="Argument.name">BASE_URL</stringProp>
            <stringProp name="Argument.value">${__P(base.url,http://localhost:8080)}</stringProp>
          </elementProp>
        </collectionProp>
      </elementProp>
    </TestPlan>
    <hashTree>
      <!-- 文章详情接口测试 -->
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="获取文章详情">
        <intProp name="ThreadGroup.num_threads">500</intProp>
        <intProp name="ThreadGroup.ramp_time">60</intProp>
        <longProp name="ThreadGroup.duration">300</longProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="GET /api/posts/{id}">
          <stringProp name="HTTPSampler.domain">${BASE_URL}</stringProp>
          <stringProp name="HTTPSampler.path">/api/v1/posts/${__Random(1,10000)}</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
        </HTTPSamplerProxy>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
```

### Gatling 测试脚本

```scala
/**
 * 文章服务性能测试
 */
class PostServiceSimulation extends Simulation {
  
  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
  
  // 场景1：获取文章详情
  val getPostDetail = scenario("获取文章详情")
    .exec(
      http("GET /api/v1/posts/{id}")
        .get("/api/v1/posts/${postId}")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(100))
    )
  
  // 场景2：获取文章列表
  val getPostList = scenario("获取文章列表")
    .exec(
      http("GET /api/v1/posts")
        .get("/api/v1/posts")
        .queryParam("page", "1")
        .queryParam("size", "20")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(200))
    )
  
  // 场景3：点赞文章
  val likePost = scenario("点赞文章")
    .exec(
      http("POST /api/v1/posts/{id}/like")
        .post("/api/v1/posts/${postId}/like")
        .header("X-User-Id", "${userId}")
        .check(status.in(200, 201))
        .check(responseTimeInMillis.lte(100))
    )
  
  // 场景4：搜索文章
  val searchPost = scenario("搜索文章")
    .exec(
      http("GET /api/v1/search")
        .get("/api/v1/search")
        .queryParam("q", "Java")
        .queryParam("page", "1")
        .queryParam("size", "20")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(300))
    )
  
  // 负载配置
  setUp(
    getPostDetail.inject(
      rampUsers(500).during(60.seconds),
      constantUsersPerSec(100).during(300.seconds)
    ),
    getPostList.inject(
      rampUsers(300).during(60.seconds),
      constantUsersPerSec(50).during(300.seconds)
    ),
    likePost.inject(
      rampUsers(500).during(60.seconds),
      constantUsersPerSec(200).during(300.seconds)
    ),
    searchPost.inject(
      rampUsers(200).during(60.seconds),
      constantUsersPerSec(20).during(300.seconds)
    )
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.percentile(99).lt(500),
     global.successfulRequests.percent.gt(99)
   )
}
```

### 缓存性能基准测试

```java
/**
 * Redis 缓存性能基准测试
 */
@SpringBootTest
class CachePerformanceBenchmark {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private PostRepository postRepository;
    
    @Test
    void benchmark_cacheHitVsMiss() {
        Long postId = 1L;
        String cacheKey = PostRedisKeys.detail(postId);
        
        // 预热缓存
        Post post = postRepository.findById(postId);
        redisTemplate.opsForValue().set(cacheKey, post, Duration.ofMinutes(10));
        
        // 缓存命中性能
        long cacheHitStart = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            redisTemplate.opsForValue().get(cacheKey);
        }
        long cacheHitTime = System.nanoTime() - cacheHitStart;
        
        // 缓存未命中性能（直接查数据库）
        long cacheMissStart = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            postRepository.findById(postId);
        }
        long cacheMissTime = System.nanoTime() - cacheMissStart;
        
        System.out.println("缓存命中 10000 次耗时: " + cacheHitTime / 1_000_000 + "ms");
        System.out.println("缓存未命中 1000 次耗时: " + cacheMissTime / 1_000_000 + "ms");
        System.out.println("缓存加速比: " + (cacheMissTime * 10.0 / cacheHitTime) + "x");
        
        // 断言：缓存命中应该比数据库查询快至少 10 倍
        assertThat(cacheHitTime * 10).isLessThan(cacheMissTime);
    }
}
```

### 数据库性能基准测试

```java
/**
 * 数据库查询性能基准测试
 */
@SpringBootTest
@Testcontainers
class DatabasePerformanceBenchmark {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Test
    void benchmark_indexedVsNonIndexedQuery() {
        // 准备测试数据
        prepareTestData(100000);
        
        // 有索引查询
        long indexedStart = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            jdbcTemplate.queryForList(
                "SELECT * FROM posts WHERE author_id = ? AND status = 'PUBLISHED' ORDER BY created_at DESC LIMIT 20",
                "user-" + (i % 100));
        }
        long indexedTime = System.nanoTime() - indexedStart;
        
        // 无索引查询（全表扫描）
        long nonIndexedStart = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            jdbcTemplate.queryForList(
                "SELECT * FROM posts WHERE content LIKE ? LIMIT 20",
                "%关键词%");
        }
        long nonIndexedTime = System.nanoTime() - nonIndexedStart;
        
        System.out.println("有索引查询 1000 次耗时: " + indexedTime / 1_000_000 + "ms");
        System.out.println("无索引查询 100 次耗时: " + nonIndexedTime / 1_000_000 + "ms");
        
        // 断言：有索引查询应该更快
        assertThat(indexedTime).isLessThan(nonIndexedTime);
    }
}
```

### 消息队列性能基准测试

```java
/**
 * RocketMQ 消息队列性能基准测试
 */
@SpringBootTest
class MessageQueuePerformanceBenchmark {
    
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    @Test
    void benchmark_messagePublishThroughput() throws Exception {
        int messageCount = 10000;
        CountDownLatch latch = new CountDownLatch(messageCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < messageCount; i++) {
            PostLikedEvent event = new PostLikedEvent(
                UUID.randomUUID().toString(), 
                (long) i, 
                "user-" + i, 
                "author-1"
            );
            
            rocketMQTemplate.asyncSend(
                RocketMQConfig.TOPIC_POST + ":" + RocketMQConfig.TAG_POST_LIKED,
                event,
                new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                        successCount.incrementAndGet();
                        latch.countDown();
                    }
                    
                    @Override
                    public void onException(Throwable e) {
                        latch.countDown();
                    }
                }
            );
        }
        
        latch.await(60, TimeUnit.SECONDS);
        long endTime = System.nanoTime();
        
        double throughput = messageCount * 1_000_000_000.0 / (endTime - startTime);
        System.out.println("消息发送吞吐量: " + String.format("%.2f", throughput) + " msg/s");
        System.out.println("成功率: " + (successCount.get() * 100.0 / messageCount) + "%");
        
        // 断言：吞吐量应该大于 1000 msg/s
        assertThat(throughput).isGreaterThan(1000);
        // 断言：成功率应该大于 99%
        assertThat(successCount.get()).isGreaterThan((int) (messageCount * 0.99));
    }
}
```
