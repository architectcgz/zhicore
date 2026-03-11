package com.zhicore.migration.service.gray;

import com.zhicore.migration.service.gray.store.GrayReleaseStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrayRollbackServiceTest {

    @Mock
    private GrayReleaseStore grayReleaseStore;

    @Test
    void advancePhaseShouldUpdateStatusConfigAndClearUserFlags() {
        GrayReleaseSettings settings = new GrayReleaseSettings(
                true,
                5,
                Set.of(),
                Set.of(),
                new GrayReleaseSettings.AlertSettings(0.01, 500)
        );
        GrayRollbackService grayRollbackService = new GrayRollbackService(grayReleaseStore, settings);
        GrayStatus currentStatus = GrayStatus.builder()
                .phase(GrayPhase.INITIAL)
                .currentRatio(GrayPhase.INITIAL.getRatio())
                .startTime(1L)
                .build();
        GrayConfig currentConfig = GrayConfig.builder()
                .enabled(true)
                .trafficRatio(GrayPhase.INITIAL.getRatio())
                .whitelistUsers(Set.of())
                .blacklistUsers(Set.of())
                .build();
        when(grayReleaseStore.getStatus()).thenReturn(currentStatus);
        when(grayReleaseStore.getConfig()).thenReturn(currentConfig);

        GrayRollbackService.AdvanceResult result = grayRollbackService.advancePhase();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPreviousPhase()).isEqualTo(GrayPhase.INITIAL);
        assertThat(result.getCurrentPhase()).isEqualTo(GrayPhase.EXPANDING);
        assertThat(result.getCurrentRatio()).isEqualTo(GrayPhase.EXPANDING.getRatio());
        assertThat(currentStatus.getPhase()).isEqualTo(GrayPhase.EXPANDING);
        assertThat(currentConfig.getTrafficRatio()).isEqualTo(GrayPhase.EXPANDING.getRatio());

        InOrder inOrder = inOrder(grayReleaseStore);
        inOrder.verify(grayReleaseStore).getStatus();
        inOrder.verify(grayReleaseStore).saveStatus(currentStatus);
        inOrder.verify(grayReleaseStore).getConfig();
        inOrder.verify(grayReleaseStore).saveConfig(currentConfig);
        inOrder.verify(grayReleaseStore).clearAllUserGrayFlags();
        verifyNoMoreInteractions(grayReleaseStore);
    }
}
