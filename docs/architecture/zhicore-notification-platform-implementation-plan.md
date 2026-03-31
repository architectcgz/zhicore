# ZhiCore Notification Platform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a unified notification platform for ZhiCore that supports optimized interaction notifications, large-scale author publish fan-out, per-type/per-channel/user-author preferences, DND, and reserved email/SMS channels.

**Architecture:** Keep `zhicore-notification` as the unified notification center, but split production paths into `interaction pipeline` and `broadcast pipeline`. Interaction notifications remain per-user inbox facts with better unread/group-state maintenance; author publish notifications move to `campaign + shard + delivery` orchestration so high-follower fan-out can be processed incrementally and policy-driven.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, PostgreSQL, Redis, RocketMQ, WebSocket, Maven, JUnit 5, Mockito

---

## Scope And Delivery Strategy

This plan is intentionally phased so each milestone produces testable software:

1. Notification schema and enum foundation
2. Preferences and DND APIs
3. User-service follower shard query support
4. Interaction notification optimization
5. Broadcast campaign planning for `PostPublishedIntegrationEvent`
6. Broadcast shard execution and channel delivery ledger
7. Digest and delayed delivery
8. Query/API consolidation, observability, and smoke verification

Implementation rule set:

1. Keep PostgreSQL as the source of truth.
2. Redis only stores stable cache snapshots or counters.
3. Prefer additive changes over destructive rewrites.
4. Ship DB schema before runtime usage.
5. Add tests before behavior changes.
6. Keep commits small and scoped.

## File Structure Map

### Existing files that will be modified

- `docker/postgres-init/02-init-tables.sql`
- `config/nacos/zhicore-notification.yml`
- `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationType.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/Notification.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/application/service/command/NotificationCommandService.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/application/service/query/NotificationQueryService.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/application/service/NotificationAggregationService.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/cache/NotificationRedisKeys.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/cache/RedisNotificationUnreadCountStore.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/po/NotificationPO.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/mapper/NotificationMapper.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/NotificationRepositoryImpl.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/mq/NotificationConsumerGroups.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/interfaces/controller/NotificationQueryController.java`
- `zhicore-user/src/main/java/com/zhicore/user/application/service/query/FollowQueryService.java`
- `zhicore-user/src/main/java/com/zhicore/user/interfaces/controller/FollowQueryController.java`
- `zhicore-user/src/main/java/com/zhicore/user/infrastructure/repository/mapper/UserFollowMapper.java`
- `zhicore-user/src/main/java/com/zhicore/user/infrastructure/repository/UserFollowRepositoryImpl.java`
- `zhicore-client/src/main/java/com/zhicore/clients/client/UserRelationClient.java`
- `zhicore-integration/src/main/java/com/zhicore/integration/messaging/post/PostPublishedIntegrationEvent.java`

### New notification-module files to create

- `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationCategory.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationChannel.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationImportance.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/AuthorSubscriptionLevel.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationCampaign.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationCampaignShard.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationDelivery.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationDigestJob.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationGroupState.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/UserNotificationPreference.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/UserNotificationDnd.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/UserAuthorSubscription.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/domain/repository/NotificationCampaignRepository.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/domain/repository/NotificationDeliveryRepository.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/domain/repository/NotificationPreferenceRepository.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/domain/repository/NotificationGroupStateRepository.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/application/service/command/NotificationPreferenceCommandService.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/application/service/query/NotificationPreferenceQueryService.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/application/service/broadcast/PostPublishedCampaignService.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/application/service/broadcast/BroadcastShardPlanner.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/application/service/broadcast/BroadcastShardExecutionService.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/application/service/channel/NotificationChannelPlanner.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/application/service/channel/NotificationInboxDeliveryService.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/application/service/channel/NotificationPushDeliveryService.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/application/service/channel/NotificationEmailDeliveryService.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/application/service/channel/NotificationSmsDeliveryService.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/application/service/digest/NotificationDigestService.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/mq/PostPublishedNotificationConsumer.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/NotificationCampaignRepositoryImpl.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/NotificationDeliveryRepositoryImpl.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/NotificationPreferenceRepositoryImpl.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/NotificationGroupStateRepositoryImpl.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/mapper/NotificationCampaignMapper.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/mapper/NotificationDeliveryMapper.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/mapper/NotificationPreferenceMapper.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/mapper/NotificationGroupStateMapper.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/interfaces/controller/NotificationPreferenceController.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/interfaces/dto/request/UpdateNotificationPreferenceRequest.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/interfaces/dto/request/UpdateNotificationDndRequest.java`
- `zhicore-notification/src/main/java/com/zhicore/notification/interfaces/dto/request/UpdateAuthorSubscriptionRequest.java`

### New client-module files to create

