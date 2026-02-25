package com.zhicore.content.application.command.commands;

import com.zhicore.content.domain.model.ContentType;
import com.zhicore.content.domain.model.OwnerSnapshot;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.TopicId;
import com.zhicore.content.domain.model.UserId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * 创建文章命令
 * 
 * @author ZhiCore Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePostCommand {
    
    /**
     * 文章 ID（值对象）
     */
    private PostId postId;
    
    /**
     * 作者 ID（值对象）
     */
    private UserId ownerId;
    
    /**
     * 标题
     */
    private String title;
    
    /**
     * 摘要
     */
    private String excerpt;
    
    /**
     * 封面图片
     */
    private String coverImage;
    
    /**
     * 文章内容（Markdown 或 HTML）
     */
    private String content;
    
    /**
     * 内容类型
     */
    private ContentType contentType;
    
    /**
     * 作者快照
     */
    private OwnerSnapshot ownerSnapshot;
    
    /**
     * 标签 ID 集合（值对象）
     */
    private Set<TagId> tagIds;
    
    /**
     * 话题 ID（值对象）
     */
    private TopicId topicId;
    
    /**
     * 获取内容类型（带默认值）
     * 
     * @return 内容类型，如果为 null 则返回 MARKDOWN
     */
    public ContentType getContentTypeOrDefault() {
        return contentType != null ? contentType : ContentType.MARKDOWN;
    }
}
