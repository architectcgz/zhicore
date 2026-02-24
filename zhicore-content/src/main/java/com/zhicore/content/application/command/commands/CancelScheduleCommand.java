package com.zhicore.content.application.command.commands;

import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 取消定时发布命令
 * 
 * @author ZhiCore Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelScheduleCommand {
    
    /**
     * 文章 ID
     */
    private PostId postId;
    
    /**
     * 用户 ID
     */
    private UserId userId;
}
