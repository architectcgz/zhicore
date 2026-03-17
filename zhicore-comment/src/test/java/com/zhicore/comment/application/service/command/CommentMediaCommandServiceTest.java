package com.zhicore.comment.application.service.command;

import com.zhicore.comment.application.port.CommentMediaUploadPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentMediaCommandService 测试")
class CommentMediaCommandServiceTest {

    @Mock
    private CommentMediaUploadPort commentMediaUploadPort;

    @InjectMocks
    private CommentMediaCommandService commentMediaCommandService;

    @Test
    @DisplayName("上传图片应委派给上传端口")
    void shouldDelegateImageUploadToPort() {
        MockMultipartFile file = new MockMultipartFile("file", "comment.png", "image/png", new byte[] {1, 2, 3});
        when(commentMediaUploadPort.uploadImage(file)).thenReturn("img-001");

        String fileId = commentMediaCommandService.uploadImage(file);

        assertEquals("img-001", fileId);
        verify(commentMediaUploadPort).uploadImage(file);
    }

    @Test
    @DisplayName("上传语音应委派给上传端口")
    void shouldDelegateVoiceUploadToPort() {
        MockMultipartFile file = new MockMultipartFile("file", "voice.mp3", "audio/mpeg", new byte[] {1, 2, 3});
        when(commentMediaUploadPort.uploadVoice(file)).thenReturn("voice-001");

        String fileId = commentMediaCommandService.uploadVoice(file);

        assertEquals("voice-001", fileId);
        verify(commentMediaUploadPort).uploadVoice(file);
    }
}
