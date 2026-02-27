package com.zhicore.ranking.infrastructure.sentinel;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.UrlCleaner;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 排行榜 URL 归一化
 *
 * <p>将带路径变量的请求 URL 归一化为模式路径，避免 Sentinel 为每个不同 ID
 * 注册独立资源导致资源数膨胀，同时确保限流规则能匹配到这些接口。</p>
 *
 * <p>示例：{@code /api/v1/ranking/posts/abc123/rank → /api/v1/ranking/posts/:id/rank}</p>
 *
 * <p><b>注意：Sentinel UrlCleaner 是全局单例</b>，Spring 容器中只能注册一个实现。
 * 当前仅处理 ranking 模块的 URL，对非 ranking 前缀直接返回原值。
 * 如果后续其他模块也需要 URL 归一化，需重构为全局 UrlCleaner + 模块级 delegate 模式。</p>
 */
@Component
public class RankingUrlCleaner implements UrlCleaner {

    private static final String PREFIX = RankingRoutes.PREFIX;

    /** 需要归一化的尾部路径段（ID 后面的段） */
    private static final Set<String> ID_SUFFIXES = Set.of("rank", "score");

    @Override
    public String clean(String originUrl) {
        if (originUrl == null || !originUrl.startsWith(PREFIX)) {
            return originUrl;
        }

        // 提取 PREFIX 之后的路径：如 /posts/abc123/rank
        String path = originUrl.substring(PREFIX.length());
        String[] segments = path.split("/");
        // segments: ["", "posts", "{id}", "rank"] — 期望 4 段（首段为空）
        if (segments.length != 4) {
            return originUrl;
        }

        String category = segments[1];  // posts / creators / topics
        String suffix = segments[3];    // rank / score

        if (!ID_SUFFIXES.contains(suffix)) {
            return originUrl;
        }

        // 根据类别归一化
        return switch (category) {
            case "posts" -> RankingRoutes.POSTS_ID + "/" + suffix;
            case "creators" -> RankingRoutes.CREATORS_ID + "/" + suffix;
            case "topics" -> RankingRoutes.TOPICS_ID + "/" + suffix;
            default -> originUrl;
        };
    }
}
