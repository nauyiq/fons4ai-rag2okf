package com.fons.cloud.ai.rag2okf.security;

import com.fons.cloud.ai.rag2okf.application.identity.AuthenticationApplicationService;
import com.fons.cloud.ai.rag2okf.common.dto.LoginCommand;
import com.fons.cloud.ai.rag2okf.application.identity.RegistrationApplicationService;
import com.fons.cloud.ai.rag2okf.common.dto.RegistrationCommand;
import com.fons.cloud.ai.rag2okf.common.dto.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.common.constants.UserStatus;
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
import com.fons.cloud.auth.satoken.api.SaTokenAuthTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证与注册应用服务的职责边界测试（T038）。
 *
 * <p>验证登录与注册两条入口的不可变契约：
 * <ul>
 *   <li>登录只校验既有本地账号并建立/注销会话，不创建新用户、工作空间或成员关系</li>
 *   <li>注册是唯一能创建本地用户、个人 Workspace 与管理员成员关系的入口</li>
 *   <li>注册事务提交后才建立会话，注册失败不建立会话</li>
 * </ul>
 *
 * <p>本测试为纯 Mock 单元测试，不依赖 Spring 容器，不通过反射或字符串断言替代真实应用服务行为。
 * 不记录邮箱、密码、摘要或 token。
 *
 * @author hongqy
 */
class AuthenticationRegistrationBoundaryTest {

    private static final String USER_KEY = "01JUSERKEY00000000000000001";
    private static final String WORKSPACE_KEY = "01JWSKEY00000000000000000001";

    private LocalAccountRepository accountRepository;
    private PasswordHasher passwordHasher;
    private AuthenticationRateLimiter rateLimiter;
    private SaTokenAuthTemplate saTokenAuthTemplate;
    private ModelBusinessKeyGenerator businessKeyGenerator;
    private KbWorkspaceDomainService workspaceDomainService;
    private KbWorkspaceMemberDomainService memberDomainService;
    private AuthenticationApplicationService authService;
    private RegistrationApplicationService registrationService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(LocalAccountRepository.class);
        passwordHasher = mock(PasswordHasher.class);
        rateLimiter = mock(AuthenticationRateLimiter.class);
        saTokenAuthTemplate = mock(SaTokenAuthTemplate.class);
        businessKeyGenerator = mock(ModelBusinessKeyGenerator.class);
        workspaceDomainService = mock(KbWorkspaceDomainService.class);
        memberDomainService = mock(KbWorkspaceMemberDomainService.class);

