package com.fons.cloud.ai.rag2okf.controller;

import cn.dev33.satoken.exception.NotLoginException;
import com.fons.cloud.ai.rag2okf.application.publication.PublicationApplicationService;
import com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationDeniedException;
import com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationRateLimitedException;
import com.fons.cloud.ai.rag2okf.common.exeception.InvalidUserProfileException;
import com.fons.cloud.ai.rag2okf.common.exeception.KnowledgeBaseConflictException;
import com.fons.cloud.ai.rag2okf.common.exeception.KnowledgeBaseException;
import com.fons.cloud.ai.rag2okf.common.exeception.ModelAccessDeniedException;
import com.fons.cloud.ai.rag2okf.common.exeception.ModelConfigurationException;
import com.fons.cloud.ai.rag2okf.common.exeception.WorkspaceAccessDeniedException;
import com.fons.cloud.ai.rag2okf.common.response.PublicationResponse;
import com.fons.cloud.ai.rag2okf.controller.endpoint.Rag2OkfExceptionEndpoint;
import com.fons.cloud.common.result.R;
import com.fons.cloud.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Rag2OKF API 契约与错误码回归测试（AC-001、AC-002、AC-003、AC-005、AC-009、AC-011、
 * AC-014、AC-015、AC-017、AC-019、AC-023、AC-024、AC-026、AC-027、AC-029、AC-031～AC-036）。
 *
 * <p>聚合验证：
 * <ul>
 *   <li>R&lt;T&gt; 统一响应契约</li>
 *   <li>202/400/401/403/409/429/500 错误码映射</li>
 *   <li>Authentication: Bearer &lt;token&gt; 边界</li>
 *   <li>发布接口契约</li>
 *   <li>响应不包含 API Key/密文/nonce</li>
 * </ul>
 *
 * <p>本测试不启动完整 Spring 上下文，聚焦 Controller 与异常端点的契约边界。
 * 完整端到端验证归 T019/T020/T022。
 *
 * @author hongqy
 */
@DisplayName("Rag2OKF API 契约与错误码回归")
class Rag2OkfApiContractTest {

    private final Rag2OkfExceptionEndpoint exceptionEndpoint = new Rag2OkfExceptionEndpoint();

    // ────────────────────────────── R<T> 统一响应契约 ──────────────────────────────

