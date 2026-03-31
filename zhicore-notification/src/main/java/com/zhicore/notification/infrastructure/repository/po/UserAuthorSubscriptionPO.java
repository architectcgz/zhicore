package com.zhicore.notification.infrastructure.repository.po;

import lombok.Data;

/**
 * 作者订阅持久化对象
 */
@Data
public class UserAuthorSubscriptionPO {

    private Long userId;
    private Long authorId;
    private String subscriptionLevel;
    private Boolean inAppEnabled;
    private Boolean websocketEnabled;
    private Boolean emailEnabled;
    private Boolean digestEnabled;
}
