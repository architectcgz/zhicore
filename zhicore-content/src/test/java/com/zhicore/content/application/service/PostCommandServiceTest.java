package com.zhicore.content.application.service;

import com.zhicore.content.application.command.CreatePostAppCommand;
import com.zhicore.content.application.command.SaveDraftCommand;
import com.zhicore.content.application.command.UpdatePostAppCommand;
import com.zhicore.content.application.command.handlers.RestorePostHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostCommandServiceTest {

    @Mock
    private PostCreateCommandService postCreateCommandService;

    @Mock
    private PostDraftCommandService postDraftCommandService;

    @Mock
    private PostLifecycleCommandService postLifecycleCommandService;

    @Mock
    private PostPublishCommandService postPublishCommandService;

    @Mock
    private PostWriteService postWriteService;

    @Mock
    private ScheduledPublishCommandService scheduledPublishCommandService;

    @Mock
    private RestorePostHandler restorePostHandler;

    @InjectMocks
    private PostCommandService postCommandService;

    @Test
    void shouldDelegateCreateAndPublishToDedicatedServices() {
        CreatePostAppCommand createCommand = new CreatePostAppCommand("标题", "内容", "markdown", null, null, List.of("Java"));
        when(postCreateCommandService.createPost(1001L, createCommand)).thenReturn(2001L);

        postCommandService.createPost(1001L, createCommand);
        postCommandService.publishPost(1001L, 2001L);

        verify(postCreateCommandService).createPost(1001L, createCommand);
        verify(postPublishCommandService).publishPost(1001L, 2001L);
    }

    @Test
    void shouldKeepOtherWriteOperationsOnPostWriteService() {
        UpdatePostAppCommand updateCommand = new UpdatePostAppCommand("标题", "内容", 1L, null, List.of("Java"));
        SaveDraftCommand saveDraftCommand = new SaveDraftCommand("内容", "markdown", true, "device-1");

        postCommandService.updatePost(1001L, 2001L, updateCommand);
        postCommandService.unpublishPost(1001L, 2001L);
        postCommandService.deletePost(1001L, 2001L);
        postCommandService.saveDraft(1001L, 2001L, saveDraftCommand);
        postCommandService.deleteDraft(1001L, 2001L);
        postCommandService.attachTags(1001L, 2001L, List.of("Java"));
        postCommandService.detachTag(1001L, 2001L, "java");

        verify(postWriteService).updatePost(1001L, 2001L, updateCommand);
        verify(postLifecycleCommandService).unpublishPost(1001L, 2001L);
        verify(postLifecycleCommandService).deletePost(1001L, 2001L);
        verify(postDraftCommandService).saveDraft(1001L, 2001L, saveDraftCommand);
        verify(postDraftCommandService).deleteDraft(1001L, 2001L);
        verify(postWriteService).replacePostTags(1001L, 2001L, List.of("Java"));
        verify(postWriteService).detachTag(1001L, 2001L, "java");
    }
}