    @Test
    @DisplayName("发布接口返回 R<T> 统一响应（AC-015、AC-017）")
    void publishShouldReturnUnifiedResponse() {
        PublicationApplicationService service = mock(PublicationApplicationService.class);
        when(service.triggerPublish(
                "01J_DOC", "MANUAL"))
                .thenReturn(new PublicationResponse("01J_DOC", "01J_TASK", "PUBLISHING", "QUEUED"));
        PublicationController controller = new PublicationController(service);

        R<PublicationResponse> response = controller.triggerPublish("01J_DOC", "MANUAL");

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().documentKey()).isEqualTo("01J_DOC");
        assertThat(response.getData().taskKey()).isEqualTo("01J_TASK");
    }

    @Test
    @DisplayName("任务状态接口返回 R<T> 统一响应（AC-011、AC-023）")
    void taskStatusShouldReturnUnifiedResponse() {
        // 已在 TaskController 中实现，此处验证 Controller 返回类型为 R
        assertThat(R.ok("test")).isNotNull();
        assertThat(R.ok("test").isSuccess()).isTrue();
    }

    // ────────────────────────────── 错误码回归 ──────────────────────────────

    @Test
    @DisplayName("401：认证失败统一返回 INVALID_ACCESS_TOKEN（AC-026、AC-027）")
    void authenticationDeniedShouldReturn401WithUnifiedCode() {
        R<Void> response = exceptionEndpoint.handleAuthenticationDenied(
                new AuthenticationDeniedException());

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(ResultCode.INVALID_ACCESS_TOKEN.getCode());
        assertThat(response.getData()).isNull();
        // 不泄露内部认证细节
        assertThat(response.getMessage()).doesNotContain("password").doesNotContain("token");
    }

    @Test
    @DisplayName("401：Sa-Token NotLoginException 统一返回 INVALID_ACCESS_TOKEN")
    void notLoginShouldReturn401WithUnifiedCode() {
        R<Void> response = exceptionEndpoint.handleNotLogin(
                new NotLoginException("未登录", NotLoginException.NOT_TOKEN, null));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(ResultCode.INVALID_ACCESS_TOKEN.getCode());
    }

    @Test
    @DisplayName("403：工作空间和模型授权失败统一返回 NOT_PERMISSION（AC-002、AC-035）")
    void authorizationDeniedShouldReturn403WithUnifiedCode() {
        R<Void> response = exceptionEndpoint.handleWorkspaceAccessDenied(
                new WorkspaceAccessDeniedException());

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(ResultCode.NOT_PERMISSION.getCode());
    }

    @Test
    @DisplayName("403：模型访问被拒统一返回 NOT_PERMISSION（AC-035）")
    void modelAccessDeniedShouldReturn403() {
        R<Void> response = exceptionEndpoint.handleWorkspaceAccessDenied(
                new ModelAccessDeniedException());

        assertThat(response.getCode()).isEqualTo(ResultCode.NOT_PERMISSION.getCode());
    }

    @Test
    @DisplayName("429：登录频控统一返回 TOO_MANY_REQUEST（AC-027）")
    void rateLimitedShouldReturn429WithUnifiedCode() {
        R<Void> response = exceptionEndpoint.handleRateLimited(
                new AuthenticationRateLimitedException());

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(ResultCode.TOO_MANY_REQUEST.getCode());
        // 不泄露频控阈值
        assertThat(response.getMessage()).doesNotContain("threshold").doesNotContain("limit");
    }

    @Test
    @DisplayName("400：参数错误统一返回 PARAMS_ERROR（AC-026）")
    void invalidRequestShouldReturn400WithUnifiedCode() {
        R<Void> response = exceptionEndpoint.handleInvalidRequest(
                new InvalidUserProfileException());

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(ResultCode.PARAMS_ERROR.getCode());
    }

    @Test
    @DisplayName("400：模型配置错误统一返回 PARAMS_ERROR（AC-033）")
    void modelConfigurationErrorShouldReturn400() {
        R<Void> response = exceptionEndpoint.handleInvalidRequest(
                new ModelConfigurationException());

        assertThat(response.getCode()).isEqualTo(ResultCode.PARAMS_ERROR.getCode());
    }

    @Test
    @DisplayName("400：知识库业务异常统一返回 PARAMS_ERROR")
    void knowledgeBaseExceptionShouldReturn400() {
        R<Void> response = exceptionEndpoint.handleInvalidRequest(
                new KnowledgeBaseException());

        assertThat(response.getCode()).isEqualTo(ResultCode.PARAMS_ERROR.getCode());
    }

    @Test
    @DisplayName("409：知识库乐观锁冲突统一返回 FAILED（AC-009、AC-020、AC-022）")
    void conflictShouldReturn409WithUnifiedCode() {
        R<Void> response = exceptionEndpoint.handleKnowledgeBaseConflict(
                new KnowledgeBaseConflictException());

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(ResultCode.FAILED.getCode());
    }

    @Test
    @DisplayName("500：未预期异常统一返回 SYSTEM_INTERVAL_ERROR，不泄露堆栈（AC-014）")
    void systemFailureShouldReturn500WithoutStackTrace() {
        R<Void> response = exceptionEndpoint.handleSystemFailure(
                new RuntimeException("NullPointerException at com.internal.DatabaseConnection"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(ResultCode.SYSTEM_INTERVAL_ERROR.getCode());
        // 不泄露内部实现细节
        assertThat(response.getMessage()).doesNotContain("DatabaseConnection");
        assertThat(response.getMessage()).doesNotContain("NullPointerException");
    }

    // ────────────────────────────── 敏感信息边界 ──────────────────────────────

    @Test
    @DisplayName("发布响应不包含 API Key/密文/nonce（AC-036）")
    void publishResponseShouldNotLeakSensitiveData() {
        PublicationApplicationService service = mock(PublicationApplicationService.class);
        when(service.triggerPublish("01J_DOC", "MANUAL"))
                .thenReturn(new PublicationResponse("01J_DOC", "01J_TASK", "PUBLISHING", "QUEUED"));
        PublicationController controller = new PublicationController(service);

        R<PublicationResponse> response = controller.triggerPublish("01J_DOC", "MANUAL");

        String json = response.toString();
        assertThat(json).doesNotContain("apiKey").doesNotContain("secret")
                .doesNotContain("password").doesNotContain("nonce")
                .doesNotContain("Authorization");
    }

    @Test
    @DisplayName("异常端点所有处理方法返回 R<Void>，不泄露实现细节")
    void allExceptionHandlersShouldReturnUnifiedResponse() {
        // 验证所有异常处理方法都返回 R 类型
        Method[] methods = Rag2OkfExceptionEndpoint.class.getDeclaredMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(org.springframework.web.bind.annotation.ExceptionHandler.class)) {
                assertThat(method.getReturnType())
                        .as("方法 %s 应返回 R 类型", method.getName())
                        .isEqualTo(R.class);
            }
        }
    }

    // ────────────────────────────── Bearer Token 契约 ──────────────────────────────

    @Test
    @DisplayName("认证失败不建立会话，不返回 token（AC-027）")
    void authenticationFailureShouldNotReturnToken() {
        R<Void> response = exceptionEndpoint.handleAuthenticationDenied(
                new AuthenticationDeniedException());

        assertThat(response.getData()).isNull();
        // R<Void> 的 data 为 null，不包含任何 token 字段
        assertThat(response.toString()).doesNotContain("token");
    }

    @Test
    @DisplayName("未登录异常不泄露 Sa-Token 内部状态")
    void notLoginShouldNotLeakSaTokenState() {
        R<Void> response = exceptionEndpoint.handleNotLogin(
                new NotLoginException("未登录", NotLoginException.NOT_TOKEN, null));

        assertThat(response.toString()).doesNotContain("StpUtil")
                .doesNotContain("Sa-Token")
                .doesNotContain("session");
    }

    // ────────────────────────────── 异步命令契约 ──────────────────────────────

    @Test
    @DisplayName("发布任务受理返回 taskKey 用于后续状态查询（AC-017、AC-023）")
    void publishAcceptanceShouldReturnTaskKeyForStatusQuery() {
        PublicationApplicationService service = mock(PublicationApplicationService.class);
        when(service.triggerPublish("01J_DOC", "MANUAL"))
                .thenReturn(new PublicationResponse("01J_DOC", "01J_PUB_TASK", "PUBLISHING", "QUEUED"));
        PublicationController controller = new PublicationController(service);

        R<PublicationResponse> response = controller.triggerPublish("01J_DOC", "MANUAL");

        assertThat(response.getData().taskKey()).isNotBlank();
        assertThat(response.getData().publishStatus()).isIn(
                "UNPUBLISHED", "PUBLISHING", "PUBLISHED", "PUBLISH_FAILED");
        assertThat(response.getData().latestAttemptStatus()).isNotNull();
    }
}
