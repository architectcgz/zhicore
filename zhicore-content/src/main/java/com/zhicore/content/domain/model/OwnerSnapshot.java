package com.zhicore.content.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 作者信息快照值对象
 * 
 * 存储作者的基本信息快照，用于提升查询性能。
 * 包含版本号机制，防止消息乱序导致的数据不一致。
 * 
 * @author ZhiCore Team
 */
@Getter
public final class OwnerSnapshot {
    
    /**
     * 作者ID（值对象）
     */
    private final UserId ownerId;
    
    /**
     * 作者昵称
     */
    private final String name;
    
    /**
     * 作者头像文件ID（UUIDv7格式）
     */
    private final String avatarId;
    
    /**
     * 作者资料版本号
     * 
     * 用于防止消息乱序，只有当新版本号大于当前版本号时才更新。
     */
    private final Long profileVersion;
    
    /**
     * 构造函数
     * 使用 @JsonCreator 支持 Jackson 反序列化
     * 
     * @param ownerId 作者ID（值对象）
     * @param name 作者昵称
     * @param avatarId 作者头像文件ID
     * @param profileVersion 作者资料版本号
     */
    @JsonCreator
    public OwnerSnapshot(@JsonProperty("ownerId") UserId ownerId,
                        @JsonProperty("name") String name,
                        @JsonProperty("avatarId") String avatarId,
                        @JsonProperty("profileVersion") Long profileVersion) {
        this.ownerId = ownerId;
        this.name = name;
        this.avatarId = avatarId;
        this.profileVersion = profileVersion;
    }
    
    /**
     * 创建默认快照（未知用户）
     * 
     * @param ownerId 作者ID（值对象）
     * @return OwnerSnapshot 实例
     */
    public static OwnerSnapshot createDefault(UserId ownerId) {
        return new OwnerSnapshot(ownerId, "未知用户", null, 0L);
    }

    /**
     * 兼容旧字段命名
     */
    public String getNickname() {
        return this.name;
    }
    
    /**
     * 版本比较：判断当前快照是否比另一个快照更新
     * 
     * @param other 另一个快照
     * @return true 如果当前快照更新
     */
    public boolean isNewerThan(OwnerSnapshot other) {
        if (other == null) {
            return true;
        }
        return this.profileVersion > other.profileVersion;
    }
    
    /**
     * 检查是否为默认快照
     * 
     * @return true 如果是默认快照
     */
    public boolean isDefault() {
        return "未知用户".equals(this.name) && 
               (this.profileVersion == null || this.profileVersion == 0L);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OwnerSnapshot that = (OwnerSnapshot) o;
        return ownerId.equals(that.ownerId) &&
                profileVersion.equals(that.profileVersion);
    }
    
    @Override
    public int hashCode() {
        int result = ownerId.hashCode();
        result = 31 * result + profileVersion.hashCode();
        return result;
    }
    
    @Override
    public String toString() {
        return "OwnerSnapshot{" +
                "ownerId=" + ownerId +
                ", name='" + name + '\'' +
                ", avatarId='" + avatarId + '\'' +
                ", profileVersion=" + profileVersion +
                '}';
    }
}
