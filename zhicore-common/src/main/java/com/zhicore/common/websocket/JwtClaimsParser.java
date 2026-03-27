package com.zhicore.common.websocket;

import com.zhicore.common.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 复用 JWT 解析逻辑，供 STOMP CONNECT 认证等非 HTTP 场景使用。
 */
public class JwtClaimsParser {

    private final JwtParser jwtParser;

    public JwtClaimsParser(JwtProperties jwtProperties) {
        SecretKey secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.jwtParser = Jwts.parser()
                .verifyWith(secretKey)
                .build();
    }

    public Claims parse(String token) {
        return jwtParser.parseSignedClaims(token).getPayload();
    }
}
