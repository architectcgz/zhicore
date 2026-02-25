package com.zhicore.content.application.command.commands;

import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 定时发布文章命令
 * 
 * @author ZhiCore Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchedulePublishCommand {
    
    /**
     * 文章 ID
     */
    private PostId postId;
    
    /**
     * 用户 ID（用于权限验证）
     */
    private UserId userId;
    
    /**
     * 定时发布时间
     */
    private LocalDateTime scheduledAt;
}
