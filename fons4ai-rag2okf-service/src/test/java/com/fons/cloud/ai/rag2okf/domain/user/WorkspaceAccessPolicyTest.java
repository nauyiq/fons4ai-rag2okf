package com.fons.cloud.ai.rag2okf.domain.user;

import com.fons.cloud.ai.rag2okf.common.constants.UserStatus;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.exeception.WorkspaceAccessDeniedException;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.service.KbUserDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbWorkspaceMemberDomainService;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.WorkspaceAccessPolicy;
import com.fons.cloud.auth.satoken.api.SaTokenAuthTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作空间本地成员授权规则的行为测试。
 *
 * @author hongqy
 */
class WorkspaceAccessPolicyTest {

    @Test
    void shouldRejectMissingMembershipBeforeAWorkspaceOperation() {
        WorkspaceAccessPolicy policy = new WorkspaceAccessPolicy(
                mock(KbUserDomainService.class),
                mock(KbWorkspaceDomainService.class),
                mock(KbWorkspaceMemberDomainService.class),
                mock(SaTokenAuthTemplate.class)
        );

        assertThatThrownBy(() -> policy.checkAccess(
                "01JUSERKEY00000000000000001", "01JWORKSPACE000000000000001", WorkspaceRole.KNOWLEDGE_USER
        )).isInstanceOf(WorkspaceAccessDeniedException.class);
    }

    @Test
    void shouldKickOutDisabledUserBeforeWorkspaceAuthorization() {
        KbUserDomainService userDomainService = mock(KbUserDomainService.class);
        SaTokenAuthTemplate saToken = mock(SaTokenAuthTemplate.class);
        KbUserEntity user = new KbUserEntity();
        user.setStatus(UserStatus.DISABLED);
        when(userDomainService.getOne(any())).thenReturn(user);
        WorkspaceAccessPolicy policy = new WorkspaceAccessPolicy(
                userDomainService,
                mock(KbWorkspaceDomainService.class),
                mock(KbWorkspaceMemberDomainService.class),
                saToken
        );

        assertThatThrownBy(() -> policy.checkAccess(
                "01JUSERKEY00000000000000001", "01JWORKSPACE000000000000001", WorkspaceRole.KNOWLEDGE_USER
        )).isInstanceOf(WorkspaceAccessDeniedException.class);

        verify(saToken).kickout("01JUSERKEY00000000000000001");
    }
}
