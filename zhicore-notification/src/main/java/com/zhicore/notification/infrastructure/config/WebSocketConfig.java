package com.zhicore.notification.infrastructure.config;

import com.zhicore.common.config.JwtProperties;
import com.zhicore.common.websocket.JwtClaimsParser;
import com.zhicore.common.websocket.StompJwtChannelInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置
 *
 * @author ZhiCore Team
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompJwtChannelInterceptor stompJwtChannelInterceptor;

    @Autowired
    public WebSocketConfig(JwtProperties jwtProperties) {
        this(new StompJwtChannelInterceptor(new JwtClaimsParser(jwtProperties)));
    }

    WebSocketConfig(StompJwtChannelInterceptor stompJwtChannelInterceptor) {
        this.stompJwtChannelInterceptor = stompJwtChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单的内存消息代理
        // /queue 用于点对点消息（通知推送）
        // /topic 用于广播消息（系统公告）
        config.enableSimpleBroker("/queue", "/topic");
        
        // 应用程序目的地前缀
        config.setApplicationDestinationPrefixes("/app");
        
        // 用户目的地前缀
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompJwtChannelInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册 STOMP 端点
        registry.addEndpoint("/ws/notification")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
