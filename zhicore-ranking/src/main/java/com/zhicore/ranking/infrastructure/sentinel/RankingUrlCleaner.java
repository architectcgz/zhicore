package com.zhicore.ranking.infrastructure.sentinel;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.UrlCleaner;
import org.springframework.stereotype.Component;

/**
 * 排行榜 URL 归一化
 *
 * <p>将带路径变量的请求 URL 归一化为模式路径，避免 Sentinel 为每个不同 ID
 * 注册独立资源导致资源数膨胀，同时确保限流规则能匹配到这些接口。</p>
 *
 * <p>示例：{@code /api/v1/ranking/posts/abc123/rank → /api/v1/ranking/posts/:id/rank}</p>
 */
@Component
public class RankingUrlCleaner implements UrlCleaner {

    private static final String PREFIX = RankingRoutes.PREFIX;

    @Override
    public String clean(String originUrl) {
        if (originUrl == null || !originUrl.startsWith(PREFIX)) {
            return originUrl;
        }

        // /api/v1/ranking/posts/{postId}/rank 或 /score
        if (originUrl.startsWith(PREFIX + "/posts/") && !originUrl.contains("/hot")
                && !originUrl.contains("/daily") && !originUrl.contains("/weekly")
                && !originUrl.contains("/monthly")) {
            return normalizeTrailingSegment(originUrl, PREFIX + "/posts/", RankingRoutes.POSTS_ID);
        }

        // /api/v1/ranking/creators/{userId}/rank 或 /score
        if (originUrl.startsWith(PREFIX + "/creators/") && !originUrl.contains("/hot")) {
            return normalizeTrailingSegment(originUrl, PREFIX + "/creators/", RankingRoutes.CREATORS_ID);
        }

        // /api/v1/ranking/topics/{topicId}/rank 或 /score
        if (originUrl.startsWith(PREFIX + "/topics/") && !originUrl.contains("/hot")) {
            return normalizeTrailingSegment(originUrl, PREFIX + "/topics/", RankingRoutes.TOPICS_ID);
        }

        return originUrl;
    }

    /**
     * 将 prefix + {id} + /suffix 归一化为 idPrefix + /suffix
     */
    private String normalizeTrailingSegment(String url, String segmentPrefix, String idPrefix) {
        String remainder = url.substring(segmentPrefix.length());
        int slashIdx = remainder.indexOf('/');
        if (slashIdx > 0) {
            String suffix = remainder.substring(slashIdx);
            return idPrefix + suffix;
        }
        return url;
    }
}
