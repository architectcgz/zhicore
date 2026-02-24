package com.zhicore.content.infrastructure.persistence.pg;

import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.model.OwnerSnapshot;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PostRepositoryPgImplIntegrationTest extends IntegrationTestBase {

    @Autowired
    private PostRepository postRepository;

    private PostId postId;
    private UserId ownerId;

    @BeforeEach
    void setUp() {
        postId = PostId.of(System.currentTimeMillis());
        ownerId = UserId.of(900001L);
    }

    private Post newPost() {
        Post post = Post.createDraft(postId, ownerId, "integration-title");
        post.setOwnerSnapshot(new OwnerSnapshot(ownerId, "integration-user", null, 1L));
        post.updateMeta("integration-title", "integration-excerpt", "11111111-1111-7111-8111-111111111111");
        return post;
    }

    @Test
    void saveAndFindById_shouldWork() {
        Post post = newPost();
        postRepository.save(post);

        Optional<Post> loaded = postRepository.findById(postId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getId()).isEqualTo(postId);
        assertThat(loaded.get().getTitle()).isEqualTo("integration-title");
        assertThat(loaded.get().getExcerpt()).isEqualTo("integration-excerpt");
        assertThat(loaded.get().getOwnerSnapshot().getName()).isEqualTo("integration-user");
    }

    @Test
    void update_shouldPersistChanges() {
        Post post = newPost();
        postRepository.save(post);

        Post loaded = postRepository.load(postId);
        loaded.updateMeta("new-title", "new-excerpt", "22222222-2222-7222-8222-222222222222");
        postRepository.update(loaded);

        Post updated = postRepository.load(postId);
        assertThat(updated.getTitle()).isEqualTo("new-title");
        assertThat(updated.getExcerpt()).isEqualTo("new-excerpt");
        assertThat(updated.getCoverImageId()).isEqualTo("22222222-2222-7222-8222-222222222222");
    }

    @Test
    void delete_shouldRemovePost() {
        postRepository.save(newPost());
        assertThat(postRepository.findById(postId)).isPresent();

        postRepository.delete(postId);

        assertThat(postRepository.findById(postId)).isEmpty();
    }
}
