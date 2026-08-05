package com.fons.cloud.ai.rag2okf.application.identity;

import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationDeniedException;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.AuthenticationRateLimiter;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.LocalAccountRepository;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.PasswordHasher;
import com.fons.cloud.ai.rag2okf.common.constants.UserStatus;
import com.fons.cloud.auth.satoken.api.SaTokenAuthTemplate;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 本地邮箱密码登录应用服务的行为测试。
 *
 * @author hongqy
 */
class AuthenticationApplicationServiceTest {

    @Test
    void shouldNormalizeEmailAndCreateSessionForActiveLocalUser() {
        LocalAccountRepository accountRepository = mock(LocalAccountRepository.class);
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        AuthenticationRateLimiter rateLimiter = mock(AuthenticationRateLimiter.class);
        SaTokenAuthTemplate saToken = mock(SaTokenAuthTemplate.class);
        KbUserEntity user = activeUser();
        when(accountRepository.findByNormalizedEmail("hongqy@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordHasher.matches("correct-password", "{bcrypt}stored"))
                .thenReturn(true);

        AuthenticationApplicationService service = new AuthenticationApplicationService(
                accountRepository, passwordHasher, rateLimiter, saToken
        );

        when(saToken.getTokenValue()).thenReturn("opaque-token");

        service.login(new LoginCommand(" HongQY@Example.com ", "correct-password", false, "127.0.0.1"));

        verify(rateLimiter).checkLoginAllowed("hongqy@example.com", "127.0.0.1");
        verify(saToken).login("01JUSERKEY00000000000000001");
        verify(accountRepository).updateLastLoginAt(eq(10L), any());
        verify(rateLimiter).clearLoginFailures("hongqy@example.com", "127.0.0.1");
    }

    @Test
    void shouldReturnTheSameDeniedOutcomeForUnknownPasswordMismatchAndDisabledAccount() {
        assertDenied(null, false, null);
        assertDenied(activeUser(), false, "{bcrypt}stored");
        KbUserEntity disabled = activeUser();
        disabled.setStatus(UserStatus.DISABLED);
        assertDenied(disabled, true, "{bcrypt}stored");
    }

    @Test
    void shouldLogoutTheCurrentSaTokenSession() {
        LocalAccountRepository accountRepository = mock(LocalAccountRepository.class);
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        AuthenticationRateLimiter rateLimiter = mock(AuthenticationRateLimiter.class);
        SaTokenAuthTemplate saToken = mock(SaTokenAuthTemplate.class);
        AuthenticationApplicationService service = new AuthenticationApplicationService(
                accountRepository, passwordHasher, rateLimiter, saToken
        );

        service.logout();

        verify(saToken).logout();
    }

    private void assertDenied(KbUserEntity user, boolean passwordMatches, String passwordHash) {
        LocalAccountRepository accountRepository = mock(LocalAccountRepository.class);
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        AuthenticationRateLimiter rateLimiter = mock(AuthenticationRateLimiter.class);
        SaTokenAuthTemplate saToken = mock(SaTokenAuthTemplate.class);
        when(accountRepository.findByNormalizedEmail("hongqy@example.com"))
                .thenReturn(Optional.ofNullable(user));
        when(passwordHasher.matches("wrong-password", passwordHash)).thenReturn(passwordMatches);
        AuthenticationApplicationService service = new AuthenticationApplicationService(
                accountRepository, passwordHasher, rateLimiter, saToken
        );

        assertThatThrownBy(() -> service.login(
                new LoginCommand("hongqy@example.com", "wrong-password", false, "127.0.0.1")
        )).isInstanceOf(AuthenticationDeniedException.class);

        verify(rateLimiter).recordLoginFailure("hongqy@example.com", "127.0.0.1");
        verify(saToken, never()).login(any());
        verify(accountRepository, never()).updateLastLoginAt(any(), any());
    }

    private KbUserEntity activeUser() {
        KbUserEntity user = new KbUserEntity();
        user.setId(10L);
        user.setUserKey("01JUSERKEY00000000000000001");
        user.setEmail("hongqy@example.com");
        user.setPasswordHash("{bcrypt}stored");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
