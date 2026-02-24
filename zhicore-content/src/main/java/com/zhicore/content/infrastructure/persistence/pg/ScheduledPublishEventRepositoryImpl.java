package com.zhicore.content.infrastructure.persistence.pg;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhicore.content.application.port.repo.ScheduledPublishEventRepository;
import com.zhicore.content.infrastructure.persistence.pg.entity.ScheduledPublishEventEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.ScheduledPublishEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 定时发布事件仓储实现（R1）
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ScheduledPublishEventRepositoryImpl implements ScheduledPublishEventRepository {

    private final ScheduledPublishEventMapper mapper;

    @Override
    public LocalDateTime dbNow() {
        return mapper.selectDbNow();
    }

    @Override
    public void save(ScheduledPublishEventEntity entity) {
        mapper.insert(entity);
    }

    @Override
    public Optional<ScheduledPublishEventEntity> findByEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                mapper.selectOne(
                        new LambdaQueryWrapper<ScheduledPublishEventEntity>()
                                .eq(ScheduledPublishEventEntity::getEventId, eventId)
                                .last("LIMIT 1")
                )
        );
    }

    @Override
    public void update(ScheduledPublishEventEntity entity) {
        mapper.updateById(entity);
    }

    @Override
    public List<ScheduledPublishEventEntity> findDueScheduledPending(LocalDateTime dbNow, LocalDateTime cooldownBefore, int limit) {
        return mapper.findDueScheduledPending(dbNow, cooldownBefore, limit);
    }

    @Override
    public List<ScheduledPublishEventEntity> findStaleScheduledPending(LocalDateTime dbNow, LocalDateTime staleBefore, int limit) {
        return mapper.findStaleScheduledPending(dbNow, staleBefore, limit);
    }

    @Override
    public int casUpdateLastEnqueueAt(ScheduledPublishEventEntity event, LocalDateTime dbNow, String newEventId) {
        if (event == null || event.getId() == null) {
            return 0;
        }
        if (event.getLastEnqueueAt() == null) {
            return mapper.casUpdateLastEnqueueAtWhenNull(event.getId(), event.getScheduledAt(), dbNow, newEventId);
        }
        return mapper.casUpdateLastEnqueueAt(event.getId(), event.getScheduledAt(), event.getLastEnqueueAt(), dbNow, newEventId);
    }

    @Override
    public Optional<ScheduledPublishEventEntity> findActiveByPostId(Long postId) {
        if (postId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                mapper.selectOne(
                        new LambdaQueryWrapper<ScheduledPublishEventEntity>()
                                .eq(ScheduledPublishEventEntity::getPostId, postId)
                                .in(
                                        ScheduledPublishEventEntity::getStatus,
                                        ScheduledPublishEventEntity.ScheduledPublishStatus.PENDING,
                                        ScheduledPublishEventEntity.ScheduledPublishStatus.SCHEDULED_PENDING
                                )
                                .orderByDesc(ScheduledPublishEventEntity::getUpdatedAt)
                                .last("LIMIT 1")
                )
        );
    }
}
