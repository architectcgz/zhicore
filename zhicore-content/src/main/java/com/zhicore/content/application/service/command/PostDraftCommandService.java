package com.zhicore.content.application.service.command;

import com.zhicore.content.application.command.SaveDraftCommand;
import com.zhicore.content.application.service.OwnedPostLoadService;
import com.zhicore.content.domain.service.DraftCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 草稿写服务。
 *
 * 收口草稿保存与删除用例，避免 PostWriteService 混入草稿持久化编排。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostDraftCommandService {

    private final OwnedPostLoadService ownedPostLoadService;
    private final DraftCommandService draftCommandService;

    public void saveDraft(Long userId, Long postId, SaveDraftCommand request) {
        ownedPostLoadService.load(postId, userId);

        draftCommandService.saveDraft(
                postId,
                userId,
                request.content(),
                request.autoSave() != null ? request.autoSave() : false
        );

        log.info("Draft saved: postId={}, userId={}, isAutoSave={}",
                postId, userId, request.autoSave());
    }

    public void deleteDraft(Long userId, Long postId) {
        ownedPostLoadService.load(postId, userId);
        draftCommandService.deleteDraft(postId, userId);
        log.info("Draft deleted: postId={}, userId={}", postId, userId);
    }
}
