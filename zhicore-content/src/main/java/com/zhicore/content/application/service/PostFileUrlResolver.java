package com.zhicore.content.application.service;

import com.zhicore.content.application.port.client.FileResourceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 文章相关文件 URL 解析器。
 *
 * 收口封面图、头像等文件地址查询逻辑，
 * 避免多个 query service 反复依赖上传服务细节。
 */
@Component
@RequiredArgsConstructor
public class PostFileUrlResolver {

    private final FileResourceClient fileResourceClient;

    public String resolve(String fileId) {
        return fileResourceClient.getFileUrl(fileId).orElse(null);
    }
}
