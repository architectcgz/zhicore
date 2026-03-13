package com.zhicore.content.application.service.command;

import com.zhicore.content.application.port.client.FileResourceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostCoverImageCommandServiceTest {

    @Mock
    private FileResourceClient fileResourceClient;

    @InjectMocks
    private PostCoverImageCommandService postCoverImageCommandService;

    @Test
    void shouldRejectInvalidFileId() {
        assertThrows(IllegalArgumentException.class,
                () -> postCoverImageCommandService.validateFileId("invalid-file-id"));
    }

    @Test
    void shouldDeleteOldCoverWhenCoverChanged() {
        when(fileResourceClient.deleteFile("018f2f4a-1234-7abc-8def-1234567890ab"))
                .thenReturn(FileResourceClient.DeleteResult.ok());

        postCoverImageCommandService.handleCoverImageChange(
                1001L,
                "018f2f4a-1234-7abc-8def-1234567890ab",
                "018f2f4a-1234-7abc-8def-1234567890ac"
        );

        verify(fileResourceClient).deleteFile("018f2f4a-1234-7abc-8def-1234567890ab");
    }

    @Test
    void shouldSkipDeleteWhenCoverNotChanged() {
        postCoverImageCommandService.handleCoverImageChange(
                1001L,
                "018f2f4a-1234-7abc-8def-1234567890ab",
                "018f2f4a-1234-7abc-8def-1234567890ab"
        );

        verify(fileResourceClient, never()).deleteFile("018f2f4a-1234-7abc-8def-1234567890ab");
    }
}
