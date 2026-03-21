package com.zhicore.comment.application.port.store;

import com.zhicore.comment.application.dto.CommentSortType;
import com.zhicore.comment.application.dto.CommentVO;
import com.zhicore.common.result.PageResult;

import java.time.Duration;
import java.util.Optional;

/**
 * 首页评论快照缓存存储。
 */
public interface CommentHomepageCacheStore {

    Optional<PageResult<CommentVO>> get(Long postId, CommentSortType sortType, int size, int hotRepliesLimit);

    void set(Long postId,
             CommentSortType sortType,
             int size,
             int hotRepliesLimit,
             PageResult<CommentVO> snapshot,
             Duration ttl);
}
