package com.zhicore.content.application.service;

import com.zhicore.content.application.port.alert.ContentAlertPort;
import com.zhicore.content.application.port.client.FileResourceClient;
import com.zhicore.content.application.port.store.PostContentStore;
import com.zhicore.content.domain.model.ContentType;
import com.zhicore.content.domain.model.PostBody;
import com.zhicore.content.domain.model.PostId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostContentImageCleanupServiceTest {

    @Mock
    private PostContentStore postContentStore;

    @Mock
    private FileResourceClient fileResourceClient;

    @Mock
    private ContentAlertPort alertService;

    private PostContentImageCleanupService service;

    @BeforeEach
    void setUp() {
        service = new PostContentImageCleanupService(postContentStore, fileResourceClient, alertService);
        ReflectionTestUtils.setField(service, "fileServiceServerUrl", "https://files.zhicore.test");
        ReflectionTestUtils.setField(service, "fileServiceCustomDomain", "");
        ReflectionTestUtils.setField(service, "fileServiceCdnDomain", "cdn.zhicore.test");
    }

    @Test
    void shouldSkipWhenPostIdIsNull() {
        service.cleanupContentImagesAsync(null);

        verify(postContentStore, never()).loadContent(any());
        verify(fileResourceClient, never()).deleteFile(any());
    }

    @Test
    void shouldAlertWhenContentLoadFails() {
        when(postContentStore.loadContent(PostId.of(1001L))).thenThrow(new RuntimeException("mongo down"));

        service.cleanupContentImagesAsync(1001L);

        verify(alertService).alertContentImageCleanupFailed(1001L, "mongo:post_contents", "mongo down");
        verify(fileResourceClient, never()).deleteFile(any());
    }

    @Test
    void shouldSkipWhenContentMissing() {
        when(postContentStore.loadContent(PostId.of(1001L))).thenReturn(Optional.empty());

        service.cleanupContentImagesAsync(1001L);

        verify(fileResourceClient, never()).deleteFile(any());
        verify(alertService, never()).alertContentImageCleanupFailed(eq(1001L), any(), any());
    }

    @Test
    void shouldDeleteOnlySelfHostedImages() {
        PostBody body = PostBody.create(
                PostId.of(1001L),
                """
                ![](https://files.zhicore.test/api/v1/upload/file/018f2f4a-1234-7abc-8def-1234567890ab)
                ![](https://external.example.com/img/018f2f4a-1234-7abc-8def-1234567890ac)
                """,
                ContentType.MARKDOWN
        );
        when(postContentStore.loadContent(PostId.of(1001L))).thenReturn(Optional.of(body));
        when(fileResourceClient.deleteFile("018f2f4a-1234-7abc-8def-1234567890ab"))
                .thenReturn(FileResourceClient.DeleteResult.ok());

        service.cleanupContentImagesAsync(1001L);

        verify(fileResourceClient).deleteFile("018f2f4a-1234-7abc-8def-1234567890ab");
        verify(fileResourceClient, never()).deleteFile("018f2f4a-1234-7abc-8def-1234567890ac");
        verify(alertService, never()).alertContentImageCleanupFailed(eq(1001L), any(), any());
    }

    @Test
    void shouldAlertWhenDeleteFails() {
        PostBody body = PostBody.create(
                PostId.of(1001L),
                "<img src=\"https://cdn.zhicore.test/api/v1/files/018f2f4a-1234-7abc-8def-1234567890ab\" />",
                ContentType.HTML
        );
        when(postContentStore.loadContent(PostId.of(1001L))).thenReturn(Optional.of(body));
        when(fileResourceClient.deleteFile("018f2f4a-1234-7abc-8def-1234567890ab"))
                .thenReturn(FileResourceClient.DeleteResult.fail("delete failed"));

        service.cleanupContentImagesAsync(1001L);

        verify(alertService).alertContentImageCleanupFailed(
                1001L,
                "https://cdn.zhicore.test/api/v1/files/018f2f4a-1234-7abc-8def-1234567890ab",
                "delete failed"
        );
    }
}