- `zhicore-client/src/main/java/com/zhicore/clients/client/UserFollowerShardClient.java`
- `zhicore-client/src/main/java/com/zhicore/clients/dto/user/FollowerShardItemDTO.java`
- `zhicore-client/src/main/java/com/zhicore/clients/dto/user/FollowerShardPageDTO.java`

### New test files to create

- `zhicore-notification/src/test/java/com/zhicore/notification/domain/model/NotificationCampaignTest.java`
- `zhicore-notification/src/test/java/com/zhicore/notification/application/service/command/NotificationPreferenceCommandServiceTest.java`
- `zhicore-notification/src/test/java/com/zhicore/notification/application/service/broadcast/PostPublishedCampaignServiceTest.java`
- `zhicore-notification/src/test/java/com/zhicore/notification/application/service/broadcast/BroadcastShardExecutionServiceTest.java`
- `zhicore-notification/src/test/java/com/zhicore/notification/application/service/channel/NotificationChannelPlannerTest.java`
- `zhicore-notification/src/test/java/com/zhicore/notification/application/service/digest/NotificationDigestServiceTest.java`
- `zhicore-notification/src/test/java/com/zhicore/notification/infrastructure/mq/PostPublishedNotificationConsumerTest.java`
- `zhicore-notification/src/test/java/com/zhicore/notification/interfaces/controller/NotificationPreferenceControllerTest.java`
- `zhicore-user/src/test/java/com/zhicore/user/application/service/query/FollowerShardQueryServiceTest.java`
- `zhicore-user/src/test/java/com/zhicore/user/interfaces/controller/FollowQueryControllerFollowerShardTest.java`

## Task 1: Expand Notification Schema And Enum Foundation

**Files:**
- Modify: `docker/postgres-init/02-init-tables.sql`
- Modify: `config/nacos/zhicore-notification.yml`
- Modify: `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationType.java`
- Modify: `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/Notification.java`
- Modify: `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/po/NotificationPO.java`
- Modify: `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/mapper/NotificationMapper.java`
- Modify: `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/NotificationRepositoryImpl.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationCategory.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationChannel.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationImportance.java`
- Test: `zhicore-notification/src/test/java/com/zhicore/notification/domain/model/NotificationTest.java`

- [ ] **Step 1: Write failing domain tests for new notification metadata**

```java
@Test
void createPostPublishedNotification_shouldSetCategoryAndGroupKey() {
    Notification notification = Notification.createPostPublishedNotification(
            1L, 200L, 100L, 300L, "post_publish:100:300", "author published");

    assertEquals(NotificationType.POST_PUBLISHED_BY_FOLLOWING, notification.getType());
    assertEquals(NotificationCategory.CONTENT, notification.getCategory());
    assertEquals("post_publish:100:300", notification.getGroupKey());
}
```

- [ ] **Step 2: Run the notification domain test and verify failure**

Run: `mvn -pl zhicore-notification -Dtest=NotificationTest test`

Expected: FAIL because the new enum values, fields, and factory methods do not exist yet.

- [ ] **Step 3: Extend the SQL schema and persistence model**

Additive SQL only. Extend `notifications` and add new tables required by later tasks:

```sql
ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS category SMALLINT,
    ADD COLUMN IF NOT EXISTS notification_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS source_event_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS group_key VARCHAR(256),
    ADD COLUMN IF NOT EXISTS payload_json JSONB,
    ADD COLUMN IF NOT EXISTS importance SMALLINT DEFAULT 0;
```

- [ ] **Step 4: Implement the minimal enum/domain/persistence changes**

Add new enum values and fields first, without yet changing runtime flow:

```java
public enum NotificationType {
    POST_LIKED,
    POST_COMMENTED,
    COMMENT_REPLIED,
    USER_FOLLOWED,
    POST_PUBLISHED_BY_FOLLOWING,
    POST_PUBLISHED_DIGEST,
    SYSTEM_ANNOUNCEMENT,
    SECURITY_ALERT
}
```

- [ ] **Step 5: Run the focused module tests**

Run: `mvn -pl zhicore-notification -Dtest=NotificationTest,NotificationApplicationServiceTest test`

Expected: PASS for the updated domain test and no regression in existing service tests touching notification creation.

- [ ] **Step 6: Commit**

```bash
git add docker/postgres-init/02-init-tables.sql \
  config/nacos/zhicore-notification.yml \
  zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationType.java \
  zhicore-notification/src/main/java/com/zhicore/notification/domain/model/Notification.java \
  zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationCategory.java \
  zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationChannel.java \
  zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationImportance.java \
  zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/po/NotificationPO.java \
  zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/mapper/NotificationMapper.java \
  zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/NotificationRepositoryImpl.java \
  zhicore-notification/src/test/java/com/zhicore/notification/domain/model/NotificationTest.java
git commit -m "feat: extend notification schema foundation"
```

