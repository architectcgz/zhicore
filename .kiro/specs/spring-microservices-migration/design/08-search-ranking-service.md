# Search & Ranking Service 设计

## Search Service

### DDD 分层结构

```
search-service/
├── src/main/java/com/blog/search/
│   ├── interfaces/
│   │   ├── controller/
│   │   │   └── SearchController.java
│   │   └── dto/
│   ├── application/
│   │   ├── service/
│   │   │   └── SearchApplicationService.java
│   │   └── event/
│   │       └── SearchEventConsumer.java
│   ├── domain/
│   │   ├── model/
│   │   │   ├── PostDocument.java
│   │   │   └── UserDocument.java
│   │   └── repository/
│   │       └── SearchRepository.java
│   └── infrastructure/
│       ├── elasticsearch/
│       │   ├── PostSearchRepository.java
│       │   └── ElasticsearchConfig.java
│       └── mq/
│           └── SearchEventConsumerImpl.java
```


### Elasticsearch 索引设计

```json
// 文章索引
PUT /posts
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "analysis": {
      "analyzer": {
        "ik_smart_pinyin": {
          "type": "custom",
          "tokenizer": "ik_smart",
          "filter": ["lowercase", "pinyin_filter"]
        }
      },
      "filter": {
        "pinyin_filter": {
          "type": "pinyin",
          "keep_full_pinyin": false,
          "keep_joined_full_pinyin": true,
          "keep_original": true,
          "limit_first_letter_length": 16,
          "remove_duplicated_term": true
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "id": { "type": "long" },
      "title": { 
        "type": "text", 
        "analyzer": "ik_max_word",
        "search_analyzer": "ik_smart",
        "fields": {
          "pinyin": {
            "type": "text",
            "analyzer": "ik_smart_pinyin"
          }
        }
      },
      "content": { 
        "type": "text", 
        "analyzer": "ik_max_word",
        "search_analyzer": "ik_smart"
      },
      "excerpt": { "type": "text", "analyzer": "ik_smart" },
      "authorId": { "type": "keyword" },
      "authorName": { "type": "keyword" },
      "tags": { "type": "keyword" },
      "categoryId": { "type": "long" },
      "status": { "type": "keyword" },
      "likeCount": { "type": "integer" },
      "commentCount": { "type": "integer" },
      "viewCount": { "type": "long" },
      "publishedAt": { "type": "date" },
      "createdAt": { "type": "date" }
    }
  }
}
```

### 搜索服务实现

```java
@Service
public class SearchApplicationService {
    
    private final ElasticsearchClient esClient;
    
    public SearchResult<PostSearchVO> searchPosts(String keyword, int page, int size) {
        SearchResponse<PostDocument> response = esClient.search(s -> s
            .index("posts")
            .query(q -> q
                .bool(b -> b
                    .should(sh -> sh
                        .multiMatch(m -> m
                            .query(keyword)
                            .fields("title^3", "title.pinyin^2", "content", "tags^2")
                            .type(TextQueryType.BestFields)
                            .fuzziness("AUTO")
                        )
                    )
                    .filter(f -> f
                        .term(t -> t.field("status").value("PUBLISHED"))
                    )
                )
            )
            .highlight(h -> h
                .fields("title", hf -> hf.preTags("<em>").postTags("</em>"))
                .fields("content", hf -> hf
                    .preTags("<em>")
                    .postTags("</em>")
                    .fragmentSize(150)
                    .numberOfFragments(3)
                )
            )
            .from(page * size)
            .size(size)
            .sort(so -> so.score(sc -> sc.order(SortOrder.Desc)))
            .sort(so -> so.field(f -> f.field("publishedAt").order(SortOrder.Desc))),
            PostDocument.class
        );
        
        return assembleSearchResult(response, page, size);
    }
    
    public List<String> suggest(String prefix, int limit) {
        SearchResponse<PostDocument> response = esClient.search(s -> s
            .index("posts")
            .query(q -> q
                .bool(b -> b
                    .should(sh -> sh
                        .prefix(p -> p.field("title").value(prefix))
                    )
                    .should(sh -> sh
                        .prefix(p -> p.field("title.pinyin").value(prefix))
                    )
                )
            )
            .size(limit)
            .source(src -> src.includes("title")),
            PostDocument.class
        );
        
        return response.hits().hits().stream()
            .map(hit -> hit.source().getTitle())
            .distinct()
            .collect(Collectors.toList());
    }
}
```


