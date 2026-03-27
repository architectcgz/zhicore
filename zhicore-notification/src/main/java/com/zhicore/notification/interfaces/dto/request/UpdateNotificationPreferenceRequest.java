package com.zhicore.notification.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新通知偏好请求。
 */
@Data
@Schema(description = "更新通知偏好请求")
public class UpdateNotificationPreferenceRequest {

    @NotNull(message = "点赞通知开关不能为空")
    @Schema(description = "点赞通知是否启用", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean likeEnabled;

    @NotNull(message = "评论通知开关不能为空")
    @Schema(description = "评论通知是否启用", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean commentEnabled;

    @NotNull(message = "关注通知开关不能为空")
    @Schema(description = "关注通知是否启用", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean followEnabled;

    @NotNull(message = "回复通知开关不能为空")
    @Schema(description = "回复通知是否启用", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean replyEnabled;

    @NotNull(message = "系统通知开关不能为空")
    @Schema(description = "系统通知是否启用", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean systemEnabled;

    @Schema(description = "关注作者发布通知是否启用；旧客户端可不传，服务端将沿用当前值", example = "true")
    private Boolean publishEnabled;
}
