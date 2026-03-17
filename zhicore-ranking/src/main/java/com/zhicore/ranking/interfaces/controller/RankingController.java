package com.zhicore.ranking.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.ForbiddenException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.util.IsoWeekUtils;
import com.zhicore.ranking.application.dto.HotPostDTO;
import com.zhicore.ranking.application.dto.RankingReplayResultDTO;
import com.zhicore.ranking.application.service.query.CreatorRankingQueryService;
import com.zhicore.ranking.application.service.HotPostDetailService;
import com.zhicore.ranking.application.service.RankingLedgerReplayService;
import com.zhicore.ranking.application.service.query.PostRankingQueryService;
import com.zhicore.ranking.application.service.query.TopicRankingQueryService;
import com.zhicore.ranking.domain.model.HotScore;
import com.zhicore.ranking.infrastructure.config.RankingProperties;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import com.zhicore.ranking.infrastructure.sentinel.RankingRoutes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 排行榜控制器
 *
 * @author ZhiCore Team
 */
@Tag(name = "排行榜管理", description = "文章、创作者、话题的热度排行相关接口")
@RestController
@RequestMapping(RankingRoutes.PREFIX)
@RequiredArgsConstructor
public class RankingController {

    private final PostRankingQueryService postRankingService;
    private final HotPostDetailService hotPostDetailService;
    private final CreatorRankingQueryService creatorRankingService;
    private final TopicRankingQueryService topicRankingService;
    private final RankingLedgerReplayService rankingLedgerReplayService;
    private final RankingProperties rankingProperties;

    // ==================== 文章排行榜 ====================

