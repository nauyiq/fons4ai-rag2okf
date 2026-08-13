package com.fons.cloud.ai.rag2okf.infrastructure.model;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fons.cloud.ai.rag2okf.common.dto.CurrentUserContext;
import com.fons.cloud.ai.rag2okf.common.constants.UserStatus;
import com.fons.cloud.ai.rag2okf.common.exeception.ModelAccessDeniedException;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUser;
import com.fons.cloud.ai.rag2okf.domain.service.KbUserDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 从 Sa-Token 会话解析当前可用本地用户的适配器。
 *
 * @author hongqy
 */
@Component
@RequiredArgsConstructor
public class SaTokenCurrentUserContext implements CurrentUserContext {

    private final KbUserDomainService userDomainService;

    @Override
    public KbUser requireCurrentUser() {
        String userKey = StpUtil.getLoginIdAsString();
        KbUser user = userDomainService.getOne(Wrappers.<KbUser>lambdaQuery()
                .eq(KbUser::getUserKey, userKey));
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new ModelAccessDeniedException();
        }
        return user;
    }
}
