package com.zhicore.notification.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 更新通知偏好请求。
 *
 * 同时兼容旧的聚合开关写法和新的按类型/渠道写法。
 */
@Schema(description = "更新通知偏好请求")
public class UpdateNotificationPreferenceRequest {

    @NotBlank(message = "通知类型不能为空")
    @Schema(description = "通知类型", example = "POST_COMMENTED")
    private String notificationType;

    @NotBlank(message = "通知渠道不能为空")
    @Schema(description = "通知渠道", example = "EMAIL")
    private String channel;

    @NotNull(message = "开关状态不能为空")
    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    private Boolean likeEnabled;
    private Boolean commentEnabled;
    private Boolean followEnabled;
    private Boolean replyEnabled;
    private Boolean systemEnabled;
    private Boolean publishEnabled;

    public String getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getLikeEnabled() {
        return likeEnabled;
    }

    public void setLikeEnabled(Boolean likeEnabled) {
        this.likeEnabled = likeEnabled;
    }

    public Boolean getCommentEnabled() {
        return commentEnabled;
    }

    public void setCommentEnabled(Boolean commentEnabled) {
        this.commentEnabled = commentEnabled;
    }

    public Boolean getFollowEnabled() {
        return followEnabled;
    }

    public void setFollowEnabled(Boolean followEnabled) {
        this.followEnabled = followEnabled;
    }

    public Boolean getReplyEnabled() {
        return replyEnabled;
    }

    public void setReplyEnabled(Boolean replyEnabled) {
        this.replyEnabled = replyEnabled;
    }

    public Boolean getSystemEnabled() {
        return systemEnabled;
    }

    public void setSystemEnabled(Boolean systemEnabled) {
        this.systemEnabled = systemEnabled;
    }

    public Boolean getPublishEnabled() {
        return publishEnabled;
    }

    public void setPublishEnabled(Boolean publishEnabled) {
        this.publishEnabled = publishEnabled;
    }
}