## Task 2: Add Preference, DND, And Author Subscription APIs

**Files:**
- Modify: `docker/postgres-init/02-init-tables.sql`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/UserNotificationPreference.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/UserNotificationDnd.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/UserAuthorSubscription.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/AuthorSubscriptionLevel.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/domain/repository/NotificationPreferenceRepository.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/application/service/command/NotificationPreferenceCommandService.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/application/service/query/NotificationPreferenceQueryService.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/NotificationPreferenceRepositoryImpl.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/mapper/NotificationPreferenceMapper.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/interfaces/controller/NotificationPreferenceController.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/interfaces/dto/request/UpdateNotificationPreferenceRequest.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/interfaces/dto/request/UpdateNotificationDndRequest.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/interfaces/dto/request/UpdateAuthorSubscriptionRequest.java`
- Test: `zhicore-notification/src/test/java/com/zhicore/notification/application/service/command/NotificationPreferenceCommandServiceTest.java`
- Test: `zhicore-notification/src/test/java/com/zhicore/notification/interfaces/controller/NotificationPreferenceControllerTest.java`

- [ ] **Step 1: Write failing service tests for preference precedence**

```java
@Test
void resolveChannels_shouldDisablePushWhenTypeDisabledInDndWindow() {
    // given user preference push enabled
    // and DND says CONTENT + PUSH quiet hours active
    // expect inbox allowed and push denied
}
```

- [ ] **Step 2: Run the focused preference tests and verify failure**

Run: `mvn -pl zhicore-notification -Dtest=NotificationPreferenceCommandServiceTest,NotificationPreferenceControllerTest test`

Expected: FAIL because repository, services, and controller do not exist.

- [ ] **Step 3: Add additive preference tables**

```sql
CREATE TABLE IF NOT EXISTS notification_user_preference (...);
CREATE TABLE IF NOT EXISTS notification_user_dnd (...);
CREATE TABLE IF NOT EXISTS notification_author_subscription (...);
```

- [ ] **Step 4: Implement minimal repository and query/command services**

Start with plain CRUD and a single resolution method:

```java
public record ChannelDecision(
        boolean inboxEnabled,
        boolean pushEnabled,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean digestOnly) {}
```

- [ ] **Step 5: Add REST endpoints and controller tests**

Required endpoints:

```text
GET  /api/v1/notification-preferences
PUT  /api/v1/notification-preferences
GET  /api/v1/notification-dnd
PUT  /api/v1/notification-dnd
GET  /api/v1/notification-authors/{authorId}/subscription
PUT  /api/v1/notification-authors/{authorId}/subscription
```

- [ ] **Step 6: Run module tests**

Run: `mvn -pl zhicore-notification -Dtest=NotificationPreferenceCommandServiceTest,NotificationPreferenceControllerTest,NotificationControllerTest test`

Expected: PASS with controller contract coverage for the new endpoints.

- [ ] **Step 7: Commit**

```bash
git add docker/postgres-init/02-init-tables.sql \
  zhicore-notification/src/main/java/com/zhicore/notification/domain/model/UserNotificationPreference.java \
  zhicore-notification/src/main/java/com/zhicore/notification/domain/model/UserNotificationDnd.java \
  zhicore-notification/src/main/java/com/zhicore/notification/domain/model/UserAuthorSubscription.java \
  zhicore-notification/src/main/java/com/zhicore/notification/domain/model/AuthorSubscriptionLevel.java \
  zhicore-notification/src/main/java/com/zhicore/notification/domain/repository/NotificationPreferenceRepository.java \
  zhicore-notification/src/main/java/com/zhicore/notification/application/service/command/NotificationPreferenceCommandService.java \
  zhicore-notification/src/main/java/com/zhicore/notification/application/service/query/NotificationPreferenceQueryService.java \
  zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/NotificationPreferenceRepositoryImpl.java \
  zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/mapper/NotificationPreferenceMapper.java \
  zhicore-notification/src/main/java/com/zhicore/notification/interfaces/controller/NotificationPreferenceController.java \
  zhicore-notification/src/main/java/com/zhicore/notification/interfaces/dto/request/UpdateNotificationPreferenceRequest.java \
  zhicore-notification/src/main/java/com/zhicore/notification/interfaces/dto/request/UpdateNotificationDndRequest.java \
  zhicore-notification/src/main/java/com/zhicore/notification/interfaces/dto/request/UpdateAuthorSubscriptionRequest.java \
  zhicore-notification/src/test/java/com/zhicore/notification/application/service/command/NotificationPreferenceCommandServiceTest.java \
  zhicore-notification/src/test/java/com/zhicore/notification/interfaces/controller/NotificationPreferenceControllerTest.java
