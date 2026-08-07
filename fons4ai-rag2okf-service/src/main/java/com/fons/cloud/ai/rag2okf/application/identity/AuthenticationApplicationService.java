package com.fons.cloud.ai.rag2okf.application.identity;

import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.lang.Validator;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.common.dto.LoginCommand;
import com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationDeniedException;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.AuthenticationRateLimiter;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.LocalAccountRepository;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.PasswordHasher;
import com.fons.cloud.ai.rag2okf.common.constants.UserStatus;
import com.fons.cloud.auth.satoken.api.SaTokenAuthTemplate;
import com.fons.cloud.common.base.exception.BizException;
import com.fons.cloud.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Locale;

/**
 * 本地邮箱密码会话认证应用服务。
 *
 * @author hongqy
 */
@Service
@RequiredArgsConstructor
public class AuthenticationApplicationService {

    private static final long DEFAULT_REMEMBER_ME_TIMEOUT_SECONDS = 60L * 60L * 24L * 14L;

    private final LocalAccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final AuthenticationRateLimiter rateLimiter;
    private final SaTokenAuthTemplate saTokenAuthTemplate;

    @Value("${sys.security.remember-me-timeout-seconds:" + DEFAULT_REMEMBER_ME_TIMEOUT_SECONDS + "}")
    private long rememberMeTimeoutSeconds;

    /**
     * 校验已有本地账号并建立 Sa-Token 会话；本方法不承担账号注册或空间创建。
     *
     * @param command 登录命令
     * @return 只可通过登录响应短暂返回的当前会话令牌
     */
    public String login(LoginCommand command) {
        String normalizedEmail = command.email() == null ? null : command.email().trim().toLowerCase(Locale.ROOT);
        if (!Validator.isEmail(normalizedEmail)) {
            throw new BizException(ResultCode.PARAMS_ERROR);
        }
        rateLimiter.checkLoginAllowed(normalizedEmail, command.clientIp());
        KbUserEntity user = accountRepository.findByNormalizedEmail(normalizedEmail).orElse(null);
        String passwordHash = user == null ? null : user.getPasswordHash();
        boolean passwordMatches = passwordHasher.matches(command.password(), passwordHash);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !passwordMatches) {
            rateLimiter.recordLoginFailure(normalizedEmail, command.clientIp());
            throw new AuthenticationDeniedException();
        }

        login(user.getUserKey(), command.rememberMe());
        accountRepository.updateLastLoginAt(user.getId(), new Date());
        rateLimiter.clearLoginFailures(normalizedEmail, command.clientIp());
        return saTokenAuthTemplate.getTokenValue();
    }

    /**
     * 注销当前 Sa-Token 会话。
     */
    public void logout() {
        saTokenAuthTemplate.logout();
    }

    private void login(String userKey, boolean rememberMe) {
        if (rememberMe) {
            StpUtil.login(userKey, new SaLoginModel().setTimeout(rememberMeTimeoutSeconds));
            return;
        }
        saTokenAuthTemplate.login(userKey);
    }
}
