package com.zhicore.message.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.common.result.ResultCode;
import com.zhicore.message.application.dto.MessageVO;
import com.zhicore.message.application.service.MessageApplicationService;
import com.zhicore.message.domain.model.MessageType;
import com.zhicore.message.interfaces.dto.request.SendMessageRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.executable.ExecutableValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageController 测试")
class MessageControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MessageApplicationService messageApplicationService;

    private MockMvc mockMvc;
    private MessageController messageController;
    private ExecutableValidator executableValidator;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        executableValidator = validator.forExecutables();
        messageController = new MessageController(messageApplicationService);
        mockMvc = MockMvcBuilders.standaloneSetup(messageController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("应该成功发送文本消息")
    void shouldSendTextMessage() throws Exception {
        SendMessageRequest request = new SendMessageRequest();
        request.setReceiverId(2L);
        request.setType(MessageType.TEXT);
        request.setContent("hello");

        MessageVO message = MessageVO.builder()
                .id(1L)
                .receiverId(2L)
                .type(MessageType.TEXT)
                .content("hello")
                .build();
        when(messageApplicationService.sendMessage(2L, MessageType.TEXT, "hello", null))
                .thenReturn(message);

        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.content").value("hello"));

        verify(messageApplicationService).sendMessage(eq(2L), eq(MessageType.TEXT), eq("hello"), eq(null));
    }

    @Test
    @DisplayName("文本消息缺少内容时应该返回400")
    void shouldRejectTextMessageWithoutContent() throws Exception {
        SendMessageRequest request = new SendMessageRequest();
        request.setReceiverId(2L);
        request.setType(MessageType.TEXT);

        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("文本消息内容不能为空"));
    }

    @Test
    @DisplayName("图片消息缺少URL时应该返回400")
    void shouldRejectImageMessageWithoutMediaUrl() throws Exception {
        SendMessageRequest request = new SendMessageRequest();
        request.setReceiverId(2L);
        request.setType(MessageType.IMAGE);

        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("图片或文件消息URL不能为空"));
    }

    @Test
    @DisplayName("发送消息被限制时应该返回业务错误响应")
    void shouldReturnBusinessErrorWhenSendMessageForbidden() throws Exception {
        SendMessageRequest request = new SendMessageRequest();
        request.setReceiverId(2L);
        request.setType(MessageType.TEXT);
        request.setContent("hello");
        when(messageApplicationService.sendMessage(2L, MessageType.TEXT, "hello", null))
                .thenThrow(new BusinessException(ResultCode.USER_BLOCKED_CANNOT_MESSAGE, "您已被对方拉黑，无法发送消息"));

        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.USER_BLOCKED_CANNOT_MESSAGE.getCode()))
                .andExpect(jsonPath("$.message").value("您已被对方拉黑，无法发送消息"));
    }

    @Test
    @DisplayName("撤回消息ID非法时应该返回400")
    void shouldRejectRecallWhenMessageIdIsInvalid() throws Exception {
        var method = MessageController.class.getMethod("recallMessage", Long.class);
        var violations = executableValidator.validateParameters(messageController, method, new Object[]{0L});

        org.junit.jupiter.api.Assertions.assertEquals(1, violations.size());
        org.junit.jupiter.api.Assertions.assertEquals("消息ID必须为正数", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("消息历史limit非法时应该返回400")
    void shouldRejectHistoryWhenLimitIsInvalid() throws Exception {
        var method = MessageController.class.getMethod("getMessageHistory", Long.class, Long.class, int.class);
        var violations = executableValidator.validateParameters(messageController, method, new Object[]{1L, null, 0});

        org.junit.jupiter.api.Assertions.assertEquals(1, violations.size());
        org.junit.jupiter.api.Assertions.assertEquals("每页数量必须为正数", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("标记已读会话ID非法时应该返回400")
    void shouldRejectMarkAsReadWhenConversationIdIsInvalid() throws Exception {
        var method = MessageController.class.getMethod("markAsRead", Long.class);
        var violations = executableValidator.validateParameters(messageController, method, new Object[]{0L});

        org.junit.jupiter.api.Assertions.assertEquals(1, violations.size());
        org.junit.jupiter.api.Assertions.assertEquals("会话ID必须为正数", violations.iterator().next().getMessage());
    }
}
