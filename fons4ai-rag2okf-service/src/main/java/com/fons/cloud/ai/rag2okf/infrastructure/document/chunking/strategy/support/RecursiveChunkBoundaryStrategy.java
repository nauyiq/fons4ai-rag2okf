package com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.support;

import com.fons.cloud.ai.rag.langchain.document.LangChain4jDocumentSplitter;
import com.fons.cloud.ai.rag.langchain.document.ParentChildDocumentSplitter;
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
 * 使用 Fons4AI 对 LangChain4j recursive splitter 的封装生成候选块。
 *
 * <p>仅在 PARENT_CHILD 模式使用 Fons4AI 的父子包装；递归边界与 overlap 始终由 SDK 计算。</p>
 *
 * @author hongqy
 */
@Component
public class RecursiveChunkBoundaryStrategy implements ChunkBoundaryStrategy {

    @Override
    public ChunkBoundaryType getType() {
        return ChunkBoundaryType.RECURSIVE;
    }

    @Override
    public List<ChunkCandidate> split(ParsedDocument document, ChunkPolicy policy, ChunkingExecutionContext context) {
        LangChain4jChunkingSupport.Parameters parameters = LangChain4jChunkingSupport.parameters(policy, false);
        List<Document> documents = LangChain4jChunkingSupport.toDocuments(document);
        List<TextSegment> segments = new ArrayList<>();
        if (policy.hierarchyType() == ChunkHierarchyType.PARENT_CHILD) {
            ParentChildDocumentSplitter splitter = new ParentChildDocumentSplitter(
                    parameters.parentChunkSize(), parameters.chunkSize(), parameters.overlap());
            documents.forEach(item -> segments.addAll(splitter.split(item)));
        } else {
            LangChain4jDocumentSplitter splitter = new LangChain4jDocumentSplitter(
                    parameters.chunkSize(), parameters.overlap());
            segments.addAll(splitter.split(documents));
        }
        return LangChain4jChunkingSupport.toCandidates(document, segments);
    }
}
