package com.fons.cloud.ai.rag2okf.application.user;

import com.fons.cloud.ai.rag2okf.domain.entity.user.UserProfileAggregate;
import com.fons.cloud.ai.rag2okf.common.constants.user.UserStatus;
import com.fons.cloud.ai.rag2okf.common.exception.user.AuthenticationDeniedException;
import com.fons.cloud.ai.rag2okf.common.response.user.UserProfileResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbUser;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbUserDomainService;
import com.fons.cloud.auth.satoken.api.SaTokenAuthTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 当前已登录用户资料的应用服务。
 *
 * @author hongqy
 */
@Service
@RequiredArgsConstructor
public class UserProfileApplicationService {

    private final KbUserDomainService userDomainService;
    private final SaTokenAuthTemplate saTokenAuthTemplate;

    /**
     * 获取当前会话所属用户的安全资料快照。
     *
     * @return 不包含密码摘要和会话令牌的当前用户资料
     */
    public UserProfileResponse currentProfile() {
        return UserProfileResponse.from(requireCurrentProfile());
    }

    /**
     * 更新当前用户允许自行编辑的资料白名单。
     *
     * @param displayName 展示名称
     * @param avatarUrl 头像地址
     * @param preferenceJson 用户偏好快照
     * @return 更新后的安全用户资料
     */
    public UserProfileResponse updateCurrentUser(String displayName, String avatarUrl, String preferenceJson) {
        UserProfileAggregate profile = requireCurrentProfile();
        KbUser user = profile.user();
        user.applyProfilePatch(displayName, avatarUrl, preferenceJson);
        userDomainService.updateProfile(user);
        return UserProfileResponse.from(profile);
    }

    /**
     * 校验会话并恢复当前有效用户的资料聚合。
     *
     * <p>账号不存在或已禁用时终止对应会话并统一返回认证失败，避免泄露账号状态。</p>
     *
     * @return 当前有效用户的资料聚合
     */
    private UserProfileAggregate requireCurrentProfile() {
        if (!saTokenAuthTemplate.isLogin()) {
            throw new AuthenticationDeniedException();
        }
        String userKey = saTokenAuthTemplate.getCurrentLoginIdAsString();
        UserProfileAggregate profile = userDomainService.findUserProfileAggregate(userKey);
        if (profile == null || profile.user().getStatus() != UserStatus.ACTIVE) {
            saTokenAuthTemplate.kickout(userKey);
            throw new AuthenticationDeniedException();
        }
        return profile;
    }

}
