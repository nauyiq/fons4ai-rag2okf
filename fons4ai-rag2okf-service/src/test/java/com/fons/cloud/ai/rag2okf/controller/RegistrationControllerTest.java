package com.fons.cloud.ai.rag2okf.controller;

import com.fons.cloud.ai.rag2okf.application.identity.RegistrationApplicationService;
import com.fons.cloud.ai.rag2okf.common.request.RegistrationRequest;
import com.fons.cloud.ai.rag2okf.common.response.LoginResponse;
import com.fons.cloud.common.result.R;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 注册 HTTP 契约测试。
 *
 * @author hongqy
 */
class RegistrationControllerTest {

    @Test
    void shouldReturnTheTokenOnlyInTheUnifiedRegistrationResponseData() {
        RegistrationApplicationService registrationService = mock(RegistrationApplicationService.class);
        when(registrationService.register(any())).thenReturn("opaque-token");
        RegistrationController controller = new RegistrationController(registrationService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        R<LoginResponse> response = controller.register(
                new RegistrationRequest("new@example.com", "secure-pass", "secure-pass", "新用户", true),
                request
        );

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().token()).isEqualTo("opaque-token");
    }
}
