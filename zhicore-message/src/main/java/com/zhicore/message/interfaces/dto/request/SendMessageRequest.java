package com.zhicore.message.interfaces.dto.request;

import com.zhicore.message.domain.model.MessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.util.StringUtils;

/**
 * 发送消息请求
 *
 * @author ZhiCore Team
 */
@Schema(description = "发送消息请求")
@Data
public class SendMessageRequest {

    /**
     * 接收者ID
     */
    @Schema(description = "接收者用户ID", example = "1001", required = true)
    @NotNull(message = "接收者ID不能为空")
    @Min(value = 1, message = "接收者ID必须为正数")
    private Long receiverId;

    /**
     * 消息类型
     */
    @Schema(description = "消息类型：TEXT-文本消息, IMAGE-图片消息, FILE-文件消息", example = "TEXT", required = true)
    @NotNull(message = "消息类型不能为空")
    private MessageType type;

    /**
     * 消息内容（文本消息必填，文件消息为文件名）
     */
    @Schema(description = "消息内容，文本消息时为消息文本，文件消息时为文件名", example = "你好，这是一条测试消息")
    private String content;

    /**
     * 媒体URL（图片/文件消息必填）
     */
    @Schema(description = "媒体文件URL，图片或文件消息时必填", example = "https://example.com/files/image.jpg")
    private String mediaUrl;

    @AssertTrue(message = "文本消息内容不能为空")
    public boolean isTextContentValid() {
        return type != MessageType.TEXT || StringUtils.hasText(content);
    }

    @AssertTrue(message = "图片或文件消息URL不能为空")
    public boolean isMediaUrlValid() {
        return type == null || type == MessageType.TEXT || StringUtils.hasText(mediaUrl);
    }
}
