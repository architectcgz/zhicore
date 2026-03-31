package com.zhicore.notification.domain.model;

import lombok.Getter;
import org.springframework.util.Assert;

/**
 * 用户作者订阅配置
 */
@Getter
public class UserAuthorSubscription {

    private final Long userId;
    private final Long authorId;
    private final AuthorSubscriptionLevel level;
    private final boolean inAppEnabled;
    private final boolean websocketEnabled;
    private final boolean emailEnabled;
    private final boolean digestEnabled;

    private UserAuthorSubscription(Long userId,
                                   Long authorId,
                                   AuthorSubscriptionLevel level,
                                   boolean inAppEnabled,
                                   boolean websocketEnabled,
                                   boolean emailEnabled,
                                   boolean digestEnabled) {
        Assert.notNull(userId, "用户ID不能为空");
        Assert.isTrue(userId > 0, "用户ID必须为正数");
        Assert.notNull(authorId, "作者ID不能为空");
        Assert.isTrue(authorId > 0, "作者ID必须为正数");
        Assert.notNull(level, "订阅级别不能为空");
        SubscriptionFlags flags = normalize(level, inAppEnabled, websocketEnabled, emailEnabled, digestEnabled);
        this.userId = userId;
        this.authorId = authorId;
        this.level = level;
        this.inAppEnabled = flags.inAppEnabled;
        this.websocketEnabled = flags.websocketEnabled;
        this.emailEnabled = flags.emailEnabled;
        this.digestEnabled = flags.digestEnabled;
    }

    public static UserAuthorSubscription of(Long userId,
                                            Long authorId,
                                            AuthorSubscriptionLevel level,
                                            boolean inAppEnabled,
                                            boolean websocketEnabled,
                                            boolean emailEnabled,
                                            boolean digestEnabled) {
        return new UserAuthorSubscription(
                userId,
                authorId,
                level,
                inAppEnabled,
                websocketEnabled,
                emailEnabled,
                digestEnabled
        );
    }

    public static UserAuthorSubscription defaultFor(Long userId, Long authorId) {
        return new UserAuthorSubscription(userId, authorId, AuthorSubscriptionLevel.ALL, true, true, false, false);
    }

    private static SubscriptionFlags normalize(AuthorSubscriptionLevel level,
                                               boolean inAppEnabled,
                                               boolean websocketEnabled,
                                               boolean emailEnabled,
                                               boolean digestEnabled) {
        return switch (level) {
            case ALL -> new SubscriptionFlags(inAppEnabled, websocketEnabled, emailEnabled, digestEnabled);
            case DIGEST_ONLY -> new SubscriptionFlags(false, false, false, true);
            case MUTED -> new SubscriptionFlags(false, false, false, false);
        };
    }

    private record SubscriptionFlags(
            boolean inAppEnabled,
            boolean websocketEnabled,
            boolean emailEnabled,
            boolean digestEnabled
    ) {
    }

    public boolean allowsChannel(NotificationChannel channel) {
        return switch (level) {
            case MUTED, DIGEST_ONLY -> false;
            case ALL -> switch (channel) {
                case IN_APP -> inAppEnabled;
                case WEBSOCKET -> websocketEnabled;
                case EMAIL -> emailEnabled;
                case SMS -> false;
            };
        };
    }
}
