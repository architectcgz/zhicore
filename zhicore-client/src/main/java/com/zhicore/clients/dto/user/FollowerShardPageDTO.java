package com.zhicore.api.dto.user;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 粉丝分片页 DTO。
 */
@Data
public class FollowerShardPageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<FollowerShardItemDTO> items = List.of();

    @JsonSerialize(using = ToStringSerializer.class)
    private Long nextCursorFollowerId;
}
