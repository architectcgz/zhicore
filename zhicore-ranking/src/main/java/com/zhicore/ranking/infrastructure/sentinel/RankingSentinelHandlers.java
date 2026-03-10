package com.zhicore.ranking.infrastructure.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.zhicore.common.exception.TooManyRequestsException;
import com.zhicore.ranking.application.dto.HotPostDTO;
import com.zhicore.ranking.application.service.PostMetadataResolver;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 排行榜服务 Sentinel 方法级 block 处理器。
 */
public final class RankingSentinelHandlers {

    private RankingSentinelHandlers() {
    }

    public static List<HotPostDTO> handleHotPostDetailsBlocked(int page, int size, BlockException ex) {
        throw tooManyRequests("排行榜请求过于频繁，请稍后重试");
    }

    public static Map<Long, PostMetadataResolver.PostMetadata> handleResolvePostMetadataBlocked(
            Collection<Long> postIds, BlockException ex) {
        throw tooManyRequests("排行榜元数据请求过于频繁，请稍后重试");
    }

    private static TooManyRequestsException tooManyRequests(String message) {
        return new TooManyRequestsException(message);
    }
}
