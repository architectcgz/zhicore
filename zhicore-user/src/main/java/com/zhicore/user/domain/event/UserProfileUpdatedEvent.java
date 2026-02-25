package com.zhicore.user.domain.event;

import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

/**
 * 用户资料更新事件
 *
 * @author ZhiCore Team
 */
@Getter
public class UserProfileUpdatedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private final Long userId;

    /**
     * 更新的昵称
     */
    private final String nickName;

    /**
     * 更新的头像文件ID
     */
    private final String avatarId;

    public UserProfileUpdatedEvent(Long userId, String nickName, String avatarId) {
        super();
        this.userId = userId;
        this.nickName = nickName;
        this.avatarId = avatarId;
    }

    @Override
    public String getTag() {
        return "profile-updated";
    }
}
