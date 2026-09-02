package com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.support;

import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkHierarchyType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkRole;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkManifest;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkManifestItem;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkPolicy;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedDocument;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.ChunkCandidate;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.ChunkHierarchyStrategy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 将每个候选块独立输出为可嵌入的平铺块。
 *
 * @author hongqy
 */
@Component
public class FlatChunkHierarchyStrategy implements ChunkHierarchyStrategy {

    @Override
    public ChunkHierarchyType getType() {
        return ChunkHierarchyType.FLAT;
    }

    @Override
    public ChunkManifest organize(ParsedDocument document, String resultKey, ChunkPolicy policy,
                                  List<ChunkCandidate> candidates) {
        List<ChunkManifestItem> items = new ArrayList<>();
        for (ChunkCandidate candidate : candidates) {
            ChunkManifestItem item = new ChunkManifestItem();
            item.setOrdinal(items.size());
            item.setRole(ChunkRole.FLAT);
            item.setEmbeddingEligible(true);
            item.setSourceBlockIds(candidate.sourceBlockIds());
            item.setSourceAnchor(candidate.sourceAnchor());
            item.setAnchorRefs(candidate.anchorRefs());
            item.setContent(candidate.content());
            item.setContentHash(ChunkManifestSupport.hash(candidate.content()));
            item.setChunkId(ChunkManifestSupport.chunkId(
                    resultKey, item.getOrdinal(), item.getRole().name(), item.getContent()));
            items.add(item);
        }
        ChunkManifest manifest = new ChunkManifest();
        manifest.setDocumentKey(document.documentKey());
        manifest.setResultKey(resultKey);
        manifest.setChunkPolicy(policy);
        manifest.setChunks(items);
        manifest.setParentCount(0);
        manifest.setChildCount(0);
        manifest.setTotalCount(items.size());
        return manifest;
    }
}
