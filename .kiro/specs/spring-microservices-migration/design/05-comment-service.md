# Comment Service 设计

## DDD 分层结构

```
comment-service/
├── src/main/java/com/blog/comment/
│   ├── interfaces/
│   │   ├── controller/
│   │   │   ├── CommentController.java
│   │   │   └── CommentLikeController.java
│   │   └── dto/
│   ├── application/
│   │   ├── service/
│   │   │   ├── CommentApplicationService.java
│   │   │   └── CommentLikeApplicationService.java
│   │   └── event/
│   │       └── CommentEventPublisher.java
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Comment.java           # 聚合根
│   │   │   ├── CommentStats.java      # 值对象
│   │   │   └── CommentLike.java
│   │   ├── repository/
│   │   │   ├── CommentRepository.java
│   │   │   └── CommentLikeRepository.java
│   │   ├── service/
│   │   │   └── CommentDomainService.java
│   │   └── event/
│   │       ├── CommentCreatedEvent.java
│   │       └── CommentLikedEvent.java
│   └── infrastructure/
│       ├── repository/
│       │   ├── CommentRepositoryImpl.java
│       │   ├── CachedCommentRepository.java
│       │   └── mapper/
│       ├── cursor/
│       │   ├── HotCursorCodec.java
│       │   └── TimeCursorCodec.java
│       └── feign/
│           ├── UserServiceClient.java
│           └── PostServiceClient.java
```

## 扁平化评论结构说明

```
评论展示结构：
┌─────────────────────────────────────────────────────────────────┐
│ 文章评论列表（parentId = null 的顶级评论）                        │
│                                                                  │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ 评论A (id=1, parentId=null, rootId=1)                       │ │
│ │ "这篇文章写得很好！"                                          │ │
│ │ 👍 100  💬 5条回复  [展开回复]                                │ │
│ └─────────────────────────────────────────────────────────────┘ │
│                                                                  │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ 评论B (id=2, parentId=null, rootId=2)                       │ │
│ │ "学到了很多"                                                  │ │
│ │ 👍 50   💬 2条回复  [展开回复]                                │ │
│ └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘

点击 [展开回复] 后查询 rootId=1 的所有回复（扁平列表）：
┌─────────────────────────────────────────────────────────────────┐
│ 评论A的回复列表（rootId = 1 且 parentId != null）                 │
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │ 回复1 (id=3, parentId=1, rootId=1, replyToUserId=作者A)  │   │
│   │ @作者A 同意！                                             │   │
│   └─────────────────────────────────────────────────────────┘   │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │ 回复2 (id=4, parentId=1, rootId=1, replyToUserId=回复1)  │   │
│   │ @回复1作者 我也这么认为                                    │   │
│   └─────────────────────────────────────────────────────────┘   │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │ 回复3 (id=5, parentId=1, rootId=1, replyToUserId=作者A)  │   │
│   │ @作者A 能详细说说吗？                                      │   │
│   └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## Comment 聚合根（充血模型）

```java
public class Comment {
    private final Long id;
    private final Long postId;
    private final String authorId;
    private final LocalDateTime createdAt;
    
    private Long parentId;           // 父评论ID（null表示顶级评论）
    private Long rootId;             // 根评论ID（顶级评论的rootId是自己）
    private String replyToUserId;    // 被回复用户ID
    private String content;
    private CommentStatus status;
    private LocalDateTime updatedAt;
    private CommentStats stats;
    
    // 私有构造函数
    private Comment(Long id, Long postId, String authorId, String content) {
        Assert.notNull(id, "评论ID不能为空");
        Assert.notNull(postId, "文章ID不能为空");
        Assert.hasText(authorId, "作者ID不能为空");
        Assert.hasText(content, "评论内容不能为空");
        
        this.id = id;
        this.postId = postId;
        this.authorId = authorId;
        this.content = content;
        this.status = CommentStatus.NORMAL;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.stats = CommentStats.empty();
    }
    
    // 工厂方法 - 创建顶级评论
    public static Comment createTopLevel(Long id, Long postId, String authorId, String content) {
        validateContent(content);
        Comment comment = new Comment(id, postId, authorId, content);
        comment.rootId = id;  // 顶级评论的rootId是自己
        return comment;
    }
    