### 索引同步（事件消费）

```java
/**
 * 文章发布索引消费者
 */
@Component
@RocketMQMessageListener(
    topic = "post-topic",
    selectorExpression = "published",
    consumerGroup = "search-post-published-consumer"
)
public class PostPublishedSearchConsumer implements RocketMQListener<PostPublishedEvent> {
    
    private final ElasticsearchClient esClient;
    private final PostServiceClient postServiceClient;
    
    @Override
    public void onMessage(PostPublishedEvent event) {
        PostDetailDTO post = postServiceClient.getPostDetail(event.getPostId());
        
        PostDocument document = PostDocument.builder()
            .id(post.getId())
            .title(post.getTitle())
            .content(post.getContent())
            .excerpt(post.getExcerpt())
            .authorId(post.getAuthorId())
            .authorName(post.getAuthorName())
            .tags(post.getTags())
            .categoryId(post.getCategoryId())
            .status("PUBLISHED")
            .likeCount(post.getLikeCount())
            .commentCount(post.getCommentCount())
            .viewCount(post.getViewCount())
            .publishedAt(post.getPublishedAt())
            .createdAt(post.getCreatedAt())
            .build();
        
        esClient.index(i -> i
            .index("posts")
            .id(String.valueOf(post.getId()))
            .document(document)
        );
    }
}

/**
 * 文章更新索引消费者
 */
@Component
@RocketMQMessageListener(
    topic = "post-topic",
    selectorExpression = "updated",
    consumerGroup = "search-post-updated-consumer"
)
public class PostUpdatedSearchConsumer implements RocketMQListener<PostUpdatedEvent> {
    
    private final ElasticsearchClient esClient;
    
    @Override
    public void onMessage(PostUpdatedEvent event) {
        // 部分更新
        esClient.update(u -> u
            .index("posts")
            .id(String.valueOf(event.getPostId()))
            .doc(Map.of(
                "title", event.getTitle(),
                "content", event.getContent(),
                "excerpt", event.getExcerpt(),
                "tags", event.getTags()
            )),
            PostDocument.class
        );
    }
}

/**
 * 文章删除索引消费者
 */
@Component
@RocketMQMessageListener(
    topic = "post-topic",
    selectorExpression = "deleted",
    consumerGroup = "search-post-deleted-consumer"
)
public class PostDeletedSearchConsumer implements RocketMQListener<PostDeletedEvent> {
    
    private final ElasticsearchClient esClient;
    
    @Override
    public void onMessage(PostDeletedEvent event) {
        esClient.delete(d -> d
            .index("posts")
            .id(String.valueOf(event.getPostId()))
        );
    }
}
```

---

## Ranking Service

### DDD 分层结构

```
ranking-service/
├── src/main/java/com/blog/ranking/
│   ├── interfaces/
│   │   ├── controller/
│   │   │   └── RankingController.java
│   │   └── dto/
│   ├── application/
│   │   ├── service/
│   │   │   ├── PostRankingService.java
│   │   │   ├── CreatorRankingService.java
│   │   │   └── TopicRankingService.java
│   │   └── event/
│   │       └── RankingEventConsumer.java
│   ├── domain/
│   │   ├── model/
│   │   │   └── HotScore.java
│   │   └── service/
│   │       └── HotScoreCalculator.java
│   └── infrastructure/
│       ├── redis/
│       │   └── RankingRedisRepository.java
│       └── scheduler/
│           └── RankingRefreshScheduler.java
```


### 热度计算算法

