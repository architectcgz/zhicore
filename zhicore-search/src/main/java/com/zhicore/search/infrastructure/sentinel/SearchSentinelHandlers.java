package com.zhicore.search.infrastructure.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.search.interfaces.dto.PostSearchVO;
import com.zhicore.search.interfaces.dto.SearchResultVO;

import java.util.List;

/**
 * 搜索服务 Sentinel 方法级 block 处理器。
 */
public final class SearchSentinelHandlers {

    private SearchSentinelHandlers() {
    }

    public static SearchResultVO<PostSearchVO> handleSearchPostsBlocked(
            String keyword, int page, int size, BlockException ex) {
        throw tooManyRequests("搜索请求过于频繁，请稍后重试");
    }

    public static List<String> handleSuggestionsBlocked(
            String prefix, String userId, int limit, BlockException ex) {
        throw tooManyRequests("搜索建议请求过于频繁，请稍后重试");
    }

    public static List<String> handleHotKeywordsBlocked(int limit, BlockException ex) {
        throw tooManyRequests("热门搜索请求过于频繁，请稍后重试");
    }

    public static List<String> handleUserHistoryBlocked(String userId, int limit, BlockException ex) {
        throw tooManyRequests("搜索历史请求过于频繁，请稍后重试");
    }

    private static BusinessException tooManyRequests(String message) {
        return new BusinessException(ResultCode.TOO_MANY_REQUESTS, message);
    }
}
