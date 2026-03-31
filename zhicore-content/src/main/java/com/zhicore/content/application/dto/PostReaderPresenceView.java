package com.zhicore.content.application.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 文章阅读在线态视图。
 */
@Value
@Builder
public class PostReaderPresenceView {
    int readingCount;
    List<ReaderAvatarView> avatars;

    @Value
    @Builder
    public static class ReaderAvatarView {
        String userId;
        String nickname;
        String avatarUrl;
    }

    public static PostReaderPresenceView empty() {
        return PostReaderPresenceView.builder()
                .readingCount(0)
                .avatars(List.of())
                .build();
    }
}
