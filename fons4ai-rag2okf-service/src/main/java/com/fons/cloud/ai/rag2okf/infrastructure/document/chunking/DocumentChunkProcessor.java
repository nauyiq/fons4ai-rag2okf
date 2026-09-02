package com.fons.cloud.ai.rag2okf.infrastructure.document.chunking;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkManifest;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkManifestItem;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkPolicy;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedDocument;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.ChunkCandidate;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.support.ChunkManifestSupport;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 协调边界策略、层级策略与清单不变量校验的分块技术组件。
 *
 * <p>该组件只消费已持久化的 ParsedDocument；不负责重新解析源文件或调用 OCR。</p>
 *
 * @author hongqy
 */
@Component
public class DocumentChunkProcessor {

    /** 边界策略注册表。 */
    private final ChunkBoundaryStrategyRegistry boundaryRegistry;

    /** 层级策略注册表。 */
    private final ChunkHierarchyStrategyRegistry hierarchyRegistry;

    /**
     * 创建分块处理器。
     *
     * @param boundaryRegistry 边界策略注册表
     * @param hierarchyRegistry 层级策略注册表
     */
    public DocumentChunkProcessor(ChunkBoundaryStrategyRegistry boundaryRegistry,
                                  ChunkHierarchyStrategyRegistry hierarchyRegistry) {
        this.boundaryRegistry = boundaryRegistry;
        this.hierarchyRegistry = hierarchyRegistry;
    }

    /**
     * 按冻结策略生成并校验分块清单。
     *
     * @param document 已校验解析制品
     * @param resultKey 当前解析结果业务标识
     * @param policy 冻结策略
     * @param context 安全执行上下文
     * @return 合法的分块清单
     */
    public ChunkManifest process(ParsedDocument document, String resultKey, ChunkPolicy policy,
                                 ChunkingExecutionContext context) {
        if (document == null || resultKey == null || resultKey.isBlank() || policy == null
                || policy.boundaryType() == null || policy.hierarchyType() == null) {
            throw new DocumentProcessingException(Rag2OkfResultCode.CHUNK_POLICY_INVALID);
        }
        List<ChunkCandidate> candidates = boundaryRegistry.require(policy.boundaryType())
                .split(document, policy, context);
        ChunkManifest manifest = hierarchyRegistry.require(policy.hierarchyType())
                .organize(document, resultKey, policy, candidates);
        manifest.setContentHash(ChunkManifestSupport.hash(manifest.getChunks().stream()
                .map(ChunkManifestSupport::manifestContent)
                .collect(Collectors.joining("\n"))));
        validate(manifest);
        return manifest;
    }

    /** 校验清单引用、角色、计数和摘要不变量。 */
    public void validate(ChunkManifest manifest) {
        if (manifest == null || manifest.getDocumentKey() == null || manifest.getDocumentKey().isBlank()
                || manifest.getResultKey() == null || manifest.getResultKey().isBlank()
                || manifest.getChunks() == null || manifest.getChunks().isEmpty()) {
            throw invalid();
        }
        Set<String> ids = new HashSet<>();
        int parents = 0;
        int children = 0;
        for (int index = 0; index < manifest.getChunks().size(); index++) {
            ChunkManifestItem item = manifest.getChunks().get(index);
            if (item == null || item.getOrdinal() != index || item.getChunkId() == null
                    || item.getChunkId().isBlank() || !ids.add(item.getChunkId())
                    || item.getContent() == null || item.getContent().isBlank()
                    || item.getContentHash() == null || item.getContentHash().isBlank()
                    || item.getSourceAnchor() == null || item.getAnchorRefs() == null
                    || item.getAnchorRefs().isEmpty() || item.getSourceBlockIds() == null
                    || item.getSourceBlockIds().isEmpty() || item.getRole() == null) {
                throw invalid();
            }
            if (!ChunkManifestSupport.hash(item.getContent()).equals(item.getContentHash())) {
                throw invalid();
            }
            switch (item.getRole()) {
                case FLAT -> {
                    if (item.getParentChunkId() != null || !item.isEmbeddingEligible()) {
                        throw invalid();
                    }
                }
                case PARENT -> {
                    parents++;
                    if (item.getParentChunkId() != null || item.isEmbeddingEligible()) {
                        throw invalid();
                    }
                }
                case CHILD -> {
                    children++;
                    if (item.getParentChunkId() == null || !ids.contains(item.getParentChunkId())
                            || !item.isEmbeddingEligible()) {
                        throw invalid();
                    }
                }
            }
        }
        if (manifest.getTotalCount() != manifest.getChunks().size()
                || manifest.getParentCount() != parents || manifest.getChildCount() != children
                || manifest.getContentHash() == null || manifest.getContentHash().isBlank()) {
            throw invalid();
        }
    }

    private DocumentProcessingException invalid() {
        return new DocumentProcessingException(Rag2OkfResultCode.PARSED_DOCUMENT_INVALID);
    }
}
