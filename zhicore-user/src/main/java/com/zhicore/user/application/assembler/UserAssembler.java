package com.zhicore.user.application.assembler;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.util.DateTimeUtils;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.application.query.view.UserManageView;
import com.zhicore.user.application.query.view.UserSimpleView;
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
     * 将领域对象转换为简要信息查询视图
     *
     * @param user 用户领域对象
     * @return 用户简要信息查询视图
     */
    public static UserSimpleView toSimpleView(User user) {
        if (user == null) {
            return null;
        }

        UserSimpleView dto = new UserSimpleView();
        dto.setId(user.getId());
        dto.setUserName(user.getUserName());
        dto.setNickname(user.getNickName());
        dto.setAvatarId(user.getAvatarId());
        dto.setProfileVersion(user.getProfileVersion());
        return dto;
    }

    /**
     * 将内部查询视图转换为对外 DTO。
     *
     * @param view 用户简要信息查询视图
     * @return 用户简要信息 DTO
     */
    public static UserSimpleDTO toSimpleDTO(UserSimpleView view) {
        if (view == null) {
            return null;
        }

        UserSimpleDTO dto = new UserSimpleDTO();
        dto.setId(view.getId());
        dto.setUserName(view.getUserName());
        dto.setNickname(view.getNickname());
        dto.setAvatarId(view.getAvatarId());
        dto.setProfileVersion(view.getProfileVersion());
        return dto;
    }

    /**
     * 将领域对象转换为管理侧查询视图。
     *
     * @param user 用户领域对象
     * @return 管理侧查询视图
     */
    public static UserManageView toManageView(User user) {
        if (user == null) {
            return null;
        }

        return UserManageView.builder()
                .id(user.getId())
                .username(user.getUserName())
                .email(user.getEmail())
                .nickname(user.getNickName())
                .avatar(user.getAvatarId())
                .status(user.getStatus().name())
                .createdAt(user.getCreatedAt())
                .roles(user.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toList()))
                .build();
    }

    /**
     * 将管理侧查询视图转换为对外 DTO。
     *
     * @param view 管理侧查询视图
     * @return 管理侧 DTO
     */
    public static com.zhicore.api.dto.admin.UserManageDTO toManageDTO(UserManageView view) {
        if (view == null) {
            return null;
        }

        return com.zhicore.api.dto.admin.UserManageDTO.builder()
                .id(view.getId())
                .username(view.getUsername())
                .email(view.getEmail())
                .nickname(view.getNickname())
                .avatar(view.getAvatar())
                .status(view.getStatus())
                .createdAt(view.getCreatedAt())
                .roles(view.getRoles())
                .build();
    }
}
