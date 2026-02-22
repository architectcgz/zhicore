package com.zhicore.content.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 为文章添加标签请求
 *
 * @author ZhiCore Team
 */
@Schema(description = "为文章添加标签请求")
@Data
public class AttachTagsRequest {

    @Schema(description = "标签列表", example = "[\"Spring Boot\", \"Java\", \"后端开发\"]", required = true)
    @NotEmpty(message = "标签列表不能为空")
    @Size(max = 10, message = "标签数量不能超过10个")
    private List<String> tags;
}
