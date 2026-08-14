package com.fons.cloud.ai.rag2okf.domain.service.user.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbModelConnection;
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
 * {@link KbModelConnectionDomainServiceImpl} 的连接所有权查询边界测试。
 */
class KbModelConnectionDomainServiceImplTest {

    private static final Long OWNER_USER_ID = 10L;

    private KbModelConnectionDomainServiceImpl connectionDomainService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                KbModelConnection.class);
        connectionDomainService = spy(new KbModelConnectionDomainServiceImpl());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldListConnectionsWithinOwnerBoundaryAndStableOrder() {
        doReturn(List.of()).when(connectionDomainService).list(any(Wrapper.class));

        connectionDomainService.listByOwnerUserId(OWNER_USER_ID);

        ArgumentCaptor<Wrapper<KbModelConnection>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(connectionDomainService).list(captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sqlSegment.contains("owner_user_id"));
        assertTrue(sqlSegment.contains("deleted"));
        assertTrue(sqlSegment.contains("order by updated desc"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFindConnectionByBusinessKeyOrIdWithinOwnerBoundary() {
        doReturn(new KbModelConnection()).when(connectionDomainService).getOne(any(Wrapper.class));

        connectionDomainService.findByConnectionKeyAndOwnerUserId("connection-key", OWNER_USER_ID);
        connectionDomainService.findByIdAndOwnerUserId(20L, OWNER_USER_ID);

        ArgumentCaptor<Wrapper<KbModelConnection>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(connectionDomainService, times(2)).getOne(captor.capture());
        List<String> sqlSegments = captor.getAllValues().stream()
                .map(wrapper -> wrapper.getSqlSegment().toLowerCase())
                .toList();
        assertTrue(sqlSegments.get(0).contains("connection_key"));
        assertTrue(sqlSegments.get(0).contains("owner_user_id"));
        assertTrue(sqlSegments.get(0).contains("deleted"));
        assertTrue(sqlSegments.get(1).contains("(id ="));
        assertTrue(sqlSegments.get(1).contains("owner_user_id"));
        assertTrue(sqlSegments.get(1).contains("deleted"));
    }
}