git commit -m "feat: add notification preference and dnd APIs"
```

## Task 3: Add Follower-Shard Query Support In User Service

**Files:**
- Modify: `zhicore-user/src/main/java/com/zhicore/user/infrastructure/repository/mapper/UserFollowMapper.java`
- Modify: `zhicore-user/src/main/java/com/zhicore/user/infrastructure/repository/UserFollowRepositoryImpl.java`
- Modify: `zhicore-user/src/main/java/com/zhicore/user/application/service/query/FollowQueryService.java`
- Modify: `zhicore-user/src/main/java/com/zhicore/user/interfaces/controller/FollowQueryController.java`
- Create: `zhicore-client/src/main/java/com/zhicore/clients/client/UserFollowerShardClient.java`
- Create: `zhicore-client/src/main/java/com/zhicore/clients/dto/user/FollowerShardItemDTO.java`
- Create: `zhicore-client/src/main/java/com/zhicore/clients/dto/user/FollowerShardPageDTO.java`
- Test: `zhicore-user/src/test/java/com/zhicore/user/application/service/query/FollowerShardQueryServiceTest.java`
- Test: `zhicore-user/src/test/java/com/zhicore/user/interfaces/controller/FollowQueryControllerFollowerShardTest.java`

- [ ] **Step 1: Write failing tests for cursor-based follower shard reads**

```java
@Test
void getFollowerShard_shouldReturnStableCursorPage() {
    // given author 200L with followers ordered by follower_id
    // expect next cursor and max page size enforcement
}
```

- [ ] **Step 2: Run the focused user-service tests and verify failure**

Run: `mvn -pl zhicore-user -Dtest=FollowerShardQueryServiceTest,FollowQueryControllerFollowerShardTest test`

Expected: FAIL because the shard endpoint and DTOs do not exist.

- [ ] **Step 3: Add mapper/repository query for stable cursor paging**

Use a query shaped like:

```sql
SELECT follower_id, created_at
FROM user_follows
WHERE following_id = #{authorId}
  AND follower_id > #{cursorFollowerId}
ORDER BY follower_id ASC
LIMIT #{size}
```

- [ ] **Step 4: Add controller endpoint for notification service usage**

Endpoint suggestion:

```text
GET /api/v1/users/{userId}/followers/shard?cursorFollowerId=0&size=2000
```

- [ ] **Step 5: Add typed client in `zhicore-client`**

```java
@GetMapping("/api/v1/users/{userId}/followers/shard")
ApiResponse<FollowerShardPageDTO> getFollowerShard(...);
```

- [ ] **Step 6: Run the focused tests**

Run: `mvn -pl zhicore-user,zhicore-client -Dtest=FollowerShardQueryServiceTest,FollowQueryControllerFollowerShardTest test`

Expected: PASS with a stable paging contract for broadcast workers.

- [ ] **Step 7: Commit**

```bash
git add zhicore-user/src/main/java/com/zhicore/user/infrastructure/repository/mapper/UserFollowMapper.java \
  zhicore-user/src/main/java/com/zhicore/user/infrastructure/repository/UserFollowRepositoryImpl.java \
  zhicore-user/src/main/java/com/zhicore/user/application/service/query/FollowQueryService.java \
  zhicore-user/src/main/java/com/zhicore/user/interfaces/controller/FollowQueryController.java \
  zhicore-client/src/main/java/com/zhicore/clients/client/UserFollowerShardClient.java \
  zhicore-client/src/main/java/com/zhicore/clients/dto/user/FollowerShardItemDTO.java \
  zhicore-client/src/main/java/com/zhicore/clients/dto/user/FollowerShardPageDTO.java \
  zhicore-user/src/test/java/com/zhicore/user/application/service/query/FollowerShardQueryServiceTest.java \
  zhicore-user/src/test/java/com/zhicore/user/interfaces/controller/FollowQueryControllerFollowerShardTest.java
