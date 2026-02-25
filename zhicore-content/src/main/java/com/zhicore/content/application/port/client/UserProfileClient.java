package com.zhicore.content.application.port.client;

import com.zhicore.content.domain.model.OwnerSnapshot;
import com.zhicore.content.domain.model.UserId;

import java.util.Optional;

/**
 * 用户资料客户端端口接口
 * 
 * 定义获取用户资料信息的契约，由基础设施层实现（如 Feign Client）。
 * 用于获取作者信息快照，支持文章创建和作者信息更新。
 * 
 * @author ZhiCore Team
 */
public interface UserProfileClient {
    
    /**
     * 获取作者信息快照
     * 
     * @param userId 用户ID（值对象）
     * @return 作者信息快照（可能为空，表示用户不存在或服务不可用）
     */
    Optional<OwnerSnapshot> getOwnerSnapshot(UserId userId);
}

