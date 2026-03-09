package com.zhicore.message.infrastructure.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.message.application.dto.ConversationVO;
import com.zhicore.message.application.dto.MessageVO;

import java.util.List;

/**
 * 消息服务 Sentinel 方法级 block 处理器。
 */
public final class MessageSentinelHandlers {

    private MessageSentinelHandlers() {
    }

    public static List<ConversationVO> handleConversationListBlocked(Long cursor, int limit, BlockException ex) {
        throw tooManyRequests("会话列表请求过于频繁，请稍后重试");
    }

    public static ConversationVO handleConversationBlocked(Long conversationId, BlockException ex) {
        throw tooManyRequests("会话详情请求过于频繁，请稍后重试");
    }

    public static ConversationVO handleConversationByUserBlocked(Long otherUserId, BlockException ex) {
        throw tooManyRequests("会话查询请求过于频繁，请稍后重试");
    }

    public static int handleConversationCountBlocked(BlockException ex) {
        throw tooManyRequests("会话数量请求过于频繁，请稍后重试");
    }

    public static List<MessageVO> handleMessageHistoryBlocked(Long conversationId, Long cursor, int limit, BlockException ex) {
        throw tooManyRequests("消息历史请求过于频繁，请稍后重试");
    }

    public static int handleUnreadCountBlocked(BlockException ex) {
        throw tooManyRequests("未读消息请求过于频繁，请稍后重试");
    }

    private static BusinessException tooManyRequests(String message) {
        return new BusinessException(ResultCode.TOO_MANY_REQUESTS, message);
    }
}
