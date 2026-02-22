package com.zhicore.api.event.user;

import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

/**
 * 用户注册事件
 *
 * @author ZhiCore Team
 */
@Getter
public class UserRegisteredEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private final Long userId;

    /**
     * 用户名
     */
    private final String userName;

    /**
     * 邮箱
     */
    private final String email;

    public UserRegisteredEvent(Long userId, String userName, String email) {
        super();
        this.userId = userId;
        this.userName = userName;
        this.email = email;
    }

    @Override
    public String getTag() {
        return "registered";
    }
}
