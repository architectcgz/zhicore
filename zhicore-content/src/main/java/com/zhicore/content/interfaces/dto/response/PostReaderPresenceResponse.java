package com.zhicore.content.interfaces.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PostReaderPresenceResponse {
    int readingCount;
    List<ReaderAvatarResponse> avatars;

    @Value
    @Builder
    public static class ReaderAvatarResponse {
        String userId;
        String nickname;
        String avatarUrl;
    }
}
