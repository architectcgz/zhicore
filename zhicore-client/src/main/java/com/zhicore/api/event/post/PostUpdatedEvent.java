package com.zhicore.api.event.post;

import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

import java.util.List;

/**
 * 文章更新事件
 *
 * @author ZhiCore Team
 */
@Getter
public class PostUpdatedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private final Long postId;

    /**
     * 标题
     */
    private final String title;

    /**
     * 内容
     */
    private final String content;

    /**
     * 摘要
     */
    private final String excerpt;

    /**
     * 标签
     */
    private final List<String> tags;

    public PostUpdatedEvent(Long postId, String title, String content, String excerpt, List<String> tags) {
        super();
        this.postId = postId;
        this.title = title;
        this.content = content;
        this.excerpt = excerpt;
        this.tags = tags;
    }

    @Override
    public String getTag() {
        return "updated";
    }
}
