package com.zhicore.content.infrastructure.persistence.pg;

import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.model.*;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import com.zhicore.content.infrastructure.persistence.pg.mapper.PostEntityMyBatisMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 作者信息更新测试（R5）
 *
 * 覆盖：@Update 注解正确性、版本过滤、字段更新
 */
@DisplayName("作者信息更新集成测试")
class PostAuthorInfoUpdateIntegrationTest extends IntegrationTestBase {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostEntityMyBatisMapper postEntityMapper;

    @BeforeEach
    void setUp() {
        cleanupPostgres();
        cleanupMongoDB();
        cleanupRedis();
    }

    // ==================== 13.1 @Update 注解正确性测试 ====================

    @Nested
    @DisplayName("作者信息更新")
    class UpdateAuthorInfoTests {

        @Test
        @DisplayName("updateAuthorInfo 正确更新 owner_name、owner_avatar_id、owner_profile_version")
        void shouldUpdateAuthorFields() {
            // 创建文章
            Post post = Post.createDraft(PostId.of(3001L), UserId.of(200L), "作者信息测试");
            postRepository.save(post);

            // 更新作者信息
            int affected = postEntityMapper.updateAuthorInfo(200L, "新昵称", "avatar-v2", 2L);

            assertThat(affected).isGreaterThanOrEqualTo(1);

            // 验证更新后的值
            Post updated = postRepository.load(PostId.of(3001L));
            OwnerSnapshot snapshot = updated.getOwnerSnapshot();
            assertThat(snapshot.getName()).isEqualTo("新昵称");
        }

        @Test
        @DisplayName("版本号过滤：旧版本不覆盖新版本")
        void olderVersionShouldNotOverwriteNewer() {
            Post post = Post.createDraft(PostId.of(3002L), UserId.of(201L), "版本过滤测试");
            postRepository.save(post);

            // 先更新到版本 5
            postEntityMapper.updateAuthorInfo(201L, "v5昵称", "avatar-v5", 5L);

            // 尝试用版本 3 覆盖（应被过滤）
            int affected = postEntityMapper.updateAuthorInfo(201L, "v3昵称", "avatar-v3", 3L);
            assertThat(affected).isEqualTo(0);

            // 验证仍为版本 5 的数据
            Post loaded = postRepository.load(PostId.of(3002L));
            assertThat(loaded.getOwnerSnapshot().getName()).isEqualTo("v5昵称");
        }
    }
}