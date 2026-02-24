package com.zhicore.content.infrastructure.persistence.pg;

import com.zhicore.content.application.port.repo.PostStatsRepository;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStats;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PostStatsRepositoryImplIntegrationTest extends IntegrationTestBase {

    @Autowired
    private PostStatsRepository postStatsRepository;

    private PostId postId;

    @BeforeEach
    void setUp() {
        postId = PostId.of(System.currentTimeMillis());
    }

    @Test
    void upsertAndFindById_shouldWork() {
        PostStats stats = new PostStats(postId, 10, 2, 1, 0, LocalDateTime.now());
        postStatsRepository.upsert(postId, stats);

        Optional<PostStats> loaded = postStatsRepository.findById(postId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getViewCount()).isEqualTo(10);
        assertThat(loaded.get().getLikeCount()).isEqualTo(2);
    }

    @Test
    void upsertTwice_shouldOverwrite() {
        postStatsRepository.upsert(postId, new PostStats(postId, 1, 1, 1, 1, LocalDateTime.now()));
        postStatsRepository.upsert(postId, new PostStats(postId, 99, 8, 7, 6, LocalDateTime.now()));

        Optional<PostStats> loaded = postStatsRepository.findById(postId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getViewCount()).isEqualTo(99);
        assertThat(loaded.get().getShareCount()).isEqualTo(6);
    }

    @Test
    void findByIds_shouldReturnExistingOnly() {
        PostId id1 = PostId.of(System.currentTimeMillis() + 1);
        PostId id2 = PostId.of(System.currentTimeMillis() + 2);
        PostId missing = PostId.of(System.currentTimeMillis() + 3);

        postStatsRepository.upsert(id1, new PostStats(id1, 1, 0, 0, 0, LocalDateTime.now()));
        postStatsRepository.upsert(id2, new PostStats(id2, 2, 0, 0, 0, LocalDateTime.now()));

        Map<PostId, PostStats> result = postStatsRepository.findByIds(List.of(id1, id2, missing));
        assertThat(result).hasSize(2);
        assertThat(result.get(id1).getViewCount()).isEqualTo(1);
        assertThat(result.get(id2).getViewCount()).isEqualTo(2);
    }
}
