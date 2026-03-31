package com.zhicore.notification.infrastructure.repository.po;

import lombok.Data;

@Data
public class UnreadCategoryCountPO {

    private Integer category;
    private Integer unreadCount;
}
