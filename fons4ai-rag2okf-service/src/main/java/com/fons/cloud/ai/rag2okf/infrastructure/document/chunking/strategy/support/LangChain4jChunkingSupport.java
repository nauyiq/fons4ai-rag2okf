package com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.support;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkRole;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkPolicy;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedDocument;
import com.fons.cloud.ai.rag2okf.common.model.document.SourceAnchor;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.ChunkCandidate;
import com.fons.cloud.ai.rag.langchain.document.MetadataKeyConstants;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ParsedDocument 与 LangChain4j 运行时对象之间的可追溯映射支持。
 *
 * <p>本类不实现任何分块算法：它只向输入 Document 写入来源 metadata，随后将
 * TextSegment 的临时层级标识映射为 Rag2OKF 候选块。metadata 缺失即失败，避免猜测来源。</p>
 *
 * @author hongqy
 */
final class LangChain4jChunkingSupport {

    /** LangChain4j 运行时 metadata：来源块业务标识。 */
    static final String SOURCE_BLOCK_ID = "rag2okf.sourceBlockId";

    /** LangChain4j 运行时 metadata：来源块类型。 */
    static final String SOURCE_BLOCK_TYPE = "rag2okf.sourceBlockType";

    /** 缺省子块大小。 */
    static final int DEFAULT_CHUNK_SIZE = 1_400;

    /** 缺省父上下文窗口大小。 */
    static final int DEFAULT_PARENT_CHUNK_SIZE = 4_000;

    /** 缺省重叠大小。 */
    static final int DEFAULT_OVERLAP = 200;

    /** 块大小的最小值。 */
    private static final int MIN_CHUNK_SIZE = 100;

    /** 块大小的最大值。 */
    private static final int MAX_CHUNK_SIZE = 20_000;

    /** 参数名称：子块大小。 */
    static final String CHUNK_SIZE_PARAMETER = "chunkSize";

    /** 参数名称：父窗口大小。 */
    static final String PARENT_CHUNK_SIZE_PARAMETER = "parentChunkSize";

    /** 参数名称：子块重叠大小。 */
    static final String OVERLAP_PARAMETER = "overlap";

    /** 参数名称：Markdown 标题级别。 */
    static final String TITLE_LEVEL_PARAMETER = "titleLevel";

    private LangChain4jChunkingSupport() {
    }

    /** 将可分块 ParsedBlock 转为独立的运行时 Document，确保来源可精确回填。 */
    static List<Document> toDocuments(ParsedDocument parsedDocument) {
        List<Document> documents = new ArrayList<>();
        for (ParsedBlock block : parsedDocument.blocks()) {
            String text = block.text();
            if (text == null || text.isBlank() || block.blockId() == null || block.blockId().isBlank()
                    || block.anchor() == null) {
                continue;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(SOURCE_BLOCK_ID, block.blockId());
            metadata.put(SOURCE_BLOCK_TYPE, block.type());
            documents.add(Document.from(text, Metadata.from(metadata)));
        }
        if (documents.isEmpty()) {
            throw invalid();
        }
        return documents;
    }

    /** 将 SDK/Fons4AI 输出还原为拥有完整来源事实的候选块。 */
    static List<ChunkCandidate> toCandidates(ParsedDocument document, List<TextSegment> segments) {
        Map<String, SourceAnchor> anchorsByBlockId = new LinkedHashMap<>();
        for (ParsedBlock block : document.blocks()) {
            anchorsByBlockId.put(block.blockId(), block.anchor());
        }
        List<ChunkCandidate> candidates = new ArrayList<>();
        for (TextSegment segment : segments) {
            Map<String, Object> metadata = segment.metadata().toMap();
            Object blockIdValue = metadata.get(SOURCE_BLOCK_ID);
            if (!(blockIdValue instanceof String blockId) || blockId.isBlank()
                    || anchorsByBlockId.get(blockId) == null || segment.text().isBlank()) {
                throw invalid();
            }
            ChunkRole role = roleOf(metadata);
            String candidateKey = stringMetadata(metadata, MetadataKeyConstants.CHUNK_ID);
            String parentCandidateKey = stringMetadata(metadata, MetadataKeyConstants.PARENT_CHUNK_ID);
            if (role == ChunkRole.CHILD && (candidateKey == null || parentCandidateKey == null)) {
                throw invalid();
            }
            if (role == ChunkRole.PARENT && candidateKey == null) {
                throw invalid();
            }
            candidates.add(new ChunkCandidate(segment.text(), List.of(blockId),
                    List.of(anchorsByBlockId.get(blockId)), role, candidateKey, parentCandidateKey));
        }
        if (candidates.isEmpty()) {
            throw invalid();
        }
        return candidates;
    }

    /** 解析 recursive 和 markdown-header 共用的三项数值参数。 */
    static Parameters parameters(ChunkPolicy policy, boolean markdown) {
        Map<String, Object> values = policy.parameters() == null ? Map.of() : policy.parameters();
        List<String> allowed = markdown
                ? List.of(CHUNK_SIZE_PARAMETER, PARENT_CHUNK_SIZE_PARAMETER, OVERLAP_PARAMETER,
                        TITLE_LEVEL_PARAMETER)
                : List.of(CHUNK_SIZE_PARAMETER, PARENT_CHUNK_SIZE_PARAMETER, OVERLAP_PARAMETER);
        if (values.keySet().stream().anyMatch(key -> !allowed.contains(key))) {
            throw policyInvalid();
        }
        int chunkSize = integer(values, CHUNK_SIZE_PARAMETER, DEFAULT_CHUNK_SIZE);
        int parentChunkSize = integer(values, PARENT_CHUNK_SIZE_PARAMETER, DEFAULT_PARENT_CHUNK_SIZE);
        int overlap = integer(values, OVERLAP_PARAMETER, DEFAULT_OVERLAP);
        int titleLevel = markdown ? integer(values, TITLE_LEVEL_PARAMETER, 3) : 0;
        if (chunkSize < MIN_CHUNK_SIZE || chunkSize > MAX_CHUNK_SIZE
                || parentChunkSize < chunkSize || parentChunkSize > MAX_CHUNK_SIZE
                || overlap < 0 || overlap >= chunkSize || (markdown && (titleLevel < 1 || titleLevel > 6))) {
            throw policyInvalid();
        }
        return new Parameters(chunkSize, parentChunkSize, overlap, titleLevel);
    }

    private static ChunkRole roleOf(Map<String, Object> metadata) {
        if (metadata.containsKey(MetadataKeyConstants.PARENT_CHUNK_ID)) {
            return ChunkRole.CHILD;
        }
        Object skipEmbedding = metadata.get(MetadataKeyConstants.SKIP_EMBEDDING);
        return skipEmbedding instanceof Number number && number.intValue() == 1
                ? ChunkRole.PARENT : ChunkRole.FLAT;
    }

    private static String stringMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value instanceof String string && !string.isBlank() ? string : null;
    }

    private static int integer(Map<String, Object> values, String key, int defaultValue) {
        Object value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Number number)) {
            throw policyInvalid();
        }
        return number.intValue();
    }

    private static DocumentProcessingException invalid() {
        return new DocumentProcessingException(Rag2OkfResultCode.PARSED_DOCUMENT_INVALID);
    }

    private static DocumentProcessingException policyInvalid() {
        return new DocumentProcessingException(Rag2OkfResultCode.CHUNK_POLICY_INVALID);
    }

    /** 已校验的 SDK 分块参数。 */
    record Parameters(int chunkSize, int parentChunkSize, int overlap, int titleLevel) {
    }
}
