package com.zhicore.user.application.assembler;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.util.DateTimeUtils;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.domain.model.Role;
import com.zhicore.user.domain.model.User;

import java.util.stream.Collectors;

/**
 * 用户装配器
 * 
 * 负责领域对象与DTO之间的转换
 *
 * @author ZhiCore Team
 */
public class UserAssembler {

    private UserAssembler() {
        // 工具类，禁止实例化
    }

    /**
     * 将领域对象转换为视图对象
     *
     * @param user 用户领域对象
     * @return 用户视图对象
     */
    public static UserVO toVO(User user) {
        if (user == null) {
            return null;
        }

        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUserName(user.getUserName());
        vo.setNickName(user.getNickName());
        vo.setEmail(user.getEmail());
        vo.setAvatarUrl(user.getAvatarId());
        vo.setBio(user.getBio());
        vo.setStatus(user.getStatus().getCode());
        vo.setEmailConfirmed(user.isEmailConfirmed());
        vo.setCreatedAt(DateTimeUtils.toOffsetDateTime(user.getCreatedAt()));

        // 转换角色
        if (user.getRoles() != null) {
            vo.setRoles(user.getRoles().stream()
                    .map(Role::getName)
                    .collect(Collectors.toSet()));
        }

        return vo;
    }

    /**
     * 将领域对象转换为简要信息DTO
     *
     * @param user 用户领域对象
     * @return 用户简要信息DTO
     */
    public static UserSimpleDTO toSimpleDTO(User user) {
        if (user == null) {
            return null;
        }

        UserSimpleDTO dto = new UserSimpleDTO();
        dto.setId(user.getId());
        dto.setUserName(user.getUserName());
        dto.setNickName(user.getNickName());  // 使用别名方法
        dto.setAvatarUrl(user.getAvatarId());  // 使用别名方法
        return dto;
    }
}
