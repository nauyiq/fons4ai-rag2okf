package com.fons.cloud.ai.rag2okf.application.user;

import com.fons.cloud.ai.rag2okf.domain.entity.user.UserProfileAggregate;
import com.fons.cloud.ai.rag2okf.common.constants.user.UserStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceMemberStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.exception.user.AuthenticationDeniedException;
import com.fons.cloud.ai.rag2okf.common.exception.user.InvalidUserProfileException;
import com.fons.cloud.ai.rag2okf.common.response.user.UserProfileResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbUser;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspace;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspaceMember;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbUserDomainService;
import com.fons.cloud.auth.satoken.api.SaTokenAuthTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link UserProfileApplicationService} 的当前用户资料用例测试。
 */
@ExtendWith(MockitoExtension.class)
class UserProfileApplicationServiceTest {

    private static final String USER_KEY = "user-key";
    private static final Long USER_ID = 10L;
    private static final Long WORKSPACE_ID = 20L;

    @Mock
    private KbUserDomainService userDomainService;
    @Mock
    private SaTokenAuthTemplate saTokenAuthTemplate;

    private UserProfileApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new UserProfileApplicationService(userDomainService, saTokenAuthTemplate);
    }

    @Test
    void shouldReturnSafeCurrentProfileFromUserAggregate() {
        UserProfileAggregate aggregate = activeProfile();
        when(saTokenAuthTemplate.isLogin()).thenReturn(true);
        when(saTokenAuthTemplate.getCurrentLoginIdAsString()).thenReturn(USER_KEY);
        when(userDomainService.findUserProfileAggregate(USER_KEY)).thenReturn(aggregate);

        UserProfileResponse response = applicationService.currentProfile();

        assertEquals(USER_KEY, response.userKey());
        assertEquals("workspace-key", response.workspaceKey());
        assertEquals("ADMIN", response.workspaceRole());
        verify(userDomainService).findUserProfileAggregate(USER_KEY);
    }

    @Test
    void shouldRejectRequestWithoutLogin() {
        when(saTokenAuthTemplate.isLogin()).thenReturn(false);

        assertThrows(AuthenticationDeniedException.class, applicationService::currentProfile);

        verifyNoInteractions(userDomainService);
    }

    @Test
    void shouldRejectDisabledUserAndKickoutSession() {
        UserProfileAggregate aggregate = activeProfile();
        aggregate.user().setStatus(UserStatus.DISABLED);
        when(saTokenAuthTemplate.isLogin()).thenReturn(true);
        when(saTokenAuthTemplate.getCurrentLoginIdAsString()).thenReturn(USER_KEY);
        when(userDomainService.findUserProfileAggregate(USER_KEY)).thenReturn(aggregate);

        assertThrows(AuthenticationDeniedException.class, applicationService::currentProfile);

        verify(saTokenAuthTemplate).kickout(USER_KEY);
    }

    @Test
    void shouldUpdateWhitelistedFieldsAndKeepWorkspaceSnapshot() {
        UserProfileAggregate aggregate = activeProfile();
        aggregate.user().setPreferenceJson("{\"theme\":\"dark\",\"language\":\"zh-CN\"}");
        when(saTokenAuthTemplate.isLogin()).thenReturn(true);
        when(saTokenAuthTemplate.getCurrentLoginIdAsString()).thenReturn(USER_KEY);
        when(userDomainService.findUserProfileAggregate(USER_KEY)).thenReturn(aggregate);

        UserProfileResponse response = applicationService.updateCurrentUser(
                "  新名称  ", "  https://example.com/avatar.png  ", "{\"theme\":\"light\"}");

        assertEquals("新名称", response.displayName());
        assertEquals("https://example.com/avatar.png", response.avatarUrl());
        assertEquals("{\"theme\":\"light\",\"language\":\"zh-CN\"}", response.preferenceJson());
        assertEquals("workspace-key", response.workspaceKey());
        verify(userDomainService).updateProfile(aggregate.user());
    }

    @Test
    void shouldRejectInvalidDisplayNameBeforePersistence() {
        UserProfileAggregate aggregate = activeProfile();
        when(saTokenAuthTemplate.isLogin()).thenReturn(true);
        when(saTokenAuthTemplate.getCurrentLoginIdAsString()).thenReturn(USER_KEY);
        when(userDomainService.findUserProfileAggregate(USER_KEY)).thenReturn(aggregate);

        assertThrows(InvalidUserProfileException.class,
                () -> applicationService.updateCurrentUser("   ", null, null));

        verify(userDomainService, never()).updateProfile(aggregate.user());
    }

    private UserProfileAggregate activeProfile() {
        KbUser user = new KbUser();
        user.setId(USER_ID);
        user.setUserKey(USER_KEY);
        user.setEmail("user@example.com");
        user.setDisplayName("原名称");
        user.setStatus(UserStatus.ACTIVE);
        user.setPreferenceJson("{}");

        KbWorkspace workspace = new KbWorkspace();
        workspace.setId(WORKSPACE_ID);
        workspace.setWorkspaceKey("workspace-key");
        workspace.setName("个人空间");

        KbWorkspaceMember member = new KbWorkspaceMember();
        member.setWorkspaceId(WORKSPACE_ID);
        member.setUserId(USER_ID);
        member.setStatus(WorkspaceMemberStatus.ACTIVE);
        member.setLocalRole(WorkspaceRole.ADMIN);
        return new UserProfileAggregate(user, workspace, member);
    }
}
