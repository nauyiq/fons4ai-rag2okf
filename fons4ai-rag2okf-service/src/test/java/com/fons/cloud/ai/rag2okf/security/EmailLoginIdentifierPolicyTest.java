package com.fons.cloud.ai.rag2okf.security;

import com.fons.cloud.ai.rag2okf.application.identity.AuthenticationApplicationService;
import com.fons.cloud.ai.rag2okf.common.dto.LoginCommand;
import com.fons.cloud.ai.rag2okf.application.identity.RegistrationApplicationService;
import com.fons.cloud.ai.rag2okf.common.dto.RegistrationCommand;
import com.fons.cloud.ai.rag2okf.common.dto.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationDeniedException;
import com.fons.cloud.ai.rag2okf.common.exeception.EmailAlreadyRegisteredException;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceMemberEntity;
import com.fons.cloud.ai.rag2okf.domain.service.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbWorkspaceMemberDomainService;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.AuthenticationRateLimiter;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.LocalAccountRepository;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.PasswordHasher;
import com.fons.cloud.common.base.exception.BizException;
import com.fons.cloud.auth.satoken.api.SaTokenAuthTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 邮箱登录标识规范化策略单元测试（T029）。
 *
 * <p>验证邮箱作为未核验所有权的登录标识时，规范化策略的正确性：
 * <ul>
 *   <li>trim 前后空白</li>
 *   <li>整体小写化</li>
 *   <li>格式校验</li>
 *   <li>254 字符边界</li>
 *   <li>大小写不敏感唯一性（同一邮箱不同大小写视为相同）</li>
 * </ul>
 *
 * @author hongqy
 */
class EmailLoginIdentifierPolicyTest {

