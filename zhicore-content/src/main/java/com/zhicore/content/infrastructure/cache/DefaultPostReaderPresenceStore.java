package com.zhicore.content.infrastructure.cache;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.content.application.dto.PostReaderPresenceSessionSnapshot;
import com.zhicore.content.application.dto.PostReaderPresenceView;
import com.zhicore.content.application.port.store.PostReaderPresenceStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultPostReaderPresenceStore implements PostReaderPresenceStore {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void touchSession(PostReaderPresenceSessionSnapshot snapshot, Duration ttl) {
        String sessionKey = PostRedisKeys.presenceSession(snapshot.getSessionId());
        String sessionsKey = PostRedisKeys.presenceSessions(snapshot.getPostId());
        try {
            StoredPresenceSession stored = new StoredPresenceSession(
                    snapshot.getSessionId(),
                    snapshot.getPostId(),
                    snapshot.getUserId(),
                    snapshot.isAnonymous(),
                    snapshot.getNickname(),
                    snapshot.getAvatarUrl(),
                    snapshot.getFingerprint(),
                    snapshot.getLastSeenAtEpochMillis(),
                    snapshot.getExpireAtEpochMillis()
            );
            stringRedisTemplate.opsForValue().set(sessionKey, objectMapper.writeValueAsString(stored), ttl);
            stringRedisTemplate.opsForZSet().add(sessionsKey, snapshot.getSessionId(), snapshot.getExpireAtEpochMillis());
            stringRedisTemplate.expire(sessionsKey, ttl.plusSeconds(10));
        } catch (Exception e) {
            throw new IllegalStateException("failed to touch presence session", e);
        }
    }

    @Override
    public void removeSession(Long postId, String sessionId) {
        stringRedisTemplate.delete(PostRedisKeys.presenceSession(sessionId));
        stringRedisTemplate.opsForZSet().remove(PostRedisKeys.presenceSessions(postId), sessionId);
    }

    @Override
    public PostReaderPresenceView query(Long postId, long nowEpochMillis, int avatarLimit) {
        String sessionsKey = PostRedisKeys.presenceSessions(postId);
        stringRedisTemplate.opsForZSet().removeRangeByScore(sessionsKey, Double.NEGATIVE_INFINITY, nowEpochMillis);
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(sessionsKey, 0, -1);
        if (tuples == null || tuples.isEmpty()) {
            return PostReaderPresenceView.empty();
        }

        List<StoredPresenceSession> validSessions = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String sessionId = tuple.getValue();
            if (sessionId == null) {
                continue;
            }
            StoredPresenceSession session = readSession(sessionId);
            if (session == null || session.getExpireAtEpochMillis() <= nowEpochMillis || !postId.equals(session.getPostId())) {
                stringRedisTemplate.opsForZSet().remove(sessionsKey, sessionId);
                continue;
            }
            validSessions.add(session);
        }

        validSessions.sort(Comparator.comparingLong(StoredPresenceSession::getLastSeenAtEpochMillis).reversed());
        Set<String> seenUserIds = new LinkedHashSet<>();
        List<PostReaderPresenceView.ReaderAvatarView> avatars = new ArrayList<>();
        for (StoredPresenceSession session : validSessions) {
            if (session.isAnonymous() || session.getUserId() == null) {
                continue;
            }
            String userId = String.valueOf(session.getUserId());
            if (!seenUserIds.add(userId)) {
                continue;
            }
            avatars.add(PostReaderPresenceView.ReaderAvatarView.builder()
                    .userId(userId)
                    .nickname(session.getNickname())
                    .avatarUrl(session.getAvatarUrl())
                    .build());
            if (avatars.size() >= avatarLimit) {
                break;
            }
        }

        return PostReaderPresenceView.builder()
                .readingCount(validSessions.size())
                .avatars(avatars)
                .build();
    }

    @Override
    public void evictPost(Long postId) {
        String sessionsKey = PostRedisKeys.presenceSessions(postId);
        Set<String> sessionIds = stringRedisTemplate.opsForZSet().range(sessionsKey, 0, -1);
        if (sessionIds != null && !sessionIds.isEmpty()) {
            List<String> detailKeys = sessionIds.stream().map(PostRedisKeys::presenceSession).toList();
            stringRedisTemplate.delete(detailKeys);
        }
        stringRedisTemplate.delete(sessionsKey);
    }

    @Override
    public boolean acquireAnonymousRegistration(Long postId, String fingerprint, Duration window, int limit) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return true;
        }
        String key = PostRedisKeys.presenceAnonymousRegisterThrottle(postId, fingerprint);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, window);
        }
        return count == null || count <= limit;
    }

    @Override
    public boolean acquireHeartbeatThrottle(Long postId, String sessionId, Duration interval) {
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                PostRedisKeys.presenceHeartbeatThrottle(postId, sessionId),
                "1",
                interval.toMillis(),
                TimeUnit.MILLISECONDS
        );
        return Boolean.TRUE.equals(acquired);
    }

    private StoredPresenceSession readSession(String sessionId) {
        String raw = stringRedisTemplate.opsForValue().get(PostRedisKeys.presenceSession(sessionId));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, StoredPresenceSession.class);
        } catch (Exception e) {
            log.warn("Failed to parse presence session: sessionId={}", sessionId, e);
            stringRedisTemplate.delete(PostRedisKeys.presenceSession(sessionId));
            return null;
        }
    }

    private static final class StoredPresenceSession {
        private final String sessionId;
        private final Long postId;
        private final Long userId;
        private final boolean anonymous;
        private final String nickname;
        private final String avatarUrl;
        private final String fingerprint;
        private final long lastSeenAtEpochMillis;
        private final long expireAtEpochMillis;

        @JsonCreator
        private StoredPresenceSession(
                @JsonProperty("sessionId") String sessionId,
                @JsonProperty("postId") Long postId,
                @JsonProperty("userId") Long userId,
                @JsonProperty("anonymous") boolean anonymous,
                @JsonProperty("nickname") String nickname,
                @JsonProperty("avatarUrl") String avatarUrl,
                @JsonProperty("fingerprint") String fingerprint,
                @JsonProperty("lastSeenAtEpochMillis") long lastSeenAtEpochMillis,
                @JsonProperty("expireAtEpochMillis") long expireAtEpochMillis) {
            this.sessionId = sessionId;
            this.postId = postId;
            this.userId = userId;
            this.anonymous = anonymous;
            this.nickname = nickname;
            this.avatarUrl = avatarUrl;
            this.fingerprint = fingerprint;
            this.lastSeenAtEpochMillis = lastSeenAtEpochMillis;
            this.expireAtEpochMillis = expireAtEpochMillis;
        }

        public String getSessionId() { return sessionId; }
        public Long getPostId() { return postId; }
        public Long getUserId() { return userId; }
        public boolean isAnonymous() { return anonymous; }
        public String getNickname() { return nickname; }
        public String getAvatarUrl() { return avatarUrl; }
        public long getLastSeenAtEpochMillis() { return lastSeenAtEpochMillis; }
        public long getExpireAtEpochMillis() { return expireAtEpochMillis; }
    }
}
