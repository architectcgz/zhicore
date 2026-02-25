package com.zhicore.content.application.command.commands;

import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 恢复文章命令
 * 
 * @author ZhiCore Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RestorePostCommand {
    
    /**
     * 文章 ID
     */
    private PostId postId;
    
    /**
     * 用户 ID（用于权限验证）
     */
    private UserId userId;
}
