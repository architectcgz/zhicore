package com.zhicore.notification.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新通知偏好请求
 */
@Data
@Schema(description = "更新通知偏好请求")
public class UpdateNotificationPreferenceRequest {

    @NotBlank(message = "通知类型不能为空")
    @Schema(description = "通知类型", example = "POST_COMMENTED", requiredMode = Schema.RequiredMode.REQUIRED)
    private String notificationType;

    @NotBlank(message = "通知渠道不能为空")
    @Schema(description = "通知渠道", example = "EMAIL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String channel;

    @NotNull(message = "开关状态不能为空")
    @Schema(description = "是否启用", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean enabled;
}
