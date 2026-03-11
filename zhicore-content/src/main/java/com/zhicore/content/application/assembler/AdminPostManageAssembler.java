package com.zhicore.content.application.assembler;

import com.zhicore.api.dto.admin.PostManageDTO;
import com.zhicore.content.domain.model.OwnerSnapshot;
import com.zhicore.content.domain.model.Post;

/**
 * 管理端文章视图装配器。
 */
public final class AdminPostManageAssembler {

    private AdminPostManageAssembler() {
    }

    public static PostManageDTO toDTO(Post post) {
        if (post == null) {
            return null;
        }

        OwnerSnapshot ownerSnapshot = post.getOwnerSnapshot();
        return PostManageDTO.builder()
                .id(post.getId().getValue())
                .title(post.getTitle())
                .authorId(post.getOwnerId().getValue())
                .authorName(ownerSnapshot != null ? ownerSnapshot.getName() : "")
                .status(post.getStatus().name())
                .viewCount(post.getStats() != null ? (int) post.getStats().getViewCount() : 0)
                .likeCount(post.getStats() != null ? post.getStats().getLikeCount() : 0)
                .commentCount(post.getStats() != null ? post.getStats().getCommentCount() : 0)
                .createdAt(post.getCreatedAt())
                .publishedAt(post.getPublishedAt())
                .build();
    }
}
