package com.zhicore.user.domain.event;

import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

/**
 * 用户拉黑事件
 *
 * @author ZhiCore Team
 */
@Getter
public class UserBlockedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    private final Long blockerId;
    private final Long blockedId;

    public UserBlockedEvent(Long blockerId, Long blockedId) {
        super();
        this.blockerId = blockerId;
        this.blockedId = blockedId;
    }

    @Override
    public String getTag() {
        return "blocked";
    }
}
