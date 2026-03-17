package com.zhicore.message.infrastructure.integration.imsystem;

import com.zhicore.common.util.JsonUtils;
import com.zhicore.message.application.event.MessageRecallSyncRequest;
import com.zhicore.message.application.event.MessageSentPublishRequest;
import com.zhicore.message.application.model.ImMessageMapping;
import com.zhicore.message.application.port.im.ImMessageGateway;
import com.zhicore.message.application.port.store.ImMessageBridgeStore;
import com.zhicore.message.domain.model.MessageType;
import com.zhicore.message.infrastructure.config.ImBridgeProperties;
import com.zhicore.message.infrastructure.feign.ImExternalMessageClient;
import com.zhicore.message.infrastructure.feign.ImInternalMessageClient;
import com.zhicore.message.infrastructure.feign.ImOpenIdClient;
import com.zhicore.message.infrastructure.feign.dto.ImBatchConvertToOpenIdsRequest;
import com.zhicore.message.infrastructure.feign.dto.ImExternalMessageResp;
import com.zhicore.message.infrastructure.feign.dto.ImRecallMessageRequest;
import com.zhicore.message.infrastructure.feign.dto.ImResponse;
import com.zhicore.message.infrastructure.feign.dto.ImSendMessageByOpenIdRequest;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * im-system 集成适配器。
 *
 * 统一收口 OpenID 转换、外部协议 DTO、内部 token 和映射缓存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImSystemMessageGateway implements ImMessageGateway {

    private static final int IM_SINGLE_CONVERSATION = 1;
    private static final int IM_TEXT_MESSAGE = 0;
    private static final int IM_IMAGE_MESSAGE = 1;
    private static final int IM_FILE_MESSAGE = 4;

    private final ImBridgeProperties imBridgeProperties;
    private final ImOpenIdClient imOpenIdClient;
    private final ImExternalMessageClient imExternalMessageClient;
    private final ImInternalMessageClient imInternalMessageClient;
    private final ImMessageBridgeStore imMessageBridgeStore;

    @Override
    public void syncSentMessage(MessageSentPublishRequest request) {
        if (!imBridgeProperties.isEnabled()) {
            return;
        }

        try {
            List<String> openIds = convertToOpenIds(request.getSenderId(), request.getReceiverId());
            if (openIds.size() < 2 || openIds.get(0) == null || openIds.get(1) == null) {
                throw new IllegalStateException(String.format(
                        "OpenId mapping is missing: messageId=%s, senderId=%s, receiverId=%s",
                        request.getMessageId(), request.getSenderId(), request.getReceiverId()));
            }

            ImSendMessageByOpenIdRequest imRequest = new ImSendMessageByOpenIdRequest();
            imRequest.setAppId(imBridgeProperties.getAppId());
            imRequest.setProvider(imBridgeProperties.getProvider());
            imRequest.setFromOpenId(openIds.get(0));
            imRequest.setToOpenId(openIds.get(1));
            imRequest.setConversationType(IM_SINGLE_CONVERSATION);
            imRequest.setMessageType(toImMessageType(request.getMessageType()));
            imRequest.setContent(resolveContent(request));
            imRequest.setExtra(resolveExtra(request));

            ImResponse<ImExternalMessageResp> response = imExternalMessageClient.sendMessage(imRequest);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new IllegalStateException(String.format(
                        "IM bridge send failed: localMessageId=%s, code=%s, message=%s",
                        request.getMessageId(),
                        response != null ? response.getCode() : null,
                        response != null ? response.getMessage() : "null response"));
            }

            imMessageBridgeStore.save(
                    request.getMessageId(),
                    new ImMessageMapping(response.getData().getMessageId(), response.getData().getClientMsgId()),
                    Duration.ofDays(imBridgeProperties.getMappingTtlDays())
            );
            log.info("IM bridge send succeeded: localMessageId={}, imMessageId={}, imConversationId={}",
                    request.getMessageId(), response.getData().getMessageId(), response.getData().getClientMsgId());
        } catch (Exception e) {
            log.warn("IM bridge send failed unexpectedly: localMessageId={}", request.getMessageId(), e);
            throw e;
        }
    }

    @Override
    public void syncRecallMessage(MessageRecallSyncRequest request) {
        if (!imBridgeProperties.isEnabled()) {
            return;
        }

        try {
            ImMessageMapping mapping = imMessageBridgeStore.find(request.getMessageId()).orElse(null);
            if (mapping == null || mapping.getImMessageId() == null) {
                throw new IllegalStateException("IM bridge mapping is missing: localMessageId=" + request.getMessageId());
            }

            ImRecallMessageRequest imRequest = new ImRecallMessageRequest();
            imRequest.setAppId(imBridgeProperties.getAppId());
            imRequest.setUserId(request.getSenderId());
            imRequest.setMessageId(mapping.getImMessageId());

            ImResponse<Void> response = imInternalMessageClient.recallMessage(imBridgeProperties.getInternalToken(), imRequest);
            if (response == null || !response.isSuccess()) {
                throw new IllegalStateException(String.format(
                        "IM bridge recall failed: localMessageId=%s, imMessageId=%s, code=%s, message=%s",
                        request.getMessageId(),
                        mapping.getImMessageId(),
                        response != null ? response.getCode() : null,
                        response != null ? response.getMessage() : "null response"));
            }

            log.info("IM bridge recall succeeded: localMessageId={}, imMessageId={}",
                    request.getMessageId(), mapping.getImMessageId());
        } catch (Exception e) {
            log.warn("IM bridge recall failed unexpectedly: localMessageId={}", request.getMessageId(), e);
            throw e;
        }
    }

    private List<String> convertToOpenIds(Long senderId, Long receiverId) {
        ImBatchConvertToOpenIdsRequest request = new ImBatchConvertToOpenIdsRequest();
        request.setAppId(imBridgeProperties.getAppId());
        request.setProvider(imBridgeProperties.getProvider());
        request.setUserIds(List.of(senderId, receiverId));

        ImResponse<List<String>> response = imOpenIdClient.batchConvertToOpenIds(request);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new IllegalStateException("Failed to convert userIds to openIds");
        }
        return response.getData();
    }

    private int toImMessageType(MessageType messageType) {
        return switch (messageType) {
            case TEXT -> IM_TEXT_MESSAGE;
            case IMAGE -> IM_IMAGE_MESSAGE;
            case FILE -> IM_FILE_MESSAGE;
        };
    }

    private String resolveContent(MessageSentPublishRequest request) {
        return switch (request.getMessageType()) {
            case TEXT -> request.getContent();
            case IMAGE -> request.getMediaUrl();
            case FILE -> request.getContent();
        };
    }

    private String resolveExtra(MessageSentPublishRequest request) {
        if (request.getMessageType() == MessageType.FILE && request.getMediaUrl() != null) {
            return JsonUtils.toJson(new FileExtra(request.getMediaUrl()));
        }
        if (request.getMessageType() == MessageType.IMAGE && request.getMediaUrl() != null) {
            return JsonUtils.toJson(new ImageExtra(request.getMediaUrl()));
        }
        return null;
    }

    private record FileExtra(String fileUrl) {
    }

    private record ImageExtra(String imageUrl) {
    }
}
