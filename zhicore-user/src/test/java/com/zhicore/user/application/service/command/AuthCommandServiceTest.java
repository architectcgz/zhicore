package com.zhicore.user.application.service.command;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.application.command.LoginCommand;
import com.zhicore.user.application.command.RefreshTokenCommand;
import com.zhicore.user.application.dto.TokenVO;
import com.zhicore.user.application.port.security.TokenProvider;
import com.zhicore.user.application.port.store.RefreshTokenStore;
import com.zhicore.user.domain.model.Role;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.model.UserStatus;
import com.zhicore.user.domain.repository.UserRepository;
import com.zhicore.user.domain.service.UserDomainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthCommandService 测试")
class AuthCommandServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDomainService userDomainService;

    @Mock
    private TokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @InjectMocks
    private AuthCommandService authCommandService;

    @Test
    @DisplayName("登录时应通过 store 写入 refresh token 白名单")
    void login_shouldStoreRefreshTokenViaStore() {
        LoginCommand request = new LoginCommand("test@example.com", "password");

        User user = buildUser(1001L);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(userDomainService.validatePassword(user, "password")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(user)).thenReturn("refresh-token");
        when(jwtTokenProvider.getTokenIdFromRefreshToken("refresh-token")).thenReturn("token-1");
        when(jwtTokenProvider.getRefreshTokenExpiration()).thenReturn(7200L);
        when(jwtTokenProvider.getAccessTokenExpiration()).thenReturn(3600L);

        TokenVO tokenVO = authCommandService.login(request);

        assertEquals("access-token", tokenVO.getAccessToken());
        assertEquals("refresh-token", tokenVO.getRefreshToken());
        verify(refreshTokenStore).store(1001L, "token-1", Duration.ofSeconds(7200L));
    }

    @Test
    @DisplayName("刷新时若 token 不在白名单应吊销该用户全部 refresh token")
    void refreshToken_shouldRevokeAllWhenWhitelistMiss() {
        RefreshTokenCommand request = new RefreshTokenCommand("refresh-token");

        when(jwtTokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("refresh-token")).thenReturn("1001");
        when(jwtTokenProvider.getTokenIdFromRefreshToken("refresh-token")).thenReturn("token-1");
        when(refreshTokenStore.exists(1001L, "token-1")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authCommandService.refreshToken(request));

        assertEquals(ResultCode.TOKEN_INVALID.getCode(), exception.getCode());
        verify(refreshTokenStore).revokeAll(1001L);
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("刷新时应通过 store 完成 token rotation")
    void refreshToken_shouldRotateTokensViaStore() {
        RefreshTokenCommand request = new RefreshTokenCommand("old-refresh-token");

        User user = buildUser(1001L);
        when(jwtTokenProvider.validateToken("old-refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("old-refresh-token")).thenReturn("1001");
        when(jwtTokenProvider.getTokenIdFromRefreshToken("old-refresh-token")).thenReturn("token-old");
        when(refreshTokenStore.exists(1001L, "token-old")).thenReturn(true);
        when(userRepository.findById(1001L)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("new-access-token");
        when(jwtTokenProvider.generateRefreshToken(user)).thenReturn("new-refresh-token");
        when(jwtTokenProvider.getTokenIdFromRefreshToken("new-refresh-token")).thenReturn("token-new");
        when(jwtTokenProvider.getRefreshTokenExpiration()).thenReturn(7200L);
        when(jwtTokenProvider.getAccessTokenExpiration()).thenReturn(3600L);

        TokenVO tokenVO = authCommandService.refreshToken(request);

        assertEquals("new-access-token", tokenVO.getAccessToken());
        assertEquals("new-refresh-token", tokenVO.getRefreshToken());
        verify(refreshTokenStore).revoke(1001L, "token-old");
        verify(refreshTokenStore).store(1001L, "token-new", Duration.ofSeconds(7200L));
    }

    private User buildUser(Long userId) {
        return User.reconstitute(new User.Snapshot(
                userId,
                "test-user",
                "测试用户",
                "test@example.com",
                "hashedPassword",
                null,
                null,
                UserStatus.ACTIVE,
                true,
                true,
                Set.of(new Role(1, "USER", "普通用户")),
                0L,
                LocalDateTime.now(),
                LocalDateTime.now()
        ));
    }
}
