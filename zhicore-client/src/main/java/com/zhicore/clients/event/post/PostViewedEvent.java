package com.zhicore.api.event.post;

import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 文章浏览事件
 *
 * @author ZhiCore Team
 */
@Getter
public class PostViewedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private final Long postId;

    /**
     * 浏览用户ID（可为空，表示匿名用户）
     */
    private final Long userId;

    /**
     * 文章作者ID
     */
    private final Long authorId;

    /**
     * 文章发布时间（用于热度计算时间衰减）
     */
    private final LocalDateTime publishedAt;

    /**
     * 客户端 IP（用于匿名用户浏览去重）
     */
    private final String clientIp;

    /**
     * User-Agent（用于匿名用户浏览去重指纹）
     */
    private final String userAgent;

    public PostViewedEvent(Long postId, Long userId, Long authorId,
                           LocalDateTime publishedAt, String clientIp, String userAgent) {
        super();
        this.postId = postId;
        this.userId = userId;
        this.authorId = authorId;
        this.publishedAt = publishedAt;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
    }

    @Override
    public String getTag() {
        return "viewed";
    }
}
