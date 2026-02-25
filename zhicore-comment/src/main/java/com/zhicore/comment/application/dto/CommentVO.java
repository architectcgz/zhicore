package com.zhicore.comment.application.dto;

import com.zhicore.api.dto.user.UserSimpleDTO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论视图对象
 *
 * @author ZhiCore Team
 */
@Data
@Builder
public class CommentVO {

    /**
     * 评论ID
     */
    private Long id;

    /**
     * 文章ID
     */
    private Long postId;

    /**
     * 根评论ID（顶级评论为null）
     */
    private Long rootId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 评论图片文件ID数组
     */
    private String[] imageIds;

    /**
     * 评论语音文件ID
     */
    private String voiceId;

    /**
     * 语音时长（秒）
     */
    private Integer voiceDuration;

    /**
     * 作者信息
     */
    private UserSimpleDTO author;

    /**
     * 被回复用户信息（顶级评论为null）
     */
    private UserSimpleDTO replyToUser;

    /**
     * 点赞数
     */
    private int likeCount;

    /**
     * 回复数（仅顶级评论有值）
     */
    private int replyCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 当前用户是否已点赞
     */
    private boolean liked;

    /**
     * 热门回复（预加载，仅顶级评论有值）
     */
    private List<CommentVO> hotReplies;
}
