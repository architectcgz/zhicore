package com.blog.search.infrastructure.elasticsearch;

import com.blog.search.domain.repository.PostSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch 索引初始化器
 * 
 * 在应用启动时自动创建索引
 *
 * @author Blog Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexInitializer implements ApplicationRunner {

    private final PostSearchRepository postSearchRepository;

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("Initializing Elasticsearch indexes...");
            postSearchRepository.initIndex();
            log.info("Elasticsearch indexes initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Elasticsearch indexes", e);
            // 不抛出异常，允许应用继续启动
        }
    }
}
