package com.zhicore.content.application.service;

import com.zhicore.content.application.port.alert.ContentAlertPort;
import com.zhicore.content.application.port.client.FileResourceClient;
import com.zhicore.content.application.port.store.PostContentStore;
import com.zhicore.content.application.util.ContentImageExtractor;
import com.zhicore.content.domain.model.PostBody;
import com.zhicore.content.domain.model.PostId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 删除文章时异步清理正文内图片资源（TASK-02）。
 *
 * <p>清理为 best-effort：失败记录日志 + 告警，不阻塞删除主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostContentImageCleanupService {

    private static final Pattern UUID_V7 = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern UUID_V7_IN_TEXT = Pattern.compile(
            "([0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})",
            Pattern.CASE_INSENSITIVE
    );

    private final PostContentStore postContentStore;
    private final FileResourceClient fileResourceClient;
    private final ContentAlertPort alertService;

    private static final String[] RELATIVE_ALLOWED_PREFIXES = new String[]{
            "/api/v1/upload/",
            "/api/v1/files/"
    };

    @Value("${file-service.client.server-url:}")
    private String fileServiceServerUrl;

    @Value("${file-service.client.custom-domain:}")
    private String fileServiceCustomDomain;

    @Value("${file-service.client.cdn-domain:}")
    private String fileServiceCdnDomain;

    @Async
    public void cleanupContentImagesAsync(Long postId) {
        if (postId == null) {
            return;
        }

        PostBody body;
        try {
            body = postContentStore.loadContent(PostId.of(postId)).orElse(null);
        } catch (Exception e) {
            log.warn("Failed to load post content for image cleanup: postId={}", postId, e);
            alertService.alertContentImageCleanupFailed(postId, "mongo:post_contents", e.getMessage());
            return;
        }

        if (body == null || body.getContent() == null || body.getContent().isBlank()) {
            return;
        }

        Set<String> imageUrls = ContentImageExtractor.extractImageUrls(body.getContent(), body.getContentTypeValue());
        if (imageUrls.isEmpty()) {
            return;
        }

        Set<String> allowedHosts = buildAllowedHosts();

        for (String imageUrl : imageUrls) {
            if (imageUrl == null || imageUrl.isBlank()) {
                continue;
            }
            if (!isSelfStorageImageUrl(imageUrl, allowedHosts)) {
                continue;
            }

            String fileId = extractUuidV7(imageUrl).orElse(null);
            if (fileId == null || !UUID_V7.matcher(fileId).matches()) {
                log.debug("Skip content image cleanup (no valid fileId found): postId={}, url={}", postId, imageUrl);
                continue;
            }

            try {
                FileResourceClient.DeleteResult result = fileResourceClient.deleteFile(fileId);
                if (!result.success()) {
                    String errorMessage = result.message() != null ? result.message() : "delete failed";
                    log.warn("Content image cleanup failed: postId={}, fileId={}, url={}, error={}",
                            postId, fileId, imageUrl, errorMessage);
                    alertService.alertContentImageCleanupFailed(postId, imageUrl, errorMessage);
                } else {
                    log.info("Content image deleted: postId={}, fileId={}, url={}", postId, fileId, imageUrl);
                }
            } catch (Exception e) {
                log.warn("Content image cleanup exception: postId={}, fileId={}, url={}", postId, fileId, imageUrl, e);
                alertService.alertContentImageCleanupFailed(postId, imageUrl, e.getMessage());
            }
        }
    }

    private Set<String> buildAllowedHosts() {
        Set<String> hosts = new LinkedHashSet<>();
        addHost(hosts, fileServiceServerUrl);
        addHost(hosts, fileServiceCustomDomain);
        addHost(hosts, fileServiceCdnDomain);
        return hosts;
    }

    private void addHost(Set<String> hosts, String urlOrHost) {
        if (urlOrHost == null || urlOrHost.isBlank()) {
            return;
        }

        String trimmed = urlOrHost.trim();
        try {
            URI uri = URI.create(trimmed.contains("://") ? trimmed : ("http://" + trimmed));
            if (uri.getHost() != null && !uri.getHost().isBlank()) {
                hosts.add(uri.getHost().toLowerCase());
            }
        } catch (Exception ignored) {
            // best-effort：解析失败则不加入白名单
        }
    }

    private boolean isSelfStorageImageUrl(String url, Set<String> allowedHosts) {
        String trimmed = url.trim();

        // 相对路径：仅允许明确的上传/文件访问路径，避免误删第三方或业务自定义路径中的 UUID
        if (trimmed.startsWith("/")) {
            for (String prefix : RELATIVE_ALLOWED_PREFIXES) {
                if (trimmed.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }

        try {
            URI uri = URI.create(trimmed);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            String lowerHost = host.toLowerCase();

            // 绝对 URL 必须命中域名白名单，避免仅凭路径 contains 误判外链为“自有存储”
            return allowedHosts != null && allowedHosts.contains(lowerHost);
        } catch (Exception e) {
            return false;
        }
    }

    private Optional<String> extractUuidV7(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = UUID_V7_IN_TEXT.matcher(text);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group(1));
        }
        return Optional.empty();
    }
}
