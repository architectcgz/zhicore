package com.zhicore.notification.application.service.channel;

import com.zhicore.notification.application.service.query.NotificationPreferenceQueryService;
import com.zhicore.notification.domain.model.AuthorSubscriptionLevel;
import com.zhicore.notification.domain.model.NotificationChannel;
import com.zhicore.notification.domain.model.UserAuthorSubscription;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationChannelPlanner {

    private final NotificationPreferenceQueryService notificationPreferenceQueryService;

    public DeliveryPlan planPostPublished(Long recipientId, Long authorId, LocalTime currentTime) {
        UserAuthorSubscription subscription = notificationPreferenceQueryService.getAuthorSubscription(recipientId, authorId);
        if (subscription.getLevel() == AuthorSubscriptionLevel.MUTED) {
            return DeliveryPlan.muted("AUTHOR_MUTED");
        }
        if (subscription.getLevel() == AuthorSubscriptionLevel.DIGEST_ONLY) {
            return DeliveryPlan.digest("AUTHOR_DIGEST_ONLY");
        }

        NotificationPreferenceQueryService.ChannelDecision decision =
                notificationPreferenceQueryService.resolveChannels(
                        recipientId,
                        com.zhicore.notification.domain.model.NotificationType.POST_PUBLISHED_BY_FOLLOWING,
                        authorId,
                        currentTime
                );

        boolean inboxEnabled = decision.isChannelEnabled(NotificationChannel.IN_APP);
        boolean websocketEnabled = decision.isChannelEnabled(NotificationChannel.WEBSOCKET);

        if (inboxEnabled && websocketEnabled) {
            return DeliveryPlan.priority(EnumSet.of(NotificationChannel.IN_APP, NotificationChannel.WEBSOCKET), "REALTIME_PUSH");
        }
        if (inboxEnabled) {
            return DeliveryPlan.normal(EnumSet.of(NotificationChannel.IN_APP), "INBOX_ONLY");
        }
        return DeliveryPlan.muted("NO_ACTIVE_CHANNEL");
    }

    public enum AudienceBucket {
        PRIORITY,
        NORMAL,
        DIGEST,
        MUTED
    }

    @Getter
    public static final class DeliveryPlan {

        private final AudienceBucket bucket;
        private final Set<NotificationChannel> channels;
        private final String reason;

        private DeliveryPlan(AudienceBucket bucket, Set<NotificationChannel> channels, String reason) {
            this.bucket = bucket;
            this.channels = Set.copyOf(channels);
            this.reason = reason;
        }

        public static DeliveryPlan priority(Set<NotificationChannel> channels, String reason) {
            return new DeliveryPlan(AudienceBucket.PRIORITY, channels, reason);
        }

        public static DeliveryPlan normal(Set<NotificationChannel> channels, String reason) {
            return new DeliveryPlan(AudienceBucket.NORMAL, channels, reason);
        }

        public static DeliveryPlan digest(String reason) {
            return new DeliveryPlan(AudienceBucket.DIGEST, EnumSet.noneOf(NotificationChannel.class), reason);
        }

        public static DeliveryPlan muted(String reason) {
            return new DeliveryPlan(AudienceBucket.MUTED, EnumSet.noneOf(NotificationChannel.class), reason);
        }

        public boolean usesChannel(NotificationChannel channel) {
            return channels.contains(channel);
        }
    }
}
