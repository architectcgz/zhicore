package com.zhicore.user.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新陌生人消息设置请求
 */
@Data
@Schema(description = "更新陌生人消息设置请求")
public class UpdateStrangerMessageSettingRequest {

    /**
     * 是否允许陌生人消息
     */
    @NotNull(message = "陌生人消息设置不能为空")
    @Schema(description = "是否允许陌生人消息", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean allowStrangerMessage;
}
