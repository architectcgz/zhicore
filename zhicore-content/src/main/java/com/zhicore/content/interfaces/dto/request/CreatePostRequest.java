package com.zhicore.content.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建文章请求
 *
 * @author ZhiCore Team
 */
@Schema(description = "创建文章请求")
@Data
public class CreatePostRequest {

    @Schema(description = "文章标题", example = "Spring Boot 最佳实践", required = true)
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过200字")
    private String title;

    @Schema(description = "文章内容（Markdown格式）", example = "# 简介\n\n这是一篇关于Spring Boot的文章...")
    private String content;

    @Schema(description = "话题ID", example = "1001")
    private Long topicId;

    @Schema(description = "封面图文件ID（从 ZhiCore-upload 服务获取）", example = "1234567890123456789")
    private String coverImageId;

    @Schema(description = "标签列表", example = "[\"Spring Boot\", \"Java\", \"后端开发\"]")
    @Size(max = 10, message = "标签数量不能超过10个")
    private List<String> tags;
}