    // 工厂方法 - 创建回复评论（扁平化：所有回复的parentId都指向顶级评论）
    public static Comment createReply(Long id, Long postId, String authorId, String content,
                                      Long rootId, String replyToUserId) {
        validateContent(content);
        Comment comment = new Comment(id, postId, authorId, content);
        comment.parentId = rootId;      // parentId 指向顶级评论
        comment.rootId = rootId;        // rootId 也指向顶级评论
        comment.replyToUserId = replyToUserId;
        return comment;
    }
    
    // 领域行为 - 编辑评论
    public void edit(String newContent, String operatorId) {
        if (!this.authorId.equals(operatorId)) {
            throw new DomainException("只能编辑自己的评论");
        }
        if (this.status == CommentStatus.DELETED) {
            throw new DomainException("已删除的评论不能编辑");
        }
        validateContent(newContent);
        
        this.content = newContent;
        this.updatedAt = LocalDateTime.now();
    }
    
    // 领域行为 - 删除评论
    public void delete(String operatorId, boolean isAdmin) {
        if (!isAdmin && !this.authorId.equals(operatorId)) {
            throw new DomainException("无权删除此评论");
        }
        if (this.status == CommentStatus.DELETED) {
            throw new DomainException("评论已经删除");
        }
        
        this.status = CommentStatus.DELETED;
        this.updatedAt = LocalDateTime.now();
    }
    
    // 查询方法
    public boolean isTopLevel() {
        return this.parentId == null;
    }
    
    public boolean isReply() {
        return this.parentId != null;
    }
    
    public boolean isOwnedBy(String userId) {
        return this.authorId.equals(userId);
    }
    
    // 内容验证
    private static void validateContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new DomainException("评论内容不能为空");
        }
        if (content.length() > 2000) {
            throw new DomainException("评论内容不能超过2000字");
        }
    }
}

// 值对象 - 评论统计
public final class CommentStats {
    private final int likeCount;
    private final int replyCount;
    
    public CommentStats(int likeCount, int replyCount) {
        this.likeCount = Math.max(0, likeCount);
        this.replyCount = Math.max(0, replyCount);
    }
    
    public static CommentStats empty() {
        return new CommentStats(0, 0);
    }
    
    public CommentStats incrementLikes() {
        return new CommentStats(likeCount + 1, replyCount);
    }
    
    public CommentStats incrementReplies() {
        return new CommentStats(likeCount, replyCount + 1);
    }
}
```

## 游标编解码器

```java
/**
 * 热度排序游标编解码器
 * 游标格式：Base64("{likeCount}_{commentId}")
 * 使用 Base64 编码避免特殊字符问题，同时隐藏内部实现细节
 */
@Component
public class HotCursorCodec {
    
    private static final String SEPARATOR = "_";
    
    /**
     * 编码游标
     */
    public String encode(int likeCount, Long commentId) {
        String raw = likeCount + SEPARATOR + commentId;
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
    
    public String encode(Comment comment) {
        return encode(comment.getStats().getLikeCount(), comment.getId());
    }
    
    /**
     * 解码游标
     */
    public HotCursor decode(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(SEPARATOR);
            
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid cursor format");
            }
            
            return new HotCursor(Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
        } catch (Exception e) {
            throw new BusinessException("无效的分页游标");
        }
    }
    
    public record HotCursor(int likeCount, Long commentId) {}
}

/**
 * 时间排序游标编解码器
 * 游标格式：Base64(ISO-8601 时间戳 + "_" + commentId)
 * 加入 commentId 保证游标唯一性（同一时间可能有多条评论）
 */
@Component
public class TimeCursorCodec {
    
    private static final String SEPARATOR = "_";
    
    public String encode(LocalDateTime timestamp, Long commentId) {
        String raw = timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + SEPARATOR + commentId;
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
    
    public String encode(Comment comment) {
        return encode(comment.getCreatedAt(), comment.getId());
    }
    
    public TimeCursor decode(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int lastSeparator = raw.lastIndexOf(SEPARATOR);
            
            String timestampStr = raw.substring(0, lastSeparator);
            Long commentId = Long.parseLong(raw.substring(lastSeparator + 1));
            LocalDateTime timestamp = LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            return new TimeCursor(timestamp, commentId);
        } catch (Exception e) {
            throw new BusinessException("无效的分页游标");
        }
    }
    
    public record TimeCursor(LocalDateTime timestamp, Long commentId) {}
}
```

## 应用层服务

```java
@Service
public class CommentApplicationService {
    
