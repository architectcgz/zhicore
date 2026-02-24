package com.zhicore.content.application.command.commands;

import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.UserId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * 更新文章标签命令
 * 
 * @author ZhiCore Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePostTagsCommand {
    
    /**
     * 文章 ID
     */
    private PostId postId;
    
    /**
     * 用户 ID（用于权限验证）
     */
    private UserId userId;
    
    /**
     * 新的标签 ID 集合
     */
    private Set<TagId> newTagIds;
}
