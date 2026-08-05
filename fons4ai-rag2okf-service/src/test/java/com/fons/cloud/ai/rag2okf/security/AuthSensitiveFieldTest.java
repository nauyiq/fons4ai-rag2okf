package com.fons.cloud.ai.rag2okf.security;

import com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationDeniedException;
import com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationRateLimitedException;
import com.fons.cloud.ai.rag2okf.common.exeception.WorkspaceAccessDeniedException;
import com.fons.cloud.ai.rag2okf.common.response.LoginResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 认证链路敏感字段零越界静态契约测试（T025）。
 *
 * <p>验证技术设计 §4.4 数据安全和 AC-027、AC-029 的敏感字段约束：
 * <ul>
 *   <li>登录失败响应不泄露邮箱注册状态或内部信息（AC-027）</li>
 *   <li>LoginResponse 只返回 token，不含 email/password/passwordHash（AC-029）</li>
 *   <li>KbUserEntity.toString() 排除 passwordHash（AC-029）</li>
 *   <li>认证异常不携带敏感原因（AC-027）</li>
 *   <li>无 CSRF token 机制（AC-026）</li>
 * </ul>
 *
 * @author hongqy
 */
@Execution(ExecutionMode.CONCURRENT)
class AuthSensitiveFieldTest {

    private static final List<String> FORBIDDEN_LOGIN_RESPONSE_FIELDS = List.of(
            "email", "password", "passwordHash", "passwordChangedAt",
            "userId", "userKey", "displayName", "status");

    /**
     * AC-029：LoginResponse 只能包含 token 字段，
     * 不得返回 email、password、passwordHash 或用户实体快照。
     */
    @Test
    void loginResponseShouldOnlyContainToken() {
        List<String> fields = recordFieldNames(LoginResponse.class);

        assertThat(fields)
                .as("LoginResponse 只能包含 token 字段（AC-029）")
                .containsExactly("token");

        for (String forbidden : FORBIDDEN_LOGIN_RESPONSE_FIELDS) {
            assertThat(fields)
                    .as("LoginResponse 不得包含敏感字段: %s", forbidden)
                    .doesNotContain(forbidden);
        }
    }

    /**
     * AC-029：KbUserEntity.toString() 不得输出 passwordHash，
     * 防止日志记录用户实体时泄露密码摘要。
     */
    @Test
    void kbUserEntityToStringShouldExcludePasswordHash() {
        KbUserEntity user = new KbUserEntity();
        user.setUserKey("uk-001");
        user.setEmail("user@example.com");
        user.setPasswordHash("{bcrypt}$2a$10$secretHashValue");

        String output = user.toString();
        assertThat(output)
                .as("KbUserEntity.toString() 不得包含 passwordHash（AC-029）")
                .doesNotContain("secretHashValue", "passwordHash");
    }

    /**
     * AC-027：AuthenticationDeniedException 必须是无参或最小信息构造，
     * 不在异常消息中区分"账号不存在"和"密码错误"。
     */
    @Test
    void authenticationDeniedExceptionShouldNotLeakFailureReason() {
        AuthenticationDeniedException exception = new AuthenticationDeniedException();

        assertThat(exception.getMessage())
                .as("AuthenticationDeniedException 消息不得泄露失败原因（AC-027）")
                .doesNotContain("账号不存在", "密码错误", "用户不存在", "password", "email");
    }

    /**
     * AC-027：AuthenticationRateLimitedException 不得在消息中泄露频控阈值或 Redis key。
     */
    @Test
    void authenticationRateLimitedExceptionShouldNotLeakThresholdOrKey() {
        AuthenticationRateLimitedException exception = new AuthenticationRateLimitedException();

        assertThat(exception.getMessage())
                .as("AuthenticationRateLimitedException 消息不得泄露频控细节（AC-027）")
                .doesNotContain("5", "20", "15", "10", "阈值", "redis", "key", "salt");
    }

    /**
     * AC-028：WorkspaceAccessDeniedException 不得在消息中泄露成员关系细节，
     * 如"用户不是成员"或"角色不足"等内部信息。
     */
    @Test
    void workspaceAccessDeniedExceptionShouldNotLeakMembershipDetails() {
        WorkspaceAccessDeniedException exception = new WorkspaceAccessDeniedException();

        assertThat(exception.getMessage())
                .as("WorkspaceAccessDeniedException 消息不得泄露成员关系细节（AC-028）")
                .doesNotContain("成员", "角色", "membership", "role", "ADMIN", "KNOWLEDGE_USER");
    }

    /**
     * AC-026：项目不得引入 CSRF token 机制。
     * Sa-Token 配置 is-read-cookie=false，采用纯 Header Bearer 模式，天然免疫 CSRF。
     *
     * <p>该测试验证认证相关核心类不包含 CSRF 字段或方法。
     */
    @Test
    void shouldNotIntroduceCsrfTokenMechanism() {
        Class<?>[] authCoreClasses = {
                LoginResponse.class,
                AuthenticationDeniedException.class,
                AuthenticationRateLimitedException.class,
                WorkspaceAccessDeniedException.class,
                KbUserEntity.class
        };

        for (Class<?> clazz : authCoreClasses) {
            List<String> fieldNames = Arrays.stream(clazz.getDeclaredFields())
                    .map(java.lang.reflect.Field::getName)
                    .toList();

            assertThat(fieldNames)
                    .as("%s 不得包含 CSRF 相关字段", clazz.getSimpleName())
                    .noneMatch(name -> name.toLowerCase().contains("csrf"));
        }
    }

    /**
     * AC-029：认证异常类不得继承携带敏感信息的基类，
     * 且必须继承 RuntimeException 以便全局异常处理器统一捕获。
     */
    @Test
    void authExceptionsMustBeRuntimeException() {
        assertThat(RuntimeException.class.isAssignableFrom(AuthenticationDeniedException.class))
                .as("AuthenticationDeniedException 必须继承 RuntimeException")
                .isTrue();
        assertThat(RuntimeException.class.isAssignableFrom(AuthenticationRateLimitedException.class))
                .as("AuthenticationRateLimitedException 必须继承 RuntimeException")
                .isTrue();
        assertThat(RuntimeException.class.isAssignableFrom(WorkspaceAccessDeniedException.class))
                .as("WorkspaceAccessDeniedException 必须继承 RuntimeException")
                .isTrue();
    }

    private List<String> recordFieldNames(Class<?> clazz) {
        if (clazz.isRecord()) {
            return Arrays.stream(clazz.getRecordComponents())
                    .map(RecordComponent::getName)
                    .toList();
        }
        return Arrays.stream(clazz.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .toList();
    }
}
