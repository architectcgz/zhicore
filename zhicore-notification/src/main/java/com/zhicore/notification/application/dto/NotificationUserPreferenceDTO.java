package com.zhicore.notification.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户通知偏好 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationUserPreferenceDTO {

    private boolean likeEnabled;
    private boolean commentEnabled;
    private boolean followEnabled;
    private boolean replyEnabled;
    private boolean systemEnabled;
}
