package com.zhicore.api.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 粉丝游标分页结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowerCursorPageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<FollowerCursorItemDTO> items;

    private LocalDateTime nextAfterCreatedAt;

    private Long nextAfterFollowerId;

    private boolean hasMore;
}
