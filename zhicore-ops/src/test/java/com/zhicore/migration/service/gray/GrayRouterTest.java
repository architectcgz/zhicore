package com.zhicore.migration.service.gray;

import com.zhicore.migration.service.gray.store.GrayReleaseStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrayRouterTest {

    @Mock
    private GrayReleaseStore grayReleaseStore;

    @Test
    void shouldCacheGrayDecisionWhenNoExistingUserFlag() {
        GrayReleaseSettings settings = new GrayReleaseSettings(
                true,
                100,
                Set.of(),
                Set.of(),
                new GrayReleaseSettings.AlertSettings(0.01, 500)
        );
        GrayRouter grayRouter = new GrayRouter(settings, grayReleaseStore);
        GrayConfig config = GrayConfig.builder()
                .enabled(true)
                .trafficRatio(100)
                .whitelistUsers(Set.of())
                .blacklistUsers(Set.of())
                .build();
        when(grayReleaseStore.getConfig()).thenReturn(config);
        when(grayReleaseStore.getUserGrayFlag("u-1")).thenReturn(null);

        boolean routed = grayRouter.shouldRouteToGray("u-1");

        assertThat(routed).isTrue();
        verify(grayReleaseStore).saveUserGrayFlag("u-1", true);
    }
}
