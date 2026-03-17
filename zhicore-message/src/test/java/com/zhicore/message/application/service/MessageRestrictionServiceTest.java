package com.zhicore.message.application.service;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.message.application.port.user.UserMessagingPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageRestrictionService 测试")
class MessageRestrictionServiceTest {

    @Mock
    private UserMessagingPort userMessagingPort;

    @InjectMocks
    private MessageRestrictionService messageRestrictionService;

    @Test
    @DisplayName("拉黑状态不可验证时应该拒绝发送消息")
    void shouldRejectWhenBlockStatusCannotBeVerified() {
        mockReceiverExists(2L);
        when(userMessagingPort.isBlocked(2L, 1L))
                .thenThrow(new RuntimeException("用户服务已降级"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> messageRestrictionService.checkCanSendMessage(1L, 2L));

        assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), exception.getCode());
        assertEquals("无法验证拉黑状态，请稍后重试", exception.getMessage());
    }

    @Test
    @DisplayName("陌生人关系不可验证时应该拒绝发送消息")
    void shouldRejectWhenStrangerStatusCannotBeVerified() {
        mockReceiverExists(2L);
        when(userMessagingPort.isBlocked(2L, 1L))
                .thenReturn(false);
        when(userMessagingPort.isFollowing(1L, 2L))
                .thenThrow(new RuntimeException("用户服务已降级"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> messageRestrictionService.checkCanSendMessage(1L, 2L));

        assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), exception.getCode());
        assertEquals("无法验证关注关系，请稍后重试", exception.getMessage());
    }

    @Test
    @DisplayName("陌生人消息设置不可验证时应该拒绝发送消息")
    void shouldRejectWhenStrangerMessageSettingCannotBeVerified() {
        mockReceiverExists(2L);
        when(userMessagingPort.isBlocked(2L, 1L))
                .thenReturn(false);
        when(userMessagingPort.isFollowing(1L, 2L))
                .thenReturn(true);
        when(userMessagingPort.isFollowing(2L, 1L))
                .thenReturn(false);
        when(userMessagingPort.isStrangerMessageAllowed(2L))
                .thenThrow(new RuntimeException("用户服务已降级"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> messageRestrictionService.checkCanSendMessage(1L, 2L));

        assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), exception.getCode());
        assertEquals("无法验证陌生人消息设置，请稍后重试", exception.getMessage());
    }

    @Test
    @DisplayName("关系校验成功且允许发送时不应抛出异常")
    void shouldAllowWhenRestrictionsPass() {
        mockReceiverExists(2L);
        when(userMessagingPort.isBlocked(2L, 1L))
                .thenReturn(false);
        when(userMessagingPort.isFollowing(1L, 2L))
                .thenReturn(true);
        when(userMessagingPort.isFollowing(2L, 1L))
                .thenReturn(true);

        assertDoesNotThrow(() -> messageRestrictionService.checkCanSendMessage(1L, 2L));
    }

    @Test
    @DisplayName("单向关注但接收方允许陌生人消息时应允许发送")
    void shouldAllowWhenReceiverAcceptsStrangerMessages() {
        mockReceiverExists(2L);
        when(userMessagingPort.isBlocked(2L, 1L))
                .thenReturn(false);
        when(userMessagingPort.isFollowing(1L, 2L))
                .thenReturn(true);
        when(userMessagingPort.isFollowing(2L, 1L))
                .thenReturn(false);
        when(userMessagingPort.isStrangerMessageAllowed(2L))
                .thenReturn(true);

        assertDoesNotThrow(() -> messageRestrictionService.checkCanSendMessage(1L, 2L));
    }

    @Test
    @DisplayName("单向关注且接收方关闭陌生人消息时应拒绝发送")
    void shouldRejectWhenReceiverDisablesStrangerMessages() {
        mockReceiverExists(2L);
        when(userMessagingPort.isBlocked(2L, 1L))
                .thenReturn(false);
        when(userMessagingPort.isFollowing(1L, 2L))
                .thenReturn(true);
        when(userMessagingPort.isFollowing(2L, 1L))
                .thenReturn(false);
        when(userMessagingPort.isStrangerMessageAllowed(2L))
                .thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> messageRestrictionService.checkCanSendMessage(1L, 2L));

        assertEquals(ResultCode.OPERATION_NOT_ALLOWED.getCode(), exception.getCode());
        assertEquals("对方不接收陌生人消息，请先关注对方", exception.getMessage());
    }

    @Test
    @DisplayName("给自己发消息时应该返回明确业务码")
    void shouldRejectWhenSenderMessagesSelf() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> messageRestrictionService.checkCanSendMessage(1L, 1L));

        assertEquals(ResultCode.CANNOT_MESSAGE_SELF.getCode(), exception.getCode());
        assertEquals(ResultCode.CANNOT_MESSAGE_SELF.getMessage(), exception.getMessage());
    }

    private void mockReceiverExists(Long receiverId) {
        UserSimpleDTO user = new UserSimpleDTO();
        user.setId(receiverId);
        user.setUserName("receiver");
        when(userMessagingPort.getUserSimple(receiverId))
                .thenReturn(user);
    }
}
