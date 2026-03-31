package com.zhicore.notification.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 更新免打扰请求。
 */
@Schema(description = "更新通知免打扰请求")
public class UpdateNotificationDndRequest {

    @NotNull(message = "免打扰开关不能为空")
    @Schema(description = "是否启用免打扰", example = "true")
    private Boolean enabled;

    @NotBlank(message = "免打扰开始时间不能为空")
    @Schema(description = "开始时间", example = "22:00")
    private String startTime;

    @NotBlank(message = "免打扰结束时间不能为空")
    @Schema(description = "结束时间", example = "08:00")
    private String endTime;

    @Schema(description = "时区", example = "Asia/Shanghai")
    private String timezone;

    @Schema(description = "命中的通知分类")
    private List<String> categories;

    @Schema(description = "命中的通知渠道")
    private List<String> channels;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public List<String> getChannels() {
        return channels;
    }

    public void setChannels(List<String> channels) {
        this.channels = channels;
    }

    @AssertTrue(message = "免打扰开始时间和结束时间不能相同")
    public boolean isWindowValid() {
        if (startTime == null || endTime == null) {
            return true;
        }
        return !startTime.equals(endTime);
    }
}
