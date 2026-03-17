package com.zhicore.message.application.service;

import com.zhicore.message.application.event.MessageRecallSyncRequest;
import com.zhicore.message.application.event.MessageSentPublishRequest;
import com.zhicore.message.application.port.im.ImMessageGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * zhicore-message 到 im-system 的写路径桥接服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImMessageBridgeService {

    private final ImMessageGateway imMessageGateway;

    public void syncSentMessage(MessageSentPublishRequest request) {
        log.debug("Dispatch IM bridge send: localMessageId={}", request.getMessageId());
        imMessageGateway.syncSentMessage(request);
    }

    public void syncRecallMessage(MessageRecallSyncRequest request) {
        log.debug("Dispatch IM bridge recall: localMessageId={}", request.getMessageId());
        imMessageGateway.syncRecallMessage(request);
    }
}
