package com.zhicore.message.application.port.im;

import com.zhicore.message.application.event.MessageRecallSyncRequest;
import com.zhicore.message.application.event.MessageSentPublishRequest;

/**
 * IM 系统桥接端口。
 *
 * application 层只依赖该抽象，不感知 Feign、OpenID 转换、
 * 内部 token、外部 DTO 或映射缓存实现细节。
 */
public interface ImMessageGateway {

    void syncSentMessage(MessageSentPublishRequest request);

    void syncRecallMessage(MessageRecallSyncRequest request);
}