    private final CommentRepository commentRepository;
    private final CommentEventPublisher eventPublisher;
    private final PostServiceClient postServiceClient;
    private final LeafIdGenerator idGenerator;
    private final RedisTemplate<String, Object> redisTemplate;
    private final HotCursorCodec hotCursorCodec;
    private final TimeCursorCodec timeCursorCodec;
    private final TransactionTemplate transactionTemplate;
    
    // ==================== 创建评论 ====================
    
    /**
     * 创建评论
     * 
     * 注意：Redis 操作放在事务提交后执行，避免事务回滚导致数据不一致
     */
    public Long createComment(String authorId, CreateCommentRequest request) {
        // 验证文章存在
        postServiceClient.validatePostExists(request.getPostId());
        
        Long commentId = idGenerator.nextId();
        final Comment[] commentHolder = new Comment[1];
        final Long rootId = request.getRootId();
        
        // 数据库操作在事务中执行
        transactionTemplate.executeWithoutResult(status -> {
            Comment comment;
            
            if (rootId == null) {
                // 创建顶级评论
                comment = Comment.createTopLevel(
                    commentId, request.getPostId(), authorId, request.getContent()
                );
            } else {
                // 创建回复评论
                Comment rootComment = commentRepository.findById(rootId);
                if (rootComment == null || !rootComment.isTopLevel()) {
                    throw new BusinessException("根评论不存在");
                }
                
                // 确定被回复用户
                String replyToUserId;
                Long replyToCommentId = request.getReplyToCommentId();
                if (replyToCommentId != null) {
                    Comment replyToComment = commentRepository.findById(replyToCommentId);
                    if (replyToComment == null) {
                        throw new BusinessException("被回复的评论不存在");
                    }
                    replyToUserId = replyToComment.getAuthorId();
                } else {
                    // 直接回复顶级评论
                    replyToUserId = rootComment.getAuthorId();
                }
                
                comment = Comment.createReply(
                    commentId, request.getPostId(), authorId, request.getContent(),
                    rootId, replyToUserId
                );
            }
            
            commentRepository.save(comment);
            commentHolder[0] = comment;
        });
        
        Comment comment = commentHolder[0];
        
        // 事务提交成功后，更新 Redis 缓存
        try {
            if (rootId != null) {
                // 更新顶级评论的回复计数
                redisTemplate.opsForValue().increment(
                    CommentRedisKeys.replyCount(rootId)
                );
            }
            
            // 更新文章评论计数
            redisTemplate.opsForValue().increment(
                PostRedisKeys.commentCount(request.getPostId())
            );
        } catch (Exception e) {
            log.warn("Redis 更新失败，将由 CDC 或定时任务修复: {}", e.getMessage());
        }
        
        // 发布事件
        eventPublisher.publish(new CommentCreatedEvent(
            commentId, request.getPostId(), authorId, 
            rootId, comment.getReplyToUserId()
        ));
        
        return commentId;
    }
    
    // ==================== 顶级评论查询 ====================
    
    /**
     * 【Web端】获取文章的顶级评论 - 传统分页
     * 适用于 Web 端，支持跳页
     */
    @Transactional(readOnly = true)
    public Page<CommentVO> getTopLevelCommentsByPage(Long postId, int page, int size, 
                                                      CommentSortType sortType) {
        Page<Comment> commentPage;
        
        if (sortType == CommentSortType.HOT) {
            commentPage = commentRepository.findTopLevelByPostIdOrderByLikesPage(postId, page, size);
        } else {
            commentPage = commentRepository.findTopLevelByPostIdOrderByTimePage(postId, page, size);
        }
        
        List<CommentVO> voList = assembleCommentVOList(commentPage.getContent());
        return new Page<>(voList, commentPage.getTotalElements(), page, size);
    }
    
