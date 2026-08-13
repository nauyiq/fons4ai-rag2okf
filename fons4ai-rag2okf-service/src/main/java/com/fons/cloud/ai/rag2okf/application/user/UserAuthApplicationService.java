package com.fons.cloud.ai.rag2okf.application.user;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.lang.Validator;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.dto.LoginCommand;
import com.fons.cloud.ai.rag2okf.common.dto.RegistrationCommand;
import com.fons.cloud.ai.rag2okf.common.utils.BusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.common.utils.PasswordEncoder;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUser;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspace;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceMember;
import com.fons.cloud.ai.rag2okf.domain.service.KbUserDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbWorkspaceMemberDomainService;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.AuthenticationRateLimiter;
import com.fons.cloud.auth.satoken.api.SaTokenAuthTemplate;
import com.fons.cloud.common.base.exception.BusinessRuntimeException;
import com.fons.cloud.common.result.R;
import com.fons.cloud.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 用户域 - 用户认证服务
 * @author hongqy
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthApplicationService {
    private final TransactionTemplate transactionTemplate;
    private final SaTokenAuthTemplate saTokenAuthTemplate;
    private final AuthenticationRateLimiter rateLimiter;

    private final KbUserDomainService userDomainService;
    private final KbWorkspaceDomainService workspaceDomainService;
    private final KbWorkspaceMemberDomainService workspaceMemberDomainService;


    @Value("${sys.security.remember-me-timeout-seconds:86400}")
    private long rememberMeTimeoutSeconds;

    /**
     * 校验已有本地账号并建立 Sa-Token 会话；本方法不承担账号注册或空间创建。
     * @param command 登录命令
     * @return 只可通过登录响应短暂返回的当前会话令牌
     */
    public R<String> login(LoginCommand command) {
        // 入参校验
        String email = command.email().trim();
        if (!Validator.isEmail(email)) {
            return R.failed(ResultCode.PARAMS_ERROR);
        }
        rateLimiter.checkLoginAllowed(email, command.clientIp());

        // 获取用户并登录
        KbUser user = userDomainService.findByEmail(email);
        if (user == null || !user.isActive() || !PasswordEncoder.matches(command.password(), user.getPasswordHash())) {
            return R.failed(Rag2OkfResultCode.PASSWORD_INCORRECT);
        }
        if (command.rememberMe()) {
            StpUtil.login(user.getUserKey(),rememberMeTimeoutSeconds);
        } else {
            StpUtil.login(user.getUserKey());
        }
        userDomainService.updateLastLoginAt(user.getId());
        rateLimiter.clearLoginFailures(email, command.clientIp());

        return R.ok(saTokenAuthTemplate.getTokenValue());
    }

    /**
     * 注销当前 Sa-Token 会话。
     */
    public void logout() {
        saTokenAuthTemplate.logout();
    }

    /**
     * 注册新本地账号并建立 Sa-Token 会话
     * @param command 注册命令
     * @return 只可通过登录响应短暂返回的当前会话令牌
     */
    public R<String> register(RegistrationCommand command) {
        String email = command.email().trim();
        rateLimiter.checkLoginAllowed(email, command.clientIp());

        KbUser existUser = userDomainService.findByEmail(email);
        if (existUser != null) {
            return R.failed(Rag2OkfResultCode.USER_EXIST);
        }

        String userKey = BusinessKeyGenerator.nextKey();
        String passwordHash = PasswordEncoder.hash(command.password());

        KbUser savedUser = KbUser.create(userKey, passwordHash, email, command.displayName());
        Boolean execute = transactionTemplate.execute(status -> {
            try {
                // 新增用户
                Assert.isTrue(userDomainService.save(savedUser), () -> BusinessRuntimeException.of(ResultCode.INSERT_FAILED));
                // 新增工作空间
                KbWorkspace workspace = KbWorkspace.create(BusinessKeyGenerator.nextKey(), savedUser.getId(), savedUser.getDisplayName());
                Assert.isTrue(workspaceDomainService.save(workspace), () -> BusinessRuntimeException.of(ResultCode.INSERT_FAILED));
                // 新增工作空间成员
                KbWorkspaceMember workspaceMember = KbWorkspaceMember.createAdmin(savedUser.getId(), workspace.getId());
                Assert.isTrue(workspaceMemberDomainService.save(workspaceMember), () -> BusinessRuntimeException.of(ResultCode.INSERT_FAILED));
                return true;
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                status.setRollbackOnly();
                return false;
            }
        });

        if (Boolean.FALSE.equals(execute)) {
            return R.failed(ResultCode.SYSTEM_BUSY);
        }

        saTokenAuthTemplate.login(savedUser.getUserKey());
        rateLimiter.clearLoginFailures(email, command.clientIp());
        return R.ok(saTokenAuthTemplate.getTokenValue());
    }
}
