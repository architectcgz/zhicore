package com.zhicore.notification.infrastructure.repository.po;

import lombok.Data;

import java.time.LocalTime;
import java.util.List;

/**
 * 用户免打扰持久化对象
 */
@Data
public class UserNotificationDndPO {

    private Long userId;
    private Boolean enabled;
    private LocalTime startTime;
    private LocalTime endTime;
    private List<String> categories;
    private List<String> channels;
}