    /**
     * 【移动端】获取文章的顶级评论 - 游标分页
     * 适用于移动端无限滚动加载
     */
    @Transactional(readOnly = true)
    public CursorPage<CommentVO> getTopLevelCommentsByCursor(Long postId, String cursor, int size,
                                                              CommentSortType sortType) {
        List<Comment> comments;
        String nextCursor = null;
        
        if (sortType == CommentSortType.HOT) {
            HotCursor hotCursor = hotCursorCodec.decode(cursor);
            comments = commentRepository.findTopLevelByPostIdOrderByLikesCursor(
                postId, hotCursor, size + 1  // 多查一条判断是否有下一页
            );
            
            boolean hasMore = comments.size() > size;
            if (hasMore) {
                comments = comments.subList(0, size);
                Comment lastComment = comments.get(comments.size() - 1);
                nextCursor = hotCursorCodec.encode(lastComment);
            }
        } else {
            TimeCursor timeCursor = timeCursorCodec.decode(cursor);
            comments = commentRepository.findTopLevelByPostIdOrderByTimeCursor(
                postId, timeCursor, size + 1
            );
            
            boolean hasMore = comments.size() > size;
            if (hasMore) {
                comments = comments.subList(0, size);
                Comment lastComment = comments.get(comments.size() - 1);
                nextCursor = timeCursorCodec.encode(lastComment);
            }
        }
        
        List<CommentVO> voList = assembleCommentVOList(comments);
        return new CursorPage<>(voList, nextCursor, nextCursor != null);
    }
    
    // ==================== 回复列表查询 ====================
    
    /**
     * 【Web端】获取评论的回复列表 - 传统分页
     * 展开某条顶级评论时调用
     */
    @Transactional(readOnly = true)
    public Page<CommentVO> getRepliesByPage(Long rootId, int page, int size) {
        Page<Comment> replyPage = commentRepository.findRepliesByRootIdPage(rootId, page, size);
        
        List<CommentVO> voList = assembleReplyVOList(replyPage.getContent());
        return new Page<>(voList, replyPage.getTotalElements(), page, size);
    }
    
    /**
     * 【移动端】获取评论的回复列表 - 游标分页
     * 展开某条顶级评论时调用，支持无限滚动
     */
    @Transactional(readOnly = true)
    public CursorPage<CommentVO> getRepliesByCursor(Long rootId, String cursor, int size) {
        TimeCursor timeCursor = timeCursorCodec.decode(cursor);
        
        List<Comment> replies = commentRepository.findRepliesByRootIdCursor(
            rootId, timeCursor, size + 1
        );
        
        boolean hasMore = replies.size() > size;
        String nextCursor = null;
        if (hasMore) {
            replies = replies.subList(0, size);
            Comment lastReply = replies.get(replies.size() - 1);
            nextCursor = timeCursorCodec.encode(lastReply);
        }
        
        List<CommentVO> voList = assembleReplyVOList(replies);
        return new CursorPage<>(voList, nextCursor, hasMore);
    }
    
    // ==================== 辅助方法 ====================
    
    private List<CommentVO> assembleCommentVOList(List<Comment> comments) {
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 批量获取用户信息
        Set<String> authorIds = comments.stream()
            .map(Comment::getAuthorId)
            .collect(Collectors.toSet());
        Map<String, UserBriefDTO> userMap = userServiceClient.batchGetUsers(authorIds);
        
        // 批量获取统计信息
        List<Long> commentIds = comments.stream().map(Comment::getId).collect(Collectors.toList());
        Map<Long, CommentStats> statsMap = getStatsFromCache(commentIds);
        
        return comments.stream()
            .map(c -> CommentVO.builder()
                .id(c.getId())
                .postId(c.getPostId())
                .content(c.getContent())
                .author(userMap.get(c.getAuthorId()))
                .likeCount(statsMap.getOrDefault(c.getId(), CommentStats.empty()).getLikeCount())
                .replyCount(statsMap.getOrDefault(c.getId(), CommentStats.empty()).getReplyCount())
                .createdAt(c.getCreatedAt())
                .build())
            .collect(Collectors.toList());
    }
    
    private List<CommentVO> assembleReplyVOList(List<Comment> replies) {
        if (replies.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 批量获取用户信息（包括被回复用户）
        Set<String> userIds = new HashSet<>();
        replies.forEach(r -> {
            userIds.add(r.getAuthorId());
            if (r.getReplyToUserId() != null) {
                userIds.add(r.getReplyToUserId());
            }
        });
        Map<String, UserBriefDTO> userMap = userServiceClient.batchGetUsers(userIds);
        
        return replies.stream()
            .map(r -> CommentVO.builder()
                .id(r.getId())
                .postId(r.getPostId())
                .rootId(r.getRootId())
                .content(r.getContent())
                .author(userMap.get(r.getAuthorId()))
                .replyToUser(r.getReplyToUserId() != null ? userMap.get(r.getReplyToUserId()) : null)
                .createdAt(r.getCreatedAt())
                .build())
            .collect(Collectors.toList());
    }
}
```


## Repository 接口与实现

```java
// Repository 接口
public interface CommentRepository {
    Comment findById(Long id);
    void save(Comment comment);
    void update(Comment comment);
    void delete(Long id);
    
