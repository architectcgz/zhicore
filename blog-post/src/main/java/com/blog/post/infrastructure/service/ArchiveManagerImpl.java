package com.blog.post.infrastructure.service;

import com.blog.post.domain.exception.DualStorageException;
import com.blog.post.domain.model.Post;
import com.blog.post.domain.repository.PostRepository;
import com.blog.post.domain.service.ArchiveManager;
import com.blog.post.infrastructure.mongodb.document.PostArchive;
import com.blog.post.infrastructure.mongodb.document.PostContent;
import com.blog.post.infrastructure.mongodb.repository.PostArchiveRepository;
import com.blog.post.infrastructure.mongodb.repository.PostContentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 归档管理器实现
 * 实现内容归档和冷热分离功能，将不活跃的文章内容迁移到MongoDB以减轻PostgreSQL压力
 *
 * @author Blog Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveManagerImpl implements ArchiveManager {

    private final PostRepository postRepository;
    private final PostContentRepository postContentRepository;
    private final PostArchiveRepository postArchiveRepository;
    private final ObjectMapper objectMapper;

    /**
     * 归档文章内容
     * 将文章的完整快照存储到MongoDB，并在PostgreSQL中标记为已归档
     *
     * @param postId 文章ID
     * @param reason 归档原因（time/inactive/manual）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archivePost(Long postId, String reason) {
        log.info("Starting to archive post: {}, reason: {}", postId, reason);
        
        try {
            // 1. 检查文章是否存在
            Optional<Post> postOpt = postRepository.findById(postId);
            if (!postOpt.isPresent()) {
                throw new DualStorageException("Post not found: " + postId);
            }
            Post post = postOpt.get();
            
            // 2. 检查是否已归档
            if (post.isArchived()) {
                log.warn("Post already archived: {}", postId);
                return;
            }
            
            // 3. 获取文章内容
            Optional<PostContent> contentOpt = postContentRepository.findByPostId(String.valueOf(postId));
            if (!contentOpt.isPresent()) {
                throw new DualStorageException("Post content not found: " + postId);
            }
            PostContent content = contentOpt.get();
            
            // 4. 创建完整快照（包含元数据）
            Map<String, Object> snapshot = createSnapshot(post, content);
            
            // 5. 保存到归档集合
            PostArchive archive = PostArchive.builder()
                    .postId(String.valueOf(postId))
                    .content(content.getRaw())
                    .contentType(content.getContentType())
                    .archivedAt(LocalDateTime.now())
                    .archiveReason(reason)
                    .snapshot(snapshot)
                    .build();
            
            postArchiveRepository.save(archive);
            log.info("Successfully saved archive to MongoDB: {}", postId);
            
            // 6. 在PostgreSQL中标记为已归档
            post.markAsArchived();
            postRepository.update(post);
            log.info("Successfully marked post as archived in PostgreSQL: {}", postId);
            
            log.info("Successfully archived post: {}", postId);
            
        } catch (Exception e) {
            log.error("Failed to archive post: {}", postId, e);
            throw new DualStorageException("Failed to archive post: " + postId, e);
        }
    }

    /**
     * 恢复归档内容
     * 将已归档的文章恢复为热数据
     *
     * @param postId 文章ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restorePost(Long postId) {
        log.info("Starting to restore archived post: {}", postId);
        
        try {
            // 1. 检查文章是否存在
            Optional<Post> postOpt = postRepository.findById(postId);
            if (!postOpt.isPresent()) {
                throw new DualStorageException("Post not found: " + postId);
            }
            Post post = postOpt.get();
            
            // 2. 检查是否已归档
            if (!post.isArchived()) {
                log.warn("Post is not archived: {}", postId);
                return;
            }
            
            // 3. 获取归档内容
            Optional<PostArchive> archiveOpt = postArchiveRepository.findByPostId(String.valueOf(postId));
            if (!archiveOpt.isPresent()) {
                log.warn("Archive not found for post: {}, marking as not archived", postId);
                post.unmarkArchived();
                postRepository.update(post);
                return;
            }
            
            // 4. 在PostgreSQL中取消归档标记
            post.unmarkArchived();
            postRepository.update(post);
            log.info("Successfully unmarked post as archived in PostgreSQL: {}", postId);
            
            // 5. 删除归档记录（可选，根据业务需求决定是否保留归档历史）
            // postArchiveRepository.deleteByPostId(postId);
            // log.info("Successfully deleted archive from MongoDB: {}", postId);
            
            log.info("Successfully restored archived post: {}", postId);
            
        } catch (Exception e) {
            log.error("Failed to restore archived post: {}", postId, e);
            throw new DualStorageException("Failed to restore archived post: " + postId, e);
        }
    }

    /**
     * 查询归档内容
     * 获取已归档文章的完整内容
     *
     * @param postId 文章ID
     * @return 归档内容
     */
    @Override
    public Optional<PostArchive> getArchivedContent(Long postId) {
        log.debug("Getting archived content for post: {}", postId);
        
        try {
            Optional<PostArchive> archive = postArchiveRepository.findByPostId(String.valueOf(postId));
            
            if (archive.isPresent()) {
                log.info("Found archived content for post: {}", postId);
            } else {
                log.debug("No archived content found for post: {}", postId);
            }
            
            return archive;
            
        } catch (Exception e) {
            log.error("Failed to get archived content for post: {}", postId, e);
            return Optional.empty();
        }
    }

    /**
     * 批量归档
     * 根据时间阈值批量归档不活跃的文章
     *
     * @param threshold 归档阈值（天数）
     * @return 归档数量
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchArchive(int threshold) {
        log.info("Starting batch archive with threshold: {} days", threshold);
        
        int archivedCount = 0;
        
        try {
            // 计算阈值时间
            LocalDateTime thresholdTime = LocalDateTime.now().minusDays(threshold);
            log.info("Archiving posts not updated since: {}", thresholdTime);
            
            // 查询需要归档的文章
            // 注意：这里需要在PostRepository中添加查询方法
            // 暂时使用简单实现，实际应该分批处理避免内存溢出
            // List<Post> postsToArchive = postRepository.findPostsForArchive(thresholdTime);
            
            // 由于PostRepository接口中没有findPostsForArchive方法，
            // 这里先记录日志，实际实现需要扩展PostRepository接口
            log.warn("Batch archive requires PostRepository.findPostsForArchive() method to be implemented");
            
            // 示例实现（需要扩展PostRepository）：
            // for (Post post : postsToArchive) {
            //     try {
            //         archivePost(String.valueOf(post.getId()), "time");
            //         archivedCount++;
            //     } catch (Exception e) {
            //         log.error("Failed to archive post: {}", post.getId(), e);
            //         // 继续处理其他文章，不中断批量归档
            //     }
            // }
            
            log.info("Batch archive completed, archived {} posts", archivedCount);
            return archivedCount;
            
        } catch (Exception e) {
            log.error("Failed to execute batch archive", e);
            return archivedCount;
        }
    }

    /**
     * 检查是否已归档
     * 判断指定文章是否已被归档
     *
     * @param postId 文章ID
     * @return 是否已归档
     */
    @Override
    public boolean isArchived(Long postId) {
        log.debug("Checking if post is archived: {}", postId);
        
        try {
            // 方法1：从PostgreSQL检查（推荐，因为更快）
            Optional<Post> postOpt = postRepository.findById(postId);
            if (postOpt.isPresent()) {
                boolean archived = postOpt.get().isArchived();
                log.debug("Post {} archived status from PostgreSQL: {}", postId, archived);
                return archived;
            }
            
            // 方法2：从MongoDB检查（作为备用）
            boolean existsInArchive = postArchiveRepository.existsByPostId(String.valueOf(postId));
            log.debug("Post {} exists in archive: {}", postId, existsInArchive);
            return existsInArchive;
            
        } catch (Exception e) {
            log.error("Failed to check if post is archived: {}", postId, e);
            return false;
        }
    }

    /**
     * 创建完整快照
     * 将文章元数据和内容打包成快照对象
     *
     * @param post 文章元数据
     * @param content 文章内容
     * @return 快照对象
     */
    private Map<String, Object> createSnapshot(Post post, PostContent content) {
        Map<String, Object> snapshot = new HashMap<>();
        
        try {
            // 文章元数据
            snapshot.put("id", post.getId());
            snapshot.put("ownerId", post.getOwnerId());
            snapshot.put("title", post.getTitle());
            snapshot.put("excerpt", post.getExcerpt());
            snapshot.put("coverImageId", post.getCoverImageId());
            snapshot.put("status", post.getStatus().name());
            snapshot.put("topicId", post.getTopicId());
            snapshot.put("publishedAt", post.getPublishedAt());
            snapshot.put("createdAt", post.getCreatedAt());
            snapshot.put("updatedAt", post.getUpdatedAt());
            
            // 文章内容
            snapshot.put("contentType", content.getContentType());
            snapshot.put("raw", content.getRaw());
            snapshot.put("html", content.getHtml());
            snapshot.put("text", content.getText());
            snapshot.put("wordCount", content.getWordCount());
            snapshot.put("readingTime", content.getReadingTime());
            
            // 富文本内容（如果有）
            if (content.getBlocks() != null) {
                snapshot.put("blocks", content.getBlocks());
            }
            if (content.getMedia() != null) {
                snapshot.put("media", content.getMedia());
            }
            
            log.debug("Created snapshot for post: {}", post.getId());
            
        } catch (Exception e) {
            log.error("Failed to create snapshot for post: {}", post.getId(), e);
            throw new DualStorageException("Failed to create snapshot", e);
        }
        
        return snapshot;
    }
}
