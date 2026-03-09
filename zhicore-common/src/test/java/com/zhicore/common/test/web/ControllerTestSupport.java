package com.zhicore.common.test.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.exception.GlobalExceptionHandler;
import jakarta.validation.Validation;
import jakarta.validation.executable.ExecutableValidator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 控制器测试共享基座，统一 MockMvc、异常处理器和方法参数校验器。
 */
public abstract class ControllerTestSupport {

    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected final ExecutableValidator executableValidator =
            Validation.buildDefaultValidatorFactory().getValidator().forExecutables();

    protected MockMvc buildMockMvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
