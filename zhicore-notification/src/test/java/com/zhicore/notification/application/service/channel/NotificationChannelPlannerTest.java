package com.zhicore.notification.application.service.channel;

import com.zhicore.notification.application.service.query.NotificationPreferenceQueryService;
import com.zhicore.notification.domain.model.AuthorSubscriptionLevel;
import com.zhicore.notification.domain.model.NotificationChannel;
import com.zhicore.notification.domain.model.UserAuthorSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationChannelPlanner 测试")
class NotificationChannelPlannerTest {

    @Mock
    private NotificationPreferenceQueryService notificationPreferenceQueryService;

    private NotificationChannelPlanner notificationChannelPlanner;

    @BeforeEach
    void setUp() {
        notificationChannelPlanner = new NotificationChannelPlanner(notificationPreferenceQueryService);
    }

    @Test
    @DisplayName("作者订阅为摘要时应该进入 DIGEST bucket")
    void planPostPublished_shouldRouteDigestOnlySubscriptionToDigestBucket() {
        when(notificationPreferenceQueryService.getAuthorSubscription(11L, 22L)).thenReturn(
                UserAuthorSubscription.of(11L, 22L, AuthorSubscriptionLevel.DIGEST_ONLY, false, false, false, true)
        );

        NotificationChannelPlanner.DeliveryPlan plan =
                notificationChannelPlanner.planPostPublished(11L, 22L, LocalTime.NOON);

        assertEquals(NotificationChannelPlanner.AudienceBucket.DIGEST, plan.getBucket());
        assertEquals("AUTHOR_DIGEST_ONLY", plan.getReason());
    }

    @Test
    @DisplayName("站内和实时通道都开启时应该进入 PRIORITY bucket")
    void planPostPublished_shouldRouteRealtimeAudienceToPriorityBucket() {
        NotificationPreferenceQueryService.ChannelDecision decision =
                mock(NotificationPreferenceQueryService.ChannelDecision.class);
        when(notificationPreferenceQueryService.getAuthorSubscription(11L, 22L)).thenReturn(
                UserAuthorSubscription.of(11L, 22L, AuthorSubscriptionLevel.ALL, true, true, false, false)
        );
        when(notificationPreferenceQueryService.resolveChannels(
                11L,
                com.zhicore.notification.domain.model.NotificationType.POST_PUBLISHED_BY_FOLLOWING,
                22L,
                LocalTime.NOON
        )).thenReturn(decision);
        when(decision.isChannelEnabled(NotificationChannel.IN_APP)).thenReturn(true);
        when(decision.isChannelEnabled(NotificationChannel.WEBSOCKET)).thenReturn(true);

        NotificationChannelPlanner.DeliveryPlan plan =
                notificationChannelPlanner.planPostPublished(11L, 22L, LocalTime.NOON);

        assertEquals(NotificationChannelPlanner.AudienceBucket.PRIORITY, plan.getBucket());
        assertTrue(plan.usesChannel(NotificationChannel.IN_APP));
        assertTrue(plan.usesChannel(NotificationChannel.WEBSOCKET));
    }

    @Test
    @DisplayName("只有站内开启时应该进入 NORMAL bucket")
    void planPostPublished_shouldRouteInboxOnlyAudienceToNormalBucket() {
        NotificationPreferenceQueryService.ChannelDecision decision =
                mock(NotificationPreferenceQueryService.ChannelDecision.class);
        when(notificationPreferenceQueryService.getAuthorSubscription(11L, 22L)).thenReturn(
                UserAuthorSubscription.of(11L, 22L, AuthorSubscriptionLevel.ALL, true, true, false, false)
        );
        when(notificationPreferenceQueryService.resolveChannels(
                11L,
                com.zhicore.notification.domain.model.NotificationType.POST_PUBLISHED_BY_FOLLOWING,
                22L,
                LocalTime.of(23, 30)
        )).thenReturn(decision);
        when(decision.isChannelEnabled(NotificationChannel.IN_APP)).thenReturn(true);
        when(decision.isChannelEnabled(NotificationChannel.WEBSOCKET)).thenReturn(false);

        NotificationChannelPlanner.DeliveryPlan plan =
                notificationChannelPlanner.planPostPublished(11L, 22L, LocalTime.of(23, 30));

        assertEquals(NotificationChannelPlanner.AudienceBucket.NORMAL, plan.getBucket());
        assertTrue(plan.usesChannel(NotificationChannel.IN_APP));
    }

    @Test
    @DisplayName("没有可用主动渠道时应该进入 MUTED bucket")
    void planPostPublished_shouldMuteWhenNoActiveChannelExists() {
        NotificationPreferenceQueryService.ChannelDecision decision =
                mock(NotificationPreferenceQueryService.ChannelDecision.class);
        when(notificationPreferenceQueryService.getAuthorSubscription(11L, 22L)).thenReturn(
                UserAuthorSubscription.of(11L, 22L, AuthorSubscriptionLevel.ALL, true, true, false, false)
        );
        when(notificationPreferenceQueryService.resolveChannels(
                11L,
                com.zhicore.notification.domain.model.NotificationType.POST_PUBLISHED_BY_FOLLOWING,
                22L,
                LocalTime.of(23, 30)
        )).thenReturn(decision);
        when(decision.isChannelEnabled(NotificationChannel.IN_APP)).thenReturn(false);
        when(decision.isChannelEnabled(NotificationChannel.WEBSOCKET)).thenReturn(false);

        NotificationChannelPlanner.DeliveryPlan plan =
                notificationChannelPlanner.planPostPublished(11L, 22L, LocalTime.of(23, 30));

        assertEquals(NotificationChannelPlanner.AudienceBucket.MUTED, plan.getBucket());
        assertEquals("NO_ACTIVE_CHANNEL", plan.getReason());
    }
}
