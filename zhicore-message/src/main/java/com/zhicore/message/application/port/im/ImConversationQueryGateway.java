package com.zhicore.message.application.port.im;

import com.zhicore.message.application.model.DirectConversationRef;
import com.zhicore.message.application.model.ImConversationView;
import java.util.List;
import java.util.Optional;

/**
 * 会话查询端口。
 *
 * 双方对接时应围绕该能力契约演进，而不是让 application
 * 直接依赖某一方当前的 HTTP/RPC DTO。
 */
public interface ImConversationQueryGateway {

    List<ImConversationView> listConversations(Long userId, Long cursor, int limit);

    Optional<ImConversationView> findConversation(Long userId, DirectConversationRef conversationRef);

    int countConversations(Long userId);
}
