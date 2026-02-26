package com.zhicore.user.domain.event;

import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

/**
 * 用户密码变更事件
 *
 * @author ZhiCore Team
 */
@Getter
public class UserPasswordChangedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    private final Long userId;

    public UserPasswordChangedEvent(Long userId) {
        super();
        this.userId = userId;
    }

    @Override
    public String getTag() {
        return "password-changed";
    }
}
