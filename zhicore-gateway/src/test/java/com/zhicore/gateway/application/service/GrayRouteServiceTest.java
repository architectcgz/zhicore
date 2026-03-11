package com.zhicore.gateway.application.service;

import com.zhicore.gateway.application.model.GrayRoutePolicy;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GrayRouteServiceTest {

    @Test
    void shouldReturnFalseWhenPolicyDisabled() {
        GrayRouteService service = new GrayRouteService(new GrayRoutePolicy(false, 100, Set.of("u-1"), Set.of()));

        boolean result = service.shouldRouteToGray("u-1", "127.0.0.1", "fallback");

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnTrueForExplicitGrayUser() {
        GrayRouteService service = new GrayRouteService(new GrayRoutePolicy(true, 0, Set.of("u-1"), Set.of()));

        boolean result = service.shouldRouteToGray("u-1", "127.0.0.1", "fallback");

        assertThat(result).isTrue();
    }

    @Test
    void shouldUseStableIdentifierForPercentageRouting() {
        GrayRouteService service = new GrayRouteService(new GrayRoutePolicy(true, 100, Set.of(), Set.of()));

        boolean result = service.shouldRouteToGray(null, "127.0.0.1", "fallback");

        assertThat(result).isTrue();
    }
}