    // ========== 顶级评论查询 ==========
    
    // Web端 - 传统分页
    Page<Comment> findTopLevelByPostIdOrderByTimePage(Long postId, int page, int size);
    Page<Comment> findTopLevelByPostIdOrderByLikesPage(Long postId, int page, int size);
    
    // 移动端 - 游标分页
    List<Comment> findTopLevelByPostIdOrderByTimeCursor(Long postId, TimeCursor cursor, int size);
    List<Comment> findTopLevelByPostIdOrderByLikesCursor(Long postId, HotCursor cursor, int size);
    
    // ========== 回复列表查询 ==========
    
    // Web端 - 传统分页
    Page<Comment> findRepliesByRootIdPage(Long rootId, int page, int size);
    
    // 移动端 - 游标分页
    List<Comment> findRepliesByRootIdCursor(Long rootId, TimeCursor cursor, int size);
    
    // 统计
    int countRepliesByRootId(Long rootId);
}

// Repository 实现
@Repository
public class CommentRepositoryImpl implements CommentRepository {
    
    private final CommentMapper commentMapper;
    
    // ========== 顶级评论 - 传统分页 ==========
    
    @Override
    public Page<Comment> findTopLevelByPostIdOrderByTimePage(Long postId, int page, int size) {
        int offset = page * size;
        List<CommentPO> poList = commentMapper.findTopLevelByPostIdOrderByTimeOffset(postId, offset, size);
        long total = commentMapper.countTopLevelByPostId(postId);
        
        List<Comment> comments = poList.stream().map(this::toDomain).collect(Collectors.toList());
        return new Page<>(comments, total, page, size);
    }
    
    @Override
    public Page<Comment> findTopLevelByPostIdOrderByLikesPage(Long postId, int page, int size) {
        int offset = page * size;
        List<CommentPO> poList = commentMapper.findTopLevelByPostIdOrderByLikesOffset(postId, offset, size);
        long total = commentMapper.countTopLevelByPostId(postId);
        
        List<Comment> comments = poList.stream().map(this::toDomain).collect(Collectors.toList());
        return new Page<>(comments, total, page, size);
    }
    
    // ========== 顶级评论 - 游标分页 ==========
    
    @Override
    public List<Comment> findTopLevelByPostIdOrderByTimeCursor(Long postId, TimeCursor cursor, int size) {
        LocalDateTime cursorTime = cursor != null ? cursor.timestamp() : null;
        Long cursorId = cursor != null ? cursor.commentId() : null;
        
        List<CommentPO> poList = commentMapper.findTopLevelByPostIdOrderByTimeCursor(
            postId, cursorTime, cursorId, size
        );
        
        return poList.stream().map(this::toDomain).collect(Collectors.toList());
    }
    
    @Override
    public List<Comment> findTopLevelByPostIdOrderByLikesCursor(Long postId, HotCursor cursor, int size) {
        Integer cursorLikeCount = cursor != null ? cursor.likeCount() : null;
        Long cursorId = cursor != null ? cursor.commentId() : null;
        
        List<CommentPO> poList = commentMapper.findTopLevelByPostIdOrderByLikesCursor(
            postId, cursorLikeCount, cursorId, size
        );
        
        return poList.stream().map(this::toDomain).collect(Collectors.toList());
    }
    
    // ========== 回复列表 ==========
    
    @Override
    public Page<Comment> findRepliesByRootIdPage(Long rootId, int page, int size) {
        int offset = page * size;
        List<CommentPO> poList = commentMapper.findRepliesByRootIdOffset(rootId, offset, size);
        long total = commentMapper.countRepliesByRootId(rootId);
        
        List<Comment> replies = poList.stream().map(this::toDomain).collect(Collectors.toList());
        return new Page<>(replies, total, page, size);
    }
    
