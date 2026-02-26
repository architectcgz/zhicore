package com.zhicore.user.domain.event;

import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

/**
 * 用户启用事件
 *
 * @author ZhiCore Team
 */
@Getter
public class UserActivatedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    private final Long userId;

    public UserActivatedEvent(Long userId) {
        super();
        this.userId = userId;
    }

    @Override
    public String getTag() {
        return "activated";
    }
}
