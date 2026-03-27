package com.zhicore.common.websocket;

import io.jsonwebtoken.Claims;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.function.Consumer;

/**
 * 在 STOMP CONNECT 帧阶段解析 Bearer Token，并绑定用户主体。
 */
public class StompJwtChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Field USER_CALLBACK_FIELD = resolveUserCallbackField();

    private final JwtClaimsParser jwtClaimsParser;

    public StompJwtChannelInterceptor(JwtClaimsParser jwtClaimsParser) {
        this.jwtClaimsParser = jwtClaimsParser;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String authorizationHeader = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new IllegalArgumentException("Missing Authorization bearer token in STOMP CONNECT");
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length());

        try {
            Claims claims = jwtClaimsParser.parse(token);
            Principal principal = new StompPrincipal(
                    claims.getSubject(),
                    claims.get("userName", String.class),
                    firstNonBlank(
                            claims.get("roles", String.class),
                            claims.get("role", String.class)
                    )
            );

            if (accessor.isMutable()) {
                accessor.setUser(principal);
                return message;
            }

            Consumer<Principal> userChangeCallback = extractUserChangeCallback(accessor);
            StompHeaderAccessor mutableAccessor = StompHeaderAccessor.wrap(message);
            if (userChangeCallback != null) {
                mutableAccessor.setUserChangeCallback(userChangeCallback);
            }
            mutableAccessor.setUser(principal);
            return MessageBuilder.createMessage(message.getPayload(), mutableAccessor.getMessageHeaders());
        } catch (Exception exception) {
            throw new IllegalArgumentException("JWT token is invalid for STOMP CONNECT", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Principal> extractUserChangeCallback(SimpMessageHeaderAccessor accessor)
            throws IllegalAccessException {
        if (USER_CALLBACK_FIELD == null) {
            return null;
        }
        return (Consumer<Principal>) USER_CALLBACK_FIELD.get(accessor);
    }

    private static Field resolveUserCallbackField() {
        try {
            Field field = SimpMessageHeaderAccessor.class.getDeclaredField("userCallback");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            return null;
        }
    }

    private String firstNonBlank(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }
}
