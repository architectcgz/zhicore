package com.zhicore.content.infrastructure.persistence.pg.mapper;

import com.zhicore.content.domain.model.PostTag;
import com.zhicore.content.infrastructure.persistence.pg.entity.PostTagEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
    @Mapping(target = "postId", source = "postId")
    @Mapping(target = "tagId", source = "tagId")
    @Mapping(target = "createdAt", source = "createdAt")
    default PostTag toDomain(PostTagEntity entity) {
        if (entity == null) {
            return null;
        }
        
        return PostTag.reconstitute(
                entity.getPostId(),
                entity.getTagId(),
                entity.getCreatedAt()
        );
    }

    /**
     * 将 PostTag 领域模型转换为 PostTagEntity
     * 
     * @param domain PostTag 领域模型
     * @return 数据库实体
     */
    @Mapping(target = "postId", source = "postId")
    @Mapping(target = "tagId", source = "tagId")
    @Mapping(target = "createdAt", source = "createdAt")
    PostTagEntity toEntity(PostTag domain);

    /**
     * 批量将 PostTagEntity 列表转换为 PostTag 领域模型列表
     * 
     * @param entities 数据库实体列表
     * @return PostTag 领域模型列表
     */
    List<PostTag> toDomainList(List<PostTagEntity> entities);
}
