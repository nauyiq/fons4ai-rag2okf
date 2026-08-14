package com.fons.cloud.ai.rag2okf.domain.service.user.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fons.cloud.ai.rag2okf.domain.entity.user.UserProfileAggregate;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbUser;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspace;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspaceMember;
import com.fons.cloud.ai.rag2okf.domain.mapper.user.KbWorkspaceMapper;
import com.fons.cloud.ai.rag2okf.domain.mapper.user.KbWorkspaceMemberMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link KbUserDomainServiceImpl} 的用户资料聚合恢复测试。
 */
@ExtendWith(MockitoExtension.class)
class KbUserDomainServiceImplTest {

    private static final String USER_KEY = "user-key";
    private static final Long USER_ID = 10L;
    private static final Long WORKSPACE_ID = 20L;

    @Mock
    private KbWorkspaceMapper workspaceMapper;
    @Mock
    private KbWorkspaceMemberMapper workspaceMemberMapper;

    private KbUserDomainServiceImpl userDomainService;

    @BeforeEach
    void setUp() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), KbWorkspace.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), KbWorkspaceMember.class);
        userDomainService = spy(new KbUserDomainServiceImpl(workspaceMapper, workspaceMemberMapper));
    }

    @Test
    void shouldRestoreUserProfileWithOwnerWorkspaceAndActiveMembership() {
        KbUser user = user();
        KbWorkspace workspace = workspace();
        KbWorkspaceMember member = membership();
        doReturn(user).when(userDomainService).findByUserKey(USER_KEY);
        when(workspaceMapper.selectOne(any())).thenReturn(workspace);
        when(workspaceMemberMapper.selectOne(any())).thenReturn(member);

        UserProfileAggregate aggregate = userDomainService.findUserProfileAggregate(USER_KEY);

        assertSame(user, aggregate.user());
        assertSame(workspace, aggregate.workspace());
        assertSame(member, aggregate.membership());
        assertWorkspaceQueryScopedByOwner();
        assertMembershipQueryScopedToWorkspaceAndUser();
    }

    @Test
    void shouldReturnNullAndStopWhenUserDoesNotExist() {
        doReturn(null).when(userDomainService).findByUserKey(USER_KEY);

        assertNull(userDomainService.findUserProfileAggregate(USER_KEY));

        verifyNoInteractions(workspaceMapper, workspaceMemberMapper);
    }

    @Test
    void shouldReturnProfileWithoutMembershipWhenPersonalWorkspaceDoesNotExist() {
        KbUser user = user();
        doReturn(user).when(userDomainService).findByUserKey(USER_KEY);
        when(workspaceMapper.selectOne(any())).thenReturn(null);

        UserProfileAggregate aggregate = userDomainService.findUserProfileAggregate(USER_KEY);

        assertSame(user, aggregate.user());
        assertNull(aggregate.workspace());
        assertNull(aggregate.membership());
        verifyNoInteractions(workspaceMemberMapper);
    }

    @SuppressWarnings("unchecked")
    private void assertWorkspaceQueryScopedByOwner() {
        ArgumentCaptor<Wrapper<KbWorkspace>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(workspaceMapper).selectOne(captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("owner_user_id"));
        assertTrue(sqlSegment.contains("workspace_type"));
        assertTrue(sqlSegment.contains("deleted"));
    }

    @SuppressWarnings("unchecked")
    private void assertMembershipQueryScopedToWorkspaceAndUser() {
        ArgumentCaptor<Wrapper<KbWorkspaceMember>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(workspaceMemberMapper).selectOne(captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("workspace_id"));
        assertTrue(sqlSegment.contains("user_id"));
        assertTrue(sqlSegment.contains("status"));
        assertTrue(sqlSegment.contains("deleted"));
    }

    private KbUser user() {
        KbUser user = new KbUser();
        user.setId(USER_ID);
        user.setUserKey(USER_KEY);
        return user;
    }

    private KbWorkspace workspace() {
        KbWorkspace workspace = new KbWorkspace();
        workspace.setId(WORKSPACE_ID);
        workspace.setOwnerUserId(USER_ID);
        return workspace;
    }

    private KbWorkspaceMember membership() {
        KbWorkspaceMember member = new KbWorkspaceMember();
        member.setWorkspaceId(WORKSPACE_ID);
        member.setUserId(USER_ID);
        return member;
    }
}
