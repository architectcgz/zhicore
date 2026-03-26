package com.zhicore.user.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 粉丝分片条目视图对象。
 */
@Data
public class FollowerShardItemVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long followerId;
    private LocalDateTime createdAt;
}
