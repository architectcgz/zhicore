package com.zhicore.content.application.assembler;

import com.zhicore.content.application.dto.TagDTO;
import com.zhicore.content.domain.model.Tag;
import org.springframework.stereotype.Component;

/**
 * Tag Assembler - 转换 Tag 领域模型到 DTO
 *
 * @author ZhiCore Team
 */
@Component
public class TagAssembler {

    /**
     * 将 Tag 领域模型转换为 TagDTO
     *
     * @param tag Tag 领域模型
     * @return TagDTO
     */
    public TagDTO toDTO(Tag tag) {
        if (tag == null) {
            return null;
        }

        return TagDTO.builder()
                .id(tag.getId())
                .name(tag.getName())
                .slug(tag.getSlug())
                .description(tag.getDescription())
                .createdAt(tag.getCreatedAt())
                .updatedAt(tag.getUpdatedAt())
                .build();
    }
}
