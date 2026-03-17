package com.zhicore.message.application.port.store;

import com.zhicore.message.application.model.ImMessageMapping;

import java.time.Duration;
import java.util.Optional;

/**
 * IM 桥接映射存储。
 */
public interface ImMessageBridgeStore {

    void save(Long localMessageId, ImMessageMapping mapping, Duration ttl);

    Optional<ImMessageMapping> find(Long localMessageId);
}