```java
@Service
public class HotScoreCalculator {
    
    // 热度权重配置
    private static final double VIEW_WEIGHT = 1.0;
    private static final double LIKE_WEIGHT = 5.0;
    private static final double COMMENT_WEIGHT = 10.0;
    private static final double FAVORITE_WEIGHT = 8.0;
    
    // 时间衰减因子（半衰期：7天）
    private static final double HALF_LIFE_DAYS = 7.0;
    
    /**
     * 计算文章热度分数
     * 公式：score = (views * 1 + likes * 5 + comments * 10 + favorites * 8) * timeDecay
     */
    public double calculatePostHotScore(PostStats stats, LocalDateTime publishedAt) {
        double baseScore = stats.getViewCount() * VIEW_WEIGHT
                         + stats.getLikeCount() * LIKE_WEIGHT
                         + stats.getCommentCount() * COMMENT_WEIGHT
                         + stats.getFavoriteCount() * FAVORITE_WEIGHT;
        
        double timeDecay = calculateTimeDecay(publishedAt);
        return baseScore * timeDecay;
    }
    
    /**
     * 计算创作者热度分数
     * 公式：score = followers * 2 + totalLikes * 1 + totalComments * 1.5 + postCount * 3
     */
    public double calculateCreatorHotScore(CreatorStats stats) {
        return stats.getFollowersCount() * 2.0
             + stats.getTotalLikes() * 1.0
             + stats.getTotalComments() * 1.5
             + stats.getPostCount() * 3.0;
    }
    
    /**
     * 时间衰减函数（指数衰减）
     */
    private double calculateTimeDecay(LocalDateTime publishedAt) {
        long daysSincePublish = ChronoUnit.DAYS.between(publishedAt, LocalDateTime.now());
        return Math.pow(0.5, daysSincePublish / HALF_LIFE_DAYS);
    }
}
```

### 排行榜服务实现

```java
@Service
public class PostRankingService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final HotScoreCalculator scoreCalculator;
    
    private static final String HOT_POSTS_KEY = "ranking:posts:hot";
    private static final String DAILY_HOT_KEY = "ranking:posts:daily:%s";
    private static final String WEEKLY_HOT_KEY = "ranking:posts:weekly:%s";
    
    /**
     * 更新文章热度分数
     */
    public void updatePostScore(Long postId, PostStats stats, LocalDateTime publishedAt) {
        double score = scoreCalculator.calculatePostHotScore(stats, publishedAt);
        
        // 更新总榜
        redisTemplate.opsForZSet().add(HOT_POSTS_KEY, postId.toString(), score);
        
        // 更新日榜
        String dailyKey = String.format(DAILY_HOT_KEY, LocalDate.now());
        redisTemplate.opsForZSet().add(dailyKey, postId.toString(), score);
        redisTemplate.expire(dailyKey, Duration.ofDays(2));
        
        // 更新周榜
        String weeklyKey = String.format(WEEKLY_HOT_KEY, getWeekNumber());
        redisTemplate.opsForZSet().add(weeklyKey, postId.toString(), score);
        redisTemplate.expire(weeklyKey, Duration.ofDays(14));
    }
    
    /**
     * 获取热门文章排行
     */
    public List<Long> getHotPosts(int page, int size) {
        int start = page * size;
        int end = start + size - 1;
        
        Set<Object> postIds = redisTemplate.opsForZSet()
            .reverseRange(HOT_POSTS_KEY, start, end);
        
        return postIds.stream()
            .map(id -> Long.parseLong(id.toString()))
            .collect(Collectors.toList());
    }
    
    /**
     * 获取日榜
     */
    public List<Long> getDailyHotPosts(LocalDate date, int limit) {
        String key = String.format(DAILY_HOT_KEY, date);
        Set<Object> postIds = redisTemplate.opsForZSet()
            .reverseRange(key, 0, limit - 1);
        
        return postIds.stream()
            .map(id -> Long.parseLong(id.toString()))
            .collect(Collectors.toList());
    }
}
```

### 定时刷新任务

```java
@Component
public class RankingRefreshScheduler {
    
    private final PostRankingService postRankingService;
    private final CreatorRankingService creatorRankingService;
    private final PostServiceClient postServiceClient;
    
    /**
     * 每小时刷新热门文章排行
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void refreshHotPosts() {
        // 获取最近7天的活跃文章
        List<PostStatsDTO> activePosts = postServiceClient.getActivePostStats(7);
        
        for (PostStatsDTO post : activePosts) {
            postRankingService.updatePostScore(
                post.getPostId(),
                post.getStats(),
                post.getPublishedAt()
            );
        }
    }
    
    /**
     * 每天凌晨刷新创作者排行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void refreshCreatorRanking() {
        creatorRankingService.refreshAllCreatorScores();
    }
}
```

### 实时热度更新（事件驱动）

定时任务只能保证周期性的全量刷新，但无法及时反映用户的实时互动。为了提高排行榜的实时性，需要在事件发生时增量更新热度分数。

