package com.zhicore.user.infrastructure.repository;

import com.zhicore.common.util.DateTimeUtils;
import com.zhicore.user.domain.model.UserBlock;
import com.zhicore.user.domain.repository.UserBlockRepository;
import com.zhicore.user.infrastructure.repository.mapper.UserBlockMapper;
import com.zhicore.user.infrastructure.repository.po.UserBlockPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户拉黑仓储实现
 *
 * @author ZhiCore Team
 */
@Repository
@RequiredArgsConstructor
public class UserBlockRepositoryImpl implements UserBlockRepository {

    private final UserBlockMapper userBlockMapper;

    @Override
    public void save(UserBlock block) {
        UserBlockPO po = toPO(block);
        userBlockMapper.insert(po);
    }

    @Override
    public void delete(Long blockerId, Long blockedId) {
        userBlockMapper.deleteByBlockerAndBlocked(blockerId, blockedId);
    }

    @Override
    public boolean exists(Long blockerId, Long blockedId) {
        return userBlockMapper.exists(blockerId, blockedId);
    }

    @Override
    public List<UserBlock> findByBlockerId(Long blockerId, int page, int size) {
        int offset = (page - 1) * size;
        List<UserBlockPO> poList = userBlockMapper.selectByBlockerId(blockerId, offset, size);
        return poList.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private UserBlock toDomain(UserBlockPO po) {
        if (po == null) {
            return null;
        }
        return UserBlock.reconstitute(
                po.getBlockerId(),
                po.getBlockedId(),
                po.getCreatedAt()
        );
    }

    private UserBlockPO toPO(UserBlock block) {
        UserBlockPO po = new UserBlockPO();
        po.setBlockerId(block.getBlockerId());
        po.setBlockedId(block.getBlockedId());
        po.setCreatedAt(block.getCreatedAt());
        return po;
    }
}
