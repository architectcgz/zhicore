package com.zhicore.gateway.service;

import com.zhicore.gateway.service.store.TokenBlacklistStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private TokenBlacklistStore tokenBlacklistStore;

    private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        tokenBlacklistService = new TokenBlacklistService(tokenBlacklistStore);
    }

    @Test
    void shouldReturnStoreResultWhenBlacklistCheckSucceeds() {
        when(tokenBlacklistStore.isBlacklisted("jwt-token")).thenReturn(Mono.just(true));

        assertTrue(Boolean.TRUE.equals(tokenBlacklistService.isBlacklisted("jwt-token").block()));

        verify(tokenBlacklistStore).isBlacklisted("jwt-token");
    }

    @Test
    void shouldFailOpenWhenBlacklistCheckThrows() {
        when(tokenBlacklistStore.isBlacklisted("jwt-token"))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));

        assertFalse(Boolean.TRUE.equals(tokenBlacklistService.isBlacklisted("jwt-token").block()));
    }

    @Test
    void shouldReturnStoreResultWhenAddBlacklistSucceeds() {
        Duration ttl = Duration.ofMinutes(30);
        when(tokenBlacklistStore.addToBlacklist("jwt-token", ttl)).thenReturn(Mono.just(true));

        assertTrue(Boolean.TRUE.equals(tokenBlacklistService.addToBlacklist("jwt-token", ttl).block()));

        verify(tokenBlacklistStore).addToBlacklist("jwt-token", ttl);
    }

    @Test
    void shouldReturnFalseWhenAddBlacklistThrows() {
        Duration ttl = Duration.ofMinutes(30);
        when(tokenBlacklistStore.addToBlacklist("jwt-token", ttl))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));

        assertFalse(Boolean.TRUE.equals(tokenBlacklistService.addToBlacklist("jwt-token", ttl).block()));
    }
}
