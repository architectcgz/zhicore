package com.zhicore.content.infrastructure.persistence.pg.mapper;

import com.zhicore.content.domain.model.PostTag;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.infrastructure.persistence.pg.entity.PostTagEntity;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * PostTag 领域模型与 PostTagEntity 的转换器
 * 
 * 使用 MapStruct 自动生成转换代码。
 * PostTag 是值对象，表示文章与标签的关联关系。
 * 
 * @author ZhiCore Team
 */
@Mapper(componentModel = "spring")
public interface PostTagEntityMapper {

    /**
     * 将 PostTagEntity 转换为 PostTag 领域模型
     * 
     * @param entity 数据库实体
     * @return PostTag 领域模型
     */
    default PostTag toDomain(PostTagEntity entity) {
        if (entity == null) {
            return null;
        }
        
        return PostTag.reconstitute(
                PostId.of(entity.getPostId()),
                TagId.of(entity.getTagId()),
                entity.getCreatedAt()
        );
    }

    /**
     * 将 PostTag 领域模型转换为 PostTagEntity
     * 
     * @param domain PostTag 领域模型
     * @return 数据库实体
     */
    default PostTagEntity toEntity(PostTag domain) {
        if (domain == null) {
            return null;
        }
        PostTagEntity entity = new PostTagEntity();
        entity.setPostId(domain.getPostId().getValue());
        entity.setTagId(domain.getTagId().getValue());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }

    /**
     * 批量将 PostTagEntity 列表转换为 PostTag 领域模型列表
     * 
     * @param entities 数据库实体列表
     * @return PostTag 领域模型列表
     */
    default List<PostTag> toDomainList(List<PostTagEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream().map(this::toDomain).toList();
    }
}
