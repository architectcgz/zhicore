package com.zhicore.comment.infrastructure.repository;

import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.model.CommentStats;
import com.zhicore.comment.domain.model.CommentStatus;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.comment.infrastructure.cursor.HotCursorCodec.HotCursor;
import com.zhicore.comment.infrastructure.cursor.TimeCursorCodec.TimeCursor;
import com.zhicore.comment.infrastructure.repository.mapper.CommentMapper;
import com.zhicore.comment.infrastructure.repository.mapper.CommentStatsMapper;
import com.zhicore.comment.infrastructure.repository.po.CommentPO;
import com.zhicore.comment.infrastructure.repository.po.CommentStatsPO;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评论仓储实现
 *
 * @author ZhiCore Team
 */
@Repository
@RequiredArgsConstructor
public class CommentRepositoryImpl implements CommentRepository {

    private final CommentMapper commentMapper;
    private final CommentStatsMapper statsMapper;

    @Override
    public Optional<Comment> findById(Long id) {
        CommentPO po = commentMapper.selectById(id);
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(po));
    }

    @Override
    public List<Comment> findByIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<CommentPO> poList = commentMapper.selectBatchIds(ids);
        return poList.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void save(Comment comment) {
        CommentPO po = toPO(comment);
        commentMapper.insert(po);
    }

    @Override
    public void update(Comment comment) {
        CommentPO po = toPO(comment);
        commentMapper.updateById(po);
    }

    @Override
    public void delete(Long id) {
        commentMapper.deleteById(id);
    }

    // ========== 顶级评论 - 传统分页 ==========

    @Override
    public PageResult<Comment> findTopLevelByPostIdOrderByTimePage(Long postId, int page, int size) {
        int offset = page * size;
        List<CommentPO> poList = commentMapper.findTopLevelByPostIdOrderByTimeOffset(postId, offset, size);
        long total = commentMapper.countTopLevelByPostId(postId);

        List<Comment> comments = poList.stream().map(this::toDomain).collect(Collectors.toList());
        return PageResult.of(page, size, total, comments);
    }

    @Override
    public PageResult<Comment> findTopLevelByPostIdOrderByLikesPage(Long postId, int page, int size) {
        int offset = page * size;
        List<CommentPO> poList = commentMapper.findTopLevelByPostIdOrderByLikesOffset(postId, offset, size);
        long total = commentMapper.countTopLevelByPostId(postId);

        List<Comment> comments = poList.stream().map(this::toDomain).collect(Collectors.toList());
        return PageResult.of(page, size, total, comments);
    }

    // ========== 顶级评论 - 游标分页 ==========

    @Override
    public List<Comment> findTopLevelByPostIdOrderByTimeCursor(Long postId, TimeCursor cursor, int size) {
        LocalDateTime cursorTime = cursor != null ? cursor.timestamp() : null;
        Long cursorId = cursor != null ? cursor.commentId() : null;

        List<CommentPO> poList = commentMapper.findTopLevelByPostIdOrderByTimeCursor(
                postId, cursorTime, cursorId, size
        );

        return poList.stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Comment> findTopLevelByPostIdOrderByLikesCursor(Long postId, HotCursor cursor, int size) {
        Integer cursorLikeCount = cursor != null ? cursor.likeCount() : null;
        Long cursorId = cursor != null ? cursor.commentId() : null;

        List<CommentPO> poList = commentMapper.findTopLevelByPostIdOrderByLikesCursor(
                postId, cursorLikeCount, cursorId, size
        );

        return poList.stream().map(this::toDomain).collect(Collectors.toList());
    }

    // ========== 回复列表 ==========

    @Override
    public PageResult<Comment> findRepliesByRootIdPage(Long rootId, int page, int size) {
        int offset = page * size;
        List<CommentPO> poList = commentMapper.findRepliesByRootIdOffset(rootId, offset, size);
        long total = commentMapper.countRepliesByRootId(rootId);

        List<Comment> replies = poList.stream().map(this::toDomain).collect(Collectors.toList());
        return PageResult.of(page, size, total, replies);
    }

    @Override
    public List<Comment> findRepliesByRootIdCursor(Long rootId, TimeCursor cursor, int size) {
        LocalDateTime cursorTime = cursor != null ? cursor.timestamp() : null;
        Long cursorId = cursor != null ? cursor.commentId() : null;

        List<CommentPO> poList = commentMapper.findRepliesByRootIdCursor(
                rootId, cursorTime, cursorId, size
        );

        return poList.stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Comment> findHotRepliesByRootId(Long rootId, int limit) {
        List<CommentPO> poList = commentMapper.findHotRepliesByRootId(rootId, limit);
        return poList.stream().map(this::toDomain).collect(Collectors.toList());
    }

    // ========== 统计查询 ==========

    @Override
    public long countTopLevelByPostId(Long postId) {
        return commentMapper.countTopLevelByPostId(postId);
    }

    @Override
    public int countRepliesByRootId(Long rootId) {
        return (int) commentMapper.countRepliesByRootId(rootId);
    }

    @Override
    public Map<Long, CommentStats> batchGetStats(List<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return new HashMap<>();
        }

        List<CommentStatsPO> statsList = statsMapper.batchSelectByCommentIds(commentIds);
        Map<Long, CommentStats> statsMap = new HashMap<>();

        for (CommentStatsPO po : statsList) {
            statsMap.put(po.getCommentId(), new CommentStats(
                    po.getLikeCount() != null ? po.getLikeCount() : 0,
                    po.getReplyCount() != null ? po.getReplyCount() : 0
            ));
        }

        // 对于没有统计记录的评论，返回空统计
        for (Long commentId : commentIds) {
            statsMap.putIfAbsent(commentId, CommentStats.empty());
        }

        return statsMap;
    }

    // ========== 转换方法 ==========

    private Comment toDomain(CommentPO po) {
        CommentStats stats = new CommentStats(
                po.getLikeCount() != null ? po.getLikeCount() : 0,
                po.getReplyCount() != null ? po.getReplyCount() : 0
        );

        return Comment.reconstitute(
                po.getId(),
                po.getPostId(),
                po.getAuthorId(),
                po.getContent(),
                po.getImageIds(),
                po.getVoiceId(),
                po.getVoiceDuration(),
                po.getParentId(),
                po.getRootId(),
                po.getReplyToUserId(),
                CommentStatus.fromCode(po.getStatus()),
                DateTimeUtils.toLocalDateTime(po.getCreatedAt()),
                DateTimeUtils.toLocalDateTime(po.getUpdatedAt()),
                stats
        );
    }

    private CommentPO toPO(Comment comment) {
        CommentPO po = new CommentPO();
        po.setId(comment.getId());
        po.setPostId(comment.getPostId());
        po.setAuthorId(comment.getAuthorId());
        po.setParentId(comment.getParentId());
        po.setRootId(comment.getRootId());
        po.setReplyToUserId(comment.getReplyToUserId());
        po.setContent(comment.getContent());
        po.setImageIds(comment.getImageIds());
        po.setVoiceId(comment.getVoiceId());
        po.setVoiceDuration(comment.getVoiceDuration());
        po.setStatus(comment.getStatus().getCode());
        po.setCreatedAt(DateTimeUtils.toOffsetDateTime(comment.getCreatedAt()));
        po.setUpdatedAt(DateTimeUtils.toOffsetDateTime(comment.getUpdatedAt()));
        return po;
    }

    // ========== 管理员查询 ==========

    @Override
    public List<Comment> findByConditions(String keyword, Long postId, Long userId, int offset, int limit) {
        List<CommentPO> poList = commentMapper.selectByConditions(keyword, postId, userId, offset, limit);
        return poList.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long countByConditions(String keyword, Long postId, Long userId) {
        return commentMapper.countByConditions(keyword, postId, userId);
    }
}
