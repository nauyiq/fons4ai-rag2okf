package com.fons.cloud.ai.rag2okf.application.identity;

import cn.hutool.core.lang.Validator;
import com.fons.cloud.ai.rag2okf.common.dto.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.common.dto.RegistrationCommand;
import com.fons.cloud.ai.rag2okf.common.constants.UserStatus;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceMemberStatus;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceStatus;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceType;
import com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationRateLimitedException;
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
import com.fons.cloud.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Locale;

/**
 * 邮箱密码注册应用服务。
 *
 * <p>原子创建本地账号、个人工作空间和管理员成员关系，并在事务提交后建立 Sa-Token 会话。
 * 注册不发送验证邮件，不保存邮箱验证状态。</p>
 *
 * @author hongqy
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationApplicationService {

    private static final int EMAIL_MAX_LENGTH = 254;
    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 64;
    private static final int DISPLAY_NAME_MAX_LENGTH = 80;

    private final LocalAccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final AuthenticationRateLimiter rateLimiter;
    private final SaTokenAuthTemplate saTokenAuthTemplate;
    private final ModelBusinessKeyGenerator businessKeyGenerator;
    private final KbWorkspaceDomainService workspaceDomainService;
    private final KbWorkspaceMemberDomainService workspaceMemberDomainService;

    /**
     * 注册新本地账号并建立会话。
     *
     * <p>校验邮箱格式与唯一性、密码强度和条款同意后，在单个事务内创建用户、个人工作空间和管理员成员关系。
     * 事务提交成功后建立 Sa-Token 会话；会话建立失败时账号保留为完整一致状态，用户可重新登录。</p>
     *
     * @param command 注册命令
     * @return 当前会话令牌
     */
    public String register(RegistrationCommand command) {
        String normalizedEmail = normalizeAndValidateEmail(command.email());
        validatePassword(command.password(), command.confirmPassword(), normalizedEmail);
        validateDisplayName(command.displayName());
        rateLimiter.checkLoginAllowed(normalizedEmail, command.clientIp());

        String userKey = businessKeyGenerator.nextKey();
        String passwordHash = passwordHasher.hash(command.password());

        KbUserEntity savedUser = createAccountInTransaction(normalizedEmail, userKey, passwordHash, command.displayName());

        saTokenAuthTemplate.login(savedUser.getUserKey());
        rateLimiter.clearLoginFailures(normalizedEmail, command.clientIp());
        return saTokenAuthTemplate.getTokenValue();
    }

    private String normalizeAndValidateEmail(String email) {
        String normalized = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        if (normalized == null || normalized.length() > EMAIL_MAX_LENGTH || !Validator.isEmail(normalized)) {
            throw new BizException(ResultCode.PARAMS_ERROR);
        }
        return normalized;
    }

    private void validatePassword(String password, String confirmPassword, String normalizedEmail) {
        if (password == null || password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
            throw new BizException(ResultCode.PARAMS_ERROR);
        }
        if (password.equals(normalizedEmail)) {
            throw new BizException(ResultCode.PARAMS_ERROR);
        }
        if (!password.equals(confirmPassword)) {
            throw new BizException(ResultCode.PARAMS_ERROR);
        }
    }

    private void validateDisplayName(String displayName) {
        if (displayName != null && displayName.trim().length() > DISPLAY_NAME_MAX_LENGTH) {
            throw new BizException(ResultCode.PARAMS_ERROR);
        }
    }

    /**
     * 在单个事务内创建本地账号、个人工作空间和管理员成员关系。
     *
     * <p>邮箱唯一索引处理大小写不同但规范化后相同的并发重复注册，
     * 通过捕获 {@link DuplicateKeyException} 返回统一冲突异常。</p>
     *
     * @param normalizedEmail 规范化邮箱
     * @param userKey         新生成的用户业务标识
     * @param passwordHash    密码摘要
     * @param displayName     展示名称，空值使用安全默认值
     * @return 保存后的用户实体
     */
    @Transactional(rollbackFor = Exception.class)
    public KbUserEntity createAccountInTransaction(String normalizedEmail, String userKey, String passwordHash, String displayName) {
        if (accountRepository.existsByNormalizedEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }

        KbUserEntity user = new KbUserEntity();
        user.setUserKey(userKey);
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordHash);
        user.setDisplayName(displayName != null && !displayName.isBlank() ? displayName.trim() : "新用户");
        user.setStatus(UserStatus.ACTIVE);
        user.setPreferenceJson("{}");
        user.setPasswordChangedAt(new Date());

        try {
            KbUserEntity savedUser = accountRepository.save(user);

            KbWorkspaceEntity workspace = new KbWorkspaceEntity();
            workspace.setWorkspaceKey(businessKeyGenerator.nextKey());
            workspace.setName(savedUser.getDisplayName() + " 的知识空间");
            workspace.setWorkspaceType(WorkspaceType.PERSONAL);
            workspace.setOwnerUserId(savedUser.getId());
            workspace.setStatus(WorkspaceStatus.ACTIVE);
            workspaceDomainService.save(workspace);

            KbWorkspaceMemberEntity member = new KbWorkspaceMemberEntity();
            member.setWorkspaceId(workspace.getId());
            member.setUserId(savedUser.getId());
            member.setLocalRole(WorkspaceRole.ADMIN);
            member.setStatus(WorkspaceMemberStatus.ACTIVE);
            workspaceMemberDomainService.save(member);

            return savedUser;
        } catch (DuplicateKeyException e) {
            throw new EmailAlreadyRegisteredException();
        }
    }
}