git commit -m "feat: add follower shard query for notification fanout"
```

## Task 4: Optimize Interaction Notifications With Group State And Incremental Unread Counts

**Files:**
- Modify: `docker/postgres-init/02-init-tables.sql`
- Modify: `zhicore-notification/src/main/java/com/zhicore/notification/application/port/store/NotificationUnreadCountStore.java`
- Modify: `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/cache/NotificationRedisKeys.java`
- Modify: `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/cache/RedisNotificationUnreadCountStore.java`
- Modify: `zhicore-notification/src/main/java/com/zhicore/notification/application/service/command/NotificationCommandService.java`
- Modify: `zhicore-notification/src/main/java/com/zhicore/notification/application/service/query/NotificationQueryService.java`
- Modify: `zhicore-notification/src/main/java/com/zhicore/notification/application/service/NotificationAggregationService.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationGroupState.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/domain/repository/NotificationGroupStateRepository.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/NotificationGroupStateRepositoryImpl.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/mapper/NotificationGroupStateMapper.java`
- Test: `zhicore-notification/src/test/java/com/zhicore/notification/application/service/query/NotificationUnreadCountContractTest.java`
- Test: `zhicore-notification/src/test/java/com/zhicore/notification/application/service/NotificationAggregationServiceTest.java`

- [ ] **Step 1: Write failing tests for unread increment/decrement and group state maintenance**

```java
@Test
void createLikeNotification_shouldIncrementUnreadCountWithoutFullCacheEvict() {
    // expect store.increment(...) and group-state upsert
}
```

- [ ] **Step 2: Run the focused tests and verify failure**

Run: `mvn -pl zhicore-notification -Dtest=NotificationUnreadCountContractTest,NotificationAggregationServiceTest,NotificationApplicationServiceTest test`

Expected: FAIL because increment/decrement contracts and group-state repository do not exist.

- [ ] **Step 3: Add group-state schema and unread counter contracts**

```sql
CREATE TABLE IF NOT EXISTS notification_group_state (
    recipient_id BIGINT NOT NULL,
    group_key VARCHAR(256) NOT NULL,
    notification_type VARCHAR(64) NOT NULL,
    latest_notification_id BIGINT NOT NULL,
    total_count INT NOT NULL,
    unread_count INT NOT NULL,
    PRIMARY KEY (recipient_id, group_key)
);
```

- [ ] **Step 4: Implement minimal unread-store atomic APIs**

```java
void incrementTotal(Long userId, NotificationCategory category);
void decrementTotal(Long userId, NotificationCategory category, int delta);
```

- [ ] **Step 5: Update command service to maintain unread counters and group state**

Flow rule:

1. Save inbox row
2. Upsert group state
3. Increment unread cache best-effort
4. Do not evict the full user cache key unless structure changes

- [ ] **Step 6: Rework aggregation query to prefer group-state reads**

Read path:

1. `notification_group_state`
2. fallback DB aggregation query
3. cache snapshot

- [ ] **Step 7: Run notification module tests**

Run: `mvn -pl zhicore-notification -Dtest=NotificationUnreadCountContractTest,NotificationAggregationServiceTest,NotificationApplicationServiceTest,NotificationControllerTest test`

Expected: PASS with no regression to existing unread and list queries.

- [ ] **Step 8: Commit**

```bash
git add docker/postgres-init/02-init-tables.sql \
  zhicore-notification/src/main/java/com/zhicore/notification/application/port/store/NotificationUnreadCountStore.java \
  zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/cache/NotificationRedisKeys.java \
  zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/cache/RedisNotificationUnreadCountStore.java \
  zhicore-notification/src/main/java/com/zhicore/notification/application/service/command/NotificationCommandService.java \
  zhicore-notification/src/main/java/com/zhicore/notification/application/service/query/NotificationQueryService.java \
  zhicore-notification/src/main/java/com/zhicore/notification/application/service/NotificationAggregationService.java \
  zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationGroupState.java \
  zhicore-notification/src/main/java/com/zhicore/notification/domain/repository/NotificationGroupStateRepository.java \
  zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/NotificationGroupStateRepositoryImpl.java \
  zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/mapper/NotificationGroupStateMapper.java \
  zhicore-notification/src/test/java/com/zhicore/notification/application/service/query/NotificationUnreadCountContractTest.java \
  zhicore-notification/src/test/java/com/zhicore/notification/application/service/NotificationAggregationServiceTest.java
