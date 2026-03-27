package com.zhicore.message.infrastructure.config;

import com.zhicore.common.websocket.StompJwtChannelInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.ChannelRegistration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketConfigTest {

    @Test
    void shouldRegisterInboundJwtInterceptor() {
        StompJwtChannelInterceptor interceptor = mock(StompJwtChannelInterceptor.class);
        ChannelRegistration registration = mock(ChannelRegistration.class);
        when(registration.interceptors(interceptor)).thenReturn(registration);

        WebSocketConfig config = new WebSocketConfig(interceptor);

        config.configureClientInboundChannel(registration);

        verify(registration).interceptors(interceptor);
    }
}
