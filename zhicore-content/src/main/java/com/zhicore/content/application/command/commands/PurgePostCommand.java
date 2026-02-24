package com.zhicore.content.application.command.commands;

import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 物理删除文章命令
 * 
 * 用于彻底删除文章（包括 PostgreSQL 和 MongoDB 中的数据）。
 * 注意：这是不可逆操作，只应在必要时使用。
 * 
 * @author ZhiCore Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurgePostCommand {
    
    /**
     * 文章 ID
     */
    private PostId postId;
    
    /**
     * 用户 ID（用于权限验证，需要管理员权限）
     */
    private UserId userId;
}
