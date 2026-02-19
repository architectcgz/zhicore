package com.blog.post.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Tag Statistics DTO
 * 
 * 标签统计数据传输对象，包含标签的基本信息和文章数量统计。
 * 用于热门标签展示、标签云等场景。
 *
 * @author Blog Team
 */
@Schema(description = "标签统计信息")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagStatsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 标签ID
     */
    @Schema(description = "标签ID（雪花算法生成）", example = "1234567890123456789")
    private Long id;

    /**
     * 标签名称
     */
    @Schema(description = "标签展示名称", example = "Spring Boot")
    private String name;

    /**
     * URL友好标识
     */
    @Schema(description = "URL友好标识（小写、连字符分隔）", example = "spring-boot")
    private String slug;

    /**
     * 文章数量
     */
    @Schema(description = "该标签下的文章数量", example = "128")
    private Integer postCount;
}
