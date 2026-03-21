package com.zhicore.comment.application.service.query;

import com.zhicore.api.client.UserSimpleBatchClient;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.comment.application.dto.CommentVO;
import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.model.CommentStats;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.common.result.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评论视图组装器。
 */
@Slf4j
@Service
public class CommentViewAssembler {

    private final CommentRepository commentRepository;
    private final UserSimpleBatchClient userServiceClient;
    private final int hotRepliesLimit;

    public CommentViewAssembler(CommentRepository commentRepository,
                                UserSimpleBatchClient userServiceClient,
                                @Value("${comment.hot-replies-limit:3}") int hotRepliesLimit) {
        this.commentRepository = commentRepository;
        this.userServiceClient = userServiceClient;
        this.hotRepliesLimit = hotRepliesLimit;
    }

    public int getHotRepliesLimit() {
        return hotRepliesLimit;
    }

    public CommentVO assembleCommentVO(Comment comment) {
        UserSimpleDTO author = fetchUserOrNull(comment.getAuthorId());

        UserSimpleDTO replyToUser = null;
        if (comment.getReplyToUserId() != null) {
            replyToUser = fetchUserOrNull(comment.getReplyToUserId());
        }

        return CommentVO.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .rootId(comment.isTopLevel() ? null : comment.getRootId())
                .content(comment.getContent())
                .imageIds(comment.getImageIds())
                .voiceId(comment.getVoiceId())
                .voiceDuration(comment.getVoiceDuration())
                .author(author)
                .replyToUser(replyToUser)
                .likeCount(comment.getStats().getLikeCount())
                .replyCount(comment.getStats().getReplyCount())
                .createdAt(comment.getCreatedAt())
                .liked(false)
                .build();
    }

    public List<CommentVO> assembleCommentVOList(List<Comment> comments) {
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> authorIds = comments.stream()
                .map(Comment::getAuthorId)
                .collect(Collectors.toSet());
        Map<Long, UserSimpleDTO> userMap = batchGetUsers(authorIds);

        List<Long> commentIds = comments.stream().map(Comment::getId).toList();
        Map<Long, List<CommentVO>> hotRepliesMap = preloadHotReplies(commentIds);

        return comments.stream()
                .map(comment -> {
                    CommentStats stats = comment.getStats() != null ? comment.getStats() : CommentStats.empty();
                    CommentVO vo = CommentVO.builder()
                            .id(comment.getId())
                            .postId(comment.getPostId())
                            .content(comment.getContent())
                            .imageIds(comment.getImageIds())
                            .voiceId(comment.getVoiceId())
                            .voiceDuration(comment.getVoiceDuration())
                            .author(userMap.get(comment.getAuthorId()))
                            .likeCount(stats.getLikeCount())
                            .replyCount(stats.getReplyCount())
                            .createdAt(comment.getCreatedAt())
                            .liked(false)
                            .build();
                    vo.setHotReplies(hotRepliesMap.getOrDefault(comment.getId(), Collections.emptyList()));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    public List<CommentVO> assembleReplyVOList(List<Comment> replies) {
        if (replies.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> userIds = new HashSet<>();
        replies.forEach(reply -> {
            userIds.add(reply.getAuthorId());
            if (reply.getReplyToUserId() != null) {
                userIds.add(reply.getReplyToUserId());
            }
        });
        Map<Long, UserSimpleDTO> userMap = batchGetUsers(userIds);

        return replies.stream()
                .map(reply -> toReplyVO(reply, userMap))
                .collect(Collectors.toList());
    }

    private Map<Long, List<CommentVO>> preloadHotReplies(List<Long> rootIds) {
        if (rootIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Comment> hotReplies = commentRepository.findHotRepliesByRootIds(rootIds, hotRepliesLimit);
        if (hotReplies.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> userIds = new HashSet<>();
        hotReplies.forEach(reply -> {
            userIds.add(reply.getAuthorId());
            if (reply.getReplyToUserId() != null) {
                userIds.add(reply.getReplyToUserId());
            }
        });
        Map<Long, UserSimpleDTO> userMap = batchGetUsers(userIds);

        Map<Long, List<CommentVO>> result = new HashMap<>();
        for (Comment reply : hotReplies) {
            result.computeIfAbsent(reply.getRootId(), ignored -> new ArrayList<>())
                    .add(toReplyVO(reply, userMap));
        }
        return result;
    }

    private CommentVO toReplyVO(Comment reply, Map<Long, UserSimpleDTO> userMap) {
        CommentStats stats = reply.getStats() != null ? reply.getStats() : CommentStats.empty();
        return CommentVO.builder()
                .id(reply.getId())
                .postId(reply.getPostId())
                .rootId(reply.getRootId())
                .content(reply.getContent())
                .imageIds(reply.getImageIds())
                .voiceId(reply.getVoiceId())
                .voiceDuration(reply.getVoiceDuration())
                .author(userMap.get(reply.getAuthorId()))
                .replyToUser(reply.getReplyToUserId() != null ? userMap.get(reply.getReplyToUserId()) : null)
                .likeCount(stats.getLikeCount())
                .createdAt(reply.getCreatedAt())
                .liked(false)
                .build();
    }

    private Map<Long, UserSimpleDTO> batchGetUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            ApiResponse<Map<Long, UserSimpleDTO>> response = userServiceClient.batchGetUsersSimple(userIds);
            return response != null && response.isSuccess() && response.getData() != null
                    ? response.getData()
                    : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("批量获取评论用户信息失败: userIds={}, error={}", userIds, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private UserSimpleDTO fetchUserOrNull(Long userId) {
        try {
            ApiResponse<UserSimpleDTO> response = userServiceClient.getUserSimple(userId);
            return response != null && response.isSuccess() ? response.getData() : null;
        } catch (Exception e) {
            log.warn("获取评论用户信息失败: userId={}, error={}", userId, e.getMessage());
            return null;
        }
    }
}
