package com.zhicore.user.application.port;

import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.common.result.HybridPageResult;

/**
 * 用户主页文章查询端口。
 *
 * 对外暴露“按作者读取公开文章列表”的能力，屏蔽下游内容服务调用细节。
 */
public interface UserPostQueryPort {

    /**
     * 获取指定作者已发布文章列表。
     *
     * @param userId 作者 ID
     * @param page 页码（1-based）
     * @param size 每页大小
     * @return 已发布文章分页结果
     */
    HybridPageResult<PostDTO> getPublishedPostsByAuthor(Long userId, int page, int size);
}