```java
/**
 * 排行榜事件消费基类
 */
public abstract class BaseRankingConsumer {
    
    protected final PostRankingService postRankingService;
    protected final HotScoreCalculator scoreCalculator;
    protected final RedisTemplate<String, Object> redisTemplate;
    
    // 热度增量权重
    protected static final double VIEW_DELTA = 1.0;
    protected static final double LIKE_DELTA = 5.0;
    protected static final double COMMENT_DELTA = 10.0;
    protected static final double FAVORITE_DELTA = 8.0;
    
    /**
     * 增量更新文章热度分数
     * 使用 Redis ZINCRBY 原子操作
     */
    protected void incrementPostScore(Long postId, double baseDelta, LocalDateTime publishedAt) {
        // 应用时间衰减因子
        double timeDecay = scoreCalculator.calculateTimeDecay(publishedAt);
        double scoreDelta = baseDelta * timeDecay;
        
        String postIdStr = postId.toString();
        
        // 原子增量更新总榜
        redisTemplate.opsForZSet().incrementScore(
            PostRankingService.HOT_POSTS_KEY, postIdStr, scoreDelta
        );
        
        // 原子增量更新日榜
        String dailyKey = String.format(PostRankingService.DAILY_HOT_KEY, LocalDate.now());
        redisTemplate.opsForZSet().incrementScore(dailyKey, postIdStr, scoreDelta);
        
        // 原子增量更新周榜
        String weeklyKey = String.format(PostRankingService.WEEKLY_HOT_KEY, 
            postRankingService.getWeekNumber());
        redisTemplate.opsForZSet().incrementScore(weeklyKey, postIdStr, scoreDelta);
    }
}

/**
 * 文章浏览热度消费者
 */
@Component
@RocketMQMessageListener(
    topic = "post-topic",
    selectorExpression = "viewed",
    consumerGroup = "ranking-post-viewed-consumer"
)
public class PostViewedRankingConsumer extends BaseRankingConsumer 
        implements RocketMQListener<PostViewedEvent> {
    
    @Override
    public void onMessage(PostViewedEvent event) {
        incrementPostScore(event.getPostId(), VIEW_DELTA, event.getPublishedAt());
    }
}

/**
 * 文章点赞热度消费者
 */
@Component
@RocketMQMessageListener(
    topic = "post-topic",
    selectorExpression = "liked",
    consumerGroup = "ranking-post-liked-consumer"
)
public class PostLikedRankingConsumer extends BaseRankingConsumer 
        implements RocketMQListener<PostLikedEvent> {
    
    @Override
    public void onMessage(PostLikedEvent event) {
        incrementPostScore(event.getPostId(), LIKE_DELTA, event.getPublishedAt());
    }
}

/**
 * 文章取消点赞热度消费者
 */
@Component
@RocketMQMessageListener(
    topic = "post-topic",
    selectorExpression = "unliked",
    consumerGroup = "ranking-post-unliked-consumer"
)
public class PostUnlikedRankingConsumer extends BaseRankingConsumer 
        implements RocketMQListener<PostUnlikedEvent> {
    
    @Override
    public void onMessage(PostUnlikedEvent event) {
        incrementPostScore(event.getPostId(), -LIKE_DELTA, event.getPublishedAt());
    }
}

/**
 * 评论创建热度消费者
 */
@Component
@RocketMQMessageListener(
    topic = "comment-topic",
    selectorExpression = "created",
    consumerGroup = "ranking-comment-created-consumer"
)
public class CommentCreatedRankingConsumer extends BaseRankingConsumer 
        implements RocketMQListener<CommentCreatedEvent> {
    
    @Override
    public void onMessage(CommentCreatedEvent event) {
        incrementPostScore(event.getPostId(), COMMENT_DELTA, event.getPostPublishedAt());
    }
}

/**
 * 文章收藏热度消费者
 */
@Component
@RocketMQMessageListener(
    topic = "post-topic",
    selectorExpression = "favorited",
    consumerGroup = "ranking-post-favorited-consumer"
)
public class PostFavoritedRankingConsumer extends BaseRankingConsumer 
        implements RocketMQListener<PostFavoritedEvent> {
    
    @Override
    public void onMessage(PostFavoritedEvent event) {
        incrementPostScore(event.getPostId(), FAVORITE_DELTA, event.getPublishedAt());
    }
}
```
