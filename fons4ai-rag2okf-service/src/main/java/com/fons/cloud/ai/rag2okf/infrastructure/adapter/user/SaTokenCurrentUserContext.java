package com.fons.cloud.ai.rag2okf.infrastructure.adapter.user;

import cn.dev33.satoken.stp.StpUtil;
import com.fons.cloud.ai.rag2okf.common.constants.user.UserStatus;
import com.fons.cloud.ai.rag2okf.common.exception.user.ModelAccessDeniedException;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbUser;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbUserDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 从 Sa-Token 会话解析当前可用本地用户的适配器。
 *
 * @author hongqy
 */
@Component
@RequiredArgsConstructor
public class SaTokenCurrentUserContext {

    private final KbUserDomainService userDomainService;

    /**
     * 取得已认证且仍可用的本地用户。
     *
     * @return 当前本地用户
     */
    public KbUser requireCurrentUser() {
        String userKey = StpUtil.getLoginIdAsString();
        KbUser user = userDomainService.findByUserKey(userKey);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new ModelAccessDeniedException();
        }
        return user;
    }

    /**
     * 获取当前可用用户的内部主键，避免跨域用例持有可变用户实体。
     *
     * @return 当前本地用户主键
     */
    public Long requireCurrentUserId() {
        return requireCurrentUser().getId();
    }
}
