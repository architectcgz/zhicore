package com.blog.admin.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 删除内容请求
 */
@Schema(description = "删除内容请求")
@Data
public class DeleteContentRequest {
    
    /**
     * 删除原因
     */
    @Schema(description = "删除原因", example = "内容违反社区规范", required = true)
    @NotBlank(message = "删除原因不能为空")
    private String reason;
}
