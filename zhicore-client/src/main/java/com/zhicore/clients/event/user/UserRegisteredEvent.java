package com.zhicore.api.event.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonCreator
    public UserRegisteredEvent(@JsonProperty("eventId") String eventId,
                               @JsonProperty("occurredAt") java.time.Instant occurredAt,
                               @JsonProperty("userId") Long userId,
                               @JsonProperty("userName") String userName,
                               @JsonProperty("email") String email) {
        super(eventId, occurredAt);
        this.userId = userId;
        this.userName = userName;
        this.email = email;
    }

    public UserRegisteredEvent(Long userId, String userName, String email) {
        this(null, null, userId, userName, email);
    }

    @Override
    public String getTag() {
        return "registered";
    }
}