    private LocalAccountRepository accountRepository;
    private PasswordHasher passwordHasher;
    private AuthenticationRateLimiter rateLimiter;
    private SaTokenAuthTemplate saToken;
    private ModelBusinessKeyGenerator keyGenerator;
    private KbWorkspaceDomainService workspaceDomainService;
    private KbWorkspaceMemberDomainService memberDomainService;
    private RegistrationApplicationService registrationService;
    private AuthenticationApplicationService authService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(LocalAccountRepository.class);
        passwordHasher = mock(PasswordHasher.class);
        rateLimiter = mock(AuthenticationRateLimiter.class);
        saToken = mock(SaTokenAuthTemplate.class);
        keyGenerator = mock(ModelBusinessKeyGenerator.class);
        workspaceDomainService = mock(KbWorkspaceDomainService.class);
        memberDomainService = mock(KbWorkspaceMemberDomainService.class);
        registrationService = new RegistrationApplicationService(
                accountRepository, passwordHasher, rateLimiter, saToken,
                keyGenerator, workspaceDomainService, memberDomainService
        );
        authService = new AuthenticationApplicationService(
                accountRepository, passwordHasher, rateLimiter, saToken
        );
    }

    @Nested
    @DisplayName("注册阶段邮箱规范化")
    class RegistrationNormalization {

        @Test
        @DisplayName("trim 前后空白后小写化")
        void shouldTrimAndLowercaseEmailOnRegistration() {
            when(accountRepository.existsByNormalizedEmail("new@example.com")).thenReturn(false);
            when(passwordHasher.hash("secure-pass")).thenReturn("{bcrypt}hashed");
            when(keyGenerator.nextKey()).thenReturn("01JUSERKEY00000000000000001", "01JWSKEY00000000000000000001");
            when(accountRepository.save(any(KbUserEntity.class))).thenAnswer(invocation -> {
                KbUserEntity user = invocation.getArgument(0);
                user.setId(10L);
                return user;
            });
            when(workspaceDomainService.save(any(KbWorkspaceEntity.class))).thenAnswer(invocation -> {
                KbWorkspaceEntity ws = invocation.getArgument(0);
                ws.setId(20L);
                return true;
            });
            when(saToken.getTokenValue()).thenReturn("opaque-token");

            registrationService.register(new RegistrationCommand(
                    "  New@Example.COM  ", "secure-pass", "secure-pass", "name", "127.0.0.1"
            ));

            // 验证传入 repository 的邮箱是规范化后的
            verify(accountRepository).existsByNormalizedEmail("new@example.com");
            verify(rateLimiter).checkLoginAllowed("new@example.com", "127.0.0.1");
        }

        @Test
        @DisplayName("大小写不敏感唯一性：User@Example.com 与 user@example.com 视为同一邮箱")
        void shouldTreatDifferentCaseAsSameEmail() {
            // 第一次注册成功
            when(accountRepository.existsByNormalizedEmail("user@example.com")).thenReturn(true);

            // 使用不同大小写注册，应命中同一规范化邮箱的唯一性检查
            assertThatThrownBy(() -> registrationService.register(new RegistrationCommand(
                    "User@Example.com", "secure-pass", "secure-pass", "name", "127.0.0.1"
            ))).isInstanceOf(EmailAlreadyRegisteredException.class);

            // 验证查询使用的是规范化后的邮箱
            verify(accountRepository).existsByNormalizedEmail("user@example.com");
        }

        @Test
        @DisplayName("拒绝无效邮箱格式")
        void shouldRejectInvalidEmailFormat() {
            assertThatThrownBy(() -> registrationService.register(new RegistrationCommand(
                    "not-an-email", "secure-pass", "secure-pass", "name", "127.0.0.1"
            ))).isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("拒绝超过 254 字符的邮箱")
        void shouldRejectEmailExceeding254Characters() {
            String longEmail = "a".repeat(250) + "@x.co";
            assertThatThrownBy(() -> registrationService.register(new RegistrationCommand(
                    longEmail, "secure-pass", "secure-pass", "name", "127.0.0.1"
            ))).isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("拒绝 null 邮箱")
        void shouldRejectNullEmail() {
            assertThatThrownBy(() -> registrationService.register(new RegistrationCommand(
                    null, "secure-pass", "secure-pass", "name", "127.0.0.1"
            ))).isInstanceOf(BizException.class);
        }
    }

    @Nested
    @DisplayName("登录阶段邮箱规范化")
    class LoginNormalization {

        @Test
        @DisplayName("trim 前后空白后小写化")
        void shouldTrimAndLowercaseEmailOnLogin() {
            when(accountRepository.findByNormalizedEmail("user@example.com"))
                    .thenReturn(Optional.of(createActiveUser("user@example.com")));
            when(passwordHasher.matches("pass", "hashed")).thenReturn(true);
            when(saToken.getTokenValue()).thenReturn("opaque-token");

            authService.login(new LoginCommand("  User@Example.COM  ", "pass", false, "127.0.0.1"));

            // 验证查询使用的是规范化后的邮箱
            verify(accountRepository).findByNormalizedEmail("user@example.com");
            verify(rateLimiter).checkLoginAllowed("user@example.com", "127.0.0.1");
        }

        @Test
        @DisplayName("大小写不敏感：User@Example.com 能登录 user@example.com 注册的账号")
        void shouldLoginWithDifferentCaseEmail() {
            when(accountRepository.findByNormalizedEmail("user@example.com"))
                    .thenReturn(Optional.of(createActiveUser("user@example.com")));
            when(passwordHasher.matches("pass", "hashed")).thenReturn(true);
            when(saToken.getTokenValue()).thenReturn("opaque-token");

            String token = authService.login(new LoginCommand(
                    "User@Example.com", "pass", false, "127.0.0.1"
            ));

            assertThat(token).isEqualTo("opaque-token");
        }

        @Test
        @DisplayName("拒绝无效邮箱格式")
        void shouldRejectInvalidEmailFormatOnLogin() {
            assertThatThrownBy(() -> authService.login(new LoginCommand(
                    "not-an-email", "pass", false, "127.0.0.1"
            ))).isInstanceOf(BizException.class);
        }
    }

    @Nested
    @DisplayName("登录统一错误：不暴露账号是否存在")
    class UnifiedLoginError {

        @Test
        @DisplayName("未注册邮箱与错误密码返回相同异常类型")
        void shouldReturnSameExceptionForUnregisteredAndWrongPassword() {
            // 未注册邮箱
            when(accountRepository.findByNormalizedEmail("unknown@example.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(new LoginCommand(
                    "unknown@example.com", "any-pass", false, "127.0.0.1"
            ))).isInstanceOf(AuthenticationDeniedException.class);

            // 已注册但密码错误
            KbUserEntity user = createActiveUser("registered@example.com");
            when(accountRepository.findByNormalizedEmail("registered@example.com"))
                    .thenReturn(Optional.of(user));
            when(passwordHasher.matches("wrong-pass", "hashed")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginCommand(
                    "registered@example.com", "wrong-pass", false, "127.0.0.1"
            ))).isInstanceOf(AuthenticationDeniedException.class);
        }

        @Test
        @DisplayName("禁用账号与错误密码返回相同异常类型")
        void shouldReturnSameExceptionForDisabledAndWrongPassword() {
            // 禁用账号
            KbUserEntity disabledUser = createActiveUser("disabled@example.com");
            disabledUser.setStatus(com.fons.cloud.ai.rag2okf.common.constants.UserStatus.DISABLED);
            when(accountRepository.findByNormalizedEmail("disabled@example.com"))
                    .thenReturn(Optional.of(disabledUser));
            when(passwordHasher.matches("correct-pass", "hashed")).thenReturn(true);

            assertThatThrownBy(() -> authService.login(new LoginCommand(
                    "disabled@example.com", "correct-pass", false, "127.0.0.1"
            ))).isInstanceOf(AuthenticationDeniedException.class);

            // 正确密码但账号禁用也返回 AuthenticationDeniedException
            verify(rateLimiter).recordLoginFailure("disabled@example.com", "127.0.0.1");
        }

        @Test
        @DisplayName("未注册、密码错误、账号禁用三种场景产生相同异常类型和错误码")
        void shouldProduceIdenticalExceptionForAllFailureScenarios() {
            // 场景 1：未注册邮箱
            when(accountRepository.findByNormalizedEmail("unknown@example.com"))
                    .thenReturn(Optional.empty());
            AuthenticationDeniedException ex1 = catchAuthDenied("unknown@example.com", "any-pass");

            // 场景 2：已注册但密码错误
            KbUserEntity user = createActiveUser("registered@example.com");
            when(accountRepository.findByNormalizedEmail("registered@example.com"))
                    .thenReturn(Optional.of(user));
            when(passwordHasher.matches("wrong-pass", "hashed")).thenReturn(false);
            AuthenticationDeniedException ex2 = catchAuthDenied("registered@example.com", "wrong-pass");

            // 场景 3：正确密码但账号禁用
            KbUserEntity disabledUser = createActiveUser("disabled@example.com");
            disabledUser.setStatus(com.fons.cloud.ai.rag2okf.common.constants.UserStatus.DISABLED);
            when(accountRepository.findByNormalizedEmail("disabled@example.com"))
                    .thenReturn(Optional.of(disabledUser));
            when(passwordHasher.matches("correct-pass", "hashed")).thenReturn(true);
            AuthenticationDeniedException ex3 = catchAuthDenied("disabled@example.com", "correct-pass");

            // 三种场景的异常类型和错误码完全一致
            assertThat(ex1.getCode()).isEqualTo(ex2.getCode()).isEqualTo(ex3.getCode());
        }

        @SuppressWarnings("unchecked")
        private AuthenticationDeniedException catchAuthDenied(String email, String password) {
            try {
                authService.login(new LoginCommand(email, password, false, "127.0.0.1"));
                throw new AssertionError("应抛出 AuthenticationDeniedException");
            } catch (AuthenticationDeniedException e) {
                return e;
            }
        }
    }

    @Nested
    @DisplayName("注册冲突防枚举")
    class RegistrationConflictAntiEnumeration {

        @Test
        @DisplayName("EmailAlreadyRegisteredException 使用统一错误码，不泄露既有用户身份")
        void shouldNotLeakExistingUserIdentity() {
            when(accountRepository.existsByNormalizedEmail("existing@example.com")).thenReturn(true);

            assertThatThrownBy(() -> registrationService.register(new RegistrationCommand(
                    "Existing@Example.com", "secure-pass", "secure-pass", "name", "127.0.0.1"
            ))).isInstanceOf(EmailAlreadyRegisteredException.class);

            // 验证查询使用的是规范化后的邮箱（大小写不敏感）
            verify(accountRepository).existsByNormalizedEmail("existing@example.com");
        }
    }

    private KbUserEntity createActiveUser(String email) {
        KbUserEntity user = new KbUserEntity();
        user.setId(1L);
        user.setUserKey("01JUSERKEY00000000000000001");
        user.setEmail(email);
        user.setPasswordHash("hashed");
        user.setStatus(com.fons.cloud.ai.rag2okf.common.constants.UserStatus.ACTIVE);
        return user;
    }
}
