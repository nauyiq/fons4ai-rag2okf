package com.fons.cloud.ai.rag2okf.domain.service.user.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceMemberStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.WorkspaceStatus;
import com.fons.cloud.ai.rag2okf.common.exception.user.WorkspaceAccessDeniedException;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspace;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspaceMember;
import com.fons.cloud.ai.rag2okf.domain.mapper.user.KbWorkspaceMemberMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link KbWorkspaceDomainServiceImpl} 的工作空间成员隔离测试。
 */
@ExtendWith(MockitoExtension.class)
class KbWorkspaceDomainServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long TARGET_WORKSPACE_ID = 20L;
    private static final String WORKSPACE_KEY = "workspace-key";

    @Mock
    private KbWorkspaceMemberMapper workspaceMemberMapper;

    private KbWorkspaceDomainServiceImpl workspaceDomainService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                KbWorkspaceMember.class);
        workspaceDomainService = spy(new KbWorkspaceDomainServiceImpl(workspaceMemberMapper));
    }

    @Test
    void shouldRejectMembershipFromAnotherWorkspaceWhenQueryingByKey() {
        KbWorkspace targetWorkspace = workspace(TARGET_WORKSPACE_ID);
        doReturn(targetWorkspace).when(workspaceDomainService).findByWorkspaceKey(WORKSPACE_KEY);
        when(workspaceMemberMapper.selectOne(any())).thenReturn(null);

        assertThrows(WorkspaceAccessDeniedException.class,
                () -> workspaceDomainService.findUserWorkspaceAggregate(USER_ID, WORKSPACE_KEY));

        assertScopedMembershipQuery();
    }

    @Test
    void shouldRejectMembershipFromAnotherWorkspaceWhenQueryingById() {
        KbWorkspace targetWorkspace = workspace(TARGET_WORKSPACE_ID);
        doReturn(targetWorkspace).when(workspaceDomainService).getById(TARGET_WORKSPACE_ID);
        when(workspaceMemberMapper.selectOne(any())).thenReturn(null);

        assertThrows(WorkspaceAccessDeniedException.class,
                () -> workspaceDomainService.findUserWorkspaceAggregate(USER_ID, TARGET_WORKSPACE_ID));

        assertScopedMembershipQuery();
    }

    @Test
    void shouldRejectDisabledWorkspace() {
        KbWorkspace targetWorkspace = workspace(TARGET_WORKSPACE_ID);
        targetWorkspace.setStatus(WorkspaceStatus.DISABLED);
        doReturn(targetWorkspace).when(workspaceDomainService).findByWorkspaceKey(WORKSPACE_KEY);

        assertThrows(WorkspaceAccessDeniedException.class,
                () -> workspaceDomainService.findUserWorkspaceAggregate(USER_ID, WORKSPACE_KEY));

        verifyNoInteractions(workspaceMemberMapper);
    }

    @Test
    void shouldRejectDisabledMembership() {
        KbWorkspace targetWorkspace = workspace(TARGET_WORKSPACE_ID);
        doReturn(targetWorkspace).when(workspaceDomainService).findByWorkspaceKey(WORKSPACE_KEY);
        when(workspaceMemberMapper.selectOne(any())).thenReturn(membership(WorkspaceMemberStatus.DISABLED));

        assertThrows(WorkspaceAccessDeniedException.class,
                () -> workspaceDomainService.findUserWorkspaceAggregate(USER_ID, WORKSPACE_KEY));
    }

    private KbWorkspace workspace(Long workspaceId) {
        KbWorkspace workspace = new KbWorkspace();
        workspace.setId(workspaceId);
        workspace.setStatus(WorkspaceStatus.ACTIVE);
        return workspace;
    }

    private KbWorkspaceMember membership(WorkspaceMemberStatus status) {
        KbWorkspaceMember membership = new KbWorkspaceMember();
        membership.setWorkspaceId(TARGET_WORKSPACE_ID);
        membership.setUserId(USER_ID);
        membership.setStatus(status);
        return membership;
    }

    @SuppressWarnings("unchecked")
    private void assertScopedMembershipQuery() {
        ArgumentCaptor<Wrapper<KbWorkspaceMember>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(workspaceMemberMapper).selectOne(queryCaptor.capture());

        String sqlSegment = queryCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("workspace_id"));
        assertTrue(sqlSegment.contains("user_id"));
        assertTrue(sqlSegment.contains("status"));
        assertTrue(sqlSegment.contains("deleted"));
    }

}
