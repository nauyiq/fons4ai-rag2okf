package com.fons.cloud.ai.rag2okf.infrastructure.support.user;

import com.fons.cloud.ai.rag2okf.common.constants.user.UserStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceMemberStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceStatus;
import com.fons.cloud.ai.rag2okf.common.exception.user.WorkspaceAccessDeniedException;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbUser;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspace;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspaceMember;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbUserDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbWorkspaceMemberDomainService;
import com.fons.cloud.auth.satoken.api.SaTokenAuthTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkspaceAccessPolicy} 的工作空间授权边界测试。
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceAccessPolicyTest {

    private static final String USER_KEY = "user-key";
    private static final String WORKSPACE_KEY = "workspace-key";
    private static final Long USER_ID = 10L;
    private static final Long WORKSPACE_ID = 20L;

    @Mock
    private KbUserDomainService userDomainService;
    @Mock
    private KbWorkspaceDomainService workspaceDomainService;
    @Mock
    private KbWorkspaceMemberDomainService workspaceMemberDomainService;
    @Mock
    private SaTokenAuthTemplate saTokenAuthTemplate;

    private WorkspaceAccessPolicy workspaceAccessPolicy;

    @BeforeEach
    void setUp() {
        workspaceAccessPolicy = new WorkspaceAccessPolicy(
                userDomainService,
                workspaceDomainService,
                workspaceMemberDomainService,
                saTokenAuthTemplate);
    }

    @Test
    void shouldAllowActiveAdminAndUseScopedMembershipQuery() {
        stubAccess(UserStatus.ACTIVE, WorkspaceStatus.ACTIVE, WorkspaceMemberStatus.ACTIVE, WorkspaceRole.ADMIN);

        assertDoesNotThrow(() -> workspaceAccessPolicy
                .checkAccess(USER_KEY, WORKSPACE_KEY, WorkspaceRole.KNOWLEDGE_USER));

        verify(userDomainService).findByUserKey(USER_KEY);
        verify(workspaceDomainService).findByWorkspaceKey(WORKSPACE_KEY);
        verify(workspaceMemberDomainService).findByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID);
    }

    @Test
    void shouldRejectMissingUserAndKickoutSession() {
        when(userDomainService.findByUserKey(USER_KEY)).thenReturn(null);

        assertThrows(WorkspaceAccessDeniedException.class,
                () -> workspaceAccessPolicy.checkAccess(USER_KEY, WORKSPACE_KEY, WorkspaceRole.KNOWLEDGE_USER));

        verify(saTokenAuthTemplate).kickout(USER_KEY);
    }

    @Test
    void shouldRejectDisabledUserAndKickoutSession() {
        KbUser user = activeUser();
        user.setStatus(UserStatus.DISABLED);
        when(userDomainService.findByUserKey(USER_KEY)).thenReturn(user);

        assertThrows(WorkspaceAccessDeniedException.class,
                () -> workspaceAccessPolicy.checkAccess(USER_KEY, WORKSPACE_KEY, WorkspaceRole.KNOWLEDGE_USER));

        verify(saTokenAuthTemplate).kickout(USER_KEY);
    }

    @Test
    void shouldRejectDisabledWorkspace() {
        stubUser(UserStatus.ACTIVE);
        stubWorkspace(WorkspaceStatus.DISABLED);

        assertThrows(WorkspaceAccessDeniedException.class,
                () -> workspaceAccessPolicy.checkAccess(USER_KEY, WORKSPACE_KEY, WorkspaceRole.KNOWLEDGE_USER));
    }

    @Test
    void shouldRejectDisabledMembership() {
        stubAccess(UserStatus.ACTIVE, WorkspaceStatus.ACTIVE, WorkspaceMemberStatus.DISABLED, WorkspaceRole.ADMIN);

        assertThrows(WorkspaceAccessDeniedException.class,
                () -> workspaceAccessPolicy.checkAccess(USER_KEY, WORKSPACE_KEY, WorkspaceRole.KNOWLEDGE_USER));
    }

    @Test
    void shouldRejectInsufficientRole() {
        stubAccess(UserStatus.ACTIVE, WorkspaceStatus.ACTIVE,
                WorkspaceMemberStatus.ACTIVE, WorkspaceRole.KNOWLEDGE_USER);

        assertThrows(WorkspaceAccessDeniedException.class,
                () -> workspaceAccessPolicy.checkAccess(USER_KEY, WORKSPACE_KEY, WorkspaceRole.ADMIN));
    }

    private void stubAccess(UserStatus userStatus,
                            WorkspaceStatus workspaceStatus,
                            WorkspaceMemberStatus memberStatus,
                            WorkspaceRole role) {
        stubUser(userStatus);
        stubWorkspace(workspaceStatus);
        stubMembership(memberStatus, role);
    }

    private void stubUser(UserStatus status) {
        KbUser user = activeUser();
        user.setStatus(status);
        when(userDomainService.findByUserKey(USER_KEY)).thenReturn(user);
    }

    private void stubWorkspace(WorkspaceStatus status) {
        KbWorkspace workspace = new KbWorkspace();
        workspace.setId(WORKSPACE_ID);
        workspace.setWorkspaceKey(WORKSPACE_KEY);
        workspace.setStatus(status);
        when(workspaceDomainService.findByWorkspaceKey(WORKSPACE_KEY)).thenReturn(workspace);
    }

    private void stubMembership(WorkspaceMemberStatus status, WorkspaceRole role) {
        KbWorkspaceMember member = new KbWorkspaceMember();
        member.setUserId(USER_ID);
        member.setWorkspaceId(WORKSPACE_ID);
        member.setStatus(status);
        member.setLocalRole(role);
        when(workspaceMemberDomainService.findByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID)).thenReturn(member);
    }

    private KbUser activeUser() {
        KbUser user = new KbUser();
        user.setId(USER_ID);
        user.setUserKey(USER_KEY);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
