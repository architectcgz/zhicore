package com.zhicore.content.application.command.commands;

import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新文章元数据命令
 * 
 * @author ZhiCore Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePostMetaCommand {
    
    /**
     * 文章 ID
     */
    private PostId postId;
    
    /**
     * 用户 ID（用于权限验证）
     */
    private UserId userId;
    
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
}
