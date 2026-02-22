package com.zhicore.admin.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 处理举报请求
 */
@Schema(description = "处理举报请求")
@Data
public class HandleReportRequest {
    
    /**
     * 处理动作（DELETE_CONTENT/WARN_USER/BAN_USER/IGNORE）
     */
    @Schema(description = "处理动作", example = "DELETE_CONTENT", required = true, 
            allowableValues = {"DELETE_CONTENT", "WARN_USER", "BAN_USER", "IGNORE"})
    @NotBlank(message = "处理动作不能为空")
    private String action;
    
    /**
     * 处理备注
     */
    @Schema(description = "处理备注", example = "内容已删除，用户已警告")
    private String remark;
}
