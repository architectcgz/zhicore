package com.zhicore.content.infrastructure.persistence.pg.mapper;

import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.infrastructure.persistence.pg.entity.TagEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Tag 领域模型与 TagEntity 的转换器
 * 
 * 使用 MapStruct 自动生成转换代码。
 * 
 * @author ZhiCore Team
 */
@Mapper(componentModel = "spring")
public interface TagEntityMapper {

    /**
     * 将 TagEntity 转换为 Tag 领域模型
     * 
     * @param entity 数据库实体
     * @return Tag 领域模型
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "slug", source = "slug")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    default Tag toDomain(TagEntity entity) {
        if (entity == null) {
            return null;
        }
        
        return Tag.reconstitute(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * 将 Tag 领域模型转换为 TagEntity
     * 
     * @param domain Tag 领域模型
     * @return 数据库实体
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "slug", source = "slug")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    TagEntity toEntity(Tag domain);

    /**
     * 批量将 TagEntity 列表转换为 Tag 领域模型列表
     * 
     * @param entities 数据库实体列表
     * @return Tag 领域模型列表
     */
    List<Tag> toDomainList(List<TagEntity> entities);
}
