package com.fons.cloud.ai.rag2okf.application.knowledgebase;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fons.cloud.ai.rag2okf.common.dto.CurrentUserContext;
import com.fons.cloud.ai.rag2okf.common.dto.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.common.dto.ModelUsagePolicy;
import com.fons.cloud.ai.rag2okf.common.exeception.WorkspaceAccessDeniedException;
import com.fons.cloud.ai.rag2okf.common.response.KnowledgeBaseSummaryResponse;
import com.fons.cloud.ai.rag2okf.common.response.PageResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.KbKnowledgeBaseEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceEntity;
import com.fons.cloud.ai.rag2okf.domain.service.KbKnowledgeBaseDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelBindingDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelProfileDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbUserDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbWorkspaceDomainService;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.WorkspaceAccessPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KnowledgeBaseApplicationService 删除鉴权与列表扩展单元测试。
 *
 * <p>覆盖 T016：删除成功软删除、非创建者 403、幂等删除、列表 canDelete 与 ownerUserKey 计算。
 *
 * @author hongqy
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("知识库应用服务：删除与列表扩展")
class KnowledgeBaseApplicationServiceTest {

    @Mock private CurrentUserContext currentUserContext;
    @Mock private WorkspaceAccessPolicy workspaceAccessPolicy;
    @Mock private KbWorkspaceDomainService workspaceDomainService;
    @Mock private KbKnowledgeBaseDomainService knowledgeBaseDomainService;
    @Mock private KbModelBindingDomainService modelBindingDomainService;
    @Mock private KbModelProfileDomainService modelProfileDomainService;
    @Mock private KbUserDomainService userDomainService;
    @Mock private ModelBusinessKeyGenerator keyGenerator;
    @Mock private ModelUsagePolicy modelUsagePolicy;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private KnowledgeBaseApplicationService service;

    // ────────────────────────────── 删除：创建者成功 ──────────────────────────────

    @Test
    @DisplayName("创建者调用删除：软删除知识库（removeById 命中）")
    void deleteByOwnerSoftDeletesKnowledgeBase() {
        currentUser(10L);
        KbKnowledgeBaseEntity kb = knowledgeBase("01J_KB", 100L, 10L);
        when(knowledgeBaseDomainService.getOne(any())).thenReturn(kb);

        service.deleteKnowledgeBase("01J_KB");

        verify(knowledgeBaseDomainService).removeById(100L);
    }

    // ────────────────────────────── 删除：非创建者 403 ──────────────────────────────

    @Test
    @DisplayName("非创建者调用删除：抛出 403 且不触发软删除")
    void deleteByNonOwnerThrowsForbidden() {
        KbUserEntity nonOwner = currentUser(10L);
        KbKnowledgeBaseEntity kb = knowledgeBase("01J_KB", 100L, 20L);
        when(knowledgeBaseDomainService.getOne(any())).thenReturn(kb);

        assertThatThrownBy(() -> service.deleteKnowledgeBase("01J_KB"))
                .isInstanceOf(WorkspaceAccessDeniedException.class);
        verify(knowledgeBaseDomainService, never()).removeById(any());
    }

    // ────────────────────────────── 删除：幂等 ──────────────────────────────

    @Test
    @DisplayName("删除已软删除/不存在的知识库：幂等返回成功，不抛异常")
    void deleteAlreadyDeletedKnowledgeBaseIsIdempotent() {
        currentUser(10L);
        when(knowledgeBaseDomainService.getOne(any())).thenReturn(null);

        service.deleteKnowledgeBase("01J_KB");

        verify(knowledgeBaseDomainService, never()).removeById(any());
    }

    // ────────────────────────────── 列表：canDelete 与 ownerUserKey ──────────────────────────────

    @Test
    @DisplayName("列表返回 canDelete 与 ownerUserKey：创建者 canDelete=true，非创建者 canDelete=false")
    void listReturnsCanDeleteAndOwnerUserKey() {
        currentUser(10L);
        workspaceStub("01J_WS", 1L);

        KbKnowledgeBaseEntity owned = knowledgeBase("01J_KB_OWN", 100L, 10L);
        KbKnowledgeBaseEntity others = knowledgeBase("01J_KB_OTHER", 101L, 20L);
        Page<KbKnowledgeBaseEntity> page = new Page<>(1, 20);
        page.setRecords(List.of(owned, others));
        page.setTotal(2);
        when(knowledgeBaseDomainService.page(any(), any())).thenReturn(page);

        KbUserEntity ownerSelf = userEntity(10L, "01J_USER_ME");
        KbUserEntity ownerOther = userEntity(20L, "01J_USER_OTHER");
        when(userDomainService.listByIds(any())).thenReturn(List.of(ownerSelf, ownerOther));

        PageResponse<KnowledgeBaseSummaryResponse> result =
                service.listKnowledgeBases("01J_WS", 0, 20);

        assertThat(result.records()).hasSize(2);
        KnowledgeBaseSummaryResponse ownedRow = result.records().get(0);
        assertThat(ownedRow.canDelete()).isTrue();
        assertThat(ownedRow.ownerUserKey()).isEqualTo("01J_USER_ME");
        KnowledgeBaseSummaryResponse othersRow = result.records().get(1);
        assertThat(othersRow.canDelete()).isFalse();
        assertThat(othersRow.ownerUserKey()).isEqualTo("01J_USER_OTHER");
    }

    // ────────────────────────────── 测试夹具 ──────────────────────────────

    private KbUserEntity currentUser(long userId) {
        KbUserEntity user = userEntity(userId, "01J_USER_" + userId);
        when(currentUserContext.requireCurrentUser()).thenReturn(user);
        return user;
    }

    private void workspaceStub(String workspaceKey, long workspaceId) {
        KbWorkspaceEntity workspace = new KbWorkspaceEntity();
        workspace.setId(workspaceId);
        workspace.setWorkspaceKey(workspaceKey);
        when(workspaceDomainService.getOne(any())).thenReturn(workspace);
    }

    private KbKnowledgeBaseEntity knowledgeBase(String key, long id, long ownerUserId) {
        KbKnowledgeBaseEntity entity = new KbKnowledgeBaseEntity();
        entity.setId(id);
        entity.setKnowledgeBaseKey(key);
        entity.setWorkspaceId(1L);
        entity.setOwnerUserId(ownerUserId);
        entity.setAutoParse(false);
        entity.setAutoPublish(false);
        return entity;
    }

    private KbUserEntity userEntity(long userId, String userKey) {
        KbUserEntity user = new KbUserEntity();
        user.setId(userId);
        user.setUserKey(userKey);
        return user;
    }
}
