package com.zhicore.content.application.service;

import com.zhicore.content.application.command.SaveDraftCommand;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.domain.service.DraftCommandService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostDraftCommandServiceTest {

    @Mock
    private OwnedPostLoadService ownedPostLoadService;

    @Mock
    private DraftCommandService draftCommandService;

    @InjectMocks
    private PostDraftCommandService postDraftCommandService;

    @Test
    void shouldSaveDraftAfterOwnershipCheck() {
        when(ownedPostLoadService.load(2001L, 1001L))
                .thenReturn(Post.createDraft(PostId.of(2001L), UserId.of(1001L), "title"));

        postDraftCommandService.saveDraft(1001L, 2001L, new SaveDraftCommand("内容", "markdown", true, "device-1"));

        verify(ownedPostLoadService).load(2001L, 1001L);
        verify(draftCommandService).saveDraft(2001L, 1001L, "内容", true);
    }

    @Test
    void shouldDeleteDraftAfterOwnershipCheck() {
        when(ownedPostLoadService.load(2001L, 1001L))
                .thenReturn(Post.createDraft(PostId.of(2001L), UserId.of(1001L), "title"));

        postDraftCommandService.deleteDraft(1001L, 2001L);

        verify(ownedPostLoadService).load(2001L, 1001L);
        verify(draftCommandService).deleteDraft(2001L, 1001L);
    }
}
