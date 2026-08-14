package com.fons.cloud.ai.rag2okf.domain.service.user.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbModelProfile;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link KbModelProfileDomainServiceImpl} 的档案所有权查询与删除边界测试。
 */
class KbModelProfileDomainServiceImplTest {

    private static final Long OWNER_USER_ID = 10L;
    private static final Long CONNECTION_ID = 20L;

    private KbModelProfileDomainServiceImpl profileDomainService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                KbModelProfile.class);
        profileDomainService = spy(new KbModelProfileDomainServiceImpl());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldListProfilesWithinOwnerAndConnectionBoundary() {
        doReturn(List.of()).when(profileDomainService).list(any(Wrapper.class));

        profileDomainService.listByOwnerUserId(OWNER_USER_ID);
        profileDomainService.listByOwnerUserIdAndConnectionId(OWNER_USER_ID, CONNECTION_ID);

        ArgumentCaptor<Wrapper<KbModelProfile>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(profileDomainService, times(2)).list(captor.capture());
        List<String> sqlSegments = captor.getAllValues().stream()
                .map(wrapper -> wrapper.getSqlSegment().toLowerCase())
                .toList();
        assertTrue(sqlSegments.get(0).contains("owner_user_id"));
        assertTrue(sqlSegments.get(0).contains("deleted"));
        assertTrue(sqlSegments.get(0).contains("order by updated desc"));
        assertTrue(sqlSegments.get(1).contains("owner_user_id"));
        assertTrue(sqlSegments.get(1).contains("connection_id"));
        assertTrue(sqlSegments.get(1).contains("deleted"));
        assertTrue(sqlSegments.get(1).contains("order by updated desc"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFindProfileWithinOwnerBoundary() {
        doReturn(new KbModelProfile()).when(profileDomainService).getOne(any(Wrapper.class));

        profileDomainService.findByProfileKeyAndOwnerUserId("profile-key", OWNER_USER_ID);

        ArgumentCaptor<Wrapper<KbModelProfile>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(profileDomainService).getOne(captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sqlSegment.contains("profile_key"));
        assertTrue(sqlSegment.contains("owner_user_id"));
        assertTrue(sqlSegment.contains("deleted"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRemoveProfilesWithinOwnerAndConnectionBoundary() {
        doReturn(true).when(profileDomainService).remove(any(Wrapper.class));

        profileDomainService.removeByConnectionIdAndOwnerUserId(CONNECTION_ID, OWNER_USER_ID);

        ArgumentCaptor<Wrapper<KbModelProfile>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(profileDomainService).remove(captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sqlSegment.contains("connection_id"));
        assertTrue(sqlSegment.contains("owner_user_id"));
        assertTrue(sqlSegment.contains("deleted"));
    }
}
