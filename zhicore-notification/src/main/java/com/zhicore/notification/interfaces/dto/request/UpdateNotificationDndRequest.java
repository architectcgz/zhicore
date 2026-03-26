package com.zhicore.notification.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 更新免打扰请求
 */
@Data
@Schema(description = "更新通知免打扰请求")
public class UpdateNotificationDndRequest {

    @NotNull(message = "免打扰开关不能为空")
    @Schema(description = "是否启用免打扰", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean enabled;

    @NotBlank(message = "免打扰开始时间不能为空")
    @Schema(description = "开始时间", example = "22:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private String startTime;

    @NotBlank(message = "免打扰结束时间不能为空")
    @Schema(description = "结束时间", example = "08:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private String endTime;

    @Schema(description = "命中的通知分类")
    private List<String> categories;

    @Schema(description = "命中的通知渠道")
    private List<String> channels;

    @AssertTrue(message = "免打扰开始时间和结束时间不能相同")
    public boolean isWindowValid() {
        if (startTime == null || endTime == null) {
            return true;
        }
        return !startTime.equals(endTime);
    }
}
