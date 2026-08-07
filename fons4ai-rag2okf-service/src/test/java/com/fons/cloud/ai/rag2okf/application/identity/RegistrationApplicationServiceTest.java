package com.fons.cloud.ai.rag2okf.application.identity;

import com.fons.cloud.ai.rag2okf.common.dto.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.common.dto.RegistrationCommand;
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
import com.fons.cloud.common.base.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 邮箱密码注册应用服务的行为测试。
 *
 * @author hongqy
 */
class RegistrationApplicationServiceTest {

    private LocalAccountRepository accountRepository;
    private PasswordHasher passwordHasher;
    private AuthenticationRateLimiter rateLimiter;
    private SaTokenAuthTemplate saToken;
    private ModelBusinessKeyGenerator keyGenerator;
    private KbWorkspaceDomainService workspaceDomainService;
    private KbWorkspaceMemberDomainService memberDomainService;
    private RegistrationApplicationService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(LocalAccountRepository.class);
        passwordHasher = mock(PasswordHasher.class);
        rateLimiter = mock(AuthenticationRateLimiter.class);
        saToken = mock(SaTokenAuthTemplate.class);
        keyGenerator = mock(ModelBusinessKeyGenerator.class);
        workspaceDomainService = mock(KbWorkspaceDomainService.class);
        memberDomainService = mock(KbWorkspaceMemberDomainService.class);
        service = new RegistrationApplicationService(
                accountRepository, passwordHasher, rateLimiter, saToken,
                keyGenerator, workspaceDomainService, memberDomainService
        );
    }

    @Test
    void shouldCreateAccountWorkspaceMemberAndSessionForValidRegistration() {
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

        String token = service.register(new RegistrationCommand(
                " New@Example.com ", "secure-pass", "secure-pass", "洪启阳", "127.0.0.1"
        ));

        assertThat(token).isEqualTo("opaque-token");
        verify(rateLimiter).checkLoginAllowed("new@example.com", "127.0.0.1");
        verify(saToken).login("01JUSERKEY00000000000000001");
        verify(rateLimiter).clearLoginFailures("new@example.com", "127.0.0.1");
        verify(workspaceDomainService).save(any(KbWorkspaceEntity.class));
        verify(memberDomainService).save(any(KbWorkspaceMemberEntity.class));
    }

    @Test
    void shouldRejectInvalidEmailFormat() {
        assertThatThrownBy(() -> service.register(new RegistrationCommand(
                "not-an-email", "secure-pass", "secure-pass", "name", "127.0.0.1"
        ))).isInstanceOf(BizException.class);
    }

    @Test
    void shouldRejectEmailExceeding254Characters() {
        String longEmail = "a".repeat(250) + "@x.co";
        assertThatThrownBy(() -> service.register(new RegistrationCommand(
                longEmail, "secure-pass", "secure-pass", "name", "127.0.0.1"
        ))).isInstanceOf(BizException.class);
    }

    @Test
    void shouldRejectPasswordShorterThan8Characters() {
        assertThatThrownBy(() -> service.register(new RegistrationCommand(
                "new@example.com", "short", "short", "name", "127.0.0.1"
        ))).isInstanceOf(BizException.class);
    }

    @Test
    void shouldRejectPasswordLongerThan64Characters() {
        String longPassword = "a".repeat(65);
        assertThatThrownBy(() -> service.register(new RegistrationCommand(
                "new@example.com", longPassword, longPassword, "name", "127.0.0.1"
        ))).isInstanceOf(BizException.class);
    }

    @Test
    void shouldRejectPasswordSameAsEmail() {
        assertThatThrownBy(() -> service.register(new RegistrationCommand(
                "new@example.com", "new@example.com", "new@example.com", "name", "127.0.0.1"
        ))).isInstanceOf(BizException.class);
    }

    @Test
    void shouldRejectMismatchedConfirmPassword() {
        assertThatThrownBy(() -> service.register(new RegistrationCommand(
                "new@example.com", "secure-pass", "different-pass", "name", "127.0.0.1"
        ))).isInstanceOf(BizException.class);
    }

    @Test
    void shouldRejectEmailAlreadyRegistered() {
        when(accountRepository.existsByNormalizedEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegistrationCommand(
                "existing@example.com", "secure-pass", "secure-pass", "name", "127.0.0.1"
        ))).isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void shouldHandleConcurrentDuplicateRegistrationViaUniqueIndex() {
        when(accountRepository.existsByNormalizedEmail("race@example.com")).thenReturn(false);
        when(passwordHasher.hash("secure-pass")).thenReturn("{bcrypt}hashed");
        when(keyGenerator.nextKey()).thenReturn("01JUSERKEY00000000000000001");
        when(accountRepository.save(any(KbUserEntity.class))).thenThrow(new DuplicateKeyException("uk_email"));

        assertThatThrownBy(() -> service.register(new RegistrationCommand(
                "race@example.com", "secure-pass", "secure-pass", "name", "127.0.0.1"
        ))).isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(workspaceDomainService, org.mockito.Mockito.never()).save(any());
        verify(saToken, org.mockito.Mockito.never()).login(any());
    }

    @Test
    void shouldUseSafeDefaultDisplayNameWhenBlank() {
        when(accountRepository.existsByNormalizedEmail("new@example.com")).thenReturn(false);
        when(passwordHasher.hash("secure-pass")).thenReturn("{bcrypt}hashed");
        when(keyGenerator.nextKey()).thenReturn("01JUSERKEY00000000000000001", "01JWSKEY00000000000000000001");
        when(accountRepository.save(any(KbUserEntity.class))).thenAnswer(invocation -> {
            KbUserEntity user = invocation.getArgument(0);
            user.setId(10L);
            assertThat(user.getDisplayName()).isEqualTo("新用户");
            return user;
        });
        when(workspaceDomainService.save(any(KbWorkspaceEntity.class))).thenAnswer(invocation -> {
            KbWorkspaceEntity ws = invocation.getArgument(0);
            ws.setId(20L);
            return true;
        });
        when(saToken.getTokenValue()).thenReturn("opaque-token");

        service.register(new RegistrationCommand(
                "new@example.com", "secure-pass", "secure-pass", "  ", "127.0.0.1"
        ));
    }
}
