package com.zhicore.content.infrastructure.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.content.domain.exception.DualStorageException;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.repository.PostRepository;
import com.zhicore.content.domain.service.DualStorageManager;
import com.zhicore.content.infrastructure.mongodb.document.PostContent;
import com.zhicore.content.infrastructure.mongodb.repository.PostContentRepository;
import com.zhicore.content.infrastructure.util.ContentEnricher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 双存储管理器实现
 * 负责协调 PostgreSQL 和 MongoDB 的数据操作，确保数据一致性
 * 使用 Sentinel 实现熔断降级保护
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DualStorageManagerImpl implements DualStorageManager {

    private final PostRepository postRepository;
    private final PostContentRepository postContentRepository;
    private final ContentEnricher contentEnricher;
    
    // 用于并行查询的线程池
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * 创建文章（三阶段提交）
     * 1. PG Insert (Status=DRAFT or original status)
     * 2. Mongo Insert
     * 3. If needed, PG Update (Status=PUBLISHED)
     * 
     * 注意：根据设计文档，三阶段提交是：
     * 1. PG写入PUBLISHING状态
     * 2. Mongo写入
     * 3. PG更新为PUBLISHED状态
     * 
     * 但由于Post领域模型的限制，我们采用以下策略：
     * - 如果文章状态是DRAFT，直接保存为DRAFT
     * - 如果文章状态是PUBLISHED，先保存为DRAFT，然后在MongoDB写入成功后调用publish()
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @SentinelResource(
        value = "createPost",
        blockHandler = "createPostBlockHandler",
        fallback = "createPostFallback"
    )
    public Long createPost(Post post, PostContent content) {
        Long postId = post.getId();
        
        try {
            // 阶段1: 写入 PostgreSQL
            // 记录原始状态，如果是要发布的文章，先保存为草稿
            PostStatus originalStatus = post.getStatus();
            boolean shouldPublish = originalStatus == PostStatus.PUBLISHED;
            
            postRepository.save(post);
            log.info("Successfully saved post metadata to PostgreSQL: {}", postId);
            
            // 阶段2: 写入 MongoDB
            try {
                content.setPostId(String.valueOf(postId));
                content.setCreatedAt(LocalDateTime.now());
                content.setUpdatedAt(LocalDateTime.now());
                
                // 增强内容：自动计算字数、阅读时间、提取媒体资源等
                PostContent enrichedContent = contentEnricher.enrich(content);
                
                postContentRepository.save(enrichedContent);
                log.info("Successfully saved post content to MongoDB: {}", postId);
                
                // 阶段3: 如果需要发布，更新 PostgreSQL 状态为 PUBLISHED
                if (shouldPublish) {
                    post.publish();
                    postRepository.update(post);
                    log.info("Successfully updated post status to PUBLISHED: {}", postId);
                }
                
                return postId;
                
            } catch (Exception mongoEx) {
                log.error("Failed to save content to MongoDB for post: {}", postId, mongoEx);
                // MongoDB 写入失败，PostgreSQL 会自动回滚（因为 @Transactional）
                throw new DualStorageException("Failed to save content to MongoDB", mongoEx);
            }
            
        } catch (Exception pgEx) {
            log.error("Failed to save post to PostgreSQL: {}", postId, pgEx);
            throw new DualStorageException("Failed to save post to PostgreSQL", pgEx);
        }
    }
    
    /**
     * 创建文章的流控/熔断处理器
     */
    public Long createPostBlockHandler(Post post, PostContent content, BlockException ex) {
        log.warn("createPost blocked by Sentinel: {}", ex.getMessage());
        throw new DualStorageException("Service is busy, please try again later", ex);
    }
    
    /**
     * 创建文章的降级处理器（业务异常）
     */
    public Long createPostFallback(Post post, PostContent content, Throwable ex) {
        log.error("createPost fallback triggered: {}", ex.getMessage(), ex);
        throw new DualStorageException("Failed to create post, please try again later", ex);
    }

    /**
     * 获取文章完整详情（并行查询）
     */
    @Override
    @SentinelResource(
        value = "getPostFullDetail",
        blockHandler = "getPostFullDetailBlockHandler",
        fallback = "getPostFullDetailFallback"
    )
    public PostDetail getPostFullDetail(Long postId) {
        try {
            // 并行查询 PostgreSQL 和 MongoDB
            CompletableFuture<Post> postFuture = CompletableFuture.supplyAsync(() -> {
                Optional<Post> postOpt = postRepository.findById(postId);
                if (!postOpt.isPresent()) {
                    // 文章不存在，抛出 BusinessException 返回 404
                    throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
                }
                return postOpt.get();
            }, executorService);
            
            CompletableFuture<PostContent> contentFuture = CompletableFuture.supplyAsync(() -> {
                Optional<PostContent> contentOpt = postContentRepository.findByPostId(String.valueOf(postId));
                if (!contentOpt.isPresent()) {
                    throw new DualStorageException("Post content not found in MongoDB: " + postId);
                }
                return contentOpt.get();
            }, executorService);
            
            // 等待两个查询都完成
            CompletableFuture.allOf(postFuture, contentFuture).join();
            
            Post post = postFuture.get();
            PostContent content = contentFuture.get();
            
            log.info("Successfully retrieved full detail for post: {}", postId);
            return new PostDetail(post, content);
            
        } catch (java.util.concurrent.CompletionException e) {
            // 解包 CompletionException，获取真实异常
            Throwable cause = e.getCause();
            if (cause instanceof BusinessException) {
                // 重新抛出 BusinessException，让它返回正确的 HTTP 状态码
                throw (BusinessException) cause;
            }
            log.error("Failed to get full detail for post: {}", postId, e);
            throw new DualStorageException("Failed to get full detail for post: " + postId, e);
        } catch (Exception e) {
            log.error("Failed to get full detail for post: {}", postId, e);
            throw new DualStorageException("Failed to get full detail for post: " + postId, e);
        }
    }
    
    /**
     * 获取文章详情的流控/熔断处理器
     * 降级策略：仅返回 PostgreSQL 数据（不包含完整内容）
     */
    public PostDetail getPostFullDetailBlockHandler(Long postId, BlockException ex) {
        log.warn("getPostFullDetail blocked by Sentinel, degrading to PG-only query: {}", postId);
        try {
            Optional<Post> postOpt = postRepository.findById(postId);
            if (!postOpt.isPresent()) {
                throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
            }
            // 返回仅包含 PostgreSQL 数据的降级响应
            return new PostDetail(postOpt.get(), null);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get degraded post detail: {}", postId, e);
            throw new DualStorageException("Service is busy and degraded query failed", e);
        }
    }
    
    /**
     * 获取文章详情的降级处理器（业务异常）
     * 降级策略：仅返回 PostgreSQL 数据
     */
    public PostDetail getPostFullDetailFallback(Long postId, Throwable ex) {
        log.error("getPostFullDetail fallback triggered, degrading to PG-only query: {}", postId, ex);
        
        // 如果是 BusinessException，直接重新抛出
        if (ex instanceof BusinessException) {
            throw (BusinessException) ex;
        }
        
        try {
            Optional<Post> postOpt = postRepository.findById(postId);
            if (!postOpt.isPresent()) {
                throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
            }
            return new PostDetail(postOpt.get(), null);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get fallback post detail: {}", postId, e);
            throw new DualStorageException("Failed to get post detail", e);
        }
    }

    /**
     * 仅获取文章内容
     */
    @Override
    @SentinelResource(
        value = "getPostContent",
        blockHandler = "getPostContentBlockHandler",
        fallback = "getPostContentFallback"
    )
    public PostContent getPostContent(Long postId) {
        try {
            Optional<PostContent> contentOpt = postContentRepository.findByPostId(String.valueOf(postId));
            if (!contentOpt.isPresent()) {
                throw new DualStorageException("Post content not found: " + postId);
            }
            log.info("Successfully retrieved content for post: {}", postId);
            return contentOpt.get();
        } catch (Exception e) {
            log.error("Failed to get content for post: {}", postId, e);
            throw new DualStorageException("Failed to get content for post: " + postId, e);
        }
    }
    
    /**
     * 获取文章内容的流控/熔断处理器
     */
    public PostContent getPostContentBlockHandler(Long postId, BlockException ex) {
        log.warn("getPostContent blocked by Sentinel: {}", postId);
        throw new DualStorageException("Service is busy, please try again later", ex);
    }
    
    /**
     * 获取文章内容的降级处理器
     */
    public PostContent getPostContentFallback(Long postId, Throwable ex) {
        log.error("getPostContent fallback triggered: {}", postId, ex);
        throw new DualStorageException("Failed to get post content", ex);
    }

    /**
     * 更新文章（双写更新）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @SentinelResource(
        value = "updatePost",
        blockHandler = "updatePostBlockHandler",
        fallback = "updatePostFallback"
    )
    public void updatePost(Post post, PostContent content) {
        Long postId = post.getId();
        
        try {
            // 更新 PostgreSQL
            postRepository.update(post);
            log.info("Successfully updated post metadata in PostgreSQL: {}", postId);
            
            // 更新 MongoDB
            try {
                content.setPostId(String.valueOf(postId));
                content.setUpdatedAt(LocalDateTime.now());
                
                // 增强内容：自动计算字数、阅读时间、提取媒体资源等
                PostContent enrichedContent = contentEnricher.enrich(content);
                
                postContentRepository.save(enrichedContent);
                log.info("Successfully updated post content in MongoDB: {}", postId);
                
            } catch (Exception mongoEx) {
                log.error("Failed to update content in MongoDB for post: {}", postId, mongoEx);
                // MongoDB 更新失败，PostgreSQL 会自动回滚
                throw new DualStorageException("Failed to update content in MongoDB", mongoEx);
            }
            
        } catch (Exception pgEx) {
            log.error("Failed to update post in PostgreSQL: {}", postId, pgEx);
            throw new DualStorageException("Failed to update post in PostgreSQL", pgEx);
        }
    }
    
    /**
     * 更新文章的流控/熔断处理器
     */
    public void updatePostBlockHandler(Post post, PostContent content, BlockException ex) {
        log.warn("updatePost blocked by Sentinel: {}", ex.getMessage());
        throw new DualStorageException("Service is busy, please try again later", ex);
    }
    
    /**
     * 更新文章的降级处理器
     */
    public void updatePostFallback(Post post, PostContent content, Throwable ex) {
        log.error("updatePost fallback triggered: {}", ex.getMessage(), ex);
        throw new DualStorageException("Failed to update post, please try again later", ex);
    }

    /**
     * 删除文章（双删除）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @SentinelResource(
        value = "deletePost",
        blockHandler = "deletePostBlockHandler",
        fallback = "deletePostFallback"
    )
    public void deletePost(Long postId) {
        try {
            // 删除 PostgreSQL
            postRepository.delete(postId);
            log.info("Successfully deleted post from PostgreSQL: {}", postId);
            
            // 删除 MongoDB
            try {
                postContentRepository.deleteByPostId(String.valueOf(postId));
                log.info("Successfully deleted post content from MongoDB: {}", postId);
                
            } catch (Exception mongoEx) {
                log.error("Failed to delete content from MongoDB for post: {}", postId, mongoEx);
                // MongoDB 删除失败，PostgreSQL 会自动回滚
                throw new DualStorageException("Failed to delete content from MongoDB", mongoEx);
            }
            
        } catch (Exception pgEx) {
            log.error("Failed to delete post from PostgreSQL: {}", postId, pgEx);
            throw new DualStorageException("Failed to delete post from PostgreSQL", pgEx);
        }
    }
    
    /**
     * 删除文章的流控/熔断处理器
     */
    public void deletePostBlockHandler(Long postId, BlockException ex) {
        log.warn("deletePost blocked by Sentinel: {}", postId);
        throw new DualStorageException("Service is busy, please try again later", ex);
    }
    
    /**
     * 删除文章的降级处理器
     */
    public void deletePostFallback(Long postId, Throwable ex) {
        log.error("deletePost fallback triggered: {}", postId, ex);
        throw new DualStorageException("Failed to delete post, please try again later", ex);
    }
}
