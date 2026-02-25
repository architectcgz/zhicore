package com.zhicore.admin.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 禁用用户请求
 */
@Schema(description = "禁用用户请求")
@Data
public class DisableUserRequest {
    
    /**
     * 禁用原因
     */
    @Schema(description = "禁用原因", example = "发布违规内容", required = true)
    @NotBlank(message = "禁用原因不能为空")
    private String reason;
}
