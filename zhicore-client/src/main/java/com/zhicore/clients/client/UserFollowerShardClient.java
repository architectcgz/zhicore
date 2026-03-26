package com.zhicore.api.client;

import com.zhicore.api.dto.user.FollowerShardPageDTO;
import com.zhicore.common.result.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户粉丝分片查询 Feign 客户端。
 */
@FeignClient(name = "zhicore-user")
public interface UserFollowerShardClient {

    @GetMapping("/api/v1/users/{userId}/followers/shard")
    ApiResponse<FollowerShardPageDTO> getFollowerShard(@PathVariable("userId") Long userId,
                                                       @RequestParam("cursorFollowerId") Long cursorFollowerId,
                                                       @RequestParam("size") Integer size);
}
