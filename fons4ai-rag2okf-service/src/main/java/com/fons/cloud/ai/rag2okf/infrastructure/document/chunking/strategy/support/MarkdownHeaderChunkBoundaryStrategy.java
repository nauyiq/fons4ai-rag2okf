package com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.support;

import com.fons.cloud.ai.rag.langchain.document.MarkdownHeaderParentSplitter;
import com.fons.cloud.ai.rag.langchain.document.MetadataKeyConstants;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkBoundaryType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkHierarchyType;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkPolicy;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedDocument;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.ChunkingExecutionContext;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.ChunkBoundaryStrategy;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.ChunkCandidate;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 使用 Fons4AI Markdown 标题 splitter 生成保留来源 metadata 的候选块。
 *
 * <p>每个 ParsedBlock 独立转换为运行期 Document，来源无法明确还原时由映射支持类拒绝输出。</p>
 *
 * @author hongqy
 */
@Component
public class MarkdownHeaderChunkBoundaryStrategy implements ChunkBoundaryStrategy {

    @Override
    public ChunkBoundaryType getType() {
        return ChunkBoundaryType.MARKDOWN_HEADER;
    }

    @Override
    public List<ChunkCandidate> split(ParsedDocument document, ChunkPolicy policy, ChunkingExecutionContext context) {
        LangChain4jChunkingSupport.Parameters parameters = LangChain4jChunkingSupport.parameters(policy, true);
        MarkdownHeaderParentSplitter splitter = new MarkdownHeaderParentSplitter(parameters.titleLevel(),
                parameters.chunkSize(), parameters.overlap());
        List<TextSegment> segments = new ArrayList<>();
        for (Document item : LangChain4jChunkingSupport.toDocuments(document)) {
            segments.addAll(splitter.split(item));
        }
        if (policy.hierarchyType() == ChunkHierarchyType.FLAT) {
            // 标题 splitter 对超长范围会补充完整 Parent；FLAT 只保留可检索片段，
            // 否则 Parent 会被平铺策略误写为可向量化的重复内容。
            segments.removeIf(this::isParentContext);
        }
        return LangChain4jChunkingSupport.toCandidates(document, segments);
    }

    /**
     * 判断 Fons4AI splitter 输出是否为仅供上下文使用的 Parent。
     *
     * @param segment Fons4AI splitter 的临时结果
     * @return 标记为跳过向量化的完整 Parent 时返回 {@code true}
     */
    private boolean isParentContext(TextSegment segment) {
        Object skipEmbedding = segment.metadata().toMap().get(MetadataKeyConstants.SKIP_EMBEDDING);
        return skipEmbedding instanceof Number number && number.intValue() == 1;
    }
}
