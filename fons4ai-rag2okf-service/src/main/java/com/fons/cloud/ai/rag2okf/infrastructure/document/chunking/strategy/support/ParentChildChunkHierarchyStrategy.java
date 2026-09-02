package com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.support;

import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkHierarchyType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkRole;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkBoundaryType;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkManifest;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkManifestItem;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkPolicy;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedDocument;
import com.fons.cloud.ai.rag2okf.common.model.document.SourceAnchor;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.ChunkCandidate;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.ChunkHierarchyStrategy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将候选块的运行期父子 metadata 映射为持久化的上下文 Parent 与可嵌入 Child。
 *
 * <p>语义候选块只在至少两个相邻块可组成真实上下文时创建 Parent，单块保持 FLAT。</p>
 *
 * @author hongqy
 */
@Component
public class ParentChildChunkHierarchyStrategy implements ChunkHierarchyStrategy {

    /** 语义候选块构造上下文父块时的最大字符数。 */
    private static final int SEMANTIC_PARENT_CONTEXT_SIZE = 4_000;

    @Override
    public ChunkHierarchyType getType() {
        return ChunkHierarchyType.PARENT_CHILD;
    }

    @Override
    public ChunkManifest organize(ParsedDocument document, String resultKey, ChunkPolicy policy,
                                  List<ChunkCandidate> candidates) {
        List<ChunkManifestItem> items = new ArrayList<>();
        List<ChunkCandidate> effectiveCandidates = organizeSemanticContext(policy, candidates);
        Map<String, String> parentIds = new LinkedHashMap<>();
        for (ChunkCandidate candidate : effectiveCandidates) {
            if (candidate.role() == ChunkRole.PARENT) {
                ChunkManifestItem parent = createItem(resultKey, items.size(), ChunkRole.PARENT,
                        null, false, candidate);
                items.add(parent);
                parentIds.put(candidate.candidateKey(), parent.getChunkId());
                continue;
            }
            if (candidate.role() == ChunkRole.CHILD) {
                String parentChunkId = parentIds.get(candidate.parentCandidateKey());
                if (parentChunkId == null) {
                    throw new DocumentProcessingException(Rag2OkfResultCode.PARSED_DOCUMENT_INVALID);
                }
                items.add(createItem(resultKey, items.size(), ChunkRole.CHILD,
                        parentChunkId, true, candidate));
                continue;
            }
            items.add(createItem(resultKey, items.size(), ChunkRole.FLAT,
                    null, true, candidate));
        }
        ChunkManifest manifest = new ChunkManifest();
        manifest.setDocumentKey(document.documentKey());
        manifest.setResultKey(resultKey);
        manifest.setChunkPolicy(policy);
        manifest.setChunks(items);
        manifest.setParentCount((int) items.stream().filter(item -> item.getRole() == ChunkRole.PARENT).count());
        manifest.setChildCount((int) items.stream().filter(item -> item.getRole() == ChunkRole.CHILD).count());
        manifest.setTotalCount(items.size());
        return manifest;
    }

    /**
     * 语义策略不复制 SDK 的边界算法，而是在相邻的语义候选块间建立有实际上下文增量的父块。
     *
     * <p>单个候选块没有额外上下文，保留为 FLAT，避免生成内容完全重复的 Parent/Child。</p>
     */
    private List<ChunkCandidate> organizeSemanticContext(ChunkPolicy policy, List<ChunkCandidate> candidates) {
        if (policy.boundaryType() != ChunkBoundaryType.SEMANTIC
                || candidates.stream().anyMatch(candidate -> candidate.role() != ChunkRole.FLAT)) {
            return candidates;
        }
        List<ChunkCandidate> result = new ArrayList<>();
        List<ChunkCandidate> group = new ArrayList<>();
        int contentLength = 0;
        int groupIndex = 0;
        for (ChunkCandidate candidate : candidates) {
            int nextLength = contentLength + candidate.content().length();
            if (!group.isEmpty() && nextLength > SEMANTIC_PARENT_CONTEXT_SIZE) {
                appendSemanticGroup(result, group, groupIndex++);
                group = new ArrayList<>();
                contentLength = 0;
            }
            group.add(candidate);
            contentLength += candidate.content().length();
        }
        appendSemanticGroup(result, group, groupIndex);
        return result;
    }

    /** 将一组相邻语义块转换为一个上下文 Parent 和多个精确 Child。 */
    private void appendSemanticGroup(List<ChunkCandidate> target, List<ChunkCandidate> group, int groupIndex) {
        if (group.size() == 1) {
            target.add(group.getFirst());
            return;
        }
        String parentKey = "semantic-parent-" + groupIndex;
        String content = group.stream().map(ChunkCandidate::content).collect(Collectors.joining("\n"));
        List<String> sourceBlockIds = group.stream().flatMap(candidate -> candidate.sourceBlockIds().stream())
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
        List<SourceAnchor> anchorRefs = group.stream()
                .flatMap(candidate -> candidate.anchorRefs().stream())
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
        target.add(new ChunkCandidate(content, sourceBlockIds, anchorRefs,
                ChunkRole.PARENT, parentKey, null));
        for (ChunkCandidate candidate : group) {
            target.add(new ChunkCandidate(candidate.content(), candidate.sourceBlockIds(), candidate.anchorRefs(),
                    ChunkRole.CHILD, null, parentKey));
        }
    }

    private ChunkManifestItem createItem(String resultKey, int ordinal, ChunkRole role,
                                         String parentChunkId, boolean embeddingEligible,
                                         ChunkCandidate candidate) {
        ChunkManifestItem item = new ChunkManifestItem();
        item.setOrdinal(ordinal);
        item.setRole(role);
        item.setParentChunkId(parentChunkId);
        item.setEmbeddingEligible(embeddingEligible);
        item.setSourceBlockIds(candidate.sourceBlockIds());
        item.setSourceAnchor(candidate.sourceAnchor());
        item.setAnchorRefs(candidate.anchorRefs());
        item.setContent(candidate.content());
        item.setContentHash(ChunkManifestSupport.hash(candidate.content()));
        item.setChunkId(ChunkManifestSupport.chunkId(resultKey, ordinal, role.name(), item.getContent()));
        return item;
    }
}
