package com.zhicore.content.application.service;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.application.command.CreatePostAppCommand;
import com.zhicore.content.application.command.commands.CreatePostCommand;
import com.zhicore.content.application.mapper.EventMapper;
import com.zhicore.content.application.port.client.UserProfileClient;
import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.workflow.CreateDraftWorkflow;
import com.zhicore.content.domain.event.DomainEvent;
import com.zhicore.content.domain.event.PostCreatedDomainEvent;
import com.zhicore.content.domain.model.OwnerSnapshot;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.integration.messaging.post.PostCreatedIntegrationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostCreateCommandServiceTest {

    @Mock
    private IdGeneratorFeignClient idGeneratorFeignClient;

    @Mock
    private TagCommandService tagCommandService;

    @Mock
    private PostCoverImageCommandService postCoverImageCommandService;

    @Mock
    private UserProfileClient userProfileClient;

    @Mock
    private CreateDraftWorkflow createDraftWorkflow;

    @Mock
    private EventPublisher domainEventPublisher;

    @Mock
    private IntegrationEventPublisher integrationEventPublisher;

    @Mock
    private EventMapper eventMapper;

    @InjectMocks
    private PostCreateCommandService postCreateCommandService;

    @Test
    void shouldCreateDraftAndPublishCreatedEvents() {
        Long userId = 1001L;
        Long postId = 2001L;
        OwnerSnapshot ownerSnapshot = new OwnerSnapshot(UserId.of(userId), "Alice", "avatar-1", 2L);
        CreatePostAppCommand request = new CreatePostAppCommand(
                "标题",
                "内容",
                "markdown",
                88L,
                "018f2f4a-1234-7abc-8def-1234567890ab",
                List.of("Java", "Spring")
        );
        PostCreatedDomainEvent domainEvent = new PostCreatedDomainEvent(
                "evt-created",
                Instant.now(),
                PostId.of(postId),
                "标题",
                "摘要",
                UserId.of(userId),
                "Alice",
                Set.of(),
                null,
                null,
                "DRAFT",
                null,
                Instant.now(),
                3L
        );
        PostCreatedIntegrationEvent integrationEvent = new PostCreatedIntegrationEvent(
                "evt-created",
                Instant.now(),
                postId,
                "标题",
                "摘要",
                userId,
                "Alice",
                List.of(),
                null,
                null,
                "DRAFT",
                null,
                Instant.now(),
                3L
        );

        when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(ApiResponse.success(postId));
        when(tagCommandService.findOrCreateBatch(List.of("Java", "Spring"))).thenReturn(List.of(
                Tag.create(11L, "Java", "java"),
                Tag.create(12L, "Spring", "spring")
        ));
        when(userProfileClient.getOwnerSnapshot(UserId.of(userId))).thenReturn(Optional.of(ownerSnapshot));
        when(createDraftWorkflow.execute(any(CreatePostCommand.class)))
                .thenReturn(new CreateDraftWorkflow.ExecutionResult(PostId.of(postId), List.of(domainEvent)));
        when(eventMapper.toIntegrationEvent(domainEvent)).thenReturn(integrationEvent);

        Long createdPostId = postCreateCommandService.createPost(userId, request);

        assertThat(createdPostId).isEqualTo(postId);
        verify(postCoverImageCommandService).validateFileId(request.coverImageId());
        verify(domainEventPublisher).publishBatch(any());
        verify(integrationEventPublisher).publish(integrationEvent);

        ArgumentCaptor<CreatePostCommand> commandCaptor = ArgumentCaptor.forClass(CreatePostCommand.class);
        verify(createDraftWorkflow).execute(commandCaptor.capture());
        CreatePostCommand command = commandCaptor.getValue();
        assertThat(command.getPostId()).isEqualTo(PostId.of(postId));
        assertThat(command.getOwnerSnapshot()).isEqualTo(ownerSnapshot);
        assertThat(command.getTagIds()).extracting("value").containsExactlyInAnyOrder(11L, 12L);
    }

    @Test
    void shouldFallbackToDefaultOwnerSnapshotWhenProfileMissing() {
        Long userId = 1001L;
        Long postId = 2001L;
        CreatePostAppCommand request = new CreatePostAppCommand(
                "标题",
                "内容",
                "markdown",
                null,
                null,
                null
        );

        when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(ApiResponse.success(postId));
        when(userProfileClient.getOwnerSnapshot(UserId.of(userId))).thenReturn(Optional.empty());
        when(createDraftWorkflow.execute(any(CreatePostCommand.class)))
                .thenReturn(new CreateDraftWorkflow.ExecutionResult(
                        PostId.of(postId),
                        List.<DomainEvent<?>>of()
                ));

        postCreateCommandService.createPost(userId, request);

        ArgumentCaptor<CreatePostCommand> commandCaptor = ArgumentCaptor.forClass(CreatePostCommand.class);
        verify(createDraftWorkflow).execute(commandCaptor.capture());
        assertThat(commandCaptor.getValue().getOwnerSnapshot().isDefault()).isTrue();
        verify(integrationEventPublisher, org.mockito.Mockito.never()).publish(any());
    }
}
