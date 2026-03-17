package com.zhicore.search.application.service.command;

import com.zhicore.search.domain.model.PostDocument;
import com.zhicore.search.domain.repository.PostSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 搜索索引写服务。
 *
 * 负责 ES 索引的创建、局部更新与删除，不承载查询职责。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexCommandService {

    private final PostSearchRepository postSearchRepository;

    public void indexPost(PostDocument document) {
        log.info("Indexing post: id={}", document.getId());
        postSearchRepository.index(document);
    }

    public void updatePostIndex(String postId, String title, String content, String excerpt) {
        log.info("Updating post index: id={}", postId);
        postSearchRepository.partialUpdate(postId, title, content, excerpt);
    }

    public void deletePostIndex(String postId) {
        log.info("Deleting post index: id={}", postId);
        postSearchRepository.delete(postId);
    }
}
