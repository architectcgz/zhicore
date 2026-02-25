package com.zhicore.content.infrastructure.persistence.pg.mapper;

import com.zhicore.content.domain.model.OwnerSnapshot;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStats;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.TopicId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.domain.model.WriteState;
import com.zhicore.content.infrastructure.persistence.pg.entity.PostEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Post 领域模型与数据库实体映射器
 * 
 * 负责 Domain Model 和 Database Entity 之间的转换。
 * 遵循依赖倒置原则，基础设施层依赖领域层。
 * 处理值对象 ID 和数据库 Long 类型之间的转换。
 * 
 * @author ZhiCore Team
 */
@Component
@RequiredArgsConstructor
public class PostEntityMapper {

    private final PostTagEntityMyBatisMapper postTagMapper;

    /**
     * 领域模型转数据库实体（值对象转 Long）
     * 
     * @param post 领域模型
     * @return 数据库实体
     */
    public PostEntity toEntity(Post post) {
        if (post == null) {
            return null;
        }

        PostEntity entity = new PostEntity();
        
        // 值对象转 Long
        entity.setId(post.getId().getValue());
        entity.setOwnerId(post.getOwnerId().getValue());
        
        // 映射作者快照（OwnerSnapshot 中的 UserId 转 Long）
        if (post.getOwnerSnapshot() != null) {
            entity.setOwnerName(post.getOwnerSnapshot().getName());
            entity.setOwnerAvatarId(post.getOwnerSnapshot().getAvatarId());
            entity.setOwnerProfileVersion(post.getOwnerSnapshot().getProfileVersion());
        }
        
        entity.setTitle(post.getTitle());
        entity.setExcerpt(post.getExcerpt());
        entity.setCoverImageId(post.getCoverImageId());
        entity.setStatus(post.getStatus().getCode());
        entity.setWriteState(post.getWriteState().name());
        entity.setIncompleteReason(post.getIncompleteReason());
        
        // TopicId 值对象转 Long
        entity.setTopicId(post.getTopicId() != null ? post.getTopicId().getValue() : null);
        
        entity.setPublishedAt(post.getPublishedAt());
        entity.setScheduledAt(post.getScheduledAt());
        entity.setCreatedAt(post.getCreatedAt());
        entity.setUpdatedAt(post.getUpdatedAt());
        entity.setIsArchived(post.getIsArchived());
        entity.setVersion(post.getVersion());
        
        return entity;
    }

    /**
     * 数据库实体转领域模型（Long 转值对象）
     * 
     * @param entity 数据库实体
     * @return 领域模型
     */
    public Post toDomain(PostEntity entity) {
        if (entity == null) {
            return null;
        }

        // Long 转值对象
        PostId postId = PostId.of(entity.getId());
        UserId ownerId = UserId.of(entity.getOwnerId());

        // 重建作者快照（使用 UserId 值对象）
        OwnerSnapshot ownerSnapshot = new OwnerSnapshot(
            ownerId,
            entity.getOwnerName(),
            entity.getOwnerAvatarId(),
            entity.getOwnerProfileVersion() != null ? entity.getOwnerProfileVersion() : 0L
        );

        // TopicId 转换（Long 转值对象）
        TopicId topicId = entity.getTopicId() != null ? TopicId.of(entity.getTopicId()) : null;

        // 查询标签关联（Long 转 TagId 值对象）
        Set<TagId> tagIds = queryTagIds(entity.getId());

        // 解析写入状态
        WriteState writeState = WriteState.NONE;
        if (entity.getWriteState() != null) {
            try {
                writeState = WriteState.valueOf(entity.getWriteState());
            } catch (IllegalArgumentException e) {
                // 如果数据库中的值无效，使用默认值
                writeState = WriteState.NONE;
            }
        }

        // 使用 reconstitute 工厂方法重建聚合根
        return Post.reconstitute(
            postId,
            ownerId,
            entity.getTitle(),
            entity.getExcerpt(),
            entity.getCoverImageId(),
            PostStatus.fromCode(entity.getStatus()),
            topicId,
            tagIds,
            entity.getPublishedAt(),
            entity.getScheduledAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getIsArchived(),
            PostStats.empty(postId), // Stats 由单独的仓储管理
            ownerSnapshot,
            writeState,
            entity.getIncompleteReason(),
            entity.getVersion()
        );
    }

    /**
     * 查询文章的标签ID列表（Long 转 TagId 值对象）
     * 
     * @param postId 文章ID（Long）
     * @return 标签ID集合（TagId 值对象）
     */
    private Set<TagId> queryTagIds(Long postId) {
        List<Long> tagIdValues = postTagMapper.selectTagIdsByPostId(postId);
        return tagIdValues.stream()
            .map(TagId::of)  // Long 转值对象
            .collect(Collectors.toSet());
    }
}

