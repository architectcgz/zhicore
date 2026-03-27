package com.zhicore.common.websocket;

import java.security.Principal;

/**
 * STOMP 会话的认证主体。
 */
public record StompPrincipal(String name, String userName, String roles) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}
