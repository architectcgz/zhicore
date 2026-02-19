package com.blog.post.infrastructure.service;

import com.blog.post.domain.exception.DualStorageException;
import com.blog.post.domain.model.Post;
import com.blog.post.domain.model.PostStatus;
import com.blog.post.domain.repository.PostRepository;
import com.blog.post.domain.service.QueryOptimizationService;
import com.blog.post.infrastructure.mongodb.document.PostContent;
import com.blog.post.infrastructure.mongodb.repository.PostContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 查询性能优化服务实现
 * 
 * 性能优化策略：
 * 1. 列表查询仅访问 PostgreSQL，不加载内容
 * 2. 详情查询并行访问 PostgreSQL 和 MongoDB
 * 3. 批量查询使用批量接口减少网络往返
 * 4. 使用游标分页提升大数据量查询性能
 *
 * @author Blog Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryOptimizationServiceImpl implements QueryOptimizationService {

    private final PostRepository postRepository;
    private final PostContentRepository postContentRepository;
    
    // 用于并行查询的线程池
    private final ExecutorService executorService = Executors.newFixedThreadPool(20);

    /**
     * 优化的文章列表查询（仅查询 PostgreSQL 元数据）
     * 性能目标：< 100ms
     */
    @Override
    public List<Post> getPostList(int offset, int limit) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 仅从 PostgreSQL 查询元数据，不加载内容
            List<Post> posts = postRepository.findPublished(offset, limit);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Retrieved {} posts in {}ms (target: <100ms)", posts.size(), duration);
            
            if (duration > 100) {
                log.warn("Post list query exceeded target time: {}ms > 100ms", duration);
            }
            
            return posts;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to get post list in {}ms", duration, e);
            throw new DualStorageException("Failed to get post list", e);
        }
    }

    /**
     * 优化的文章详情查询（并行查询 PG 和 Mongo）
     * 性能目标：< 200ms
     */
    @Override
    public PostDetailDTO getPostDetail(Long postId) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 并行查询 PostgreSQL 和 MongoDB
            CompletableFuture<Post> postFuture = CompletableFuture.supplyAsync(() -> {
                Optional<Post> postOpt = postRepository.findById(postId);
                if (!postOpt.isPresent()) {
                    throw new DualStorageException("Post not found in PostgreSQL: " + postId);
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
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Retrieved post detail for {} in {}ms (target: <200ms)", postId, duration);
            
            if (duration > 200) {
                log.warn("Post detail query exceeded target time: {}ms > 200ms", duration);
            }
            
            return new PostDetailDTO(post, content);
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to get post detail for {} in {}ms", postId, duration, e);
            throw new DualStorageException("Failed to get post detail: " + postId, e);
        }
    }

    /**
     * 批量查询文章元数据（仅 PostgreSQL）
     */
    @Override
    public Map<Long, Post> batchGetPostMetadata(List<Long> postIds) {
        long startTime = System.currentTimeMillis();
        
        try {
            if (postIds == null || postIds.isEmpty()) {
                return new HashMap<>();
            }
            
            // 使用批量查询接口
            Map<Long, Post> posts = postRepository.findByIds(postIds);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Batch retrieved {} post metadata in {}ms", posts.size(), duration);
            
            return posts;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to batch get post metadata in {}ms", duration, e);
            throw new DualStorageException("Failed to batch get post metadata", e);
        }
    }

    /**
     * 批量查询文章内容（仅 MongoDB）
     */
    @Override
    public Map<Long, PostContent> batchGetPostContent(List<Long> postIds) {
        long startTime = System.currentTimeMillis();
        
        try {
            if (postIds == null || postIds.isEmpty()) {
                return new HashMap<>();
            }
            
            // 转换 Long ID 为 String ID（MongoDB 使用 String）
            List<String> stringIds = postIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
            
            // 使用批量查询接口
            List<PostContent> contents = postContentRepository.findByPostIdIn(stringIds);
            
            // 转换为 Map（String postId -> Long key）
            Map<Long, PostContent> contentMap = contents.stream()
                .collect(Collectors.toMap(
                    content -> Long.parseLong(content.getPostId()), 
                    content -> content
                ));
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Batch retrieved {} post contents in {}ms", contentMap.size(), duration);
            
            return contentMap;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to batch get post content in {}ms", duration, e);
            throw new DualStorageException("Failed to batch get post content", e);
        }
    }

    /**
     * 批量查询文章完整详情（并行批量查询）
     */
    @Override
    public List<PostDetailDTO> batchGetPostDetails(List<Long> postIds) {
        long startTime = System.currentTimeMillis();
        
        try {
            if (postIds == null || postIds.isEmpty()) {
                return new ArrayList<>();
            }
            
            // 并行批量查询 PostgreSQL 和 MongoDB
            CompletableFuture<Map<Long, Post>> postsFuture = CompletableFuture.supplyAsync(() -> 
                batchGetPostMetadata(postIds), executorService);
            
            CompletableFuture<Map<Long, PostContent>> contentsFuture = CompletableFuture.supplyAsync(() -> 
                batchGetPostContent(postIds), executorService);
            
            // 等待两个查询都完成
            CompletableFuture.allOf(postsFuture, contentsFuture).join();
            
            Map<Long, Post> posts = postsFuture.get();
            Map<Long, PostContent> contents = contentsFuture.get();
            
            // 组装结果
            List<PostDetailDTO> details = postIds.stream()
                .filter(posts::containsKey)
                .map(postId -> {
                    Post post = posts.get(postId);
                    PostContent content = contents.get(postId);
                    
                    if (content != null) {
                        return new PostDetailDTO(post, content);
                    } else {
                        log.warn("Content not found for post: {}", postId);
                        return new PostDetailDTO(post, true);
                    }
                })
                .collect(Collectors.toList());
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Batch retrieved {} post details in {}ms", details.size(), duration);
            
            return details;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to batch get post details in {}ms", duration, e);
            throw new DualStorageException("Failed to batch get post details", e);
        }
    }

    /**
     * 根据作者ID查询文章列表（仅元数据，使用游标分页）
     */
    @Override
    public List<Post> getPostListByOwner(Long ownerId, LocalDateTime cursor, int limit) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 使用游标分页，性能优于偏移分页
            List<Post> posts = postRepository.findByOwnerIdCursor(ownerId, PostStatus.PUBLISHED, cursor, limit);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Retrieved {} posts for owner {} in {}ms", posts.size(), ownerId, duration);
            
            return posts;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to get post list for owner {} in {}ms", ownerId, duration, e);
            throw new DualStorageException("Failed to get post list for owner: " + ownerId, e);
        }
    }

    /**
     * 查询已发布文章列表（仅元数据，使用游标分页）
     */
    @Override
    public List<Post> getPublishedPostList(LocalDateTime cursor, int limit) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 使用游标分页，性能优于偏移分页
            List<Post> posts = postRepository.findPublishedCursor(cursor, limit);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Retrieved {} published posts in {}ms", posts.size(), duration);
            
            return posts;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to get published post list in {}ms", duration, e);
            throw new DualStorageException("Failed to get published post list", e);
        }
    }
}
