package com.zhicore.api.dto.user;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 粉丝分片条目 DTO。
 */
@Data
public class FollowerShardItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long followerId;
    private OffsetDateTime createdAt;
}
