package com.zhicore.content.application.util;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正文图片 URL 提取工具。
 *
 * <p>支持：
 * <ul>
 *   <li>HTML: {@code <img src="...">}</li>
 *   <li>Markdown: {@code ![](...)} / {@code ![](<...>)}</li>
 * </ul>
 *
 * <p>注意：仅负责提取 URL，不负责判断是否为自有存储，也不负责删除。
 */
public final class ContentImageExtractor {

    private ContentImageExtractor() {
    }

    private static final Pattern HTML_IMG_SRC = Pattern.compile(
            "(?i)<img\\b[^>]*\\bsrc\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)'|([^\\s>]+))[^>]*>"
    );

    private static final Pattern MARKDOWN_IMG = Pattern.compile(
            "!\\[[^\\]]*]\\(\\s*<?([^\\s)>]+)>?(?:\\s+\"[^\"]*\")?\\s*\\)"
    );

    public static Set<String> extractImageUrls(String content, String contentType) {
        Set<String> urls = new LinkedHashSet<>();
        if (content == null || content.isBlank()) {
            return urls;
        }

        // 容错：即使指定了 contentType，也尽量解析两种格式（不少编辑器会混用）
        // 注意：当前实现不依赖 contentType，参数仅保留以便未来做更精细的分支处理。
        String ignored = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        extractByPattern(urls, content, HTML_IMG_SRC);
        extractByPattern(urls, content, MARKDOWN_IMG);

        return urls;
    }

    private static void extractByPattern(Set<String> urls, String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String url = null;
            for (int i = 1; i <= matcher.groupCount(); i++) {
                url = matcher.group(i);
                if (url != null && !url.isBlank()) {
                    break;
                }
            }
            if (url == null) {
                continue;
            }
            String trimmed = url.trim();
            if (!trimmed.isEmpty()) {
                urls.add(trimmed);
            }
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