    @Override
    public List<Comment> findRepliesByRootIdCursor(Long rootId, TimeCursor cursor, int size) {
        LocalDateTime cursorTime = cursor != null ? cursor.timestamp() : null;
        Long cursorId = cursor != null ? cursor.commentId() : null;
        
        List<CommentPO> poList = commentMapper.findRepliesByRootIdCursor(
            rootId, cursorTime, cursorId, size
        );
        
        return poList.stream().map(this::toDomain).collect(Collectors.toList());
    }
}
```

## MyBatis Mapper

```java
@Mapper
public interface CommentMapper {
    
    // ==================== 顶级评论 - 传统分页（Web端）====================
    
    @Select("""
        SELECT * FROM comments 
        WHERE post_id = #{postId} AND parent_id IS NULL AND status = 0
        ORDER BY created_at DESC
        LIMIT #{size} OFFSET #{offset}
        """)
    List<CommentPO> findTopLevelByPostIdOrderByTimeOffset(
        @Param("postId") Long postId,
        @Param("offset") int offset,
        @Param("size") int size
    );
    
    @Select("""
        SELECT c.*, COALESCE(cs.like_count, 0) as like_count
        FROM comments c
        LEFT JOIN comment_stats cs ON c.id = cs.comment_id
        WHERE c.post_id = #{postId} AND c.parent_id IS NULL AND c.status = 0
        ORDER BY COALESCE(cs.like_count, 0) DESC, c.id DESC
        LIMIT #{size} OFFSET #{offset}
        """)
    List<CommentPO> findTopLevelByPostIdOrderByLikesOffset(
        @Param("postId") Long postId,
        @Param("offset") int offset,
        @Param("size") int size
    );
    
    @Select("""
        SELECT COUNT(*) FROM comments 
        WHERE post_id = #{postId} AND parent_id IS NULL AND status = 0
        """)
    long countTopLevelByPostId(@Param("postId") Long postId);
    
    // ==================== 顶级评论 - 游标分页（移动端）====================
    
    @Select("""
        <script>
        SELECT * FROM comments 
        WHERE post_id = #{postId} AND parent_id IS NULL AND status = 0
        <if test="cursorTime != null">
            AND (created_at &lt; #{cursorTime} 
                 OR (created_at = #{cursorTime} AND id &lt; #{cursorId}))
        </if>
        ORDER BY created_at DESC, id DESC
        LIMIT #{size}
        </script>
        """)
    List<CommentPO> findTopLevelByPostIdOrderByTimeCursor(
        @Param("postId") Long postId,
        @Param("cursorTime") LocalDateTime cursorTime,
        @Param("cursorId") Long cursorId,
        @Param("size") int size
    );
    
    @Select("""
        <script>
        SELECT c.*, COALESCE(cs.like_count, 0) as like_count
        FROM comments c
        LEFT JOIN comment_stats cs ON c.id = cs.comment_id
        WHERE c.post_id = #{postId} AND c.parent_id IS NULL AND c.status = 0
        <if test="cursorLikeCount != null">
            AND (
                COALESCE(cs.like_count, 0) &lt; #{cursorLikeCount}
                OR (COALESCE(cs.like_count, 0) = #{cursorLikeCount} AND c.id &lt; #{cursorId})
            )
        </if>
        ORDER BY COALESCE(cs.like_count, 0) DESC, c.id DESC
        LIMIT #{size}
        </script>
        """)
    List<CommentPO> findTopLevelByPostIdOrderByLikesCursor(
        @Param("postId") Long postId,
        @Param("cursorLikeCount") Integer cursorLikeCount,
        @Param("cursorId") Long cursorId,
        @Param("size") int size
    );
    
    // ==================== 回复列表 - 传统分页（Web端）====================
    
    @Select("""
        SELECT * FROM comments 
        WHERE root_id = #{rootId} AND parent_id IS NOT NULL AND status = 0
        ORDER BY created_at ASC
        LIMIT #{size} OFFSET #{offset}
        """)
    List<CommentPO> findRepliesByRootIdOffset(
        @Param("rootId") Long rootId,
        @Param("offset") int offset,
        @Param("size") int size
    );
    
    @Select("""
        SELECT COUNT(*) FROM comments 
        WHERE root_id = #{rootId} AND parent_id IS NOT NULL AND status = 0
        """)
    long countRepliesByRootId(@Param("rootId") Long rootId);
    
    // ==================== 回复列表 - 游标分页（移动端）====================
    