    /**
     * 获取热门文章排行（总榜）
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 文章ID列表
     */
    @Operation(
            summary = "获取热门文章排行（总榜）",
            description = "获取全站热门文章排行榜，按热度分数降序排列"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/posts/hot")
    public ApiResponse<List<String>> getHotPosts(
            @Parameter(description = "页码（从0开始）", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小（最大100）", example = "20")
            @RequestParam(required = false) Integer size) {
        if (size == null) {
            size = rankingProperties.getDefaultSize();
        }
        size = Math.min(size, rankingProperties.getMaxSize());
        List<String> postIds = postRankingService.getHotPosts(page, size);
        return ApiResponse.success(postIds);
    }

    /**
     * 获取热门文章排行（总榜）- 包含文章详情
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 热门文章列表（包含详情）
     */
    @Operation(
            summary = "获取热门文章排行（总榜）- 包含文章详情",
            description = "获取全站热门文章排行榜，包含文章的关键信息（标题、摘要、作者等）和热度数据"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/posts/hot/details")
    public ApiResponse<List<HotPostDTO>> getHotPostsWithDetails(
            @Parameter(description = "页码（从0开始）", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小（最大100）", example = "20")
            @RequestParam(required = false) Integer size) {
        if (size == null) {
            size = rankingProperties.getDefaultSize();
        }
        size = Math.min(size, rankingProperties.getMaxSize());
        List<HotPostDTO> posts = hotPostDetailService.getHotPostsWithDetails(page, size);
        return ApiResponse.success(posts);
    }

    /**
     * 获取热门文章排行带分数（总榜）
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 热度分数列表
     */
    @Operation(
            summary = "获取热门文章排行带分数（总榜）",
            description = "获取全站热门文章排行榜，包含热度分数和排名信息"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/posts/hot/scores")
    public ApiResponse<List<HotScore>> getHotPostsWithScore(
            @Parameter(description = "页码（从0开始）", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小（最大100）", example = "20")
            @RequestParam(required = false) Integer size) {
        if (size == null) {
            size = rankingProperties.getDefaultSize();
        }
        size = Math.min(size, rankingProperties.getMaxSize());
        List<HotScore> scores = postRankingService.getHotPostsWithScore(page, size);
        return ApiResponse.success(scores);
    }

    /**
     * 获取热门文章排行（日榜）
     *
     * @param date 日期（格式：yyyy-MM-dd），默认今天
     * @param limit 数量限制
     * @return 文章ID列表
     */
    @Operation(
            summary = "获取热门文章排行（日榜）",
            description = "获取指定日期的热门文章排行榜，默认为今天"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/posts/daily")
    public ApiResponse<List<String>> getDailyHotPosts(
            @Parameter(description = "日期（格式：yyyy-MM-dd），默认今天", example = "2024-01-28")
            @RequestParam(required = false) LocalDate date,
            @Parameter(description = "数量限制（最大100）", example = "20")
            @RequestParam(required = false) Integer limit) {
        if (date == null) {
            date = LocalDate.now();
        }
        if (limit == null) {
            limit = rankingProperties.getDefaultSize();
        }
        limit = Math.min(limit, rankingProperties.getMaxSize());
        List<String> postIds = postRankingService.getDailyHotPosts(date, limit);
        return ApiResponse.success(postIds);
    }

    /**
     * 获取热门文章排行（周榜）
     *
     * @param week 周数，默认本周
     * @param limit 数量限制
     * @return 文章ID列表
     */
    @Operation(
            summary = "获取热门文章排行（周榜）",
            description = "获取指定周的热门文章排行榜，默认为本周"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/posts/weekly")
    public ApiResponse<List<String>> getWeeklyHotPosts(
            @Parameter(description = "ISO week-based year，默认本周所属年份", example = "2026")
            @RequestParam(required = false) Integer year,
            @Parameter(description = "周数，默认本周", example = "1")
            @RequestParam(required = false) Integer week,
            @Parameter(description = "数量限制（最大100）", example = "20")
            @RequestParam(required = false) Integer limit) {
        if (limit == null) {
            limit = rankingProperties.getDefaultSize();
        }
        limit = Math.min(limit, rankingProperties.getMaxSize());
        LocalDate now = LocalDate.now();
        int currentWeekBasedYear = RankingRedisKeys.getWeekBasedYear(now);
        if (year == null) {
            year = currentWeekBasedYear;
        }
        if (week == null) {
            week = RankingRedisKeys.getWeekNumber(now);
        }
        if (year < 2020 || year > currentWeekBasedYear + 1) {
            return ApiResponse.fail(400, "年份参数无效，必须在2020到明年之间");
        }
        if (!IsoWeekUtils.isValidWeek(year, week)) {
            return ApiResponse.fail(400, "周参数无效，该年份不存在这一周");
        }
        List<String> postIds = postRankingService.getWeeklyHotPosts(year, week, limit);
        return ApiResponse.success(postIds);
    }

    /**
     * 获取热门文章排行带分数（日榜）
     *
     * @param date 日期（格式：yyyy-MM-dd），默认今天
     * @param limit 数量限制
     * @return 热度分数列表
     */
    @Operation(
            summary = "获取热门文章排行带分数（日榜）",
            description = "获取指定日期的热门文章排行榜，包含热度分数和排名信息"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/posts/daily/scores")
    public ApiResponse<List<HotScore>> getDailyHotPostsWithScore(
            @Parameter(description = "日期（格式：yyyy-MM-dd），默认今天", example = "2024-01-28")
            @RequestParam(required = false) LocalDate date,
            @Parameter(description = "数量限制（最大100）", example = "20")
            @RequestParam(required = false) Integer limit) {
        if (date == null) {
            date = LocalDate.now();
        }
        if (limit == null) {
            limit = rankingProperties.getDefaultSize();
        }
        limit = Math.min(limit, rankingProperties.getMaxSize());
        List<HotScore> scores = postRankingService.getDailyHotPostsWithScore(date, limit);
        return ApiResponse.success(scores);
    }

    /**
     * 获取热门文章排行带分数（周榜）
     *
     * @param week 周数，默认本周
     * @param limit 数量限制
     * @return 热度分数列表
     */
    @Operation(
            summary = "获取热门文章排行带分数（周榜）",
            description = "获取指定周的热门文章排行榜，包含热度分数和排名信息"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/posts/weekly/scores")
    public ApiResponse<List<HotScore>> getWeeklyHotPostsWithScore(
            @Parameter(description = "ISO week-based year，默认本周所属年份", example = "2026")
            @RequestParam(required = false) Integer year,
            @Parameter(description = "周数，默认本周", example = "1")
            @RequestParam(required = false) Integer week,
            @Parameter(description = "数量限制（最大100）", example = "20")
            @RequestParam(required = false) Integer limit) {
        if (limit == null) {
            limit = rankingProperties.getDefaultSize();
        }
        limit = Math.min(limit, rankingProperties.getMaxSize());
        LocalDate now = LocalDate.now();
        int currentWeekBasedYear = RankingRedisKeys.getWeekBasedYear(now);
        if (year == null) {
            year = currentWeekBasedYear;
        }
        if (week == null) {
            week = RankingRedisKeys.getWeekNumber(now);
        }
        if (year < 2020 || year > currentWeekBasedYear + 1) {
            return ApiResponse.fail(400, "年份参数无效，必须在2020到明年之间");
        }
        if (!IsoWeekUtils.isValidWeek(year, week)) {
            return ApiResponse.fail(400, "周参数无效，该年份不存在这一周");
        }
        List<HotScore> scores = postRankingService.getWeeklyHotPostsWithScore(year, week, limit);
        return ApiResponse.success(scores);
    }

    /**
     * 获取热门文章排行（月榜）
     *
     * @param year 年份，默认当前年份
     * @param month 月份（1-12），默认当前月份
     * @param limit 数量限制
     * @return 文章ID列表
     */
    @Operation(
            summary = "获取热门文章排行（月榜）",
            description = "获取指定月份的热门文章排行榜，默认为当前月份"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/posts/monthly")
    public ApiResponse<List<String>> getMonthlyHotPosts(
            @Parameter(description = "年份，默认当前年份", example = "2024")
            @RequestParam(required = false) Integer year,
            @Parameter(description = "月份（1-12），默认当前月份", example = "1")
            @RequestParam(required = false) Integer month,
            @Parameter(description = "数量限制（最大100）", example = "20")
            @RequestParam(required = false) Integer limit) {
        LocalDate now = LocalDate.now();
        if (year == null) {
            year = now.getYear();
        }
        if (month == null) {
            month = now.getMonthValue();
        }
        if (limit == null) {
            limit = rankingProperties.getDefaultSize();
        }
        limit = Math.min(limit, rankingProperties.getMaxSize());
        
        // 参数验证
        if (year < 2020 || year > now.getYear() + 1) {
            return ApiResponse.fail(400, "年份参数无效，必须在2020到明年之间");
        }
        if (month < 1 || month > 12) {
            return ApiResponse.fail(400, "月份参数无效，必须在1-12之间");
        }
        
        List<String> postIds = postRankingService.getMonthlyHotPosts(year, month, limit);
        return ApiResponse.success(postIds);
    }

    /**
     * 获取热门文章排行带分数（月榜）
     *
     * @param year 年份，默认当前年份
     * @param month 月份（1-12），默认当前月份
     * @param limit 数量限制
     * @return 热度分数列表
     */
    @Operation(
            summary = "获取热门文章排行带分数（月榜）",
            description = "获取指定月份的热门文章排行榜，包含热度分数和排名信息"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/posts/monthly/scores")
    public ApiResponse<List<HotScore>> getMonthlyHotPostsWithScore(
            @Parameter(description = "年份，默认当前年份", example = "2024")
            @RequestParam(required = false) Integer year,
            @Parameter(description = "月份（1-12），默认当前月份", example = "1")
            @RequestParam(required = false) Integer month,
            @Parameter(description = "数量限制（最大100）", example = "20")
            @RequestParam(required = false) Integer limit) {
        LocalDate now = LocalDate.now();
        if (year == null) {
            year = now.getYear();
        }
        if (month == null) {
            month = now.getMonthValue();
        }
        if (limit == null) {
            limit = rankingProperties.getDefaultSize();
        }
        limit = Math.min(limit, rankingProperties.getMaxSize());
        
        // 参数验证
        if (year < 2020 || year > now.getYear() + 1) {
            return ApiResponse.fail(400, "年份参数无效，必须在2020到明年之间");
        }
        if (month < 1 || month > 12) {
            return ApiResponse.fail(400, "月份参数无效，必须在1-12之间");
        }
        
        List<HotScore> scores = postRankingService.getMonthlyHotPostsWithScore(year, month, limit);
        return ApiResponse.success(scores);
    }

    /**
     * 获取文章排名
     *
     * @param postId 文章ID
     * @return 排名（从1开始）
     */
    @Operation(
            summary = "获取文章排名",
            description = "获取指定文章在热门排行榜中的排名"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/posts/{postId}/rank")
    public ApiResponse<Long> getPostRank(
            @Parameter(description = "文章ID", example = "1234567890", required = true)
            @PathVariable String postId) {
        Long rank = postRankingService.getPostRank(postId);
        return ApiResponse.success(rank);
    }

    /**
     * 获取文章热度分数
     *
     * @param postId 文章ID
     * @return 热度分数
     */
    @Operation(
            summary = "获取文章热度分数",
            description = "获取指定文章的热度分数"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/posts/{postId}/score")
    public ApiResponse<Double> getPostScore(
            @Parameter(description = "文章ID", example = "1234567890", required = true)
            @PathVariable String postId) {
        Double score = postRankingService.getPostScore(postId);
        return ApiResponse.success(score);
    }

    // ==================== 创作者排行榜 ====================

    /**
     * 获取创作者排行
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 用户ID列表
     */
    @Operation(
            summary = "获取创作者排行",
            description = "获取全站创作者排行榜，按热度分数降序排列"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/creators/hot")
    public ApiResponse<List<String>> getHotCreators(
            @Parameter(description = "页码（从0开始）", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小（最大100）", example = "20")
            @RequestParam(required = false) Integer size) {
        if (size == null) {
            size = rankingProperties.getDefaultSize();
        }
        size = Math.min(size, rankingProperties.getMaxSize());
        List<String> userIds = creatorRankingService.getHotCreators(page, size);
        return ApiResponse.success(userIds);
    }

    /**
     * 获取创作者排行带分数
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 热度分数列表
     */
    @Operation(
            summary = "获取创作者排行带分数",
            description = "获取全站创作者排行榜，包含热度分数和排名信息"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/creators/hot/scores")
    public ApiResponse<List<HotScore>> getHotCreatorsWithScore(
            @Parameter(description = "页码（从0开始）", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小（最大100）", example = "20")
            @RequestParam(required = false) Integer size) {
        if (size == null) {
            size = rankingProperties.getDefaultSize();
        }
        size = Math.min(size, rankingProperties.getMaxSize());
        List<HotScore> scores = creatorRankingService.getHotCreatorsWithScore(page, size);
        return ApiResponse.success(scores);
    }

    /**
     * 获取创作者排名
     *
     * @param userId 用户ID
     * @return 排名（从1开始）
     */
    @Operation(
            summary = "获取创作者排名",
            description = "获取指定创作者在排行榜中的排名"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/creators/{userId}/rank")
    public ApiResponse<Long> getCreatorRank(
            @Parameter(description = "用户ID", example = "1234567890", required = true)
            @PathVariable String userId) {
        Long rank = creatorRankingService.getCreatorRank(userId);
        return ApiResponse.success(rank);
    }

    /**
     * 获取创作者热度分数
     *
     * @param userId 用户ID
     * @return 热度分数
     */
    @Operation(
            summary = "获取创作者热度分数",
            description = "获取指定创作者的热度分数"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/creators/{userId}/score")
    public ApiResponse<Double> getCreatorScore(
            @Parameter(description = "用户ID", example = "1234567890", required = true)
            @PathVariable String userId) {
        Double score = creatorRankingService.getCreatorScore(userId);
        return ApiResponse.success(score);
    }

    // ==================== 话题排行榜 ====================

    /**
     * 获取热门话题排行
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 话题ID列表
     */
    @Operation(
            summary = "获取热门话题排行",
            description = "获取全站热门话题排行榜，按热度分数降序排列"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/topics/hot")
    public ApiResponse<List<Long>> getHotTopics(
            @Parameter(description = "页码（从0开始）", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小（最大100）", example = "20")
            @RequestParam(required = false) Integer size) {
        if (size == null) {
            size = rankingProperties.getDefaultSize();
        }
        size = Math.min(size, rankingProperties.getMaxSize());
        List<Long> topicIds = topicRankingService.getHotTopics(page, size);
        return ApiResponse.success(topicIds);
    }

    /**
     * 获取热门话题排行带分数
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 热度分数列表
     */
    @Operation(
            summary = "获取热门话题排行带分数",
            description = "获取全站热门话题排行榜，包含热度分数和排名信息"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/topics/hot/scores")
    public ApiResponse<List<HotScore>> getHotTopicsWithScore(
            @Parameter(description = "页码（从0开始）", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小（最大100）", example = "20")
            @RequestParam(required = false) Integer size) {
        if (size == null) {
            size = rankingProperties.getDefaultSize();
        }
        size = Math.min(size, rankingProperties.getMaxSize());
        List<HotScore> scores = topicRankingService.getHotTopicsWithScore(page, size);
        return ApiResponse.success(scores);
    }

    /**
     * 获取话题排名
     *
     * @param topicId 话题ID
     * @return 排名（从1开始）
     */
    @Operation(
            summary = "获取话题排名",
            description = "获取指定话题在排行榜中的排名"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/topics/{topicId}/rank")
    public ApiResponse<Long> getTopicRank(
            @Parameter(description = "话题ID", example = "1", required = true)
            @PathVariable Long topicId) {
        Long rank = topicRankingService.getTopicRank(topicId);
        return ApiResponse.success(rank);
    }

    /**
     * 获取话题热度分数
     *
     * @param topicId 话题ID
     * @return 热度分数
     */
    @Operation(
            summary = "获取话题热度分数",
            description = "获取指定话题的热度分数"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/topics/{topicId}/score")
    public ApiResponse<Double> getTopicScore(
            @Parameter(description = "话题ID", example = "1", required = true)
            @PathVariable Long topicId) {
        Double score = topicRankingService.getTopicScore(topicId);
        return ApiResponse.success(score);
    }

    @Operation(
            summary = "管理接口：从 ledger 全量补算排行榜",
            description = "清空当前 ranking 的物化状态后，按 ranking_event_ledger 顺序重放，重建 post_state、period_score 和活跃 Redis 榜单"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "补算成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @PostMapping("/admin/rebuild-from-ledger")
    public ApiResponse<RankingReplayResultDTO> rebuildFromLedger() {
        requireAdminOperator();
        int replayedEvents = rankingLedgerReplayService.rebuildFromLedger();
        return ApiResponse.success(RankingReplayResultDTO.builder()
                .replayedEvents(replayedEvents)
                .rebuiltAt(LocalDateTime.now())
                .build());
    }

    private void requireAdminOperator() {
        UserContext.requireUserId();
        if (!UserContext.isAdmin()) {
            throw new ForbiddenException("仅管理员可执行排行榜补算");
        }
    }
}
