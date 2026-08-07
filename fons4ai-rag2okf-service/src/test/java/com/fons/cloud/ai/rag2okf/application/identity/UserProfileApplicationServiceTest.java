package com.fons.cloud.ai.rag2okf.application.identity;

import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbWorkspaceMapper;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbWorkspaceMemberMapper;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.LocalAccountRepository;
import com.fons.cloud.ai.rag2okf.common.constants.UserStatus;
import com.fons.cloud.auth.satoken.api.SaTokenAuthTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 当前用户资料应用服务的行为测试。
 *
 * @author hongqy
 */
class UserProfileApplicationServiceTest {

    @Test
    void shouldOnlyPersistTheProfileWhitelistForCurrentActiveUser() {
        LocalAccountRepository accountRepository = mock(LocalAccountRepository.class);
        SaTokenAuthTemplate saToken = mock(SaTokenAuthTemplate.class);
        KbWorkspaceMapper workspaceMapper = mock(KbWorkspaceMapper.class);
        KbWorkspaceMemberMapper workspaceMemberMapper = mock(KbWorkspaceMemberMapper.class);
        KbUserEntity user = new KbUserEntity();
        user.setId(10L);
        user.setUserKey("01JUSERKEY00000000000000001");
        user.setEmail("hongqy@example.com");
        user.setPasswordHash("{bcrypt}secret");
        user.setStatus(UserStatus.ACTIVE);
        when(saToken.isLogin()).thenReturn(true);
        when(saToken.getCurrentLoginIdAsString()).thenReturn(user.getUserKey());
        when(accountRepository.findByUserKey(user.getUserKey())).thenReturn(Optional.of(user));
        UserProfileApplicationService service = new UserProfileApplicationService(accountRepository, saToken, workspaceMapper, workspaceMemberMapper);

        service.updateCurrentUser(" Hong QY ", "https://example.com/avatar.png", "{\"theme\":\"dark\"}");

        ArgumentCaptor<KbUserEntity> captor = ArgumentCaptor.forClass(KbUserEntity.class);
        verify(accountRepository).updateProfile(captor.capture());
        assertThat(captor.getValue())
                .extracting(KbUserEntity::getDisplayName, KbUserEntity::getEmail, KbUserEntity::getPasswordHash)
                .containsExactly("Hong QY", "hongqy@example.com", "{bcrypt}secret");
    }
}
