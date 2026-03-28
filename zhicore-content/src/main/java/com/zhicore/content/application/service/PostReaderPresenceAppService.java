package com.zhicore.content.application.service;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.content.application.dto.PostReaderPresenceSessionSnapshot;
import com.zhicore.content.application.dto.PostReaderPresenceView;
import com.zhicore.content.application.port.client.UserProfileClient;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.store.PostReaderPresenceStore;
import com.zhicore.content.domain.model.OwnerSnapshot;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.model.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostReaderPresenceAppService {

    private static final Duration SESSION_TTL = Duration.ofSeconds(40);
    private static final Duration ANONYMOUS_REGISTER_WINDOW = Duration.ofSeconds(60);
    private static final Duration ANONYMOUS_HEARTBEAT_INTERVAL = Duration.ofSeconds(1);
    private static final int MAX_AVATARS = 3;
    private static final int MAX_ANONYMOUS_REGISTRATIONS_PER_WINDOW = 8;

    private final PostRepository postRepository;
    private final PostReaderPresenceStore postReaderPresenceStore;
    private final UserProfileClient userProfileClient;
    private final PostFileUrlResolver postFileUrlResolver;

    @Transactional(readOnly = true)
    public PostReaderPresenceView register(Long postId, String sessionId, String userAgent, String remoteIp) {
        requireVisiblePost(postId);
        long now = System.currentTimeMillis();
        Long userId = UserContext.getUserId();
        boolean anonymous = userId == null;
        String fingerprint = anonymous ? buildFingerprint(remoteIp, userAgent) : null;

        if (anonymous && !postReaderPresenceStore.acquireAnonymousRegistration(
                postId,
                fingerprint,
                ANONYMOUS_REGISTER_WINDOW,
                MAX_ANONYMOUS_REGISTRATIONS_PER_WINDOW
        )) {
            log.debug("Presence anonymous registration throttled: postId={}, fingerprint={}", postId, fingerprint);
            return safeQuery(postId, now);
        }

        if (anonymous && !postReaderPresenceStore.acquireHeartbeatThrottle(postId, sessionId, ANONYMOUS_HEARTBEAT_INTERVAL)) {
            return safeQuery(postId, now);
        }

        OwnerSnapshot ownerSnapshot = resolveOwnerSnapshot(userId);
        PostReaderPresenceSessionSnapshot snapshot = PostReaderPresenceSessionSnapshot.builder()
                .sessionId(sessionId)
                .postId(postId)
                .userId(userId)
                .anonymous(anonymous)
                .nickname(ownerSnapshot != null ? ownerSnapshot.getNickname() : null)
                .avatarUrl(resolveAvatarUrl(ownerSnapshot))
                .fingerprint(fingerprint)
                .lastSeenAtEpochMillis(now)
                .expireAtEpochMillis(now + SESSION_TTL.toMillis())
                .build();

        try {
            postReaderPresenceStore.touchSession(snapshot, SESSION_TTL);
        } catch (Exception e) {
            log.warn("Presence register degraded: postId={}, sessionId={}", postId, sessionId, e);
            return PostReaderPresenceView.empty();
        }
        return safeQuery(postId, now);
    }

    @Transactional(readOnly = true)
    public PostReaderPresenceView query(Long postId) {
        requireVisiblePost(postId);
        return safeQuery(postId, System.currentTimeMillis());
    }

    public void leave(Long postId, String sessionId) {
        try {
            postReaderPresenceStore.removeSession(postId, sessionId);
        } catch (Exception e) {
            log.warn("Presence leave failed: postId={}, sessionId={}", postId, sessionId, e);
        }
    }

    public void evictPost(Long postId) {
        try {
            postReaderPresenceStore.evictPost(postId);
        } catch (Exception e) {
            log.warn("Presence evict post failed: postId={}", postId, e);
        }
    }

    private Post requireVisiblePost(Long postId) {
        return postRepository.findById(postId)
                .filter(post -> post.getStatus() == PostStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "文章不存在"));
    }

    private PostReaderPresenceView safeQuery(Long postId, long now) {
        try {
            return postReaderPresenceStore.query(postId, now, MAX_AVATARS);
        } catch (Exception e) {
            log.warn("Presence query degraded: postId={}", postId, e);
            return PostReaderPresenceView.empty();
        }
    }

    private OwnerSnapshot resolveOwnerSnapshot(Long userId) {
        if (userId == null) {
            return null;
        }
        Optional<OwnerSnapshot> snapshot = userProfileClient.getOwnerSnapshot(UserId.of(userId));
        return snapshot.orElseGet(() -> new OwnerSnapshot(
                UserId.of(userId),
                UserContext.getUserName() != null ? UserContext.getUserName() : "已登录用户",
                null,
                0L
        ));
    }

    private String resolveAvatarUrl(OwnerSnapshot snapshot) {
        if (snapshot == null || snapshot.getAvatarId() == null || snapshot.getAvatarId().isBlank()) {
            return null;
        }
        return postFileUrlResolver.resolve(snapshot.getAvatarId());
    }

    private String buildFingerprint(String remoteIp, String userAgent) {
        String source = (remoteIp == null ? "unknown-ip" : remoteIp.trim()) + "|" +
                (userAgent == null ? "unknown-ua" : userAgent.trim());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(source.hashCode());
        }
    }
}
