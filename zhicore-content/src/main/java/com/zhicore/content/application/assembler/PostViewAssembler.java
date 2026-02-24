package com.zhicore.content.application.assembler;

import com.zhicore.content.application.dto.PostBriefVO;
import com.zhicore.content.application.dto.PostVO;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.infrastructure.persistence.mongo.document.PostContent;

/**
 * 文章装配器
 *
 * @author ZhiCore Team
 */
public final class PostViewAssembler {

    private PostViewAssembler() {
    }

    /**
     * 转换为详情视图对象
     * Note: 不包含内容字段（raw, html），使用 toVOWithContent() 获取完整内容
     */
    public static PostVO toVO(Post post) {
        if (post == null) {
            return null;
        }
        PostVO vo = new PostVO();
        vo.setId(post.getId().getValue());
        vo.setOwnerId(post.getOwnerId().getValue());
        vo.setOwnerName(post.getOwnerSnapshot() != null ? post.getOwnerSnapshot().getName() : null);
        vo.setTitle(post.getTitle());
        // raw 和 html 需要从 MongoDB 获取，使用 toVOWithContent() 方法
        vo.setExcerpt(post.getExcerpt());
        vo.setStatus(post.getStatus().name());
        vo.setTopicId(post.getTopicId() != null ? post.getTopicId().getValue() : null);
        vo.setPublishedAt(post.getPublishedAt());
        vo.setScheduledAt(post.getScheduledAt());
        vo.setCreatedAt(post.getCreatedAt());
        vo.setUpdatedAt(post.getUpdatedAt());
        
        // 统计数据
        if (post.getStats() != null) {
            vo.setLikeCount(post.getStats().getLikeCount());
            vo.setCommentCount(post.getStats().getCommentCount());
            vo.setFavoriteCount(post.getStats().getShareCount());
            vo.setViewCount(post.getStats().getViewCount());
        }
        
        return vo;
    }

    /**
     * 转换为详情视图对象（包含 MongoDB 内容）
     * 用于双存储架构，从 MongoDB 获取内容字段
     */
    public static PostVO toVOWithContent(Post post, PostContent content) {
        if (post == null) {
            return null;
        }
        PostVO vo = new PostVO();
        vo.setId(post.getId().getValue());
        vo.setOwnerId(post.getOwnerId().getValue());
        vo.setOwnerName(post.getOwnerSnapshot() != null ? post.getOwnerSnapshot().getName() : null);
        vo.setTitle(post.getTitle());
        
        // 从 MongoDB 内容中获取 raw 和 html
        if (content != null) {
            vo.setRaw(content.getRaw());
            vo.setHtml(content.getHtml());
        }
        
        vo.setExcerpt(post.getExcerpt());
        vo.setStatus(post.getStatus().name());
        vo.setTopicId(post.getTopicId() != null ? post.getTopicId().getValue() : null);
        vo.setPublishedAt(post.getPublishedAt());
        vo.setScheduledAt(post.getScheduledAt());
        vo.setCreatedAt(post.getCreatedAt());
        vo.setUpdatedAt(post.getUpdatedAt());
        
        // 统计数据
        if (post.getStats() != null) {
            vo.setLikeCount(post.getStats().getLikeCount());
            vo.setCommentCount(post.getStats().getCommentCount());
            vo.setFavoriteCount(post.getStats().getShareCount());
            vo.setViewCount(post.getStats().getViewCount());
        }
        
        return vo;
    }

    /**
     * 转换为简要视图对象
     */
    public static PostBriefVO toBriefVO(Post post) {
        if (post == null) {
            return null;
        }
        PostBriefVO vo = new PostBriefVO();
        vo.setId(post.getId().getValue());
        vo.setOwnerId(post.getOwnerId().getValue());
        vo.setOwnerName(post.getOwnerSnapshot() != null ? post.getOwnerSnapshot().getName() : null);
        vo.setTitle(post.getTitle());
        vo.setExcerpt(post.getExcerpt());
        vo.setStatus(post.getStatus().name());
        vo.setPublishedAt(post.getPublishedAt());
        vo.setCreatedAt(post.getCreatedAt());
        
        // 统计数据
        if (post.getStats() != null) {
            vo.setLikeCount(post.getStats().getLikeCount());
            vo.setCommentCount(post.getStats().getCommentCount());
            vo.setFavoriteCount(post.getStats().getShareCount());
            vo.setViewCount(post.getStats().getViewCount());
        }
        
        return vo;
    }
}