    @Select("""
        <script>
        SELECT * FROM comments 
        WHERE root_id = #{rootId} AND parent_id IS NOT NULL AND status = 0
        <if test="cursorTime != null">
            AND (created_at &gt; #{cursorTime} 
                 OR (created_at = #{cursorTime} AND id &gt; #{cursorId}))
        </if>
        ORDER BY created_at ASC, id ASC
        LIMIT #{size}
        </script>
        """)
    List<CommentPO> findRepliesByRootIdCursor(
        @Param("rootId") Long rootId,
        @Param("cursorTime") LocalDateTime cursorTime,
        @Param("cursorId") Long cursorId,
        @Param("size") int size
    );
}
```

## Controller 接口

```java
@RestController
@RequestMapping("/api/comments")
public class CommentController {
    
    private final CommentApplicationService commentService;
    
    // ==================== 创建评论 ====================
    
    @PostMapping
    public Result<Long> createComment(@RequestBody @Valid CreateCommentRequest request,
                                      @CurrentUser String userId) {
        Long commentId = commentService.createComment(userId, request);
        return Result.success(commentId);
    }
    
    // ==================== 顶级评论查询 ====================
    
    /**
     * 【Web端】获取文章评论 - 传统分页
     */
    @GetMapping("/post/{postId}/page")
    public Result<Page<CommentVO>> getCommentsByPage(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "TIME") CommentSortType sort) {
        Page<CommentVO> result = commentService.getTopLevelCommentsByPage(postId, page, size, sort);
        return Result.success(result);
    }
    
