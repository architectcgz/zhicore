package com.zhicore.content.interfaces.controller;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.application.dto.PostReaderPresenceView;
import com.zhicore.content.application.service.PostReaderPresenceAppService;
import com.zhicore.content.interfaces.dto.request.PostReaderPresenceLeaveRequest;
import com.zhicore.content.interfaces.dto.request.PostReaderPresenceSessionRequest;
import com.zhicore.content.interfaces.dto.response.PostReaderPresenceResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts/{postId}/readers")
@RequiredArgsConstructor
public class PostReaderPresenceController {

    private final PostReaderPresenceAppService postReaderPresenceAppService;

    @PostMapping("/session")
    public ApiResponse<PostReaderPresenceResponse> register(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Valid @RequestBody PostReaderPresenceSessionRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(toResponse(postReaderPresenceAppService.register(
                postId,
                request.getSessionId(),
                servletRequest.getHeader("User-Agent"),
                extractClientIp(servletRequest)
        )));
    }

    @PostMapping("/session/leave")
    public ApiResponse<Void> leave(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Valid @RequestBody PostReaderPresenceLeaveRequest request) {
        postReaderPresenceAppService.leave(postId, request.getSessionId());
        return ApiResponse.success();
    }

    @GetMapping("/presence")
    public ApiResponse<PostReaderPresenceResponse> query(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        return ApiResponse.success(toResponse(postReaderPresenceAppService.query(postId)));
    }

    private PostReaderPresenceResponse toResponse(PostReaderPresenceView view) {
        return PostReaderPresenceResponse.builder()
                .readingCount(view.getReadingCount())
                .avatars(view.getAvatars().stream().map(item -> PostReaderPresenceResponse.ReaderAvatarResponse.builder()
                        .userId(item.getUserId())
                        .nickname(item.getNickname())
                        .avatarUrl(item.getAvatarUrl())
                        .build()).toList())
                .build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
