package com.zhicore.notification.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新作者订阅请求
 */
@Data
@Schema(description = "更新作者订阅请求")
public class UpdateAuthorSubscriptionRequest {

    @NotBlank(message = "订阅级别不能为空")
    @Schema(description = "订阅级别", example = "ALL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String level;

    @NotNull(message = "站内通知开关不能为空")
    @Schema(description = "站内通知是否启用", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean inAppEnabled;

    @NotNull(message = "实时推送开关不能为空")
    @Schema(description = "实时推送是否启用", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean websocketEnabled;

    @NotNull(message = "邮件开关不能为空")
    @Schema(description = "邮件是否启用", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean emailEnabled;

    @NotNull(message = "摘要开关不能为空")
    @Schema(description = "摘要是否启用", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean digestEnabled;
}
