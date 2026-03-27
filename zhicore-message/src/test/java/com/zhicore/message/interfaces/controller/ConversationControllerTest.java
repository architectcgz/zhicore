package com.zhicore.message.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.common.result.ResultCode;
import com.zhicore.message.application.dto.ConversationVO;
import com.zhicore.message.application.service.query.ConversationQueryService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.executable.ExecutableValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationQueryController 测试")
class ConversationControllerTest {

    @Mock
    private ConversationQueryService conversationQueryService;

    private MockMvc mockMvc;
    private ConversationQueryController conversationController;
    private ExecutableValidator executableValidator;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        executableValidator = validator.forExecutables();
        conversationController = new ConversationQueryController(conversationQueryService);
        mockMvc = MockMvcBuilders.standaloneSetup(conversationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("未登录获取会话列表时应该返回未授权")
    void shouldReturnUnauthorizedWhenGetConversationListWithoutLogin() throws Exception {
        UserContext.clear();

        mockMvc.perform(get("/api/v1/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    @DisplayName("应该成功获取会话列表")
    void shouldGetConversationList() throws Exception {
        UserContext.setUser(new UserContext.UserInfo("1", "sender"));

        ConversationVO conversation = ConversationVO.builder()
                .id(1L)
                .otherUserId(2L)
                .build();
        when(conversationQueryService.getConversationList(null, 20))
                .thenReturn(List.of(conversation));

        mockMvc.perform(get("/api/v1/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value("1"))
                .andExpect(jsonPath("$.data[0].otherUserId").value("2"));
    }

    @Test
    @DisplayName("应该支持通过消息模块前缀获取会话列表")
    void shouldGetConversationListViaMessageModuleRoute() throws Exception {
        UserContext.setUser(new UserContext.UserInfo("1", "sender"));

        ConversationVO conversation = ConversationVO.builder()
                .id(1L)
                .otherUserId(2L)
                .build();
        when(conversationQueryService.getConversationList(null, 20))
                .thenReturn(List.of(conversation));

        mockMvc.perform(get("/api/v1/messages/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value("1"))
                .andExpect(jsonPath("$.data[0].otherUserId").value("2"));
    }

    @Test
    @DisplayName("会话不存在时应该返回业务错误响应")
    void shouldReturnBusinessErrorWhenConversationNotFound() throws Exception {
        UserContext.setUser(new UserContext.UserInfo("1", "sender"));

        when(conversationQueryService.getConversation(99L))
                .thenThrow(new BusinessException(ResultCode.CONVERSATION_NOT_FOUND));

        mockMvc.perform(get("/api/v1/conversations/{conversationId}", 99L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.CONVERSATION_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(ResultCode.CONVERSATION_NOT_FOUND.getMessage()));
    }

    @Test
    @DisplayName("会话列表limit非法时应该返回400")
    void shouldRejectConversationListWhenLimitIsInvalid() throws Exception {
        var method = ConversationQueryController.class.getMethod("getConversationList", Long.class, int.class);
        var violations = executableValidator.validateParameters(conversationController, method, new Object[]{null, 0});

        org.junit.jupiter.api.Assertions.assertEquals(1, violations.size());
        org.junit.jupiter.api.Assertions.assertEquals("每页数量必须为正数", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("会话ID非法时应该返回400")
    void shouldRejectConversationWhenConversationIdIsInvalid() throws Exception {
        var method = ConversationQueryController.class.getMethod("getConversation", Long.class);
        var violations = executableValidator.validateParameters(conversationController, method, new Object[]{0L});

        org.junit.jupiter.api.Assertions.assertEquals(1, violations.size());
        org.junit.jupiter.api.Assertions.assertEquals("会话ID必须为正数", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("用户ID非法时应该返回400")
    void shouldRejectConversationByUserWhenUserIdIsInvalid() throws Exception {
        var method = ConversationQueryController.class.getMethod("getConversationByUser", Long.class);
        var violations = executableValidator.validateParameters(conversationController, method, new Object[]{0L});

        org.junit.jupiter.api.Assertions.assertEquals(1, violations.size());
        org.junit.jupiter.api.Assertions.assertEquals("用户ID必须为正数", violations.iterator().next().getMessage());
    }
}