git commit -m "feat: optimize interaction notification group state"
```

## Task 5: Plan Author Publish Broadcast Campaigns

**Files:**
- Modify: `docker/postgres-init/02-init-tables.sql`
- Modify: `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/mq/NotificationConsumerGroups.java`
- Modify: `zhicore-integration/src/main/java/com/zhicore/integration/messaging/post/PostPublishedIntegrationEvent.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationCampaign.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationCampaignShard.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/domain/repository/NotificationCampaignRepository.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/application/service/broadcast/PostPublishedCampaignService.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/application/service/broadcast/BroadcastShardPlanner.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/mq/PostPublishedNotificationConsumer.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/NotificationCampaignRepositoryImpl.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/mapper/NotificationCampaignMapper.java`
- Test: `zhicore-notification/src/test/java/com/zhicore/notification/domain/model/NotificationCampaignTest.java`
- Test: `zhicore-notification/src/test/java/com/zhicore/notification/application/service/broadcast/PostPublishedCampaignServiceTest.java`
- Test: `zhicore-notification/src/test/java/com/zhicore/notification/infrastructure/mq/PostPublishedNotificationConsumerTest.java`

- [ ] **Step 1: Write failing tests for campaign creation and shard planning**

```java
@Test
void handlePostPublished_shouldCreateCampaignAndFirstShard() {
    // given a published post and follower count
    // expect campaign persisted with status PLANNED
}
```

- [ ] **Step 2: Run the focused tests and verify failure**

Run: `mvn -pl zhicore-notification,zhicore-integration -Dtest=NotificationCampaignTest,PostPublishedCampaignServiceTest,PostPublishedNotificationConsumerTest test`

Expected: FAIL because campaign domain and consumer are absent.

- [ ] **Step 3: Add campaign and shard tables**

```sql
CREATE TABLE IF NOT EXISTS notification_campaign (...);
CREATE TABLE IF NOT EXISTS notification_campaign_shard (...);
```

- [ ] **Step 4: Extend the publish event payload if required**

Prefer additive fields only:

```java
private final Long authorId;
private final String title;
private final String excerpt;
```

If event expansion is too risky for phase one, document a follow-up compensation read instead.

- [ ] **Step 5: Implement the consumer and planner**

Minimal planner behavior:

1. derive campaign type `POST_PUBLISHED`
2. estimate audience using follow stats
3. create first shard cursor
4. mark campaign `PLANNED`

- [ ] **Step 6: Run focused tests**

Run: `mvn -pl zhicore-notification,zhicore-integration -Dtest=NotificationCampaignTest,PostPublishedCampaignServiceTest,PostPublishedNotificationConsumerTest test`

Expected: PASS with campaign creation covered.

- [ ] **Step 7: Commit**

```bash
git add docker/postgres-init/02-init-tables.sql \
  zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/mq/NotificationConsumerGroups.java \
  zhicore-integration/src/main/java/com/zhicore/integration/messaging/post/PostPublishedIntegrationEvent.java \
  zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationCampaign.java \
  zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationCampaignShard.java \
  zhicore-notification/src/main/java/com/zhicore/notification/domain/repository/NotificationCampaignRepository.java \
  zhicore-notification/src/main/java/com/zhicore/notification/application/service/broadcast/PostPublishedCampaignService.java \
  zhicore-notification/src/main/java/com/zhicore/notification/application/service/broadcast/BroadcastShardPlanner.java \
  zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/mq/PostPublishedNotificationConsumer.java \
  zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/NotificationCampaignRepositoryImpl.java \
  zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/mapper/NotificationCampaignMapper.java \
  zhicore-notification/src/test/java/com/zhicore/notification/domain/model/NotificationCampaignTest.java \
  zhicore-notification/src/test/java/com/zhicore/notification/application/service/broadcast/PostPublishedCampaignServiceTest.java \
  zhicore-notification/src/test/java/com/zhicore/notification/infrastructure/mq/PostPublishedNotificationConsumerTest.java
git commit -m "feat: plan publish notification campaigns"
```

## Task 6: Execute Broadcast Shards And Produce Delivery Plans

**Files:**
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationDelivery.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/domain/repository/NotificationDeliveryRepository.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/application/service/broadcast/BroadcastShardExecutionService.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/application/service/channel/NotificationChannelPlanner.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/NotificationDeliveryRepositoryImpl.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/mapper/NotificationDeliveryMapper.java`
- Modify: `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/feign/UserServiceClient.java`
- Test: `zhicore-notification/src/test/java/com/zhicore/notification/application/service/broadcast/BroadcastShardExecutionServiceTest.java`
- Test: `zhicore-notification/src/test/java/com/zhicore/notification/application/service/channel/NotificationChannelPlannerTest.java`

- [ ] **Step 1: Write failing tests for audience segmentation**

```java
@Test
void executeShard_shouldSplitRecipientsIntoPriorityNormalDigestMuted() {
    // expect preference + dnd + author subscription to produce four buckets
}
```

- [ ] **Step 2: Run focused tests and verify failure**

Run: `mvn -pl zhicore-notification -Dtest=BroadcastShardExecutionServiceTest,NotificationChannelPlannerTest test`

Expected: FAIL because shard executor and delivery planner are not implemented.

- [ ] **Step 3: Add delivery ledger schema**

```sql
CREATE TABLE IF NOT EXISTS notification_delivery (
    delivery_id BIGINT PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    campaign_id BIGINT,
    notification_id BIGINT,
    channel VARCHAR(32) NOT NULL,
    dedupe_key VARCHAR(256) NOT NULL,
    delivery_status VARCHAR(32) NOT NULL
);
```

- [ ] **Step 4: Implement planner precedence**

Decision order:

1. type enabled?
2. author subscription level?
3. channel enabled?
4. DND active?
5. high-priority bypass?

- [ ] **Step 5: Implement shard executor**

Minimal execution:

1. pull follower shard page from `UserFollowerShardClient`
2. evaluate channel plan per recipient
3. create inbox rows for `PRIORITY` and `NORMAL`
4. enqueue digest jobs for `DIGEST`
5. record muted/skip reasons in delivery ledger

- [ ] **Step 6: Run focused tests**

Run: `mvn -pl zhicore-notification -Dtest=BroadcastShardExecutionServiceTest,NotificationChannelPlannerTest,NotificationPreferenceCommandServiceTest test`

