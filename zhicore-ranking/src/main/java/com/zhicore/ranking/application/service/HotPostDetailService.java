package com.zhicore.ranking.application.service;

import com.zhicore.api.client.PostServiceClient;
import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.ranking.application.dto.HotPostDTO;
import com.zhicore.ranking.domain.model.HotScore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 热门文章详情服务
 * 负责获取热门文章的详细信息
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotPostDetailService {

    private final PostRankingService postRankingService;
    private final PostServiceClient postServiceClient;

    /**
     * 获取热门文章列表（包含详细信息）
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 热门文章列表
     */
    public List<HotPostDTO> getHotPostsWithDetails(int page, int size) {
        // 步骤 1: 获取热门文章 ID 和分数
        List<HotScore> hotScores = postRankingService.getHotPostsWithScore(page, size);
        
        if (hotScores == null || hotScores.isEmpty()) {
            return new ArrayList<>();
        }

        // 步骤 2: 提取文章 ID 列表
        List<Long> postIds = hotScores.stream()
                .map(score -> Long.parseLong(score.getEntityId()))
                .collect(Collectors.toList());

        // 步骤 3: 批量获取文章详情
        ApiResponse<List<PostDTO>> response = postServiceClient.getPostsSimple(postIds);
        
        if (response == null || !response.isSuccess() || response.getData() == null) {
            log.warn("批量获取文章详情失败: postIds={}", postIds);
            return new ArrayList<>();
        }

        List<PostDTO> posts = response.getData();

        // 步骤 4: 创建 postId -> PostDTO 的映射
        Map<Long, PostDTO> postMap = posts.stream()
                .collect(Collectors.toMap(PostDTO::getId, post -> post));

        // 步骤 5: 组装 HotPostDTO 列表（保持排名顺序）
        List<HotPostDTO> result = new ArrayList<>();
        for (HotScore score : hotScores) {
            Long postId = Long.parseLong(score.getEntityId());
            PostDTO post = postMap.get(postId);
            
            if (post == null) {
                log.warn("文章不存在或已删除: postId={}", postId);
                continue;
            }

            HotPostDTO hotPost = convertToHotPostDTO(post, score);
            result.add(hotPost);
        }

        return result;
    }

    /**
     * 将 PostDTO 和 HotScore 转换为 HotPostDTO
     */
    private HotPostDTO convertToHotPostDTO(PostDTO post, HotScore score) {
        return HotPostDTO.builder()
                .id(post.getId().toString())
                .title(post.getTitle())
                .excerpt(post.getExcerpt())
                .coverImageUrl(post.getCoverImage())
                .ownerId(post.getOwnerId() != null ? post.getOwnerId().toString() : null)
                .ownerName(post.getAuthor() != null ? post.getAuthor().getNickname() : null)
                // avatarId 而非 avatarUrl：UserSimpleDTO 返回的是资源 ID，前端需拼接 CDN 地址
                .ownerAvatar(post.getAuthor() != null ? post.getAuthor().getAvatarId() : null)
                .topicId(post.getTags() != null && !post.getTags().isEmpty() 
                        ? post.getTags().get(0).getId() 
                        : null)
                .topicName(post.getTags() != null && !post.getTags().isEmpty() 
                        ? post.getTags().get(0).getName() 
                        : null)
                .publishedAt(post.getPublishedAt())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .favoriteCount(post.getFavoriteCount())
                .viewCount(post.getViewCount() != null ? post.getViewCount().longValue() : 0L)
                .hotScore(score.getScore())
                .rank(score.getRank())
                .build();
    }
}
