package com.zhicore.api.event.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

/**
 * 用户关注事件
 *
 * @author ZhiCore Team
 */
@Getter
public class UserFollowedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 关注者ID
     */
    private final Long followerId;

    /**
     * 被关注者ID
     */
    private final Long followingId;

    @JsonCreator
    public UserFollowedEvent(@JsonProperty("eventId") String eventId,
                             @JsonProperty("occurredAt") java.time.LocalDateTime occurredAt,
                             @JsonProperty("followerId") Long followerId,
                             @JsonProperty("followingId") Long followingId) {
        super(eventId, occurredAt);
        this.followerId = followerId;
        this.followingId = followingId;
    }

    public UserFollowedEvent(Long followerId, Long followingId) {
        this(null, null, followerId, followingId);
    }

    @Override
    public String getTag() {
        return "followed";
    }
}