    /**
     * 【移动端】获取文章评论 - 游标分页
     */
    @GetMapping("/post/{postId}/cursor")
    public Result<CursorPage<CommentVO>> getCommentsByCursor(
            @PathVariable Long postId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "TIME") CommentSortType sort) {
        CursorPage<CommentVO> result = commentService.getTopLevelCommentsByCursor(postId, cursor, size, sort);
        return Result.success(result);
    }
    
    // ==================== 回复列表查询 ====================
    
    /**
     * 【Web端】获取评论回复 - 传统分页
     */
    @GetMapping("/{commentId}/replies/page")
    public Result<Page<CommentVO>> getRepliesByPage(
            @PathVariable Long commentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<CommentVO> result = commentService.getRepliesByPage(commentId, page, size);
        return Result.success(result);
    }
    
    /**
     * 【移动端】获取评论回复 - 游标分页
     */
    @GetMapping("/{commentId}/replies/cursor")
    public Result<CursorPage<CommentVO>> getRepliesByCursor(
            @PathVariable Long commentId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        CursorPage<CommentVO> result = commentService.getRepliesByCursor(commentId, cursor, size);
        return Result.success(result);
    }
}
```

## 数据库表设计 (Comment_DB)

```sql
-- 评论表
CREATE TABLE comments (
    id BIGINT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    author_id VARCHAR(36) NOT NULL,
    parent_id BIGINT,                    -- 父评论ID（顶级评论为null，回复指向顶级评论）
    root_id BIGINT NOT NULL,             -- 根评论ID（顶级评论的rootId是自己）
    reply_to_user_id VARCHAR(36),        -- 被回复用户ID
    content TEXT NOT NULL,
    status SMALLINT DEFAULT 0,           -- 0:正常 1:删除
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 顶级评论查询索引（按时间排序）
CREATE INDEX idx_comments_post_time ON comments(post_id, status, created_at DESC) 
    WHERE parent_id IS NULL;

-- 顶级评论查询索引（按热度排序需要联合 comment_stats）
CREATE INDEX idx_comments_post_parent ON comments(post_id, parent_id, status);

-- 回复列表查询索引
CREATE INDEX idx_comments_root ON comments(root_id, status, created_at ASC) 
    WHERE parent_id IS NOT NULL;

-- 用户评论查询索引
CREATE INDEX idx_comments_author ON comments(author_id, created_at DESC);

-- 评论统计表
CREATE TABLE comment_stats (
    comment_id BIGINT PRIMARY KEY,
    like_count INT DEFAULT 0,
    reply_count INT DEFAULT 0
);

-- 热度排序联合索引
CREATE INDEX idx_comment_stats_likes ON comment_stats(like_count DESC, comment_id DESC);

-- 评论点赞表
CREATE TABLE comment_likes (
    id BIGINT PRIMARY KEY,
    comment_id BIGINT NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (comment_id, user_id)
);

CREATE INDEX idx_comment_likes_user ON comment_likes(user_id, created_at DESC);
```

## 评论点赞服务

```java
@Service
public class CommentLikeApplicationService {
    
    private final CommentLikeRepository likeRepository;
    private final CommentEventPublisher eventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private final TransactionTemplate transactionTemplate;
    
    /**
     * 点赞评论
     * 
     * 注意：Redis 操作放在事务提交后执行，避免事务回滚导致数据不一致
     */
    public void likeComment(String userId, Long commentId) {
        String likeKey = CommentRedisKeys.userLiked(userId, commentId);
        Boolean alreadyLiked = redisTemplate.hasKey(likeKey);
        
        if (Boolean.TRUE.equals(alreadyLiked)) {
            throw new BusinessException("已经点赞过了");
        }
        
        // 数据库操作在事务中执行
        transactionTemplate.executeWithoutResult(status -> {
            CommentLike like = new CommentLike(commentId, userId);
            likeRepository.save(like);
        });
        
        // 事务提交成功后，更新 Redis 缓存
        try {
            redisTemplate.opsForValue().increment(CommentRedisKeys.likeCount(commentId));
            redisTemplate.opsForValue().set(likeKey, "1");
        } catch (Exception e) {
            log.warn("Redis 更新失败，commentId={}, userId={}", commentId, userId, e);
        }
        
        eventPublisher.publish(new CommentLikedEvent(commentId, userId));
    }
    
    /**
     * 取消点赞评论
     * 
     * 注意：Redis 操作放在事务提交后执行，避免事务回滚导致数据不一致
     */
    public void unlikeComment(String userId, Long commentId) {
        String likeKey = CommentRedisKeys.userLiked(userId, commentId);
        Boolean liked = redisTemplate.hasKey(likeKey);
        
        if (!Boolean.TRUE.equals(liked)) {
            throw new BusinessException("尚未点赞");
        }
        
        // 数据库操作在事务中执行
        transactionTemplate.executeWithoutResult(status -> {
            likeRepository.delete(commentId, userId);
        });
        
        // 事务提交成功后，更新 Redis 缓存
        try {
            redisTemplate.opsForValue().decrement(CommentRedisKeys.likeCount(commentId));
            redisTemplate.delete(likeKey);
        } catch (Exception e) {
            log.warn("Redis 更新失败，commentId={}, userId={}", commentId, userId, e);
        }
    }
    
    public Map<Long, Boolean> batchCheckLiked(String userId, List<Long> commentIds) {
        List<String> keys = commentIds.stream()
            .map(id -> CommentRedisKeys.userLiked(userId, id))
            .collect(Collectors.toList());
        
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String key : keys) {
                connection.exists(key.getBytes());
            }
            return null;
        });
        
        Map<Long, Boolean> likedMap = new HashMap<>();
        for (int i = 0; i < commentIds.size(); i++) {
            likedMap.put(commentIds.get(i), (Boolean) results.get(i));
        }
        return likedMap;
    }
}
```

## DTO 定义

```java
// 创建评论请求
@Data
public class CreateCommentRequest {
    @NotNull
    private Long postId;
    
    @NotBlank
    @Size(max = 2000)
    private String content;
    
    private Long rootId;           // 回复时必填，顶级评论为null
    private Long replyToCommentId; // 回复某条具体评论时填写
}

// 评论VO
@Data
@Builder
public class CommentVO {
    private Long id;
    private Long postId;
    private Long rootId;           // 顶级评论为null
    private String content;
    private UserBriefDTO author;
    private UserBriefDTO replyToUser;  // 被回复用户，顶级评论为null
    private int likeCount;
    private int replyCount;        // 仅顶级评论有值
    private LocalDateTime createdAt;
    private boolean liked;         // 当前用户是否已点赞
}

// 传统分页响应
@Data
@AllArgsConstructor
public class Page<T> {
    private List<T> content;
    private long totalElements;
    private int page;
    private int size;
    private int totalPages;
    
    public Page(List<T> content, long totalElements, int page, int size) {
        this.content = content;
        this.totalElements = totalElements;
        this.page = page;
        this.size = size;
        this.totalPages = (int) Math.ceil((double) totalElements / size);
    }
}

// 游标分页响应
@Data
@AllArgsConstructor
public class CursorPage<T> {
    private List<T> content;
    private String nextCursor;  // 下一页游标，null表示没有更多
    private boolean hasMore;
}
```
