package com.blog.api.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户简要信息 DTO
 */
@Data
public class UserSimpleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String userName;
    private String nickname;
    private String avatar;
    
    /**
     * 资料版本号（用于事件顺序性保证）
     */
    private Long profileVersion;

    // Alias methods for compatibility
    public String getNickName() {
        return nickname;
    }

    public void setNickName(String nickName) {
        this.nickname = nickName;
    }

    public String getAvatarUrl() {
        return avatar;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatar = avatarUrl;
    }
    
    /**
     * 获取头像ID（兼容方法）
     * 
     * @return 头像ID
     */
    public String getAvatarId() {
        return avatar;
    }
    
    /**
     * 设置头像ID（兼容方法）
     * 
     * @param avatarId 头像ID
     */
    public void setAvatarId(String avatarId) {
        this.avatar = avatarId;
    }
}
