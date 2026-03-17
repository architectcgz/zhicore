package com.zhicore.message.application.port.im;

import com.zhicore.message.application.model.DirectConversationRef;
import com.zhicore.message.application.model.ImMessageView;
import java.util.List;

/**
 * 消息查询端口。
 *
 * application 通过业务语义查询消息历史与未读数，
 * 不感知 provider 的 conversationId、消息分页协议和响应结构。
 */
public interface ImMessageQueryGateway {

    List<ImMessageView> getMessageHistory(Long userId, DirectConversationRef conversationRef, Long cursor, int limit);

    int countUnreadMessages(Long userId);
}
