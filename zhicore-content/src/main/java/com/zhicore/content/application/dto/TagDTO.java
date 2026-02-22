package com.zhicore.content.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Tag DTO
 * 
 * 标签数据传输对象，用于在 API 层传递标签信息。
 * 标签用于对文章进行分类和检索，每个标签都有唯一的 slug 标识。
 *
 * @author ZhiCore Team
 */
@Schema(description = "标签信息")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagDTO implements Serializable {

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
     * 标签描述
     */
    @Schema(description = "标签描述（可选）", example = "Spring Boot 是一个基于 Spring 框架的快速开发框架")
    private String description;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2026-01-29T10:00:00")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间", example = "2026-01-29T10:00:00")
    private LocalDateTime updatedAt;
}
