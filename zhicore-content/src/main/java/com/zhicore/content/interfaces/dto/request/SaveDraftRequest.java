package com.zhicore.content.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 保存草稿请求
 *
 * @author ZhiCore Team
 */
@Data
@Schema(description = "保存草稿请求")
public class SaveDraftRequest {

    @NotBlank(message = "草稿内容不能为空")
    @Schema(description = "草稿内容")
    private String content;

    @Pattern(regexp = "^(markdown|html|rich)$", message = "内容类型必须是 markdown、html 或 rich")
    @Schema(description = "内容类型", 
            allowableValues = {"markdown", "html", "rich"},
            defaultValue = "markdown")
    private String contentType = "markdown";

    @Schema(description = "是否自动保存")
    private Boolean isAutoSave = false;

    @Schema(description = "设备ID（可选）")
    private String deviceId;
}
