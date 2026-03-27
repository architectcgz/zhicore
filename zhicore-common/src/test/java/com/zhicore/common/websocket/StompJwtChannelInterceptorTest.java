package com.zhicore.common.websocket;

import com.zhicore.common.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class StompJwtChannelInterceptorTest {

    private static final String JWT_SECRET =
            "test-secret-key-for-stomp-channel-interceptor-must-be-at-least-256-bits";

    private final StompJwtChannelInterceptor interceptor =
            new StompJwtChannelInterceptor(new JwtClaimsParser(jwtProperties()));

    @Test
    void connectFrameWithBearerTokenShouldBindPrincipal() {
        Message<byte[]> connectMessage = createConnectMessage("Bearer " + createAccessToken("123456789", "alice"));

        Message<?> interceptedMessage = interceptor.preSend(connectMessage, mock(MessageChannel.class));
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(interceptedMessage);

        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("123456789");
    }

    @Test
    void connectFrameShouldTriggerExistingUserChangeCallback() {
        AtomicReference<String> boundUserId = new AtomicReference<>();
        Message<byte[]> connectMessage = createConnectMessage("Bearer " + createAccessToken("123456789", "alice"));
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(connectMessage, StompHeaderAccessor.class);
        assertThat(accessor).isNotNull();
        accessor.setUserChangeCallback(user -> boundUserId.set(user.getName()));

        interceptor.preSend(connectMessage, mock(MessageChannel.class));

        assertThat(boundUserId.get()).isEqualTo("123456789");
    }

    @Test
    void connectFrameWithoutAuthorizationShouldBeRejected() {
        Message<byte[]> connectMessage = createConnectMessage(null);

        assertThatThrownBy(() -> interceptor.preSend(connectMessage, mock(MessageChannel.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Authorization");
    }

    @Test
    void connectFrameWithInvalidTokenShouldBeRejected() {
        Message<byte[]> connectMessage = createConnectMessage("Bearer invalid-token");

        assertThatThrownBy(() -> interceptor.preSend(connectMessage, mock(MessageChannel.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT");
    }

    private Message<byte[]> createConnectMessage(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorizationHeader != null) {
            accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private String createAccessToken(String userId, String userName) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claim("userName", userName)
                .claim("roles", "USER")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(JWT_SECRET);
        return properties;
    }
}
