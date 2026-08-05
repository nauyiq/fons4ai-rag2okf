package com.fons.cloud.ai.rag2okf.controller.endpoint;

import com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationDeniedException;
import com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationRateLimitedException;
import com.fons.cloud.ai.rag2okf.common.exeception.WorkspaceAccessDeniedException;
import com.fons.cloud.common.result.R;
import com.fons.cloud.common.result.ResultCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一异常端点的响应契约测试。
 *
 * @author hongqy
 */
class Rag2OkfExceptionEndpointTest {

    private final Rag2OkfExceptionEndpoint endpoint = new Rag2OkfExceptionEndpoint();

    @Test
    void shouldHideAuthenticationFailureDetailsInTheUnifiedResponse() {
        R<Void> response = endpoint.handleAuthenticationDenied(new AuthenticationDeniedException());

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(ResultCode.INVALID_ACCESS_TOKEN.getCode());
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldMapAuthorizationAndRateLimitFailuresToFrameworkResultCodes() {
        R<Void> forbidden = endpoint.handleWorkspaceAccessDenied(new WorkspaceAccessDeniedException());
        R<Void> limited = endpoint.handleRateLimited(new AuthenticationRateLimitedException());

        assertThat(forbidden.getCode()).isEqualTo(ResultCode.NOT_PERMISSION.getCode());
        assertThat(limited.getCode()).isEqualTo(ResultCode.TOO_MANY_REQUEST.getCode());
    }
}
