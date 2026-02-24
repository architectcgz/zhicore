package com.zhicore.content.application.command.commands;

import com.zhicore.content.domain.model.ContentType;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新文章内容命令
 * 
 * 用于更新文章的正文内容，不包括元数据（标题、摘要等）。
 * 
 * @author ZhiCore Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePostContentCommand {
    
    /**
     * 文章 ID
     */
    private PostId postId;
    
    /**
     * 用户 ID（用于权限验证）
     */
    private UserId userId;
    
    /**
     * 文章内容（Markdown 或 HTML）
     */
    private String content;
    
    /**
     * 内容类型
     */
    private ContentType contentType;
    
    /**
     * 获取内容类型（带默认值）
     * 
     * @return 内容类型，如果为 null 则返回 MARKDOWN
     */
    public ContentType getContentTypeOrDefault() {
        return contentType != null ? contentType : ContentType.MARKDOWN;
    }
}
