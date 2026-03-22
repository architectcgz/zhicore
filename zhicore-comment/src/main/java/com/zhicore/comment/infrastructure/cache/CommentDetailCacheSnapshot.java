package com.zhicore.comment.infrastructure.cache;

import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.model.CommentStats;
import com.zhicore.comment.domain.model.CommentStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 评论详情缓存快照。
 *
 * 避免直接缓存领域对象，把派生 getter 一并序列化进 Redis。
 */
@Getter
@Setter
@NoArgsConstructor
class CommentDetailCacheSnapshot {

    private Long id;
    private Long postId;
    private Long authorId;
    private String content;
    private String[] imageIds;
    private String voiceId;
    private Integer voiceDuration;
    private Long parentId;
    private Long rootId;
    private Long replyToUserId;
    private CommentStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private CommentStats stats;

    static CommentDetailCacheSnapshot from(Comment comment) {
        CommentDetailCacheSnapshot snapshot = new CommentDetailCacheSnapshot();
        snapshot.id = comment.getId();
        snapshot.postId = comment.getPostId();
        snapshot.authorId = comment.getAuthorId();
        snapshot.content = comment.getContent();
        snapshot.imageIds = copyImageIds(comment.getImageIds());
        snapshot.voiceId = comment.getVoiceId();
        snapshot.voiceDuration = comment.getVoiceDuration();
        snapshot.parentId = comment.getParentId();
        snapshot.rootId = comment.getRootId();
        snapshot.replyToUserId = comment.getReplyToUserId();
        snapshot.status = comment.getStatus();
        snapshot.createdAt = comment.getCreatedAt();
        snapshot.updatedAt = comment.getUpdatedAt();
        snapshot.stats = comment.getStats();
        return snapshot;
    }

    Comment toDomain() {
        return Comment.reconstitute(
                id,
                postId,
                authorId,
                content,
                copyImageIds(imageIds),
                voiceId,
                voiceDuration,
                parentId,
                rootId,
                replyToUserId,
                status,
                createdAt,
                updatedAt,
                stats
        );
    }

    private static String[] copyImageIds(String[] imageIds) {
        return imageIds == null ? null : Arrays.copyOf(imageIds, imageIds.length);
    }
}
