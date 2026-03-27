package com.zhicore.api.dto.user;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 粉丝游标分页单项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowerCursorItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long followerId;

    private LocalDateTime followedAt;
}
