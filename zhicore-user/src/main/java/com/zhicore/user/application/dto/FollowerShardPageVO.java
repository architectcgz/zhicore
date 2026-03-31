package com.zhicore.user.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

/**
 * 粉丝分片分页视图对象。
 */
@Data
public class FollowerShardPageVO {

    private List<FollowerShardItemVO> items = List.of();

    @JsonSerialize(using = ToStringSerializer.class)
    private Long nextCursorFollowerId;
}
