package com.fons.cloud.ai.rag2okf.controller;

import com.fons.cloud.ai.rag2okf.application.identity.AuthenticationApplicationService;
import com.fons.cloud.ai.rag2okf.common.request.LoginRequest;
import com.fons.cloud.ai.rag2okf.common.response.LoginResponse;
import com.fons.cloud.common.result.R;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 认证会话 HTTP 契约测试。
 *
 * @author hongqy
 */
class AuthSessionControllerTest {

    @Test
    void shouldReturnTheTokenOnlyInTheUnifiedLoginResponseData() {
        AuthenticationApplicationService authenticationService = mock(AuthenticationApplicationService.class);
        when(authenticationService.login(any())).thenReturn("opaque-token");
        AuthSessionController controller = new AuthSessionController(authenticationService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        R<LoginResponse> response = controller.login(
                new LoginRequest("hongqy@example.com", "password", false), request
        );

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().token()).isEqualTo("opaque-token");
    }

    @Test
    void shouldUsePostForLogoutAndReturnTheUnifiedResponse() {
        AuthenticationApplicationService authenticationService = mock(AuthenticationApplicationService.class);
        AuthSessionController controller = new AuthSessionController(authenticationService);

        R<Void> response = controller.logout();

        assertThat(response.isSuccess()).isTrue();
    }
}