        authService = new AuthenticationApplicationService(
                accountRepository, passwordHasher, rateLimiter, saTokenAuthTemplate
        );
        registrationService = new RegistrationApplicationService(
                accountRepository, passwordHasher, rateLimiter, saTokenAuthTemplate,
                businessKeyGenerator, workspaceDomainService, memberDomainService
        );
    }

    @Nested
    @DisplayName("登录入口：只校验既有账号并管理会话，不创建任何实体")
    class LoginBoundary {

        @Test
        @DisplayName("登录成功时只查询账号并建立会话，从不创建用户/工作空间/成员")
        void loginShouldOnlyAuthenticateExistingAccountWithoutCreatingAnything() {
            when(accountRepository.findByNormalizedEmail("user@example.com"))
                    .thenReturn(Optional.of(createActiveUser()));
            when(passwordHasher.matches("any-password", "hashed")).thenReturn(true);
            when(saTokenAuthTemplate.getTokenValue()).thenReturn("opaque-token");

            authService.login(new LoginCommand("user@example.com", "any-password", false, "127.0.0.1"));

            // 登录只查询既有账号，不创建新账号
            verify(accountRepository).findByNormalizedEmail("user@example.com");
            verify(accountRepository, never()).save(any(KbUserEntity.class));
            // 登录不创建工作空间或成员关系
            verify(workspaceDomainService, never()).save(any(KbWorkspaceEntity.class));
            verify(memberDomainService, never()).save(any(KbWorkspaceMemberEntity.class));
            // 登录成功建立会话
            verify(saTokenAuthTemplate).login(USER_KEY);
        }

        @Test
        @DisplayName("登录失败（未注册邮箱）时不建立会话")
        void loginShouldNotEstablishSessionWhenEmailUnregistered() {
            when(accountRepository.findByNormalizedEmail("unknown@example.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(new LoginCommand(
                    "unknown@example.com", "any-password", false, "127.0.0.1"
            ))).isInstanceOf(AuthenticationDeniedException.class);

            verify(saTokenAuthTemplate, never()).login(anyString());
        }

        @Test
        @DisplayName("登录失败（密码错误）时不建立会话")
        void loginShouldNotEstablishSessionWhenPasswordMismatch() {
            when(accountRepository.findByNormalizedEmail("user@example.com"))
                    .thenReturn(Optional.of(createActiveUser()));
            when(passwordHasher.matches("wrong-password", "hashed")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginCommand(
                    "user@example.com", "wrong-password", false, "127.0.0.1"
            ))).isInstanceOf(AuthenticationDeniedException.class);

            verify(saTokenAuthTemplate, never()).login(anyString());
        }

        @Test
        @DisplayName("注销只销毁当前会话，不触碰任何持久化实体")
        void logoutShouldOnlyDestroySessionWithoutTouchingEntities() {
            authService.logout();

            verify(saTokenAuthTemplate).logout();
            verify(accountRepository, never()).save(any(KbUserEntity.class));
            verify(workspaceDomainService, never()).save(any(KbWorkspaceEntity.class));
            verify(memberDomainService, never()).save(any(KbWorkspaceMemberEntity.class));
        }
    }

    @Nested
    @DisplayName("注册入口：唯一能创建用户/工作空间/成员，且事务提交后才建立会话")
    class RegistrationBoundary {

        @Test
        @DisplayName("注册成功时创建用户、工作空间、管理员成员关系并建立会话")
        void registerShouldBeSoleEntryCreatingUserWorkspaceAndMember() {
            when(accountRepository.existsByNormalizedEmail("new@example.com")).thenReturn(false);
            when(passwordHasher.hash("secure-password")).thenReturn("{bcrypt}hashed");
            when(businessKeyGenerator.nextKey()).thenReturn(USER_KEY, WORKSPACE_KEY);
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
            when(saTokenAuthTemplate.getTokenValue()).thenReturn("opaque-token");

            registrationService.register(new RegistrationCommand(
                    "new@example.com", "secure-password", "secure-password", "name", "127.0.0.1"
            ));

            // 注册是唯一创建入口：用户、工作空间、管理员成员关系均被创建
            verify(accountRepository).save(any(KbUserEntity.class));
            verify(workspaceDomainService).save(any(KbWorkspaceEntity.class));
            verify(memberDomainService).save(any(KbWorkspaceMemberEntity.class));
            // 事务提交后才建立会话
            verify(saTokenAuthTemplate).login(USER_KEY);
        }

        @Test
        @DisplayName("注册冲突（邮箱已存在）时不建立会话、不创建工作空间")
        void registerShouldNotEstablishSessionOrCreateWorkspaceWhenEmailExists() {
            when(accountRepository.existsByNormalizedEmail("existing@example.com")).thenReturn(true);

            assertThatThrownBy(() -> registrationService.register(new RegistrationCommand(
                    "existing@example.com", "secure-password", "secure-password", "name", "127.0.0.1"
            ))).isInstanceOf(EmailAlreadyRegisteredException.class);

            verify(saTokenAuthTemplate, never()).login(anyString());
            verify(workspaceDomainService, never()).save(any(KbWorkspaceEntity.class));
            verify(memberDomainService, never()).save(any(KbWorkspaceMemberEntity.class));
        }

        @Test
        @DisplayName("注册并发冲突（唯一索引DuplicateKeyException）时不建立会话、不创建工作空间")
        void registerShouldNotEstablishSessionOrCreateWorkspaceOnConcurrentDuplicate() {
            when(accountRepository.existsByNormalizedEmail("race@example.com")).thenReturn(false);
            when(passwordHasher.hash("secure-password")).thenReturn("{bcrypt}hashed");
            when(businessKeyGenerator.nextKey()).thenReturn(USER_KEY);
            when(accountRepository.save(any(KbUserEntity.class)))
                    .thenThrow(new DuplicateKeyException("uk_email"));

            assertThatThrownBy(() -> registrationService.register(new RegistrationCommand(
                    "race@example.com", "secure-password", "secure-password", "name", "127.0.0.1"
            ))).isInstanceOf(EmailAlreadyRegisteredException.class);

            verify(saTokenAuthTemplate, never()).login(anyString());
            verify(workspaceDomainService, never()).save(any(KbWorkspaceEntity.class));
            verify(memberDomainService, never()).save(any(KbWorkspaceMemberEntity.class));
        }
    }

    private KbUserEntity createActiveUser() {
        KbUserEntity user = new KbUserEntity();
        user.setId(1L);
        user.setUserKey(USER_KEY);
        user.setEmail("user@example.com");
        user.setPasswordHash("hashed");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
