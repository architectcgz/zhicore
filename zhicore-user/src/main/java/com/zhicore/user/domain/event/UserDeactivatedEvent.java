package com.zhicore.user.domain.event;

import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

/**
 * 用户禁用事件
 *
 * @author ZhiCore Team
 */
@Getter
public class UserDeactivatedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    private final Long userId;

    public UserDeactivatedEvent(Long userId) {
        super();
        this.userId = userId;
    }

    @Override
    public String getTag() {
        return "deactivated";
    }
}
