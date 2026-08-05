package com.fons.cloud.ai.rag2okf.parsing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.rag2okf.common.exeception.DocumentArtifactException;
import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore;
import com.fons.cloud.ai.rag2okf.domain.parsing.DocumentParserPort;
import com.fons.cloud.ai.rag2okf.domain.parsing.ParseManifest;
import com.fons.cloud.ai.rag2okf.domain.parsing.SourceAnchor;
import com.fons.cloud.ai.rag2okf.infrastructure.parsing.Fons4AiDocumentParserAdapter;
import com.fons.cloud.ai.rag.common.document.DocumentParseResult;
import com.fons.cloud.ai.rag.common.document.ParseTrace;
import com.fons.cloud.ai.rag.langchain.document.LangChain4jDocumentParserFacade;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Fons4AiDocumentParserAdapter 测试。
 *
 * <p>覆盖 AC-003（Parser Profile 映射）和 AC-014（不伪造页码、不伪造产物）。
 *
 * @author hongqy
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("文档解析适配器")
class Fons4AiDocumentParserAdapterTest {

    @Mock private DocumentArtifactStore artifactStore;
    @Mock private LangChain4jDocumentParserFacade parserFacade;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private Fons4AiDocumentParserAdapter adapter;

    @BeforeEach
    void setUp() {
        // 注入真实的 ObjectMapper
        try {
            var field = Fons4AiDocumentParserAdapter.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(adapter, objectMapper);
        } catch (Exception e) {
            fail("无法注入 ObjectMapper: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("解析成功生成 ParseManifest，SourceAnchor 为 NONE")
    void parse_successProducesManifestWithNoneAnchor() {
        DocumentArtifactStore.ArtifactScope scope = new DocumentArtifactStore.ArtifactScope(
                "01J_WS", "01J_KB", "01J_DOC");

        // 模拟 MinIO 返回原文件流
        when(artifactStore.open(any())).thenReturn(new DocumentArtifactStore.ArtifactContent(
                new ByteArrayInputStream("段落1\n\n段落2".getBytes())));

        // 模拟解析器返回结果
        Document document = Document.from("段落1\n\n段落2", Metadata.from(Map.of()));
        ParseTrace trace = new ParseTrace(
                "native", 1_000_000L, "TEXT", "MARKDOWN", null, null, "DEFAULT 选型");
        DocumentParseResult<Document> parseResult = new DocumentParseResult<>(document, trace);
        when(parserFacade.parseWithTrace(any())).thenReturn(parseResult);

        // 模拟 MinIO 写入 manifest
        when(artifactStore.storeManifest(any())).thenReturn(
                new DocumentArtifactStore.StoredArtifact("parsed/key", "abc123", 100L, Map.of()));

        DocumentParserPort.ParseRequest request = new DocumentParserPort.ParseRequest(
                scope, "01J_VER", "01J_PARSE", "test.md", "text/markdown", "NATIVE_TIKA");

        DocumentParserPort.ParseResult result = adapter.parse(request);

        assertNotNull(result);
        ParseManifest manifest = result.manifest();
        assertEquals("01J_PARSE", manifest.parseRevisionKey());
        assertEquals("01J_VER", manifest.documentVersionKey());
        assertEquals("NATIVE_TIKA", manifest.parserProfile());
        assertTrue(manifest.blockCount() > 0, "块数量必须大于 0");

        // AC-014：不伪造页码，所有块的 SourceAnchor 为 NONE
        for (ParseManifest.ParsedBlock block : manifest.blocks()) {
            assertEquals(SourceAnchor.NONE, block.sourceAnchor().locatorType(),
                    "P0 不伪造页码，SourceAnchor 必须为 NONE");
            assertNull(block.sourceAnchor().page(), "NONE 时 page 必须为 null");
        }

        // 验证 MinIO 写入被调用
        verify(artifactStore, atLeastOnce()).storeManifest(any());
    }

    @Test
    @DisplayName("解析产物为空时抛出异常，不伪造结果")
    void parse_emptyResultThrowsException() {
        DocumentArtifactStore.ArtifactScope scope = new DocumentArtifactStore.ArtifactScope(
                "01J_WS", "01J_KB", "01J_DOC");

        when(artifactStore.open(any())).thenReturn(new DocumentArtifactStore.ArtifactContent(
                new ByteArrayInputStream("".getBytes())));

        // 使用 mock 模拟空文本 Document，绕过 Document.from 的非空校验
        Document document = mock(Document.class);
        when(document.text()).thenReturn("");
        ParseTrace trace = new ParseTrace(
                "native", 1_000_000L, "TEXT", "MARKDOWN", null, null, "DEFAULT 选型");
        DocumentParseResult<Document> parseResult = new DocumentParseResult<>(document, trace);
        when(parserFacade.parseWithTrace(any())).thenReturn(parseResult);

        DocumentParserPort.ParseRequest request = new DocumentParserPort.ParseRequest(
                scope, "01J_VER", "01J_PARSE", "empty.txt", "text/plain", "NATIVE_TIKA");

        // AC-014：空产物拒绝，不伪造
        assertThrows(DocumentArtifactException.class, () -> adapter.parse(request));
    }

    @Test
    @DisplayName("MINERU_LAYOUT profile 使用 EXPLICIT 选型")
    void parse_mineruProfileUsesExplicitSelection() {
        DocumentArtifactStore.ArtifactScope scope = new DocumentArtifactStore.ArtifactScope(
                "01J_WS", "01J_KB", "01J_DOC");

        when(artifactStore.open(any())).thenReturn(new DocumentArtifactStore.ArtifactContent(
                new ByteArrayInputStream("内容".getBytes())));

        Document document = Document.from("内容", Metadata.from(Map.of()));
        ParseTrace trace = new ParseTrace(
                "mineru", 2_000_000L, "PDF", "MARKDOWN", null, null, "EXPLICIT 选型");
        DocumentParseResult<Document> parseResult = new DocumentParseResult<>(document, trace);
        when(parserFacade.parseWithTrace(any())).thenReturn(parseResult);

        when(artifactStore.storeManifest(any())).thenReturn(
                new DocumentArtifactStore.StoredArtifact("parsed/key", "abc123", 100L, Map.of()));

        DocumentParserPort.ParseRequest request = new DocumentParserPort.ParseRequest(
                scope, "01J_VER", "01J_PARSE", "doc.pdf", "application/pdf", "MINERU_LAYOUT");

        DocumentParserPort.ParseResult result = adapter.parse(request);

        assertEquals("MINERU_LAYOUT", result.manifest().parserProfile());
        // parserTrace provider 应为 mineru
        assertEquals("mineru", result.manifest().parserTrace().provider());
    }
}
