package com.fons.cloud.ai.rag2okf.domain.service.document.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fons.cloud.ai.rag2okf.common.constants.document.ResultStage;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocumentResult;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/** 文档结果 CAS 唯一目标与软删除边界测试。 */
class KbDocumentResultDomainServiceImplTest {

    private KbDocumentResultDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                KbDocumentResult.class);
        service = spy(new KbDocumentResultDomainServiceImpl());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCommitParsedArtifactsWithinUniqueResultBoundary() {
        doReturn(true).when(service).update(any(Wrapper.class));

        service.commitParsedArtifacts(
                "result-key", 11L, 1, "json-key", "markdown-key", 2, 0);

        ArgumentCaptor<Wrapper<KbDocumentResult>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).update(captor.capture());
        String sql = captor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sql.contains("result_key"));
        assertTrue(sql.contains("document_id"));
        assertTrue(sql.contains("version"));
        assertTrue(sql.contains("stage"));
        assertTrue(sql.contains("deleted"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAdvanceStageWithinUniqueResultBoundary() {
        doReturn(true).when(service).update(any(Wrapper.class));

        service.advanceStage("result-key", 11L, 1, ResultStage.INIT, ResultStage.PARSE);

        ArgumentCaptor<Wrapper<KbDocumentResult>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).update(captor.capture());
        String sql = captor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sql.contains("result_key"));
        assertTrue(sql.contains("document_id"));
        assertTrue(sql.contains("deleted"));
    }
}
