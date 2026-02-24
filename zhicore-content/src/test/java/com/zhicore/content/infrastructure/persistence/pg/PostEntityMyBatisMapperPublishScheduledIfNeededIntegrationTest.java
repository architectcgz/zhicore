package com.zhicore.content.infrastructure.persistence.pg;

import com.zhicore.content.infrastructure.IntegrationTestBase;
import com.zhicore.content.infrastructure.persistence.pg.entity.PostEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.PostEntityMyBatisMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PostEntityMyBatisMapperPublishScheduledIfNeededIntegrationTest extends IntegrationTestBase {

    @Autowired
    private PostEntityMyBatisMapper postEntityMyBatisMapper;

    @Test
    void publishScheduledIfNeededShouldUpdateOnceAndBeIdempotent() {
        PostEntity post = new PostEntity();
        post.setId(20001L);
        post.setOwnerId(30001L);
        post.setOwnerName("n");
        post.setTitle("t");
        post.setStatus(2); // SCHEDULED
        post.setWriteState("NONE");
        post.setIsArchived(false);
        post.setVersion(0L);
        post.setScheduledAt(LocalDateTime.now().plusMinutes(10));

        postEntityMyBatisMapper.insert(post);

        LocalDateTime publishedAt = LocalDateTime.now();
        Long newVersion = postEntityMyBatisMapper.publishScheduledIfNeeded(20001L, publishedAt);
        assertThat(newVersion).isEqualTo(1L);

        PostEntity updated = postEntityMyBatisMapper.selectById(20001L);
        assertThat(updated.getStatus()).isEqualTo(1);
        assertThat(updated.getPublishedAt()).isNotNull();
        assertThat(updated.getScheduledAt()).isNull();
        assertThat(updated.getVersion()).isEqualTo(1L);

        Long second = postEntityMyBatisMapper.publishScheduledIfNeeded(20001L, LocalDateTime.now());
        assertThat(second).isNull();
    }
}

