package com.zhicore.api.client;

import com.zhicore.api.dto.user.FollowerCursorPageDTO;
import com.zhicore.common.result.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

/**
 * 用户粉丝游标查询契约。
 */
public interface UserFollowerCursorClient {

    @GetMapping("/api/v1/users/{userId}/followers/cursor")
    ApiResponse<FollowerCursorPageDTO> getFollowersByCursor(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "afterCreatedAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime afterCreatedAt,
            @RequestParam(value = "afterFollowerId", required = false) Long afterFollowerId,
            @RequestParam(value = "limit", defaultValue = "200") Integer limit
    );
}
