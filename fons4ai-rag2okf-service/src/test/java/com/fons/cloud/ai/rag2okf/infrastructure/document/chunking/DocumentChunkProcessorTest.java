package com.fons.cloud.ai.rag2okf.infrastructure.document.chunking;

import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkBoundaryType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkHierarchyType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkRole;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkManifest;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkPolicy;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedDocument;
import com.fons.cloud.ai.rag2okf.common.model.document.SourceAnchor;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.ChunkBoundaryStrategy;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.ChunkHierarchyStrategy;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.support.FlatChunkHierarchyStrategy;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.support.MarkdownHeaderChunkBoundaryStrategy;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.support.ParentChildChunkHierarchyStrategy;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.support.RecursiveChunkBoundaryStrategy;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.support.SemanticChunkBoundaryStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SDK 优先分块与 Manifest 来源映射集成测试。
 *
 * @author hongqy
 */
class DocumentChunkProcessorTest {

    /** Recursive 的父子关系只由 Fons4AI 临时 metadata 建立，最终清单保留页面来源。 */
    @Test
    void shouldMapRecursiveParentChildSegmentsToTraceableManifest() {
        DocumentChunkProcessor processor = processor();
        ChunkPolicy policy = new ChunkPolicy(ChunkBoundaryType.RECURSIVE, ChunkHierarchyType.PARENT_CHILD,
                Map.of("chunkSize", 100, "parentChunkSize", 400, "overlap", 20));

        ChunkManifest manifest = processor.process(parsedDocument("a".repeat(600)), "result-1", policy,
                new ChunkingExecutionContext(1L, Map.of()));

        assertTrue(manifest.getChunks().stream().anyMatch(item -> item.getRole() == ChunkRole.PARENT));
        assertTrue(manifest.getChunks().stream().anyMatch(item -> item.getRole() == ChunkRole.CHILD));
        assertTrue(manifest.getChunks().stream().allMatch(item -> !item.getAnchorRefs().isEmpty()
                && item.getSourceAnchor().page() != null));
        assertTrue(manifest.getChunks().stream().filter(item -> item.getRole() == ChunkRole.CHILD)
                .allMatch(item -> item.getParentChunkId() != null));
    }

    /** Markdown 标题切分保留原文空行，并在超长标题范围内产生可追溯父子关系。 */
    @Test
    void shouldMapMarkdownHeaderSegmentsWithoutDroppingBlankLines() {
        DocumentChunkProcessor processor = processor();
        ChunkPolicy policy = new ChunkPolicy(ChunkBoundaryType.MARKDOWN_HEADER, ChunkHierarchyType.PARENT_CHILD,
                Map.of("chunkSize", 100, "parentChunkSize", 400, "overlap", 20, "titleLevel", 1));

        ChunkManifest manifest = processor.process(parsedDocument("# 标题\n\n" + "正文\n\n".repeat(150)),
                "result-2", policy, new ChunkingExecutionContext(1L, Map.of()));

        assertTrue(manifest.getChunks().stream().anyMatch(item -> item.getRole() == ChunkRole.PARENT));
        assertTrue(manifest.getChunks().stream().anyMatch(item -> item.getContent().contains("\n\n")));
        assertTrue(manifest.getChunks().stream().allMatch(item -> item.getSourceBlockIds().equals(List.of("block-1"))));
    }

    /** Markdown 的 FLAT 模式只保留可检索的子片段，不能把完整 Parent 也写为可嵌入块。 */
    @Test
    void shouldExcludeMarkdownParentContextFromFlatManifest() {
        DocumentChunkProcessor processor = processor();
        ChunkPolicy policy = new ChunkPolicy(ChunkBoundaryType.MARKDOWN_HEADER, ChunkHierarchyType.FLAT,
                Map.of("chunkSize", 100, "parentChunkSize", 400, "overlap", 20, "titleLevel", 1));

        ChunkManifest manifest = processor.process(parsedDocument("# 标题\n\n" + "正文\n\n".repeat(150)),
                "result-2-flat", policy, new ChunkingExecutionContext(1L, Map.of()));

        assertTrue(manifest.getChunks().stream().allMatch(item -> item.getRole() == ChunkRole.FLAT
                && item.isEmbeddingEligible() && item.getContent().length() <= 100));
    }

    /** FLAT 只改变输出关系，不改变 LangChain4j 的递归边界来源。 */
    @Test
    void shouldMapRecursiveSegmentsToFlatManifest() {
        DocumentChunkProcessor processor = processor();
        ChunkPolicy policy = new ChunkPolicy(ChunkBoundaryType.RECURSIVE, ChunkHierarchyType.FLAT,
                Map.of("chunkSize", 100, "parentChunkSize", 400, "overlap", 20));

        ChunkManifest manifest = processor.process(parsedDocument("b".repeat(300)), "result-3", policy,
                new ChunkingExecutionContext(1L, Map.of()));

        assertFalse(manifest.getChunks().isEmpty());
        assertTrue(manifest.getChunks().stream().allMatch(item -> item.getRole() == ChunkRole.FLAT
                && item.getParentChunkId() == null && item.isEmbeddingEligible()));
        assertEquals(0, manifest.getParentCount());
        assertEquals(0, manifest.getChildCount());
    }

    /** 语义策略缺少冻结的 EMBEDDING 档案必须失败，不能回退为其他边界策略。 */
    @Test
    void shouldFailClosedWhenSemanticEmbeddingProfileIsMissing() {
        List<ChunkBoundaryStrategy> boundaries = List.of(new SemanticChunkBoundaryStrategy(null));
        List<ChunkHierarchyStrategy> hierarchies = List.of(new FlatChunkHierarchyStrategy());
        DocumentChunkProcessor processor = new DocumentChunkProcessor(
                new ChunkBoundaryStrategyRegistry(boundaries), new ChunkHierarchyStrategyRegistry(hierarchies));
        ChunkPolicy policy = new ChunkPolicy(ChunkBoundaryType.SEMANTIC, ChunkHierarchyType.FLAT, Map.of());

        assertThrows(DocumentProcessingException.class,
                () -> processor.process(parsedDocument("语义分块文本"), "result-4", policy,
                        new ChunkingExecutionContext(1L, Map.of())));
    }

    private DocumentChunkProcessor processor() {
        List<ChunkBoundaryStrategy> boundaries = List.of(
                new RecursiveChunkBoundaryStrategy(), new MarkdownHeaderChunkBoundaryStrategy());
        List<ChunkHierarchyStrategy> hierarchies = List.of(
                new FlatChunkHierarchyStrategy(), new ParentChildChunkHierarchyStrategy());
        return new DocumentChunkProcessor(new ChunkBoundaryStrategyRegistry(boundaries),
                new ChunkHierarchyStrategyRegistry(hierarchies));
    }

    private ParsedDocument parsedDocument(String text) {
        ParsedBlock block = new ParsedBlock("block-1", 0, ParsedBlock.PAGE_MARKDOWN, null, text,
                null, null, null, SourceAnchor.page(1), Map.of());
        return new ParsedDocument("document-1", 1, null, null, null, List.of(block), List.of(), null);
    }
}
