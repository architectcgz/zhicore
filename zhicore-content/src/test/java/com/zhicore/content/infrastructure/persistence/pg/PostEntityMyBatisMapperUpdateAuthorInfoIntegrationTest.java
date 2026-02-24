package com.zhicore.content.infrastructure.persistence.pg;

import com.zhicore.content.infrastructure.IntegrationTestBase;
import com.zhicore.content.infrastructure.persistence.pg.entity.PostEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.PostEntityMyBatisMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class PostEntityMyBatisMapperUpdateAuthorInfoIntegrationTest extends IntegrationTestBase {

    @Autowired
    private PostEntityMyBatisMapper postEntityMyBatisMapper;

    @Test
    void updateAuthorInfoShouldUpdateWhenVersionIsNewer() {
        PostEntity post = new PostEntity();
        post.setId(10001L);
        post.setOwnerId(20001L);
        post.setOwnerName("old-name");
        post.setOwnerAvatarId("old-avatar");
        post.setOwnerProfileVersion(0L);
        post.setTitle("t");
        post.setStatus(0);
        post.setWriteState("NONE");
        post.setIsArchived(false);
        post.setVersion(0L);

        postEntityMyBatisMapper.insert(post);

        int updated = postEntityMyBatisMapper.updateAuthorInfo(
                20001L,
                "new-name",
                "new-avatar",
                1L
        );
        assertThat(updated).isEqualTo(1);

        PostEntity updatedPost = postEntityMyBatisMapper.selectById(10001L);
        assertThat(updatedPost.getOwnerName()).isEqualTo("new-name");
        assertThat(updatedPost.getOwnerAvatarId()).isEqualTo("new-avatar");
        assertThat(updatedPost.getOwnerProfileVersion()).isEqualTo(1L);

        int notUpdated = postEntityMyBatisMapper.updateAuthorInfo(
                20001L,
                "newer-name",
                "newer-avatar",
                1L
        );
        assertThat(notUpdated).isZero();
    }
}

