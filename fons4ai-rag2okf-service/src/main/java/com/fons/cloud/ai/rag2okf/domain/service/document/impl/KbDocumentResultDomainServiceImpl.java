package com.fons.cloud.ai.rag2okf.domain.service.document.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ResultStage;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocumentResult;
import com.fons.cloud.ai.rag2okf.domain.mapper.document.KbDocumentResultMapper;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbDocumentResultDomainService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档结果领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbDocumentResultDomainServiceImpl
        extends ServiceImpl<KbDocumentResultMapper, KbDocumentResult>
        implements KbDocumentResultDomainService {

    @Override
    public Map<Long, KbDocumentResult> findCurrentByDocumentIds(List<Long> documentIds) {
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        List<KbDocumentResult> results = this.list(Wrappers.<KbDocumentResult>lambdaQuery()
                .in(KbDocumentResult::getDocumentId, documentIds)
                .eq(KbDocumentResult::getDeleted, false)
                .orderByDesc(KbDocumentResult::getId));
        Map<Long, KbDocumentResult> currentResults = new LinkedHashMap<>();
        results.forEach(result -> currentResults.putIfAbsent(result.getDocumentId(), result));
        return Map.copyOf(currentResults);
    }

    @Override
    public KbDocumentResult findByResultKey(String resultKey) {
        return this.getOne(Wrappers.<KbDocumentResult>lambdaQuery()
                .eq(KbDocumentResult::getDeleted, false)
                .eq(KbDocumentResult::getResultKey, resultKey));
    }

    @Override
    public KbDocumentResult findCurrentByDocumentId(Long documentId) {
        return this.getOne(Wrappers.<KbDocumentResult>lambdaQuery()
                .eq(KbDocumentResult::getDeleted, false)
                .eq(KbDocumentResult::getDocumentId, documentId)
                .orderByDesc(KbDocumentResult::getId)
                .last("LIMIT 1"));
    }

    @Override
    public KbDocumentResult findBySourceFileToken(String sourceFileToken) {
        return this.getOne(Wrappers.<KbDocumentResult>lambdaQuery()
                .eq(KbDocumentResult::getDeleted, false)
                .eq(KbDocumentResult::getSourceFileToken, sourceFileToken));
    }

    @Override
    public void advanceStage(
            String resultKey, Long documentId, Integer expectedResultVersion,
            ResultStage fromStage, ResultStage toStage) {
        if (!fromStage.canAdvanceTo(toStage)) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
        boolean updated = this.update(Wrappers.<KbDocumentResult>lambdaUpdate()
                .set(KbDocumentResult::getStage, toStage)
                .setSql("version = version + 1")
                .eq(KbDocumentResult::getResultKey, resultKey)
                .eq(KbDocumentResult::getDocumentId, documentId)
                .eq(KbDocumentResult::getVersion, expectedResultVersion)
                .eq(KbDocumentResult::getStage, fromStage)
                .eq(KbDocumentResult::getDeleted, false));
        if (!updated) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
    }

    @Override
    public void commitParsedArtifacts(
            String resultKey,
            Long documentId,
            Integer expectedResultVersion,
            String parsedDocumentObjectKey,
            String parsedMarkdownObjectKey,
            int blockCount,
            int warningCount) {
        boolean updated = this.update(Wrappers.<KbDocumentResult>lambdaUpdate()
                .set(KbDocumentResult::getParsedDocumentObjectKey, parsedDocumentObjectKey)
                .set(KbDocumentResult::getParsedMarkdownObjectKey, parsedMarkdownObjectKey)
                .set(KbDocumentResult::getBlockCount, blockCount)
                .set(KbDocumentResult::getWarningCount, warningCount)
                .set(KbDocumentResult::getStage, ResultStage.PARSE)
                .setSql("version = version + 1")
                .eq(KbDocumentResult::getResultKey, resultKey)
                .eq(KbDocumentResult::getDocumentId, documentId)
                .eq(KbDocumentResult::getVersion, expectedResultVersion)
                .eq(KbDocumentResult::getStage, ResultStage.INIT)
                .eq(KbDocumentResult::getDeleted, false));
        if (!updated) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
    }

    @Override
    public void commitChunkManifest(
            String resultKey, Long documentId, Integer expectedResultVersion, ResultStage fromStage,
            String chunkManifestObjectKey, int parentCount, int childCount, int totalCount,
            String contentHash) {
        if (fromStage != ResultStage.PARSE && fromStage != ResultStage.CHUNK && fromStage != ResultStage.PUBLISH) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
        boolean updated = this.update(Wrappers.<KbDocumentResult>lambdaUpdate()
                .set(KbDocumentResult::getChunkManifestObjectKey, chunkManifestObjectKey)
                .set(KbDocumentResult::getParentCount, parentCount)
                .set(KbDocumentResult::getChildCount, childCount)
                .set(KbDocumentResult::getTotalCount, totalCount)
                .set(KbDocumentResult::getContentHash, contentHash)
                .set(KbDocumentResult::getStage, ResultStage.CHUNK)
                .setSql("version = version + 1")
                .eq(KbDocumentResult::getResultKey, resultKey)
                .eq(KbDocumentResult::getDocumentId, documentId)
                .eq(KbDocumentResult::getVersion, expectedResultVersion)
                .eq(KbDocumentResult::getStage, fromStage)
                .eq(KbDocumentResult::getDeleted, false));
        if (!updated) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
    }
}
