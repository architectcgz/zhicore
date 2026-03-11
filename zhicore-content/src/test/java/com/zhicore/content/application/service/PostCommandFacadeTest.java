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
class PostCommandFacadeTest {

    @Mock
    private PostCreateCommandService postCreateCommandService;

    @Mock
    private PostDraftCommandService postDraftCommandService;

    @Mock
    private PostLifecycleCommandService postLifecycleCommandService;

    @Mock
    private PostUpdateCommandService postUpdateCommandService;

    @Mock
    private PostTagRelationCommandService postTagRelationCommandService;

    @Mock
    private PostPublishCommandService postPublishCommandService;

    @Mock
    private ScheduledPublishCommandService scheduledPublishCommandService;

    @Mock
    private RestorePostHandler restorePostHandler;

    @InjectMocks
    private PostCommandFacade postCommandFacade;

    @Test
    void shouldDelegateCreateAndPublishToDedicatedServices() {
        CreatePostAppCommand createCommand = new CreatePostAppCommand("标题", "内容", "markdown", null, null, List.of("Java"));
        when(postCreateCommandService.createPost(1001L, createCommand)).thenReturn(2001L);

        postCommandFacade.createPost(1001L, createCommand);
        postCommandFacade.publishPost(1001L, 2001L);

        verify(postCreateCommandService).createPost(1001L, createCommand);
        verify(postPublishCommandService).publishPost(1001L, 2001L);
    }

    @Test
    void shouldKeepOtherWriteOperationsOnDedicatedServices() {
        UpdatePostAppCommand updateCommand = new UpdatePostAppCommand("标题", "内容", 1L, null, List.of("Java"));
        SaveDraftCommand saveDraftCommand = new SaveDraftCommand("内容", "markdown", true, "device-1");

        postCommandFacade.updatePost(1001L, 2001L, updateCommand);
        postCommandFacade.unpublishPost(1001L, 2001L);
        postCommandFacade.deletePost(1001L, 2001L);
        postCommandFacade.saveDraft(1001L, 2001L, saveDraftCommand);
        postCommandFacade.deleteDraft(1001L, 2001L);
        postCommandFacade.attachTags(1001L, 2001L, List.of("Java"));
        postCommandFacade.detachTag(1001L, 2001L, "java");

        verify(postUpdateCommandService).updatePost(1001L, 2001L, updateCommand);
        verify(postLifecycleCommandService).unpublishPost(1001L, 2001L);
        verify(postLifecycleCommandService).deletePost(1001L, 2001L);
        verify(postDraftCommandService).saveDraft(1001L, 2001L, saveDraftCommand);
        verify(postDraftCommandService).deleteDraft(1001L, 2001L);
        verify(postTagRelationCommandService).replacePostTags(1001L, 2001L, List.of("Java"));
        verify(postTagRelationCommandService).detachTag(1001L, 2001L, "java");
    }
}
