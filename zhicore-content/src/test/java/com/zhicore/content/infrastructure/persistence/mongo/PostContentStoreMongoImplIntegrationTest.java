package com.zhicore.content.infrastructure.persistence.mongo;

import com.zhicore.content.application.port.store.PostContentStore;
import com.zhicore.content.domain.model.ContentType;
import com.zhicore.content.domain.model.PostBody;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PostContentStoreMongoImplIntegrationTest extends IntegrationTestBase {

    @Autowired
    private PostContentStore postContentStore;

    private PostId postId;

    @BeforeEach
    void setUp() {
        cleanupMongoDB();
        postId = PostId.of(System.currentTimeMillis());
    }

    @Test
    void saveAndGetAndDeleteContent_shouldWork() {
        PostBody body = PostBody.create(postId, "# title", ContentType.MARKDOWN);
        postContentStore.saveContent(postId, body);

        Optional<PostBody> loaded = postContentStore.getContent(postId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getContent()).isEqualTo("# title");
        assertThat(loaded.get().getContentType()).isEqualTo(ContentType.MARKDOWN);

        postContentStore.deleteContent(postId);
        assertThat(postContentStore.getContent(postId)).isEmpty();
    }

    @Test
    void saveExistingContent_shouldOverwrite() {
        postContentStore.saveContent(postId, PostBody.create(postId, "v1", ContentType.MARKDOWN));
        postContentStore.saveContent(postId, PostBody.create(postId, "v2", ContentType.HTML));

        Optional<PostBody> loaded = postContentStore.getContent(postId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getContent()).isEqualTo("v2");
        assertThat(loaded.get().getContentType()).isEqualTo(ContentType.HTML);
    }
}
