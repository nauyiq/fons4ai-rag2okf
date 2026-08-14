package com.fons.cloud.ai.rag2okf.infrastructure.adapter.user;

import cn.dev33.satoken.stp.StpUtil;
import com.fons.cloud.ai.rag2okf.common.constants.user.UserStatus;
import com.fons.cloud.ai.rag2okf.common.exception.user.ModelAccessDeniedException;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbUser;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbUserDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SaTokenCurrentUserContext} 的当前身份查询边界测试。
 */
@ExtendWith(MockitoExtension.class)
class SaTokenCurrentUserContextTest {

    private static final String USER_KEY = "user-key";

    @Mock
    private KbUserDomainService userDomainService;

    @Test
    void shouldQueryCurrentUserThroughExplicitDomainMethod() {
        KbUser user = new KbUser();
        user.setStatus(UserStatus.ACTIVE);
        when(userDomainService.findByUserKey(USER_KEY)).thenReturn(user);
        SaTokenCurrentUserContext context = new SaTokenCurrentUserContext(userDomainService);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsString).thenReturn(USER_KEY);

            assertSame(user, context.requireCurrentUser());
        }

        verify(userDomainService).findByUserKey(USER_KEY);
    }

    @Test
    void shouldRejectMissingCurrentUser() {
        when(userDomainService.findByUserKey(USER_KEY)).thenReturn(null);
        SaTokenCurrentUserContext context = new SaTokenCurrentUserContext(userDomainService);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsString).thenReturn(USER_KEY);

            assertThrows(ModelAccessDeniedException.class, context::requireCurrentUser);
        }
    }
}
