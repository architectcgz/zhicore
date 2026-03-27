package com.zhicore.notification.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新通知免打扰请求。
 */
@Data
@Schema(description = "更新通知免打扰请求")
public class UpdateNotificationDndRequest {

    private static final String TIME_PATTERN = "^(?:[01]\\d|2[0-3]):[0-5]\\d$";

    @NotNull(message = "免打扰开关不能为空")
    @Schema(description = "是否开启免打扰", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean enabled;

    @Schema(description = "开始时间，格式 HH:mm", example = "22:00")
    private String startTime;

    @Schema(description = "结束时间，格式 HH:mm", example = "08:00")
    private String endTime;

    @Schema(description = "时区", example = "Asia/Shanghai")
    private String timezone;

    @AssertTrue(message = "免打扰时间格式必须为HH:mm")
    public boolean isTimeWindowValid() {
        if (!Boolean.TRUE.equals(enabled)) {
            return true;
        }
        return matches(startTime) && matches(endTime);
    }

    private boolean matches(String value) {
        return value != null && value.matches(TIME_PATTERN);
    }
}
