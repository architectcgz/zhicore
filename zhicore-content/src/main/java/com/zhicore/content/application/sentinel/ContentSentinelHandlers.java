package com.zhicore.content.application.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.zhicore.api.dto.admin.PostManageDTO;
import com.zhicore.common.exception.TooManyRequestsException;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.result.HybridPageResult;
import com.zhicore.content.application.dto.admin.outbox.OutboxDeadPageResponse;
import com.zhicore.content.application.dto.PostBriefVO;
import com.zhicore.content.application.dto.PostContentVO;
import com.zhicore.content.application.dto.PostVO;
import com.zhicore.content.application.dto.TagDTO;
import com.zhicore.content.application.dto.TagStatsDTO;
import com.zhicore.content.application.query.model.PostListQuery;

import java.util.List;
import java.util.Map;

/**
 * 文章服务 Sentinel 方法级 block 处理器。
 */
public final class ContentSentinelHandlers {

    private ContentSentinelHandlers() {
    }

    public static PostVO handleGetPostDetailBlocked(Long postId, BlockException ex) {
        throw tooManyRequests("文章详情请求过于频繁，请稍后重试");
    }

    public static HybridPageResult<PostBriefVO> handleGetPostListBlocked(PostListQuery query, BlockException ex) {
        throw tooManyRequests("文章列表请求过于频繁，请稍后重试");
    }

    public static PostContentVO handleGetPostContentBlocked(Long postId, BlockException ex) {
        throw tooManyRequests("文章内容请求过于频繁，请稍后重试");
    }

    public static TagDTO handleGetTagDetailBlocked(String slug, BlockException ex) {
        throw tooManyRequests("标签详情请求过于频繁，请稍后重试");
    }

    public static PageResult<TagDTO> handleListTagsBlocked(int page, int size, BlockException ex) {
        throw tooManyRequests("标签列表请求过于频繁，请稍后重试");
    }

    public static List<TagDTO> handleSearchTagsBlocked(String keyword, int limit, BlockException ex) {
        throw tooManyRequests("标签搜索请求过于频繁，请稍后重试");
    }

    public static PageResult<PostVO> handleGetPostsByTagBlocked(String slug, int page, int size, BlockException ex) {
        throw tooManyRequests("标签文章列表请求过于频繁，请稍后重试");
    }

    public static List<TagStatsDTO> handleGetHotTagsBlocked(int limit, BlockException ex) {
        throw tooManyRequests("热门标签请求过于频繁，请稍后重试");
    }

    public static Boolean handleIsPostLikedBlocked(Long userId, Long postId, BlockException ex) {
        throw tooManyRequests("点赞状态查询过于频繁，请稍后重试");
    }

    public static Map<Long, Boolean> handleBatchCheckPostLikedBlocked(Long userId, List<Long> postIds, BlockException ex) {
        throw tooManyRequests("批量点赞状态查询过于频繁，请稍后重试");
    }

    public static Integer handleGetPostLikeCountBlocked(Long postId, BlockException ex) {
        throw tooManyRequests("点赞数查询过于频繁，请稍后重试");
    }

    public static Boolean handleIsPostFavoritedBlocked(Long userId, Long postId, BlockException ex) {
        throw tooManyRequests("收藏状态查询过于频繁，请稍后重试");
    }

    public static Map<Long, Boolean> handleBatchCheckPostFavoritedBlocked(Long userId, List<Long> postIds,
                                                                           BlockException ex) {
        throw tooManyRequests("批量收藏状态查询过于频繁，请稍后重试");
    }

    public static Integer handleGetPostFavoriteCountBlocked(Long postId, BlockException ex) {
        throw tooManyRequests("收藏数查询过于频繁，请稍后重试");
    }

    public static PageResult<PostManageDTO> handleAdminQueryPostsBlocked(String keyword, String status, Long authorId,
                                                                         int page, int size, BlockException ex) {
        throw tooManyRequests("后台文章列表请求过于频繁，请稍后重试");
    }

    public static OutboxDeadPageResponse handleListDeadOutboxBlocked(int page, int size, String eventType,
                                                                     BlockException ex) {
        throw tooManyRequests("死信事件分页请求过于频繁，请稍后重试");
    }

    private static TooManyRequestsException tooManyRequests(String message) {
        return new TooManyRequestsException(message);
    }
}
