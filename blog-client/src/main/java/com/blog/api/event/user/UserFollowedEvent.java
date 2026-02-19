package com.blog.api.event.user;

import com.blog.api.event.DomainEvent;
import lombok.Getter;

/**
 * 用户关注事件
 *
 * @author Blog Team
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

    public UserFollowedEvent(Long followerId, Long followingId) {
        super();
        this.followerId = followerId;
        this.followingId = followingId;
    }

    @Override
    public String getTag() {
        return "followed";
    }
}
