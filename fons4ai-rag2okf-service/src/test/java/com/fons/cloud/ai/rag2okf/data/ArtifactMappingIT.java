package com.fons.cloud.ai.rag2okf.data;

import com.fons.cloud.ai.rag2okf.common.response.DocumentDetailResponse;
import com.fons.cloud.ai.rag2okf.common.response.DocumentSummaryResponse;
import com.fons.cloud.ai.rag2okf.common.response.DocumentUploadResponse;
import com.fons.cloud.ai.rag2okf.common.response.ParsePreviewResponse;
import com.fons.cloud.ai.rag2okf.common.response.ChunkPreviewResponse;
import com.fons.cloud.ai.rag2okf.domain.parsing.SourceAnchor;
import com.fons.cloud.ai.rag2okf.domain.publication.PublicationProjectionPort.ChunkProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档生命周期字段映射契约测试（T021）。
 *
 * <p>验证技术设计 §4.2 字段映射契约：文件名/类型/状态/SourceAnchor/发布投影映射正确，
 * 敏感字段不进响应，向量字段存在且维度契约可追溯。
 *
 * <p>该测试不替代真实 MySQL/ES/MinIO 集成测试，只负责在没有容器的构建环境中尽早发现
 * 字段映射回归、敏感字段泄露或向量字段缺失。
 *
 * <p>关联 AC：AC-012、AC-021、AC-022、AC-024、AC-027、AC-035。
 *
 * @author hongqy
 */
@Execution(ExecutionMode.CONCURRENT)
class ArtifactMappingIT {

    /**
     * AC-012：解析预览响应必须包含结构化内容、来源定位（SourceAnchor）和分块调试预览。
     */
    @Test
    void parsePreviewShouldExposeStructuredBlocksAndSourceAnchor() {
        List<String> blockFields = fieldNames(ParsePreviewResponse.ParsedBlockView.class);
        assertThat(blockFields).contains("index", "content", "sourceAnchor");

        List<String> anchorFields = fieldNames(SourceAnchor.class);
        assertThat(anchorFields).contains("locatorType", "page", "blockIndex");
    }

    /**
     * AC-012 / AC-021：分块预览响应必须包含分块序号、内容和 parent 关系，
     * 支持重新分块期间的调试预览。
     */
    @Test
    void chunkPreviewShouldExposeIndexContentAndParentLinkage() {
        List<String> chunkFields = fieldNames(ChunkPreviewResponse.ChunkView.class);
        assertThat(chunkFields).contains("index", "content", "parentChunkId", "skipEmbedding");
    }

    /**
     * AC-021 / AC-022：文档详情响应必须包含 parseStatus / publishStatus / hasActivePublication，
     * 用于判断"重新分块期间继续使用当前已发布内容"的边界。
     */
    @Test
    void documentDetailShouldExposeParseAndPublishStatusForActivePublicationBoundary() {
        List<String> fields = fieldNames(DocumentDetailResponse.class);
        assertThat(fields).contains(
                "documentKey", "displayName", "parseStatus", "publishStatus", "hasActivePublication");
    }

    /**
     * AC-022 / AC-024：文档摘要和上传响应必须包含 currentFileToken，
     * 用于并发上传/重试时的 CAS 乐观锁控制，避免重复当前文件。
     */
    @Test
    void documentSummariesShouldExposeCurrentFileTokenForCasControl() {
        assertThat(fieldNames(DocumentSummaryResponse.class)).contains("currentFileToken");
        assertThat(fieldNames(DocumentUploadResponse.class)).contains("currentFileToken");
    }

    /**
     * AC-035 / D-007：发布投影必须包含 vector 字段，承载 dense_vector 向量数据，
     * 用于 V2 RAG 混合检索。
     */
    @Test
    void chunkProjectionShouldContainVectorFieldForDenseVectorSchema() {
        List<String> fields = fieldNames(ChunkProjection.class);
        assertThat(fields).contains(
                "chunkKey", "parentChunkKey", "chunkLevel", "ordinal",
                "rawText", "displayText", "embeddingText",
                "titlePath", "sourceLocatorType", "pageNumber", "startOffset", "endOffset",
                "vector");
    }

    /**
     * D-007：ChunkProjection.chunkLevel 必须支持 PARENT 和 CHILD 两种层级，
     * 与 ChunkManifest 的 parentCount/childCount 对应。
     */
    @Test
    void chunkProjectionLevelConstantsShouldCoverParentAndChild() throws Exception {
        String parentLevel = (String) ChunkProjection.class.getField("LEVEL_PARENT").get(null);
        String childLevel = (String) ChunkProjection.class.getField("LEVEL_CHILD").get(null);

        assertThat(parentLevel).isEqualTo("PARENT");
        assertThat(childLevel).isEqualTo("CHILD");
    }

    /**
     * §4.2：文档响应不得包含任何持久化内部主键（id）、密文、nonce 或 key_version 字段，
     * 避免敏感字段通过响应泄露。
     */
    @Test
    void documentResponsesShouldNotLeakInternalIdsOrSensitiveFields() {
        List<String> sensitivePatterns = List.of(
                "id", "passwordHash", "password", "apiKeyCiphertext", "apiKeyNonce",
                "keyVersion", "apiKey", "ciphertext", "nonce");

        assertNoSensitiveFields(DocumentDetailResponse.class, sensitivePatterns);
        assertNoSensitiveFields(DocumentSummaryResponse.class, sensitivePatterns);
        assertNoSensitiveFields(DocumentUploadResponse.class, sensitivePatterns);
        assertNoSensitiveFields(ParsePreviewResponse.class, sensitivePatterns);
        assertNoSensitiveFields(ChunkPreviewResponse.class, sensitivePatterns);
    }

    private List<String> fieldNames(Class<?> recordClass) {
        if (recordClass.isRecord()) {
            return Arrays.stream(recordClass.getRecordComponents())
                    .map(RecordComponent::getName)
                    .toList();
        }
        return Arrays.stream(recordClass.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .toList();
    }

    private void assertNoSensitiveFields(Class<?> responseClass, List<String> sensitivePatterns) {
        List<String> fields = fieldNames(responseClass);
        List<String> leaked = fields.stream()
                .filter(field -> sensitivePatterns.stream().anyMatch(field::equalsIgnoreCase))
                .toList();
        assertThat(leaked)
                .as("%s 不得包含敏感字段: %s", responseClass.getSimpleName(), leaked)
                .isEmpty();
    }
}
