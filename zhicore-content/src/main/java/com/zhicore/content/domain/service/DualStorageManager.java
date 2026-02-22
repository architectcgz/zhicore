package com.zhicore.content.domain.service;

import com.zhicore.content.domain.model.Post;
import com.zhicore.content.infrastructure.mongodb.document.PostContent;

/**
 * 双存储管理器接口
 * 负责协调 PostgreSQL 和 MongoDB 的数据操作，确保数据一致性
 *
 * @author ZhiCore Team
 */
public interface DualStorageManager {

    /**
     * 创建文章（三阶段提交）
     * 1. PG Insert (Status=PUBLISHING)
     * 2. Mongo Insert
     * 3. PG Update (Status=PUBLISHED)
     *
     * @param post 文章元数据
     * @param content 文章内容
     * @return 文章ID
     */
    Long createPost(Post post, PostContent content);

    /**
     * 获取文章完整详情（供搜索服务或编辑使用）
     * 并行查询 PostgreSQL 和 MongoDB
     *
     * @param postId 文章ID
     * @return 文章完整详情
     */
    PostDetail getPostFullDetail(Long postId);

    /**
     * 仅获取文章内容（供前端延迟加载）
     *
     * @param postId 文章ID
     * @return 文章内容
     */
    PostContent getPostContent(Long postId);

    /**
     * 更新文章（双写更新）
     *
     * @param post 文章元数据
     * @param content 文章内容
     */
    void updatePost(Post post, PostContent content);

    /**
     * 删除文章（双删除）
     *
     * @param postId 文章ID
     */
    void deletePost(Long postId);

    /**
     * 文章完整详情
     */
    class PostDetail {
        private final Post post;
        private final PostContent content;
        private final boolean contentUnavailable;

        public PostDetail(Post post, PostContent content) {
            this.post = post;
            this.content = content;
            this.contentUnavailable = false;
        }

        public PostDetail(Post post, PostContent content, boolean contentUnavailable) {
            this.post = post;
            this.content = content;
            this.contentUnavailable = contentUnavailable;
        }

        public Post getPost() {
            return post;
        }

        public PostContent getContent() {
            return content;
        }

        public boolean isContentUnavailable() {
            return contentUnavailable;
        }
    }
}
