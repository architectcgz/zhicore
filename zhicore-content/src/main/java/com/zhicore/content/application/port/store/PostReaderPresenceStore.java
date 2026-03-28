package com.zhicore.content.application.port.store;

import com.zhicore.content.application.dto.PostReaderPresenceSessionSnapshot;
import com.zhicore.content.application.dto.PostReaderPresenceView;

import java.time.Duration;

/**
 * 文章阅读在线态存储端口。
 */
public interface PostReaderPresenceStore {

    void touchSession(PostReaderPresenceSessionSnapshot snapshot, Duration ttl);

    void removeSession(Long postId, String sessionId);

    PostReaderPresenceView query(Long postId, long nowEpochMillis, int avatarLimit);

    void evictPost(Long postId);

    boolean acquireAnonymousRegistration(Long postId, String fingerprint, Duration window, int limit);

    boolean acquireHeartbeatThrottle(Long postId, String sessionId, Duration interval);
}