Expected: PASS with deterministic segmentation behavior.

- [ ] **Step 7: Commit**

```bash
git add zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationDelivery.java \
  zhicore-notification/src/main/java/com/zhicore/notification/domain/repository/NotificationDeliveryRepository.java \
  zhicore-notification/src/main/java/com/zhicore/notification/application/service/broadcast/BroadcastShardExecutionService.java \
  zhicore-notification/src/main/java/com/zhicore/notification/application/service/channel/NotificationChannelPlanner.java \
  zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/NotificationDeliveryRepositoryImpl.java \
  zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/repository/mapper/NotificationDeliveryMapper.java \
  zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/feign/UserServiceClient.java \
  zhicore-notification/src/test/java/com/zhicore/notification/application/service/broadcast/BroadcastShardExecutionServiceTest.java \
  zhicore-notification/src/test/java/com/zhicore/notification/application/service/channel/NotificationChannelPlannerTest.java
git commit -m "feat: execute broadcast shards with channel planning"
```

## Task 7: Add Inbox, Push, Digest, Email, And SMS Delivery Services

**Files:**
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/application/service/channel/NotificationInboxDeliveryService.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/application/service/channel/NotificationPushDeliveryService.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/application/service/channel/NotificationEmailDeliveryService.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/application/service/channel/NotificationSmsDeliveryService.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationDigestJob.java`
- Create: `zhicore-notification/src/main/java/com/zhicore/notification/application/service/digest/NotificationDigestService.java`
- Modify: `zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/push/NotificationPushService.java`
- Test: `zhicore-notification/src/test/java/com/zhicore/notification/application/service/digest/NotificationDigestServiceTest.java`
- Test: `zhicore-notification/src/test/java/com/zhicore/notification/infrastructure/push/NotificationPushServiceTest.java`

- [ ] **Step 1: Write failing tests for DND and digest behavior**

```java
@Test
void scheduleDigest_shouldAccumulatePublishNotificationsDuringQuietHours() {
    // expect one digest job instead of immediate push delivery
}
```

- [ ] **Step 2: Run focused tests and verify failure**

Run: `mvn -pl zhicore-notification -Dtest=NotificationDigestServiceTest,NotificationPushServiceTest test`

Expected: FAIL because digest service and reserved email/SMS abstractions do not exist.

- [ ] **Step 3: Add minimal channel service interfaces**

```java
public interface ChannelDeliveryService {
    NotificationChannel channel();
    DeliveryResult deliver(NotificationDelivery delivery);
}
```

- [ ] **Step 4: Implement inbox and push first, keep email/SMS as reserved no-op adapters**

Rules:

1. `INBOX` must persist inbox records
2. `PUSH` may call existing `NotificationPushService`
3. `EMAIL` and `SMS` should record `PLANNED` or `SKIPPED_UNCONFIGURED`, not fail the transaction

- [ ] **Step 5: Implement digest service**

Digest job behavior:

1. collect pending `DIGEST` deliveries by user and window
2. render a compact payload
3. emit one inbox row and optional push/email summary after quiet hours

- [ ] **Step 6: Run focused tests**

Run: `mvn -pl zhicore-notification -Dtest=NotificationDigestServiceTest,NotificationPushServiceTest,NotificationControllerTest test`

Expected: PASS with DND-to-digest conversion covered.

- [ ] **Step 7: Commit**

```bash
git add zhicore-notification/src/main/java/com/zhicore/notification/application/service/channel/NotificationInboxDeliveryService.java \
  zhicore-notification/src/main/java/com/zhicore/notification/application/service/channel/NotificationPushDeliveryService.java \
  zhicore-notification/src/main/java/com/zhicore/notification/application/service/channel/NotificationEmailDeliveryService.java \
  zhicore-notification/src/main/java/com/zhicore/notification/application/service/channel/NotificationSmsDeliveryService.java \
  zhicore-notification/src/main/java/com/zhicore/notification/domain/model/NotificationDigestJob.java \
  zhicore-notification/src/main/java/com/zhicore/notification/application/service/digest/NotificationDigestService.java \
  zhicore-notification/src/main/java/com/zhicore/notification/infrastructure/push/NotificationPushService.java \
  zhicore-notification/src/test/java/com/zhicore/notification/application/service/digest/NotificationDigestServiceTest.java \
  zhicore-notification/src/test/java/com/zhicore/notification/infrastructure/push/NotificationPushServiceTest.java
