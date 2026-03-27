package com.zhicore.notification.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户免打扰 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationUserDndDTO {

    private boolean enabled;
    private String startTime;
    private String endTime;
    private String timezone;
}
