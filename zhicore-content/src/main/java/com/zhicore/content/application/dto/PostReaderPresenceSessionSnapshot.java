package com.zhicore.content.application.dto;

import lombok.Builder;
import lombok.Value;

/**
 * 阅读在线态 session 快照。
 */
@Value
@Builder
public class PostReaderPresenceSessionSnapshot {
    String sessionId;
    Long postId;
    Long userId;
    boolean anonymous;
    String nickname;
    String avatarUrl;
    String fingerprint;
    long lastSeenAtEpochMillis;
    long expireAtEpochMillis;
}
