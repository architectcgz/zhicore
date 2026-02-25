package com.zhicore.content.infrastructure.persistence.pg.mapper;

import com.zhicore.content.domain.model.Category;
import com.zhicore.content.infrastructure.persistence.pg.entity.CategoryEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Category 领域模型与 CategoryEntity 的转换器
 * 
 * 使用 MapStruct 自动生成转换代码。
 * 
 * @author ZhiCore Team
 */
@Mapper(componentModel = "spring")
public interface CategoryEntityMapper {

    /**
     * 将 CategoryEntity 转换为 Category 领域模型
     * 
     * @param entity 数据库实体
     * @return Category 领域模型
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "slug", source = "slug")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "parentId", source = "parentId")
    @Mapping(target = "sortOrder", source = "sortOrder")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    default Category toDomain(CategoryEntity entity) {
        if (entity == null) {
            return null;
        }
        
        return Category.reconstitute(
                String.valueOf(entity.getId()),
                entity.getName(),
                entity.getSlug(),
                entity.getDescription(),
                entity.getParentId() != null ? String.valueOf(entity.getParentId()) : null,
                entity.getSortOrder() != null ? entity.getSortOrder() : 0,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * 将 Category 领域模型转换为 CategoryEntity
     * 
     * @param domain Category 领域模型
     * @return 数据库实体
     */
    @Mapping(target = "id", expression = "java(Long.valueOf(domain.getId()))")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "slug", source = "slug")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "parentId", expression = "java(domain.getParentId() != null ? Long.valueOf(domain.getParentId()) : null)")
    @Mapping(target = "sortOrder", source = "sortOrder")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    CategoryEntity toEntity(Category domain);

    /**
     * 批量将 CategoryEntity 列表转换为 Category 领域模型列表
     * 
     * @param entities 数据库实体列表
     * @return Category 领域模型列表
     */
    List<Category> toDomainList(List<CategoryEntity> entities);
}
