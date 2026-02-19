package com.blog.post.domain.service;

import com.blog.post.domain.model.Post;
import com.blog.post.infrastructure.mongodb.document.PostContent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 查询性能优化服务接口
 * 负责优化文章查询性能，包括列表查询、详情查询和批量查询
 *
 * @author Blog Team
 */
public interface QueryOptimizationService {

    /**
     * 优化的文章列表查询（仅查询 PostgreSQL 元数据）
     * 响应时间目标：< 100ms
     *
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 文章元数据列表
     */
    List<Post> getPostList(int offset, int limit);

    /**
     * 优化的文章详情查询（并行查询 PG 和 Mongo）
     * 响应时间目标：< 200ms
     *
     * @param postId 文章ID
     * @return 文章详情（包含元数据和内容）
     */
    PostDetailDTO getPostDetail(Long postId);

    /**
     * 批量查询文章元数据（仅 PostgreSQL）
     *
     * @param postIds 文章ID列表
     * @return 文章元数据映射（postId -> Post）
     */
    Map<Long, Post> batchGetPostMetadata(List<Long> postIds);

    /**
     * 批量查询文章内容（仅 MongoDB）
     *
     * @param postIds 文章ID列表
     * @return 文章内容映射（postId -> PostContent）
     */
    Map<Long, PostContent> batchGetPostContent(List<Long> postIds);

    /**
     * 批量查询文章完整详情（并行批量查询）
     *
     * @param postIds 文章ID列表
     * @return 文章详情列表
     */
    List<PostDetailDTO> batchGetPostDetails(List<Long> postIds);

    /**
     * 根据作者ID查询文章列表（仅元数据，使用游标分页）
     *
     * @param ownerId 作者ID
     * @param cursor 游标（上次查询的最后一条记录的时间）
     * @param limit 限制数量
     * @return 文章元数据列表
     */
    List<Post> getPostListByOwner(Long ownerId, LocalDateTime cursor, int limit);

    /**
     * 查询已发布文章列表（仅元数据，使用游标分页）
     *
     * @param cursor 游标（上次查询的最后一条记录的时间）
     * @param limit 限制数量
     * @return 文章元数据列表
     */
    List<Post> getPublishedPostList(LocalDateTime cursor, int limit);

    /**
     * 文章详情DTO
     */
    class PostDetailDTO {
        private final Post metadata;
        private final PostContent content;
        private final boolean contentUnavailable;

        public PostDetailDTO(Post metadata, PostContent content) {
            this.metadata = metadata;
            this.content = content;
            this.contentUnavailable = false;
        }

        public PostDetailDTO(Post metadata, boolean contentUnavailable) {
            this.metadata = metadata;
            this.content = null;
            this.contentUnavailable = contentUnavailable;
        }

        public Post getMetadata() {
            return metadata;
        }

        public PostContent getContent() {
            return content;
        }

        public boolean isContentUnavailable() {
            return contentUnavailable;
        }
    }
}