git commit -m "feat: add channel delivery and digest handling"
```

## Task 8: Consolidate Query APIs, Config, Metrics, And Verification

**Files:**
- Modify: `zhicore-notification/src/main/java/com/zhicore/notification/interfaces/controller/NotificationQueryController.java`
- Modify: `zhicore-notification/src/main/java/com/zhicore/notification/application/service/query/NotificationQueryService.java`
- Modify: `zhicore-notification/src/main/resources/application.yml`
- Modify: `config/nacos/zhicore-notification.yml`
- Modify: `database/test-data/scripts/README-NOTIFICATIONS.md`
- Modify: `docs/architecture/zhicore-notification-platform-design.md`
- Test: `zhicore-notification/src/test/java/com/zhicore/notification/interfaces/controller/NotificationControllerTest.java`
- Test: `zhicore-notification/src/test/java/com/zhicore/notification/integration/NotificationAggregationPropertiesIntegrationTest.java`

- [ ] **Step 1: Write failing controller tests for new unread breakdown and preference-aware queries**

```java
@Test
void getUnreadCount_shouldReturnCategoryBreakdown() {
    // expect total + interaction + content + system + security
}
```

- [ ] **Step 2: Run focused tests and verify failure**

Run: `mvn -pl zhicore-notification -Dtest=NotificationControllerTest,NotificationAggregationPropertiesIntegrationTest test`

Expected: FAIL because controller response shape and config fields are not yet updated.

- [ ] **Step 3: Extend query contracts**

Expose:

1. unread breakdown
2. group-state-backed notification list
3. author subscription state for current user

- [ ] **Step 4: Add configuration and metric names**

Example config block:

```yaml
notification:
  broadcast:
    shard-size: 2000
    priority-push-threshold: 100000
  digest:
    default-delay-minutes: 30
```

- [ ] **Step 5: Update docs and smoke-data notes**

Document:

1. how to seed high-follower test data
2. how to verify DND behavior
3. what counters and logs to watch during fan-out tests

- [ ] **Step 6: Run verification suite**

Run:

```bash
mvn -pl zhicore-notification -Dtest=NotificationControllerTest,NotificationAggregationServiceTest,NotificationUnreadCountContractTest,PostPublishedNotificationConsumerTest test
mvn -pl zhicore-user -Dtest=FollowQueryControllerFollowerShardTest,FollowQueryServiceTest test
```

Expected: PASS in both modules.

- [ ] **Step 7: Commit**

```bash
git add zhicore-notification/src/main/java/com/zhicore/notification/interfaces/controller/NotificationQueryController.java \
  zhicore-notification/src/main/java/com/zhicore/notification/application/service/query/NotificationQueryService.java \
  zhicore-notification/src/main/resources/application.yml \
  config/nacos/zhicore-notification.yml \
  database/test-data/scripts/README-NOTIFICATIONS.md \
  docs/architecture/zhicore-notification-platform-design.md \
  zhicore-notification/src/test/java/com/zhicore/notification/interfaces/controller/NotificationControllerTest.java \
  zhicore-notification/src/test/java/com/zhicore/notification/integration/NotificationAggregationPropertiesIntegrationTest.java
git commit -m "feat: finalize notification platform query and config"
```

## End-To-End Validation Checklist

- [ ] Seed or prepare a test author with at least 10,000 followers.
- [ ] Publish one post and verify a `notification_campaign` row is created.
- [ ] Verify shards progress from `PLANNED` to `RUNNING` to `COMPLETED`.
- [ ] Verify `PRIORITY` followers receive inbox plus push.
- [ ] Verify `NORMAL` followers receive inbox and optional push according to preference.
- [ ] Verify `DIGEST` followers do not get immediate push and instead accumulate digest work.
- [ ] Verify `MUTED` followers are skipped with a recorded reason.
- [ ] Verify unread counts increment without a full user-cache wipe.
- [ ] Verify marking inbox rows read decrements unread counters correctly.
- [ ] Verify existing like/comment/follow notifications still work.

## Rollout Order

1. Deploy schema additions first.
2. Deploy preference and DND APIs with write-path disabled by feature flag.
3. Deploy follower-shard API in `zhicore-user`.
4. Enable optimized interaction-notification path.
5. Deploy broadcast campaign creation with shard execution disabled.
6. Enable shard execution for internal test authors only.
7. Enable digest path.
8. Gradually enable publish notification fan-out by author cohort.

## Open Decisions To Resolve During Execution

1. Whether `PostPublishedIntegrationEvent` should be expanded immediately or use compensation reads first.
2. Whether `DIGEST` followers should get an immediate low-priority inbox row or only a later digest row.
3. Whether `EMAIL` should support digest in phase one or remain reserved-only.
4. What exact threshold defines `PRIORITY` push audience for high-follower authors.

## Notes For Implementers

1. Do not rework the entire notification module in one giant commit.
2. Do not delete the existing interaction notification flow before group-state and unread increment coverage is in place.
3. Keep `EMAIL` and `SMS` as capability placeholders unless product explicitly enables providers.
4. If schema evolution becomes too large for one step, split Task 1 and Task 2 schema changes into separate commits before runtime code.
