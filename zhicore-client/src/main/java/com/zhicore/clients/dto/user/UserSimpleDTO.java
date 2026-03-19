package com.zhicore.api.dto.user;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户简要信息 DTO
 */
@Data
public class UserSimpleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String userName;
    private String nickname;
    private String avatarId;
    
    /**
     * 资料版本号（用于事件顺序性保证）
     */
    private Long profileVersion;

}
