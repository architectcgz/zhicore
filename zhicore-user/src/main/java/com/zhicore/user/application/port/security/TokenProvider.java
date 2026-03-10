package com.zhicore.user.application.port.security;

import com.zhicore.user.domain.model.User;

/**
 * Token 能力端口。
 */
public interface TokenProvider {

    String generateAccessToken(User user);

    String generateRefreshToken(User user);

    String getTokenIdFromRefreshToken(String token);

    Long getRefreshTokenExpiration();

    String getUserIdFromToken(String token);

    boolean validateToken(String token);

    Long getAccessTokenExpiration();
}
